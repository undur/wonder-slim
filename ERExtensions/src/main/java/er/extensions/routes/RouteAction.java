package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WODirectAction;
import com.webobjects.appserver.WORequest;

/**
 * The direct action RouteRequestHandler invokes: passes the request on to the default RouteTable.
 */

public class RouteAction extends WODirectAction {

	public RouteAction( WORequest r ) {
		super( r );
	}

	/**
	 * Legacy entry for front ends that pass the route URL in the "url" query parameter or Apache's redirect_url header.
	 *
	 * @return The result of invoking the route matching the provided URL.
	 */
	public WOActionResults handlerAction() {
		return RouteTable.defaultRouteTable().handle( request(), true );
	}

	@Override
	public WOActionResults defaultAction() {
		return RouteTable.defaultRouteTable().handle( request(), RouteRequestHandler.routePath( request() ) );
	}
}
