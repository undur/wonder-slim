package er.extensions.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXApplication;
import er.extensions.appserver.ERXNotification;
import er.extensions.foundation.ERXProperties;
import er.extensions.appserver.ERXRequest;
import er.routing.conversion.Converters;
import er.routing.matching.Router;
import er.routing.options.CrossOrigin;
import er.routing.options.CrossSite;
import er.routing.options.Host;
import er.routing.options.Method;
import er.routing.options.RouteBehavior;
import er.routing.options.RouteCondition;
import er.routing.options.RouteOption;
import er.routing.options.RouteRequest;
import er.routing.options.TrailingSlash;

/**
 * The router (docs/ROUTING.md): routes with named parameters, conditions (methods, hosts), trailing slash policies,
 * groups, and tables ranked so an application overrides a plugin. A request no route answers goes to the application's
 * fallback, then its not found handler (see {@link RouteGroup#fallback(RouteHandler)}).
 *
 * <pre>
 * public interface Routes {
 *     PlainRoute item = Route.plain();
 * }
 *
 * ERXRouter.declare( routes -&gt; routes.map( "/items/{id}", Routes.item, ItemPage.class ) ); // the Application's constructor
 * </pre>
 */

public class ERXRouter {

	private static final Logger logger = LoggerFactory.getLogger( ERXRouter.class );

	/**
	 * userInfo key that tells wo-adaptor-jetty a response is "unhandled": the adaptor discards it and lets the next Jetty
	 * handler try the request (an ng-objects handler in the same server, say). Set when the not found handler declines
	 * too. The same literal as the adaptor's, which ERExtensions doesn't depend on. Other adaptors serve the 404.
	 */
	public static final String UNHANDLED_RESPONSE_KEY = "wo-unhandled-response";

	/**
	 * Request userInfo key for why routes that matched a URL passed it on (a {@code List<String>}, one line per route),
	 * which the not found page in development shows: "why is this a 404"
	 */
	public static final String DECLINES_KEY = "er.extensions.routes.declines";

	/**
	 * true for the application's router (the one its declarations build), which answers with the development pages in
	 * development; a router made otherwise (a test's) answers the plain 404
	 */
	private boolean _applicationRouter;

	/**
	 * What answers a request no route answered, before the not found handler; null for nothing
	 */
	private RouteHandler _fallback;

	/**
	 * What answers a request nothing else answered; null for the default
	 */
	private RouteHandler _notFound;

	/**
	 * What the core router routes to: a handler, and the group it was mapped in (for its wrapping)
	 */
	record Mapped( RouteHandler handler, RouteGroup group, Linkable route, Class<? extends Record> parametersClass, CrossSite crossSite, CrossOrigin crossOrigin, String answer ) {}

	/**
	 * @return What answers a route, for a list of the routes: a page's name, or a handler's class (a lambda's is "a
	 *         handler")
	 */
	static String answerOf( final Object handlerOrAction ) {
		return switch( handlerOrAction ) {
			case PageSetters pageSetters -> pageSetters.pageClass().getSimpleName();
			case PageSetters.RecordPage<?> recordPage -> recordPage.pageClass().getSimpleName();
			default -> handlerOrAction.getClass().isHidden() || handlerOrAction.getClass().isAnonymousClass() || handlerOrAction.getClass().isSynthetic() ? "a handler" : handlerOrAction.getClass().getSimpleName();
		};
	}

	private final Router<Mapped> _router;
	private final Converters _converters = new Converters( type -> {
		undeclared( "the converter for " + type.getName() + ", registered" );
		owned( type );
	} );

	/**
	 * Who is declaring routes now: the application, or a plugin's table ({@link #declaringAs(String, Runnable)})
	 */
	private String _declaring;

	/**
	 * Who registered each type's converter (or provides its objects)
	 */
	private final Map<Class<?>, String> _converterOwners = new java.util.concurrent.ConcurrentHashMap<>();

