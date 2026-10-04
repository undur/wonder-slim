package er.routing;

import java.util.Map;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

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
	 * @return The route's URL for parameter values by name, in the current context
	 */
	public default String url( final Map<String, Object> values ) {
		return url( values, ERXWOContext.currentContext() );
	}

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

	/**
	 * @return The route's complete URL for parameter values by name, without a request (a background job's email): at the
	 *         application's public address ({@code er.routing.publicAddress}), which must be set. Host parameters are given, since
	 *         there's no request to take them from.
	 */
	public String completeURL( Map<String, Object> values );

	/**
	 * @return A redirect to the route ({@code 303 See Other}) for parameter values by name: what a form's post answers
	 *         with (post, redirect, get)
	 */
	public default WOResponse redirect( final Map<String, Object> values, final WOContext context ) {
		return RouteURLs.seeOther( url( values, context ) );
	}

	/**
	 * @return A redirect to the route ({@code 303 See Other}) for parameter values by name, in the current context
	 */
	public default WOResponse redirect( final Map<String, Object> values ) {
		return redirect( values, ERXWOContext.currentContext() );
	}

	/**
	 * @return A redirect to the route ({@code 303 See Other}) without parameters of its own
	 */
	public default WOResponse redirect( final WOContext context ) {
		return redirect( Map.of(), context );
	}
}
