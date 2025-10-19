package er.extensions.routes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.appserver.ERXRequest;

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

	public WOActionResults handle( final WORequest request ) {
		final String routeURL = routeURLFromRequestParameters( request );

		final String ipAddress = ((ERXRequest)request).remoteHostAddress();
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
	 * @return Route URL usable for development (i.e. invoking the direct action directly)
	 */
	public static String urlForDevelopment( String url, final WOContext context ) {
		final NSMutableDictionary<String, Object> params = new NSMutableDictionary<>( url, "url" );
		url = context.directActionURLForActionNamed( RouteAction.class.getSimpleName() + "/handler", params );
		url = url.replace( "&", "&amp;" );
		return url;
	}

	public void map( final String pattern, final RouteHandler routeHandler ) {
		_routes.add( new Route( pattern, routeHandler ) );
	}

	@Deprecated
	public void map( final String pattern, final BiFunction<RouteURL, WOContext, WOActionResults> function ) {
		map( pattern, new BiFunctionRouteHandler( function ) );
	}
	
	public void map( final String pattern, final Function<RouteInvocation, WOActionResults> function ) {
		map( pattern, new FunctionRouteHandler( function ) );
	}

	public void map( final String pattern, final Class<? extends WOComponent> componentClass ) {
		map( pattern, new ComponentRouteHandler( componentClass ) );
	}

	/**
	 * Maps a URL pattern to a given RouteHandler
	 */
	public record Route(

			/**
			 * The pattern this route uses
			 */
			String pattern,

			/**
			 * The routeHandler that will handle requests passed to this route
			 */
			RouteHandler routeHandler ) {}

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
	
	public static class FunctionRouteHandler implements RouteHandler {
		private Function<RouteInvocation, WOActionResults> _function;
		
		public FunctionRouteHandler( final Function<RouteInvocation, WOActionResults> function ) {
			_function = function;
		}
		
		@Override
		public WOActionResults handle( RouteInvocation routeRequest ) {
			return _function.apply(routeRequest);
		}
	}

	public static class BiFunctionRouteHandler implements RouteHandler {
		private BiFunction<RouteURL, WOContext, WOActionResults> _function;

		public BiFunctionRouteHandler( final BiFunction<RouteURL, WOContext, WOActionResults> function ) {
			_function = function;
		}

		@Override
		public WOActionResults handle( RouteInvocation invocation ) {
			return _function.apply( invocation.routeURL(), invocation.request().context() );
		}
	}

	public static class ComponentRouteHandler implements RouteHandler {
		private Class<? extends WOComponent> _componentClass;

		public ComponentRouteHandler( final Class<? extends WOComponent> componentClass ) {
			_componentClass = componentClass;
		}

		@Override
		public WOActionResults handle( RouteInvocation invocation ) {
			return WOApplication.application().pageWithName( _componentClass.getName(), invocation.request().context() );
		}
	}
}