	private static final String APPLICATION_OWNER = "the application";
	private final Map<String, RouteGroup> _namedGroups = new ConcurrentHashMap<>();
	private final Map<String, List<Consumer<RouteGroup>>> _pendingJoins = new LinkedHashMap<>();
	private int _loggedOverrides;

	/**
	 * True once the joins are checked: at launch for the default router, otherwise on the first request
	 */
	private volatile boolean _joinsChecked;

	/**
	 * True for a router whose routes are declared ({@link #declare(Consumer)}): changed outside a declaration, it throws
	 */
	private volatile boolean _declaredOnly;

	/**
	 * The routes the router gives patterns to, and how it has each: published to the routes once their declarations
	 * succeed, so a link goes to the current routes
	 */
	private final Map<RouteIdentity<?>, Object> _bindings = new java.util.IdentityHashMap<>();

	/**
	 * Checks made once the routes are declared (every route then has its pattern): a redirect's names against its route's
	 */
	private final List<Runnable> _checks = new java.util.concurrent.CopyOnWriteArrayList<>();
	private ApplicationRoutes _application;

	/**
	 * Whether routes are declared again when their classes change. Development's default.
	 */
	public static final String RELOAD_PROPERTY = "er.routing.reload";

	private static RouteDeclarations _declarations;

	/**
	 * @return The application's router, created on first use, so an application declares its routes and has nothing
	 *         to set up. Called by a route declaration, the router it's
	 *         declaring into. The router is replaced when the routes are declared again (in development), so read it
	 *         each time, don't keep it.
	 */
	public static ERXRouter defaultRouter() {
		final ERXRouter declaring = RouteDeclarations.DECLARING.get();
		return declaring != null ? declaring : declarations().router();
	}

	/**
	 * Declares routes in the application's router: the declaration gives the route constants their patterns. In
	 * development it runs again when its classes change (the declaring class's folder, a constants interface's, a route's
	 * record's), so a changed route is there without a restart.
	 *
	 * <pre>
	 * public interface Routes {
	 *     Route&lt;Search&gt; search = Route.of( Search.class );
	 * }
	 *
	 * ERXRouter.declare( routes -&gt; routes.map( "/search", Routes.search, SearchPage.class ) ); // the Application's constructor
	 * </pre>
	 *
	 * The declaration gets the application's routes, ranked before every plugin's, so its routes override theirs. Options
 * apply to every route it declares, as a group's apply to its routes:
 * {@code ERXRouter.declare( routes -> … , TrailingSlash.REDIRECT )}.
	 *
	 * The application's routes are changed only in a declaration: outside one, mapping a route, reaching the application's
	 * routes or a table, or registering a converter throws, since declaring the routes again would lose it. Declarations
	 * run again in the order they were first made (an application and its plugins), into a new router that replaces the
	 * current one once all of them succeed. One that fails leaves the current routes in place, and routed requests answer
	 * with why until the routes are declared again. Set {@value #RELOAD_PROPERTY} to false to declare them once.
	 */
	public static void declare( final Consumer<? super ApplicationRoutes> declaration ) {
		declare( declaration, new RouteOption[0] );
	}

	/**
	 * Declares the application's routes as {@link #declare(Consumer)} does, the options applying to every route the
	 * declaration declares, as a group's apply to its routes:
	 * {@code ERXRouter.declare( routes -> … , TrailingSlash.REDIRECT )}
	 */
	public static void declare( final Consumer<? super ApplicationRoutes> declaration, final RouteOption... options ) {
		declarations().declare( router -> router.declaringAs( APPLICATION_OWNER, () -> declaration.accept( router.application( options ) ) ), RouteIdentity.caller() );
	}

	/**
	 * Declares a plugin's routes, as {@link #declare(Consumer)} does the application's: in a table of the
	 * plugin's own, ranked below the application's and the tables declared before it (plugins in dependency order). The
	 * same route in a higher ranked table overrides it. Options apply to every route it declares.
	 *
	 * <pre>
	 * ERXRouter.declare( "guestbook", routes -&gt; routes.map( "/guestbook", … ) );
	 * </pre>
	 */
	public static void declare( final String table, final Consumer<RouteGroup> declaration ) {
		declare( table, declaration, new RouteOption[0] );
	}

