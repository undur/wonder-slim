package er.extensions.dev;

import java.net.InetAddress;
import java.util.List;

import com.webobjects.appserver.WORequest;

/**
 * Access control for the development endpoints (eval, the captured console log, runtime problems): they only answer
 * requests from this machine.
 *
 * Decided from the address of the connection the request came in on, as the adaptor recorded it, never from request
 * headers, which the client controls. When that address isn't known, access is refused.
 *
 * That address is the connection's peer, so a proxy or web server adaptor on this machine makes every request it
 * forwards look local. Such requests carry the headers proxies and WO adaptors add, and are refused when they do.
 * (Headers can only make the check stricter: a client adding them locks itself out.) A forwarder that adds no
 * headers, such as an ssh tunnel, can't be told apart.
 */

public class ERXDevAccess {

	/**
	 * Headers added by reverse proxies and WO's web server adaptors, naming the client they forward for
	 */
	private static final List<String> FORWARDING_HEADERS = List.of( "x-forwarded-for", "forwarded", "x-real-ip", "x-webobjects-remote-addr", "remote_addr", "remote_host" );

	private ERXDevAccess() {}

	/**
	 * @return true if the request came in on a connection from this machine (a loopback address), not forwarded by a proxy
	 */
	public static boolean isFromThisMachine( final WORequest request ) {
		final InetAddress address = request._originatingAddress();

		if( address == null || !address.isLoopbackAddress() ) {
			return false;
		}

		for( final String header : FORWARDING_HEADERS ) {
			if( request.headerForKey( header ) != null ) {
				return false;
			}
		}

		return true;
	}
}
