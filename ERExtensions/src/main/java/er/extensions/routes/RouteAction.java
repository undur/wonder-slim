package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WODirectAction;
import com.webobjects.appserver.WORequest;

/**
 * A Direct Action that passes requests on to the default RouteTable for handling.
 *
 * The "real" URL to handle is obtained from either the query parameter [url]
 * or the [redirect_url] header passed in by Apache's 404 handler.
 */

public class RouteAction extends WODirectAction {

	public RouteAction( WORequest r ) {
		super( r );
	}

	/**
	 * @return The result of invoking the route mathing the provided URL.
	 */
	public WOActionResults handlerAction() {
		return RouteTable.defaultRouteTable().handle( request(), true );
	}
	
	@Override
	public WOActionResults defaultAction() {

		// The request's URL  doesn't have an adaptor prefix, so we set it on our context to ensure proper dynamic URL generation
		context()._url().setPrefix(WOApplication.application().adaptorPath());

		return RouteTable.defaultRouteTable().handle( request(), false );
	}
}