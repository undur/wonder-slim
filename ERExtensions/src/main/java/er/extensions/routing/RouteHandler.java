package er.extensions.routing;

import com.webobjects.appserver.WOActionResults;

/**
 * What a route mapped in an {@link ERXRouter} does, and its fallback and not found handler. Returns {@link #DECLINED}
 * to pass the request on: to the next matching route, then the fallback, then the not found handler.
 */

@FunctionalInterface
public interface RouteHandler {

	/**
	 * Returned by a handler that has no answer for the request, which passes it on. Not a response, so never handed to
	 * WebObjects.
	 */
	public static final WOActionResults DECLINED = () -> {
		throw new IllegalStateException( "RouteHandler.DECLINED isn't a response: the router passes a declined request on instead of answering with it" );
	};

	public WOActionResults handle( RouteInvocation invocation );
}
