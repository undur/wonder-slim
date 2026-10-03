package er.routing;

import com.webobjects.appserver.WORequest;

/**
 * EXPERIMENTAL (route-links branch). The host a request was made to: the {@code Host} header.
 *
 * The framework must determine the host in exactly one place, used by everything that needs it (routing, URL
 * generation, ERXRequest), and whether a forwarded header from a front end counts is decided there, with #67. This is
 * that place for the router until it converges, when it moves to ERXRequest.
 */

public class RequestHost {

	private RequestHost() {}

	/**
	 * @return The host the request was made to, as the client sent it (possibly with a port), null if it sent none
	 */
	public static String host( final WORequest request ) {
		return request.headerForKey( "host" );
	}
}
