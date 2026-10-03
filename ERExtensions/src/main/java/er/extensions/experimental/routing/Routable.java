package er.extensions.experimental.routing;

import com.webobjects.appserver.WOActionResults;

import er.extensions.routes.RouteInvocation;

/**
 * EXPERIMENTAL (route-links branch). An endpoint's parameter record that does the route's work itself, see
 * {@link Endpoint#of(String, Class)}.
 */

public interface Routable {

	/**
	 * @return The answer to a request for the route, with these parameters
	 */
	public WOActionResults invoke( RouteInvocation invocation );
}
