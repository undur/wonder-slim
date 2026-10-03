package er.extensions.experimental.routing;

import com.webobjects.appserver.WOActionResults;

/**
 * EXPERIMENTAL (route-links branch). Wraps every route of a group: answers the request itself (a redirect to a login
 * page, a 403) or passes it on to the route.
 *
 * <pre>
 * admin.wrap( ( invocation, next ) -&gt; loggedIn( invocation ) ? next.handle( invocation ) : login() );
 * </pre>
 */

@FunctionalInterface
public interface RouteFilter {

	public WOActionResults filter( RoutedInvocation invocation, RoutedHandler next );
}