	/**
	 * Declares a plugin's routes as {@link #declare(String, Consumer)} does, the options applying to every route the
	 * declaration declares
	 */
	public static void declare( final String table, final Consumer<RouteGroup> declaration, final RouteOption... options ) {
		declarations().declare( router -> router.declaringAs( "the table " + table, () -> declaration.accept( options.length == 0 ? router.table( table ) : router.table( table ).group( "", options ) ) ), RouteIdentity.caller() );
	}

	/**
	 * Runs a declaration as the given owner of the converters it registers
	 */
	void declaringAs( final String owner, final Runnable declaration ) {
		final String previous = _declaring;
		_declaring = owner;

		try {
			declaration.run();
		}
		finally {
			_declaring = previous;
		}
	}

	/**
	 * Records who registered a type's converter: one owner to a type, and only the application replaces a built-in
	 * converter, so a plugin's converters can't change how the application's routes convert (#199)
	 *
	 * @throws IllegalStateException if the type has another owner, or a plugin replaces a built-in converter
	 */
	private void owned( final Class<?> type ) {
		final String owner = _declaring == null ? APPLICATION_OWNER : _declaring;

		if( _converters.isBuiltIn( type ) && !owner.equals( APPLICATION_OWNER ) ) {
			throw new IllegalStateException( "%s registers a converter for %s, which is built in: only the application replaces a built-in converter, since a plugin's would change how the application's routes convert. Register one for a type of the plugin's own".formatted( owner, type.getName() ) );
		}

		final String previous = _converterOwners.putIfAbsent( type, owner );

		if( previous != null && !previous.equals( owner ) ) {
			throw new IllegalStateException( "%s registers a converter for %s, which %s registered: a type has one converter, so routes convert it the same way wherever they are".formatted( owner, type.getName(), previous ) );
		}
	}

	private static synchronized RouteDeclarations declarations() {
		if( _declarations == null ) {
			final boolean reload = ERXProperties.booleanForKeyWithDefault( RELOAD_PROPERTY, ERXApplication.isDevelopmentModeSafe() );
			_declarations = new RouteDeclarations( ERXRouter::applicationRouter, reload ? new ClassChanges() : RouteDeclarations.Changes.NONE );

			// By the time the application is about to listen for requests, the groups plugins joined are named. The public
			// address is read then too, so a value that isn't one stops the launch.
			ERXNotification.ApplicationWillFinishLaunchingNotification.addObserver( notification -> {
				_declarations.router().checkJoins();
				_declarations.checkDeclared();
				PublicAddress.configured();
			} );
		}

		return _declarations;
	}

	/**
	 * Answers a request with the application's router (its routes declared again first, if their classes changed): the
	 * request handler for routes calls this
	 *
	 * @param path The URL's path, as the routes see it ({@code /books/7})
	 */
	static WOActionResults handleRequest( final WORequest request, final String path ) {
		logger.info( "Handling URL: {};{};{}", path, ERXRequest.remoteAddress( request ), request.headerForKey( "user-agent" ) );
		final RouteDeclarations declarations = declarations();
		declarations.declareAgainIfChanged();
		return declarations.router().handle( request, path );
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
		return _router.routes().stream().map( e -> new RouteDescription( e.path().source(), e.conditions(), e.trailingSlash(), e.table(), e.handler().route(), e.handler().parametersClass(), e.handler().crossSite(), e.handler().route() instanceof Route<?> r && r.reportsFields(), e.handler().answer() ) ).toList();
	}

	/**
	 * @return The converters for route parameters (a declaration reaches them through its routes,
	 *         {@link RouteGroup#converters()})
	 */
	Converters converters() {
		return _converters;
	}

