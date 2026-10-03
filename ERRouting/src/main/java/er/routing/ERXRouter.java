package er.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.routing.core.Converters;
import er.routing.core.Host;
import er.routing.core.RouteOption;
import er.routing.core.RouteCondition;
import er.routing.core.RouteRequest;
import er.routing.core.Router;
import er.routing.core.TrailingSlash;
import er.extensions.appserver.ERXNotification;
import er.extensions.routes.RouteClaims;
import er.extensions.appserver.ERXApplication;
import er.extensions.foundation.ERXProperties;
import er.extensions.routes.RouteTable;

/**
 * EXPERIMENTAL (route-links branch). The router, see docs/ROUTE_LINKS.md and #178: routes with named parameters,
 * conditions (host, methods), trailing slash policies, groups, and tables ranked so an application overrides a plugin.
 *
 * Kept beside the existing route table until they converge: the default router ({@link #defaultRouter()}) is mapped into
 * it as one route, which declines what the router has no route for, so the table's fallback and not found still apply.
 *
 * <pre>
 * final RouteGroup routes = ERXRouter.defaultRouter().application();
 * routes.map( "/items/{id}", ri -&gt; ItemPage.page( ri, ri.parameter( "id" ) ) );
 * </pre>
 */

public class ERXRouter {

	private static final Logger logger = LoggerFactory.getLogger( ERXRouter.class );

	/**
	 * What the core router routes to: a handler, and the group it was mapped in (for its wrapping)
	 */
	record Mapped( RouteHandler handler, RouteGroup group, Linkable route, Class<? extends Record> parametersClass, CrossSite crossSite ) {}

	private final Router<Mapped> _router;
	private final Converters _converters = new Converters( type -> undeclared( "the converter for " + type.getName() ) );
	private final Map<String, RouteGroup> _namedGroups = new ConcurrentHashMap<>();
	private final Map<String, List<Consumer<RouteGroup>>> _pendingJoins = new LinkedHashMap<>();
	private int _loggedOverrides;

	/**
	 * True once the joins are checked: at launch for the default router, otherwise on the first request
	 */
	private volatile boolean _joinsChecked;

	/**
	 * The first route or converter added outside a declaration ({@link #declare(Function)}), which declaring the routes
	 * again wouldn't bring back, null if there's none
	 */
	private volatile String _undeclared;
	private RouteGroup _application;

	/**
	 * Whether routes are declared again when their classes change. Development's default.
	 */
	public static final String RELOAD_PROPERTY = "er.routing.reload";

	private static RouteDeclarations _declarations;

	/**
	 * @return The application's router, created on first use and mapped into the default route table then, so an
	 *         application declares its routes and has nothing to set up. Called by a route declaration, the router it's
	 *         declaring into. The router is replaced when the routes are declared again (in development), so read it
	 *         each time, don't keep it.
	 */
	public static ERXRouter defaultRouter() {
		final ERXRouter declaring = RouteDeclarations.DECLARING.get();
		return declaring != null ? declaring : declarations().router();
	}

	/**
	 * Declares routes in the application's router: the declaration maps them, and returns the object holding them, for
	 * links. In development it runs again when its classes change (or a route's record's), so a changed route is there
	 * without a restart.
	 *
	 * <pre>
	 * _routes = ERXRouter.declare( BookclubRoutes::new ); // BookclubRoutes( ERXRouter router ) maps the routes
	 * </pre>
	 *
	 * Declarations run again in the order they were first made (an application and its plugins), into a new router that
	 * replaces the current one once all of them succeed. One that fails leaves the current routes in place, and routed
	 * requests answer with why until the routes are declared again. Set {@value #RELOAD_PROPERTY} to false to declare
	 * them once.
	 *
	 * @return The routes declared: their holder, for links, is {@link Declared#get()}
	 */
	public static <T> Declared<T> declare( final Function<ERXRouter, T> declaration ) {
		return declarations().declare( declaration );
	}

	private static synchronized RouteDeclarations declarations() {
		if( _declarations == null ) {
			final boolean reload = ERXProperties.booleanForKeyWithDefault( RELOAD_PROPERTY, ERXApplication.isDevelopmentModeSafe() );
			_declarations = new RouteDeclarations( ERXRouter::new, reload ? new ClassChanges() : RouteDeclarations.Changes.NONE );
			RouteTable.defaultRouteTable().map( "/*", new DefaultRouter() );

			// By the time the application is about to listen for requests, the groups plugins joined are named. The public
			// address is read then too, so a value that isn't one stops the launch.
			ERXNotification.ApplicationWillFinishLaunchingNotification.addObserver( notification -> {
				_declarations.router().checkJoins();
				PublicAddress.configured();
			} );
		}

		return _declarations;
	}

