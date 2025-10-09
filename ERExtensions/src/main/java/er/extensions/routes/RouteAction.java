package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WORequest;

import er.extensions.appserver.ERXDirectAction;

/**
 * A Direct Action class that takes care of passing requests on to a RouteHandler.
 *
 * URLs are be passed to the handler action either by query parameter ("url")
 * or are retrieved from the redirect_url header passed in by Apache's 404 handler.
 */

public class RouteAction extends ERXDirectAction {

	public RouteAction( WORequest r ) {
		super( r );
	}

	/**
	 * @return The result of invoking the route mathing the provided URL.
	 */
	public WOActionResults handlerAction() {
		return RouteTable.defaultRouteTable().handle( request() );
	}
}