	/**
	 * @return The application's routes: a table ranked before every other, whenever it's created, so the application's
	 *         routes override a plugin's
	 */
	/**
	 * @return The application's routes with the given options: for a declaration with options, a group of the application's
	 *         that applies them, and that takes what only the application's routes take (a fallback, a not found handler)
	 */
	synchronized ApplicationRoutes application( final RouteOption... options ) {
		if( options.length == 0 ) {
			return application();
		}

		final ApplicationRoutes application = application();
		return new ApplicationRoutes( this, application.table(), application, application.allOptions( options ) );
	}

	synchronized ApplicationRoutes application() {
		undeclared( "the application's routes, reached" );

		if( _application == null ) {
			_application = new ApplicationRoutes( this, _router.table( "application", Integer.MIN_VALUE ), null, List.of() );
		}

		return _application;
	}

	/**
	 * @return The routes of a new table, for a plugin, ranked below the application's and the tables created before it
	 *         (plugins in dependency order). The same route in a higher ranked table overrides it.
	 */
	RouteGroup table( final String name ) {
		undeclared( "the table " + name + ", created" );
		return new RouteGroup( this, _router.table( name ), null, "", List.of() );
	}

	/**
	 * The classes declaring into the router (those reaching its tables), whose folders are watched for changes
	 */
	private final java.util.Set<Class<?>> _declaringClasses = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/**
	 * Notes a class mapping routes into the router: a plugin's, whose folder may be another than the application's
	 */
	void declaredFrom( final Class<?> type ) {
		if( type != null && RouteDeclarations.DECLARING.get() == this ) {
			_declaringClasses.add( type );
		}
	}

	java.util.Set<Class<?>> declaringClasses() {
		return _declaringClasses;
	}

	/**
	 * Runs the check once the routes are declared, with the joins' (see {@link #checkJoins()})
	 */
	void checkOnceDeclared( final Runnable check ) {
		_checks.add( check );
	}

	/**
	 * Makes the router's routes declared only ({@link #declare(Consumer)})
	 */
	void declaredOnly() {
		_declaredOnly = true;
	}

	/**
	 * Refuses a change to a router whose routes are declared, unless a declaration into it makes it. Reaching the
	 * application's routes or a table counts, since what's added through them (a plugin's filter on the application's
	 * admin group, say) would be lost when the routes are declared again.
	 *
	 * @throws IllegalStateException naming what was done outside a declaration
	 */
	void undeclared( final String what ) {
		if( _declaredOnly && RouteDeclarations.DECLARING.get() != this ) {
			throw new IllegalStateException( "%s outside a route declaration: the application's routes are declared in ERXRouter.declare( routes -> … ), which runs again when they change in development".formatted( capitalized( what ) ) );
		}
	}

	private static String capitalized( final String text ) {
		return text.isEmpty() ? text : Character.toUpperCase( text.charAt( 0 ) ) + text.substring( 1 );
	}

	/**
	 * Gives a route its pattern in this router
	 *
	 * @throws IllegalArgumentException if it has one already
	 */
	synchronized void bind( final RouteIdentity<?> route, final Object binding ) {
		if( _bindings.containsKey( route ) ) {
			throw new IllegalArgumentException( "The route %s is declared twice: as %s, and as %s".formatted( route.name(), _bindings.get( route ), binding ) );
		}

		_bindings.put( route, binding );

		// Outside a declaration (a router of its own), it's the current routes at once
		if( RouteDeclarations.DECLARING.get() != this ) {
			route.publish( binding );
		}
	}

	/**
	 * @return How the router has the route, null if it doesn't
	 */
	synchronized Object binding( final RouteIdentity<?> route ) {
		return _bindings.get( route );
	}

	/**
	 * Makes the router's routes the current ones, for links
	 */
	synchronized void publishBindings() {
		_bindings.forEach( RouteIdentity::publish );
	}

	/**
	 * @return The router's routes, as constants and as bound
	 */
	synchronized Map<RouteIdentity<?>, Object> bindings() {
		return Map.copyOf( _bindings );
	}