	/**
	 * The default router in the route table: the current one, its routes declared again first if their classes changed
	 */
	private static class DefaultRouter implements er.extensions.routes.RouteHandler, RouteClaims {

		@Override
		public WOActionResults handle( final er.extensions.routes.RouteInvocation invocation ) {
			_declarations.declareAgainIfChanged();
			return _declarations.router().handle( invocation );
		}

		@Override
		public boolean claims( final String url ) {
			return _declarations.router()._router.hasRouteFor( url );
		}
	}

	/**
	 * A router whose routes ignore the trailing slash unless they set their own policy
	 */
	public ERXRouter() {
		this( TrailingSlash.IGNORE );
	}

	public ERXRouter( final TrailingSlash trailingSlash ) {
		_router = new Router<>( trailingSlash );
	}

	/**
	 * @return Every route, in precedence order: its pattern, conditions, trailing slash policy and table (for a page
	 *         listing them, say)
	 */
	public List<RouteDescription> routes() {
		return _router.routes().stream().map( e -> new RouteDescription( e.path().source(), e.conditions(), e.trailingSlash(), e.table(), e.handler().route(), e.handler().parametersClass(), e.handler().crossSite(), e.handler().route() instanceof Route<?> r && r.reportsFields() ) ).toList();
	}

	/**
	 * @return The converters for route parameters: register an application's own types here, before declaring routes
	 *         taking them
	 */
	public Converters converters() {
		return _converters;
	}

	/**
	 * @return The application's routes: a table ranked before every other, whenever it's created, so the application's
	 *         routes override a plugin's
	 */
	public synchronized RouteGroup application() {
		if( _application == null ) {
			_application = new RouteGroup( this, _router.table( "application", Integer.MIN_VALUE ), null, "", List.of() );
		}

		return _application;
	}

	/**
	 * @return The routes of a new table, for a plugin, ranked below the application's and the tables created before it
	 *         (plugins in dependency order). The same route in a higher ranked table overrides it.
	 */
	public RouteGroup table( final String name ) {
		return new RouteGroup( this, _router.table( name ), null, "", List.of() );
	}

	/**
	 * @return The first route or converter added outside a declaration, null if there's none
	 */
	String undeclared() {
		return _undeclared;
	}

	/**
	 * Notes something added to the router, unless it's added by a declaration into it
	 */
	private void undeclared( final String what ) {
		if( _undeclared == null && RouteDeclarations.DECLARING.get() != this ) {
			_undeclared = what;
		}
	}

	/**
	 * @return true if one of the routes answers the host (without a port), or it's the public address's
	 */
	boolean isOwnHost( final String host ) {
		final PublicAddress.Origin publicAddress = PublicAddress.configured();

		if( publicAddress != null && publicAddress.host().equals( host ) ) {
			return true;
		}

		final RouteRequest request = new RouteRequest( "GET", host, "/" );
		return _router.routes().stream().flatMap( e -> e.conditions().stream() ).filter( Host.class::isInstance ).anyMatch( h -> h.test( request ) instanceof RouteCondition.Satisfied );
	}

	/**
	 * Maps a route in a table, logging any override it makes
	 */
	void map( final Router<Mapped>.Table table, final String pattern, final Mapped mapped, final List<RouteOption> options ) {
		refuseHandlerKeyCollision( pattern );

		undeclared( "the route " + pattern );
		table.map( pattern, mapped, options.stream().filter( option -> !(option instanceof RouteBehavior) ).toArray( RouteOption[]::new ) );

		final var overrides = _router.overrides();

		for( ; _loggedOverrides < overrides.size(); _loggedOverrides++ ) {
			final var override = overrides.get( _loggedOverrides );
			logger.info( "The route {} overrides {}", override.route(), override.overridden() );
		}
	}

