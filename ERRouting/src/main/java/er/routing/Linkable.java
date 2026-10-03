package er.routing;

import java.util.Map;

import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXWOContext;

/**
 * EXPERIMENTAL (route-links branch). A route a link, a form or a redirect can go to: a typed route ({@link Route}) or a
 * plain one ({@link PlainRoute}). {@code <wo:route>} and {@code <wo:routeForm>} take either.
 */

public interface Linkable {

	/**
	 * @return The route's URL for parameter values by name: the route's parameters (path and host), and query parameters.
	 *         A host parameter the values don't have is the request's, if it's on the route's host. Values are converted
	 *         to URL text by the router's converters; a string is taken as text, and checked against the parameter's type.
	 */
	public String url( Map<String, Object> values, WOContext context );

	/**
	 * @return The route's URL without parameters of its own (host parameters taken from the request), in the context
	 */
	public default String url( final WOContext context ) {
		return url( Map.of(), context );
	}

	/**
	 * @return The route's URL without parameters of its own, in the current context
	 */
	public default String url() {
		return url( ERXWOContext.currentContext() );
	}
}