	/**
	 * @return The request as the router's core sees it: its method, host, path, scheme and headers
	 */
	static RouteRequest routeRequest( final WORequest request, final String path ) {
		return new RouteRequest( request.method(), RequestHost.host( request ), path, request.isSecure(), request::headerForKey );
	}

	/**
	 * @return The answer to a browser's preflight for a route allowing the request's origin
	 */
	private static WOResponse preflight( final CrossOrigin crossOrigin, final RouteRequest request, final Set<String> methods ) {
		final WOResponse response = new WOResponse();
		response.setStatus( 204 );
		crossOrigin.preflightHeaders( request, methods ).forEach( ( name, value ) -> response.setHeader( value, name ) );

		if( crossOrigin.variesByOrigin() ) {
			response.appendHeader( "Origin", "vary" );
		}

		return response;
	}

	/**
	 * @return The answer to a request refused as coming from another site
	 */
	private static WOResponse forbidden() {
		final WOResponse response = new WOResponse();
		response.setStatus( 403 );
		response.setHeader( "text/plain; charset=utf-8", "content-type" );
		response.setContent( "A request from another site, refused" );
		return response;
	}

	/**
	 * @return The public address's host and port, as an Origin header names them, null without one
	 */
	private static String publicAuthority() {
		final PublicAddress.Origin publicAddress = PublicAddress.configured();
		return publicAddress == null ? null : publicAddress.host() + publicAddress.portSuffix();
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

		undeclared( "the route " + pattern + ", mapped" );
		table.map( pattern, mapped, options.stream().filter( option -> !(option instanceof RouteBehavior) ).toArray( RouteOption[]::new ) );

		final var overrides = _router.overrides();

		for( ; _loggedOverrides < overrides.size(); _loggedOverrides++ ) {
			final var override = overrides.get( _loggedOverrides );
			logger.info( "The route {} overrides {}", override.route(), override.overridden() );
		}
	}

	/**
	 * A route whose first segment is a registered request handler key ({@code /wa/…}) can never be reached: the handler
	 * gets those URLs. Refused when it's mapped, rather than silently never matching.
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

		_checks.forEach( Runnable::run );

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
	 * @return The answer to a request: the first matching route's that doesn't decline ({@code 405}, or a {@code 308} to
	 *         the declared trailing slash form), else the fallback's, else the not found handler's. When that declines
	 *         too, a bare 404 marked unhandled ({@link #UNHANDLED_RESPONSE_KEY}), passing the request on.
	 */
	WOActionResults handle( final WORequest request, final String path ) {
		final RouteInvocation invocation = new RouteInvocation( path, request, Map.of(), _converters );
		final WOActionResults routed = route( invocation );

		if( routed != RouteHandler.DECLINED ) {
			return routed;
		}

		if( _fallback != null ) {
			final WOActionResults results = answered( _fallback, invocation, "fallback" );

			if( results != RouteHandler.DECLINED ) {
				return results;
			}
		}

		final WOActionResults results = answered( notFound(), invocation, "not found handler" );
		return results != RouteHandler.DECLINED ? results : passedOn();
	}

	/**
	 * @return A router for the application's declarations
	 */
	private static ERXRouter applicationRouter() {
		final ERXRouter router = new ERXRouter();
		router._applicationRouter = true;
		return router;
	}

	void fallback( final RouteHandler fallback ) {
		_fallback = Objects.requireNonNull( fallback );
	}

	void notFound( final RouteHandler notFound ) {
		_notFound = Objects.requireNonNull( notFound );
	}

	/**
	 * @return The not found handler: the application's, or the development pages in development (a welcome page at
	 *         {@code /}, otherwise a 404 listing the routes and why those that matched passed the URL on), and a plain
	 *         404 deployed
	 */
	private RouteHandler notFound() {
		if( _notFound != null ) {
			return _notFound;
		}

		return _applicationRouter && ERXApplication.isDevelopmentModeSafe() ? DevelopmentNotFound.HANDLER : invocation -> {
			final WOResponse response = new WOResponse();
			response.setStatus( 404 );
			response.setContent( "No route found for URL: " + invocation.url() );
			return response;
		};
	}