	/**
	 * A route whose first segment is a registered request handler key ({@code /wa/…}) can never be reached: the handler
	 * gets those URLs. Refused when it's mapped, as the route table does, rather than silently never matching.
	 */
	private static void refuseHandlerKeyCollision( final String pattern ) {
		final WOApplication application = WOApplication.application();
		final int end = pattern.indexOf( '/', 1 );
		final String firstSegment = end == -1 ? pattern.substring( 1 ) : pattern.substring( 1, end );

		if( application != null && !firstSegment.isEmpty() && !firstSegment.startsWith( "{" ) && !firstSegment.equals( "*" ) && application.requestHandlerForKey( firstSegment ) != null ) {
			throw new IllegalArgumentException( "The route %s can never be reached: its first segment '%s' is a request handler key, and request handlers take precedence over routes".formatted( pattern, firstSegment ) );
		}
	}

	/**
	 * Registers a group under a name, for plugins to join ({@link RouteGroup#join(String)})
	 */
	synchronized void name( final String name, final RouteGroup group ) {
		if( _namedGroups.putIfAbsent( name, group ) != null ) {
			throw new IllegalArgumentException( "A group is already named '%s'".formatted( name ) );
		}

		// Plugins that joined the group before it was named map their routes now
		final List<Consumer<RouteGroup>> pending = _pendingJoins.remove( name );

		if( pending != null ) {
			pending.forEach( join -> join.accept( group ) );
		}
	}

	/**
	 * @throws IllegalStateException if a group was joined that's never been named: the routes a plugin mapped in it don't
	 *         exist. Checked for the default router before the application listens for requests, so it fails at startup,
	 *         and by any router on its first request. A failed check fails again on every request.
	 */
	public synchronized void checkJoins() {
		if( !_pendingJoins.isEmpty() ) {
			throw new IllegalStateException( "Groups were joined that are never named: %s. The routes mapped in them don't exist. Their names are %s".formatted( _pendingJoins.keySet(), _namedGroups.keySet() ) );
		}

		_joinsChecked = true;
	}

	/**
	 * Runs the action with the named group: now, if it's named, otherwise once it is
	 */
	synchronized void whenNamed( final String name, final Consumer<RouteGroup> action ) {
		final RouteGroup group = _namedGroups.get( name );

		if( group != null ) {
			action.accept( group );
		}
		else {
			_pendingJoins.computeIfAbsent( name, n -> new ArrayList<>() ).add( action );
		}
	}

	/**
	 * @return The group of the given name
	 * @throws IllegalArgumentException if no group has the name
	 */
	RouteGroup namedGroup( final String name ) {
		final RouteGroup group = _namedGroups.get( name );

		if( group == null ) {
			throw new IllegalArgumentException( "No group is named '%s'. The named groups are %s, and a group is named before a plugin joins it".formatted( name, _namedGroups.keySet() ) );
		}

		return group;
	}

	/**
	 * Maps the router into a route table as one route, ahead of the routes mapped after it
	 */
	public void mapInto( final RouteTable routeTable ) {
		routeTable.map( "/*", new MappedRouter() );
	}

	/**
	 * The router as one route of a route table, claiming the URLs it has routes for (not every URL its {@code /*} pattern
	 * matches), so the table's {@code hasRouteFor()} answers for the router's routes
	 */
	private class MappedRouter implements er.extensions.routes.RouteHandler, RouteClaims {

		@Override
		public WOActionResults handle( final er.extensions.routes.RouteInvocation invocation ) {
			return ERXRouter.this.handle( invocation );
		}

		@Override
		public boolean claims( final String url ) {
			return _router.hasRouteFor( url );
		}
	}

	/**
	 * @return The answer of the first matching route that doesn't decline, {@code 405}, a {@code 308} to the declared
	 *         trailing slash form, or {@link RouteHandler#DECLINED}
	 */
	public WOActionResults handle( final er.extensions.routes.RouteInvocation invocation ) {

		// A router first used after launch checks its joins on its first request
		if( !_joinsChecked ) {
			checkJoins();
		}

		final WORequest request = invocation.request();
		final RouteRequest routeRequest = new RouteRequest( request.method(), RequestHost.host( request ), invocation.url() );

		return switch( _router.route( routeRequest ) ) {
			case Router.Matched<Mapped> matched -> answer( matched, invocation );
			case Router.MethodNotAllowed<Mapped> notAllowed -> "OPTIONS".equals( routeRequest.method() ) ? options( notAllowed ) : methodNotAllowed( notAllowed );
			case Router.Redirect<Mapped> redirect -> redirect( redirect, invocation );
			case Router.NoMatch<Mapped> noMatch -> RouteHandler.DECLINED;
		};
	}

