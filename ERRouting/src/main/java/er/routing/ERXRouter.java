package er.routing;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.routing.core.Converters;
import er.routing.core.RouteOption;
import er.routing.core.RouteRequest;
import er.routing.core.Router;
import er.routing.core.TrailingSlash;
import er.extensions.routes.RouteTable;

/**
 * EXPERIMENTAL (route-links branch). The router, see docs/ROUTE_LINKS.md and #178: routes with named parameters,
 * conditions (host, methods), trailing slash policies, groups, and tables ranked so an application overrides a plugin.
 *
 * Kept beside the existing route table until they converge: {@link #mapInto(RouteTable)} maps the router into it as one
 * route, which declines what the router has no route for, so the table's fallback and not found still apply.
 *
 * <pre>
 * final ERXRouter router = new ERXRouter();
 * final RouteGroup routes = router.table( "application" );
 * routes.map( "/items/{id}", ri -&gt; ItemPage.page( ri, ri.parameter( "id" ) ) );
 * router.mapInto( RouteTable.defaultRouteTable() );
 * </pre>
 */

public class ERXRouter {

	private static final Logger logger = LoggerFactory.getLogger( ERXRouter.class );

	/**
	 * What the core router routes to: a handler, and the group it was mapped in (for its wrapping)
	 */
	record Mapped( RouteHandler handler, RouteGroup group ) {}

	private final Router<Mapped> _router;
	private final Converters _converters = new Converters();
	private final java.util.Map<String, RouteGroup> _namedGroups = new java.util.concurrent.ConcurrentHashMap<>();
	private int _loggedOverrides;

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
	 * @return The converters for route parameters: register an application's own types here, before declaring routes
	 *         taking them
	 */
	public Converters converters() {
		return _converters;
	}

	/**
	 * @return The routes of a new table, ranked below the tables created before it: the application's first, then
	 *         plugins' in dependency order. The same route in a higher ranked table overrides it.
	 */
	public RouteGroup table( final String name ) {
		return new RouteGroup( this, _router.table( name ), null, "", List.of() );
	}

	/**
	 * Maps a route in a table, logging any override it makes
	 */
	void map( final Router<Mapped>.Table table, final String pattern, final Mapped mapped, final List<RouteOption> options ) {
		table.map( pattern, mapped, options.toArray( RouteOption[]::new ) );

		final var overrides = _router.overrides();

		for( ; _loggedOverrides < overrides.size(); _loggedOverrides++ ) {
			final var override = overrides.get( _loggedOverrides );
			logger.info( "The route {} overrides {}", override.route(), override.overridden() );
		}
	}

	/**
	 * Registers a group under a name, for plugins to join ({@link RouteGroup#join(String)})
	 */
	void name( final String name, final RouteGroup group ) {
		if( _namedGroups.putIfAbsent( name, group ) != null ) {
			throw new IllegalArgumentException( "A group is already named '%s'".formatted( name ) );
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
		routeTable.map( "/*", this::handle );
	}

	/**
	 * @return The answer of the first matching route that doesn't decline, {@code 405}, a {@code 308} to the declared
	 *         trailing slash form, or {@link RouteHandler#DECLINED}
	 */
	public WOActionResults handle( final er.extensions.routes.RouteInvocation invocation ) {
		final WORequest request = invocation.request();
		final RouteRequest routeRequest = new RouteRequest( request.method(), RequestHost.host( request ), invocation.url() );

		return switch( _router.route( routeRequest ) ) {
			case Router.Matched<Mapped> matched -> answer( matched, invocation );
			case Router.MethodNotAllowed<Mapped> notAllowed -> methodNotAllowed( notAllowed );
			case Router.Redirect<Mapped> redirect -> redirect( redirect, invocation );
			case Router.NoMatch<Mapped> noMatch -> RouteHandler.DECLINED;
		};
	}

	private WOActionResults answer( final Router.Matched<Mapped> matched, final er.extensions.routes.RouteInvocation invocation ) {

		for( final Router.Candidate<Mapped> candidate : matched.candidates() ) {
			final Mapped mapped = candidate.handler();
			final RouteInvocation routedInvocation = new RouteInvocation( invocation.url(), invocation.request(), candidate.parameters(), _converters );
			WOActionResults results;

			try {
				results = mapped.group().wrapped( mapped.handler() ).handle( routedInvocation );
			}
			catch( Declined declined ) {
				results = RouteHandler.DECLINED;
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