	/**
	 * @return The handler's answer, or {@link RouteHandler#DECLINED}
	 */
	private static WOActionResults answered( final RouteHandler handler, final RouteInvocation invocation, final String role ) {
		final WOActionResults results = handler.handle( invocation );

		if( results == null ) {
			throw new IllegalStateException( "The %s %s returned null for URL '%s'. Return RouteHandler.DECLINED to pass the URL on".formatted( role, handler, invocation.url() ) );
		}

		return results;
	}

	/**
	 * @return The answer to a URL everything declined: a bare 404 marked unhandled ({@link #UNHANDLED_RESPONSE_KEY}),
	 *         which wo-adaptor-jetty discards to let the next handler in the server try the request
	 */
	private static WOResponse passedOn() {
		final WOResponse response = new WOResponse();
		response.setStatus( 404 );
		response.setUserInfoForKey( "true", UNHANDLED_RESPONSE_KEY );
		return response;
	}

	/**
	 * Adds why a route passed the request on, for the not found page in development (a handler declining can say why
	 * more simply by throwing {@link Declined})
	 */
	@SuppressWarnings( "unchecked" )
	public static void explainDecline( final WORequest request, final String explanation ) {
		List<String> declines = (List<String>)request.userInfoForKey( DECLINES_KEY );

		if( declines == null ) {
			declines = new ArrayList<>();
			request.setUserInfoForKey( declines, DECLINES_KEY );
		}

		declines.add( explanation );
	}

	/**
	 * @return The answer of the first matching route that doesn't decline, {@code 405}, a {@code 308} to the declared
	 *         trailing slash form, or {@link RouteHandler#DECLINED}
	 */
	WOActionResults route( final RouteInvocation invocation ) {

		// A router first used after launch checks its joins on its first request
		if( !_joinsChecked ) {
			checkJoins();
		}

		final WORequest request = invocation.request();
		final RouteRequest routeRequest = routeRequest( request, invocation.url() );

		return switch( _router.route( routeRequest ) ) {
			case Router.Matched<Mapped> matched -> answer( matched, invocation, routeRequest );
			case Router.MethodNotAllowed<Mapped> notAllowed -> "OPTIONS".equals( routeRequest.method() ) ? options( notAllowed, routeRequest ) : methodNotAllowed( notAllowed );
			case Router.Redirect<Mapped> redirect -> redirect( redirect, invocation );
			case Router.NoMatch<Mapped> noMatch -> RouteHandler.DECLINED;
		};
	}

