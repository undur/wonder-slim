package er.extensions.experimental.routing;

import com.webobjects.appserver.WOActionResults;

import er.extensions.routes.RouteHandler;

/**
 * EXPERIMENTAL (route-links branch). What a route mapped in an {@link ERXRouter} does. Returns
 * {@link RouteHandler#DECLINED} to pass the request on to the next matching route.
 */

@FunctionalInterface
public interface RoutedHandler {

	public WOActionResults handle( RoutedInvocation invocation );
}
