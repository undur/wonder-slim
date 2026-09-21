package er.extensions.routes;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXShortURLs;
import er.extensions.foundation.ERXHTTPUtilities;

/**
 * Route handling.
 * 
 * TODO: Having some docs here would be nice // Hugi 2025-10-09
 */

public class RouteTable {

	private static final Logger logger = LoggerFactory.getLogger( RouteTable.class );

	/**
	 * Invoked when no route was found to handle a given URL
	 */
	private static final NotFoundRouteHandler NOT_FOUND_ROUTE_HANDLER = new NotFoundRouteHandler();

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

	private List<Route> routes() {
		return _routes;
	}

	private RouteHandler handlerForURL( final String url ) {

		for( final Route route : routes() ) {
			if( matches( route.pattern, url ) ) {
				return route.routeHandler;
			}
		}

		return null;
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
		final String ipAddress = ERXHTTPUtilities.ipAddressFromRequest(request);
		final String userAgent = request.headerForKey( "user-agent" );

		logger.info( "Handling URL: {};{};{}", routeURL, ipAddress, userAgent );

		RouteHandler routeHandler = handlerForURL( routeURL );

		if( routeHandler == null ) {
			routeHandler = NOT_FOUND_ROUTE_HANDLER;
		}

		return routeHandler.handle( new RouteInvocation( routeURL, request ) );
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
	 * @return true if a mapped route claims the given URL (the path, without a
	 *         query string). Lets other URL handling — short URLs — defer to
	 *         explicit routes.
	 */
	public boolean hasRouteFor( final String url ) {
		return handlerForURL( url ) != null;
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
	 * handler try the request (e.g. an ng-objects handler in the same server). Same literal as
	 * WOAdaptorJetty.UNHANDLED_RESPONSE_KEY, duplicated on purpose since ERExtensions must not depend on the adaptor. Other
	 * adaptors ignore it and just serve the 404.
	 */
	public static final String UNHANDLED_RESPONSE_KEY = "wo-unhandled-response";

	/**
	 * For returning 404
	 */
	public static class NotFoundRouteHandler implements RouteHandler {
		@Override
		public WOActionResults handle( final RouteInvocation invocation ) {
			final WOResponse response = new WOResponse();
			response.setStatus( 404 );
			response.setContent( "No route found for URL: " + invocation.url() );
			response.setUserInfoForKey( "true", UNHANDLED_RESPONSE_KEY );
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