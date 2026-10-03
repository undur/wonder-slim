package er.extensions.experimental.routing;

import com.webobjects.appserver.WOActionResults;

/**
 * EXPERIMENTAL (route-links branch). An endpoint's parameter record that does the route's work itself, see
 * {@link RouteGroup#endpoint(String, Class, er.extensions.experimental.routing.core.RouteCondition...)}.
 */

public interface Routable {

	/**
	 * @return The answer to a request for the route, with these parameters
	 */
	public WOActionResults invoke( RoutedInvocation invocation );
}
