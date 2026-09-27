package er.extensions.dev;

import java.net.InetAddress;

import com.webobjects.appserver.WORequest;

/**
 * Access control for the development endpoints (eval, the captured console log, runtime problems): they only answer
 * requests from this machine.
 *
 * Decided from the address of the connection the request came in on, as the adaptor recorded it, never from request
 * headers, which the client controls. When that address isn't known, access is refused.
 */

public class ERXDevAccess {

	private ERXDevAccess() {}

	/**
	 * @return true if the request came in on a connection from this machine (a loopback address)
	 */
	public static boolean isFromThisMachine( final WORequest request ) {
		final InetAddress address = request._originatingAddress();
		return address != null && address.isLoopbackAddress();
	}
}