	private WOActionResults answer( final Router.Matched<Mapped> matched, final er.extensions.routes.RouteInvocation invocation ) {

		for( final Router.Candidate<Mapped> candidate : matched.candidates() ) {
			final Mapped mapped = candidate.handler();

			// A post from a page on a site the route doesn't take those from
			if( mapped.crossSite().refuses( invocation.request(), RequestHost.host( invocation.request() ), PublicAddress.configured(), this::isOwnHost ) ) {
				logger.debug( "The route {} refused {} {} from another site (origin {})", candidate.entry(), invocation.request().method(), invocation.url(), invocation.request().headerForKey( "origin" ) );
				return CrossSite.forbidden();
			}

			final RouteInvocation routedInvocation = new RouteInvocation( invocation.url(), invocation.request(), candidate.parameters(), _converters );
			WOActionResults results;

			try {
				results = mapped.group().wrapped( mapped.handler() ).handle( routedInvocation );
			}
			catch( Declined declined ) {
				logger.debug( "The route {} declined {}: {}", candidate.entry(), invocation.url(), declined.getMessage() );
				results = RouteHandler.DECLINED;
			}
			catch( NotCanonical notCanonical ) {

				// A wildcard route has no URL of its own to redirect to, so it declines, and the next candidate gets the request
				if( candidate.entry().path().isWildcard() ) {
					continue;
				}

				return canonicalRedirect( candidate, notCanonical, invocation );
			}

			if( results == null ) {
				throw new IllegalStateException( "The route %s returned null for URL '%s'. Return RouteHandler.DECLINED to pass the URL on to the next route".formatted( candidate.entry(), invocation.url() ) );
			}

			if( results != RouteHandler.DECLINED ) {
				return results;
			}
		}

		return RouteHandler.DECLINED;
	}

	private static WOResponse methodNotAllowed( final Router.MethodNotAllowed<Mapped> notAllowed ) {
		final WOResponse response = new WOResponse();
		response.setStatus( 405 );
		response.setHeader( String.join( ", ", notAllowed.allowedMethods() ), "allow" );
		response.setHeader( "text/plain; charset=utf-8", "content-type" );
		response.setContent( "Method not allowed" );
		return response;
	}

	/**
	 * A {@code 308} to the URL with a route parameter's canonical text ({@code /books/007} to {@code /books/7}), keeping
	 * the query string
	 */
	private static WOActionResults canonicalRedirect( final Router.Candidate<Mapped> candidate, final NotCanonical notCanonical, final er.extensions.routes.RouteInvocation invocation ) {
		final Map<String, String> values = new LinkedHashMap<>( candidate.parameters() );
		values.put( notCanonical.name, notCanonical.canonicalText );
		final Host host = (Host)candidate.entry().conditions().stream().filter( Host.class::isInstance ).findFirst().orElse( null );

		final String uri = invocation.request().uri();
		final int q = uri.indexOf( '?' );
		final String url = RouteURLs.url( candidate.entry().path(), host, values, List.of(), invocation.context() );

		final WOResponse response = new WOResponse();
		response.setStatus( 308 );
		response.setHeader( q == -1 ? url : url + uri.substring( q ), "location" );
		return response;
	}

	/**
	 * The answer to {@code OPTIONS} at a path whose routes don't take it themselves: the methods they accept
	 */
	private static WOResponse options( final Router.MethodNotAllowed<Mapped> notAllowed ) {
		final Set<String> allowed = new TreeSet<>( notAllowed.allowedMethods() );
		allowed.add( "OPTIONS" );

		final WOResponse response = new WOResponse();
		response.setStatus( 204 );
		response.setHeader( String.join( ", ", allowed ), "allow" );
		return response;
	}

	/**
	 * A {@code 308} to the declared trailing slash form, keeping the query string
	 */
	private static WOResponse redirect( final Router.Redirect<Mapped> redirect, final er.extensions.routes.RouteInvocation invocation ) {
		final String uri = invocation.request().uri();
		final int q = uri.indexOf( '?' );
		final String query = q == -1 ? null : uri.substring( q + 1 );

		final WOResponse response = new WOResponse();
		response.setStatus( 308 );
		response.setHeader( RouteURLs.url( redirect.path(), query, Objects.requireNonNull( invocation.context() ) ), "location" );
		return response;
	}
}
