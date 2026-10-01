package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;

/**
 * A little like a request handler, but for handling RouteInvocations.
 *
 * A handler answers a URL, or returns {@link #DECLINED} when it has no answer for it, and the route table passes the URL
 * on to the next handler in its chain (see {@link RouteTable}).
 */

@FunctionalInterface
public interface RouteHandler {

	/**
	 * Returned by a handler that has no answer for the URL it was given: the route table passes the URL on to the next
	 * handler in its chain. Not a response, so never handed to WebObjects.
	 */
	public static final WOActionResults DECLINED = () -> {
		throw new IllegalStateException( "RouteHandler.DECLINED isn't a response. The route table passes a declined URL on instead of answering with it" );
	};

	/**
	 * @return The answer to the invocation's URL, or {@link #DECLINED} to pass it on. Never null.
	 */
	public abstract WOActionResults handle( final RouteInvocation invocation );
}
