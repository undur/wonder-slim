package er.routing;

import com.webobjects.appserver.WOActionResults;

/**
 * EXPERIMENTAL (route-links branch). What a route mapped in an {@link ERXRouter} does. Returns {@link #DECLINED} to pass
 * the request on to the next matching route.
 */

@FunctionalInterface
public interface RouteHandler {

	/**
	 * Returned by a route that has no answer for the request: the next matching route gets it. The same object as the
	 * existing route table's, so a request every route declines passes on through that table too.
	 */
	public static final WOActionResults DECLINED = er.extensions.routes.RouteHandler.DECLINED;

	public WOActionResults handle( RouteInvocation invocation );
}
