package er.extensions.routing;

import com.webobjects.appserver.WOActionResults;

/**
 * Wraps every route of a group: answers the request itself (a redirect to a login
 * page, a 403) or passes it on to the route.
 *
 * <pre>
 * admin.wrap( ( invocation, next ) -&gt; loggedIn( invocation ) ? next.handle( invocation ) : login() );
 * </pre>
 */

@FunctionalInterface
public interface RouteFilter {

	public WOActionResults filter( RouteInvocation invocation, RouteHandler next );
}
