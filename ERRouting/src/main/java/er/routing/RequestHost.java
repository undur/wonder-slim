package er.routing;

import com.webobjects.appserver.WORequest;

import er.extensions.appserver.ERXRequestOrigin;

/**
 * EXPERIMENTAL (route-links branch). The host a request was made to, as the framework determines it in one place
 * ({@link ERXRequestOrigin}, #67): a trusted front end's forwarded host, or the request's {@code Host}.
 */

public class RequestHost {

	private RequestHost() {}

	/**
	 * @return The host the request was made to (possibly with a port), null if none is known
	 */
	public static String host( final WORequest request ) {
		return ERXRequestOrigin.host( request );
	}
}