	private WOActionResults answer( final Router.Matched<Mapped> matched, final RouteInvocation invocation, final RouteRequest request ) {

		for( final Router.Candidate<Mapped> candidate : matched.candidates() ) {
			final Mapped mapped = candidate.handler();
			final boolean allowedOrigin = mapped.crossOrigin() != null && mapped.crossOrigin().allows( request );

			// A browser's preflight for another site's script: answered for the route, which takes every method
			if( allowedOrigin && CrossOrigin.isPreflight( request ) ) {
				return preflight( mapped.crossOrigin(), request, Set.of( request.header( "access-control-request-method" ).toUpperCase( java.util.Locale.ROOT ) ) );
			}

			// A post from a page on a site the route doesn't take those from (a named origin it allows calls is taken)
			if( !(allowedOrigin && mapped.crossOrigin().waivesCrossSite()) && mapped.crossSite().refuses( request, RequestHost.host( invocation.request() ), publicAuthority(), this::isOwnHost ) ) {
				logger.debug( "The route {} refused {} {} from another site (origin {})", candidate.entry(), invocation.request().method(), invocation.url(), invocation.request().headerForKey( "origin" ) );
				return forbidden();
			}

			final RouteInvocation routedInvocation = new RouteInvocation( invocation.url(), invocation.request(), candidate.parameters(), _converters, mapped.route() );

			WOActionResults results;

			try {
				results = mapped.group().wrapped( mapped.handler() ).handle( routedInvocation );
			}
			catch( Declined declined ) {
				logger.debug( "The route {} declined {}: {}", candidate.entry(), invocation.url(), declined.getMessage() );
				routedInvocation.declinedBecause( declined.getMessage() );
				results = RouteHandler.DECLINED;
			}
			catch( NotCanonical notCanonical ) {

				// A wildcard route has no URL of its own to redirect to, so it declines, and the next candidate gets the request
				if( candidate.entry().path().isWildcard() ) {
					explainDecline( invocation.request(), "%s: its parameter '%s' isn't in its canonical text, and a wildcard route has no URL to redirect to".formatted( candidate.entry(), notCanonical.name ) );
					continue;
				}

				return canonicalRedirect( candidate, notCanonical, invocation );
			}

			if( results == null ) {
				throw new IllegalStateException( "The route %s returned null for URL '%s'. Return RouteHandler.DECLINED to pass the URL on to the next route".formatted( candidate.entry(), invocation.url() ) );
			}

			if( results != RouteHandler.DECLINED ) {

				// Another site's script may read the answer. A route answering named origins varies by Origin whatever the
				// request's, so a cache doesn't hand one origin's answer to another.
				if( mapped.crossOrigin() != null ) {
					final WOResponse response = results.generateResponse();
					if( mapped.crossOrigin().variesByOrigin() ) {
						response.appendHeader( "Origin", "vary" );
					}

					if( allowedOrigin ) {
						mapped.crossOrigin().allowHeaders( request ).forEach( ( name, value ) -> response.setHeader( value, name ) );
					}

					return response;
				}

				return results;
			}

			// Why, for the not found page in development
			explainDecline( invocation.request(), "%s: %s".formatted( candidate.entry(), routedInvocation.declineReason() == null ? "its handler declined" : routedInvocation.declineReason() ) );
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
	private static WOActionResults canonicalRedirect( final Router.Candidate<Mapped> candidate, final NotCanonical notCanonical, final RouteInvocation invocation ) {
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
	private static WOResponse options( final Router.MethodNotAllowed<Mapped> notAllowed, final RouteRequest request ) {
		final Set<String> allowed = new TreeSet<>( notAllowed.allowedMethods() );
		allowed.add( "OPTIONS" );

		// A browser's preflight for another site's script: answered for the routes allowing its origin, with their methods
		if( CrossOrigin.isPreflight( request ) ) {
			for( final Router.Entry<Mapped> route : notAllowed.routes() ) {
				final CrossOrigin crossOrigin = route.handler().crossOrigin();

				if( crossOrigin != null && crossOrigin.allows( request ) ) {
					final Set<String> methods = new TreeSet<>();
					notAllowed.routes().stream().filter( r -> r.handler().crossOrigin() != null && r.handler().crossOrigin().allows( request ) ).forEach( r -> r.conditions().stream().filter( Method.class::isInstance ).forEach( m -> methods.addAll( ((Method)m).accepted() ) ) );
					return preflight( crossOrigin, request, methods );
				}
			}
		}

		final WOResponse response = new WOResponse();
		response.setStatus( 204 );
		response.setHeader( String.join( ", ", allowed ), "allow" );
		return response;
	}

	/**
	 * A {@code 308} to the declared trailing slash form, keeping the query string
	 */
	private static WOResponse redirect( final Router.Redirect<Mapped> redirect, final RouteInvocation invocation ) {
		final String uri = invocation.request().uri();
		final int q = uri.indexOf( '?' );
		final String query = q == -1 ? null : uri.substring( q + 1 );

		final WOResponse response = new WOResponse();
		response.setStatus( 308 );
		response.setHeader( RouteURLs.url( redirect.path(), query, Objects.requireNonNull( invocation.context() ) ), "location" );
		return response;
	}
}
