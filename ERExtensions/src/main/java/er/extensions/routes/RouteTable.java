package er.extensions.routes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXRequest;

/**
 * Route handling: a chain of route handlers. Each handler in turn either answers the URL or declines it (returns
 * {@link RouteHandler#DECLINED}), and the first answer is the response.
 *
 * <ol>
 * <li><b>The routes</b> whose pattern matches the URL, in the order they're mapped.</li>
 * <li><b>The fallback</b>, if one is set: {@link #setFallbackRouteHandler(RouteHandler)}. {@code new ERXPublicResources()}
 * is the one wonder-slim has: a file in the application's {@code public} folder answers its URL, and anything else is
 * declined.</li>
 * <li><b>Not found</b>: {@link #setNotFoundRouteHandler(RouteHandler)}. A plain 404 by default, and in development
 * {@link ERXDevelopmentNotFoundRouteHandler}'s pages.</li>
 * </ol>
 *
 * The fallback and not found are always the last two, after every route, whenever they were set.
 *
 * A URL every handler declines is passed on to the next handler in the server: the answer is a bare 404 marked
 * unhandled (see {@link #UNHANDLED_RESPONSE_KEY}), which wo-adaptor-jetty discards to let the next handler try the
 * request. An application sharing its server with another (such as an ng-objects application) has not found decline
 * every URL, with {@link PassOnRouteHandler}.
 */
public class RouteTable {

	private static final Logger logger = LoggerFactory.getLogger( RouteTable.class );

	/**
	 * Answers a URL nothing claims, see {@link #setNotFoundRouteHandler(RouteHandler)}
	 */
	private RouteHandler _notFoundRouteHandler = new NotFoundRouteHandler();

	/**
	 * Gets a URL no route claims before the not found handler does, see {@link #setFallbackRouteHandler(RouteHandler)}.
	 * Null for none.
	 */
	private RouteHandler _fallbackRouteHandler;

	/**
	 * A list of all routes mapped by this table
	 */
	private List<Route> _routes = new ArrayList<>();

	/**
	 * The default global route table used by RouteAction to access actions
	 */
	private static RouteTable _defaultRouteTable = new RouteTable();

	public static RouteTable defaultRouteTable() {
		return _defaultRouteTable;
	}

	/**
	 * @return The handler answering a URL nothing claims
	 */
	public RouteHandler notFoundRouteHandler() {
		return _notFoundRouteHandler;
	}

	/**
	 * Sets the handler answering a URL nothing claims. The default is {@link NotFoundRouteHandler}, a plain 404; in
	 * development, ERXApplication sets {@link ERXDevelopmentNotFoundRouteHandler} before the application's constructor
	 * runs, so the application can set a handler of its own, or the plain one back, in its constructor.
	 */
	public void setNotFoundRouteHandler( final RouteHandler routeHandler ) {
		_notFoundRouteHandler = Objects.requireNonNull( routeHandler );
	}

	/**
	 * Sets the fallback, or null for none: it gets a URL no route answers before the not found handler does.
	 * {@code new ERXPublicResources()} serves the files in the application's {@code public} folder, and declines anything
	 * else; set it in the application's constructor.
	 */
	public void setFallbackRouteHandler( final RouteHandler routeHandler ) {
		_fallbackRouteHandler = routeHandler;
	}

	/**
	 * @return The routes mapped by this table, in the order they're matched
	 */
	public List<Route> routes() {
		return Collections.unmodifiableList( _routes );
	}

	/**
	 * Check if the given handler matches the given URL.
	 *
	 * FIXME: We're currently only checking if the pattern starts with the given pattern. We want some real pattern matching here // Hugi 2021-12-30
	 */
	private static boolean matches( final String pattern, final String url ) {
		if( pattern.endsWith( "*" ) ) {
			final String patternWithoutWildcard = pattern.substring( 0, pattern.length() - 1 );
			return url.startsWith( patternWithoutWildcard );
		}

		return pattern.equals( url );
	}

	/**
	 * Handles the request against the route table.
	 *
	 * @param urlInParams true to take the route URL from the request's parameters/headers (see {@link #routeURLFromRequestParameters}) instead of the request URI
	 */
	public WOActionResults handle( final WORequest request, boolean urlInParams ) {
		return handle( request, urlInParams ? routeURLFromRequestParameters( request ) : RouteRequestHandler.routePath( request ) );
	}

	/**
	 * Handles the request against the given route path — the request URL as
	 * the routes see it. See {@link RouteRequestHandler#routePath(WORequest)}.
	 */
	public WOActionResults handle( final WORequest request, final String routeURL ) {
		final String ipAddress = ERXRequest.remoteAddress( request );
		final String userAgent = request.headerForKey( "user-agent" );

		logger.info( "Handling URL: {};{};{}", routeURL, ipAddress, userAgent );

		final RouteInvocation invocation = new RouteInvocation( routeURL, request );

		for( final Route route : _routes ) {
			if( matches( route.pattern(), routeURL ) ) {
				final WOActionResults results = answer( route.routeHandler(), invocation );

				if( results != RouteHandler.DECLINED ) {
					return results;
				}
			}
		}

		if( _fallbackRouteHandler != null ) {
			final WOActionResults results = answer( _fallbackRouteHandler, invocation );

			if( results != RouteHandler.DECLINED ) {
				return results;
			}
		}

		final WOActionResults results = answer( _notFoundRouteHandler, invocation );

		if( results != RouteHandler.DECLINED ) {
			return results;
		}

		return passedOn();
	}

