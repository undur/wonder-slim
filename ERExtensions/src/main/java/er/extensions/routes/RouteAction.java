package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WORequest;

import er.extensions.appserver.ERXDirectAction;

/**
 * A Direct Action that passes requests on to the default RouteTable for handling.
 *
 * The "real" URL to handle is obtained from either the query parameter [url]
 * or the [redirect_url] header passed in by Apache's 404 handler.
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