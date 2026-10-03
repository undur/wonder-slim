package er.routing;

import com.webobjects.appserver.WOActionResults;

/**
 * EXPERIMENTAL (route-links branch). A route's parameter record that does the route's work itself, see
 * {@link RouteGroup#route(String, Class, er.routing.core.RouteOption...)}.
 */

public interface Routable {

	/**
	 * @return The answer to a request for the route, with these parameters
	 */
	public WOActionResults invoke( RouteInvocation invocation );
}
