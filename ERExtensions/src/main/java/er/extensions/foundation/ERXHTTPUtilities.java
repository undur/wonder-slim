package er.extensions.foundation;

import com.webobjects.appserver.WORequest;

/**
 * FIXME: Introduced as a temporary fix until we have a good look at ERXREquest.remoteHostAddress() // Hugi 2026-03-30
 */

public class ERXHTTPUtilities {

	private static final String HEADER_REMOTE_HOST = "remote_host";
	private static final String HEADER_REMOTE_ADDR = "remote_addr";
	private static final String HEADER_REMOTE_USER = "remote_user";
	private static final String HEADER_WEBOBJECTS_REMOTE_ADDR = "x-webobjects-remote-addr";

	/**
	 * @return The IP-address that initiated the WORequest (if present)
	 */
	@Deprecated
	public static String ipAddressFromRequest( final WORequest request ) {
		String host = request.headerForKey( HEADER_REMOTE_HOST );

		if( host != null ) {
			return host;
		}

		host = request.headerForKey( HEADER_REMOTE_ADDR );

		if( host != null ) {
			return host;
		}

		host = request.headerForKey( HEADER_REMOTE_USER );

		if( host != null ) {
			return host;
		}

		host = request.headerForKey( HEADER_WEBOBJECTS_REMOTE_ADDR );

		if( host != null ) {
			return host;
		}

		return null;
	}

}