	/**
	 * @return The handler's answer, or {@link RouteHandler#DECLINED}
	 */
	private static WOActionResults answer( final RouteHandler routeHandler, final RouteInvocation invocation ) {
		final WOActionResults results = routeHandler.handle( invocation );

		if( results == null ) {
			throw new IllegalStateException( "The route handler %s returned null for URL '%s'. Return RouteHandler.DECLINED to pass the URL on to the next handler".formatted( routeHandler, invocation.url() ) );
		}

		return results;
	}

	/**
	 * @return The answer to a URL every handler declined: a bare 404 marked unhandled (see {@link #UNHANDLED_RESPONSE_KEY}),
	 *         which wo-adaptor-jetty discards to let the next handler in the server try the request. Nothing else is
	 *         generated, since nobody sees it.
	 */
	private static WOResponse passedOn() {
		final WOResponse response = new WOResponse();
		response.setStatus( 404 );
		response.setUserInfoForKey( "true", UNHANDLED_RESPONSE_KEY );
		return response;
	}

	/**
	 * @return The requested URL
	 *
	 *  - from the "URL"query parameter (usually used for development)
	 *  - or from the redirect_url header provided by Apache's 404 handler
	 */
	private static String routeURLFromRequestParameters( final WORequest request ) {
		String url = request.stringFormValueForKey( "url" );

		if( url == null ) {
			url = request.headerForKey( "redirect_url" );
		}

		return url;
	}

	/**
	 * @return true if a mapped route's pattern matches the given URL (the path, without a query string), whether or not
	 *         its handler answers it when asked. Lets other URL handling — short URLs — defer to explicit routes.
	 */
	public boolean hasRouteFor( final String url ) {
		return _routes.stream().anyMatch( route -> route.routeHandler() instanceof RouteClaims claims ? claims.claims( url ) : matches( route.pattern(), url ) );
	}

	public void map( final String pattern, final RouteHandler routeHandler ) {
		refuseHandlerKeyCollision( pattern );
		_routes.add( new Route( pattern, routeHandler ) );
	}

	/**
	 * A route whose first segment is a registered request handler key can never be reached: the first
	 * segment decides between WebObjects' request handlers and the route table, and the handler wins.
	 * Failing at mapping time beats a route that silently never matches.
	 */
	private static void refuseHandlerKeyCollision( final String pattern ) {
		final WOApplication application = WOApplication.application();

		if( application == null || pattern == null || !pattern.startsWith( "/" ) ) {
			return;
		}

		final int end = pattern.indexOf( '/', 1 );
		final String firstSegment = end == -1 ? pattern.substring( 1 ) : pattern.substring( 1, end );

		if( !firstSegment.isEmpty() && !firstSegment.endsWith( "*" ) && application.requestHandlerForKey( firstSegment ) != null ) {
			throw new IllegalArgumentException( "Route '" + pattern + "' can never be matched: its first segment '" + firstSegment + "' is a registered request handler key, and request handlers take precedence over routes" );
		}
	}

	public void map( final String pattern, final Class<? extends WOComponent> componentClass ) {
		map( pattern, new ComponentClassRouteHandler( componentClass ) );
	}

	/**
	 * Maps a URL pattern to a given RouteHandler
	 * 
	 * @param pattern The pattern this route uses
	 * @param routeHandler The routeHandler that will handle requests passed to this route
	 */
	public record Route( String pattern, RouteHandler routeHandler ) {}

	/**
	 * userInfo key that tells wo-adaptor-jetty a response is "unhandled": the adaptor discards it and lets the next Jetty
	 * handler try the request (e.g. an ng-objects handler in the same server). Set when every handler declined. Same
	 * literal as WOAdaptorJetty.UNHANDLED_RESPONSE_KEY, duplicated on purpose since ERExtensions must not depend on the
	 * adaptor. Other adaptors ignore it and just serve the 404.
	 */
	public static final String UNHANDLED_RESPONSE_KEY = "wo-unhandled-response";

	/**
	 * Declines every URL, so a URL no route answers is passed on to the next handler in the server (see
	 * {@link RouteTable}). The not found handler for an application sharing its server with another handler, such as an
	 * ng-objects application.
	 */
	public static class PassOnRouteHandler implements RouteHandler {
		@Override
		public WOActionResults handle( final RouteInvocation invocation ) {
			return DECLINED;
		}
	}

	/**
	 * For returning 404
	 */
	public static class NotFoundRouteHandler implements RouteHandler {
		@Override
		public WOActionResults handle( final RouteInvocation invocation ) {
			final WOResponse response = new WOResponse();
			response.setStatus( 404 );
			response.setContent( "No route found for URL: " + invocation.url() );
			return response;
		}
	}

	public record ComponentClassRouteHandler( Class<? extends WOComponent> componentClass )  implements RouteHandler {

		@Override
		public WOActionResults handle( RouteInvocation invocation ) {
			return WOApplication.application().pageWithName( componentClass().getName(), invocation.request().context() );
		}
	}
}