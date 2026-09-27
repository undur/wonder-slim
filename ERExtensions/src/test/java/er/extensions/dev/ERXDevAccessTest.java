package er.extensions.dev;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;

public class ERXDevAccessTest {

	private static WORequest request( final String originatingAddress, final Map<String, List<String>> headers ) throws Exception {
		final WORequest request = new WORequest( "GET", "/", "HTTP/1.1", headers, null, null );

		if( originatingAddress != null ) {
			request._setOriginatingAddress( InetAddress.getByName( originatingAddress ) );
		}

		return request;
	}

	@Test
	public void loopbackIsAllowed() throws Exception {
		assertTrue( ERXDevAccess.isFromThisMachine( request( "127.0.0.1", Map.of() ) ) );
		assertTrue( ERXDevAccess.isFromThisMachine( request( "::1", Map.of() ) ) );
	}

	@Test
	public void otherAddressesAreRefused() throws Exception {
		assertFalse( ERXDevAccess.isFromThisMachine( request( "172.16.1.111", Map.of() ) ) );
	}

	@Test
	public void unknownAddressIsRefused() throws Exception {
		assertFalse( ERXDevAccess.isFromThisMachine( request( null, Map.of() ) ) );
	}

	@Test
	public void headersDontGrantAccess() throws Exception {
		final Map<String, List<String>> headers = Map.of( "remote_host", List.of( "127.0.0.1" ), "x-webobjects-remote-addr", List.of( "127.0.0.1" ), "x-forwarded-for", List.of( "127.0.0.1" ) );
		assertFalse( ERXDevAccess.isFromThisMachine( request( "172.16.1.111", headers ) ) );
		assertFalse( ERXDevAccess.isFromThisMachine( request( null, headers ) ) );
	}

	@Test
	public void forwardedRequestsAreRefused() throws Exception {
		assertFalse( ERXDevAccess.isFromThisMachine( request( "127.0.0.1", Map.of( "x-forwarded-for", List.of( "203.0.113.4" ) ) ) ) );
		assertFalse( ERXDevAccess.isFromThisMachine( request( "127.0.0.1", Map.of( "remote_addr", List.of( "203.0.113.4" ) ) ) ) );
		assertFalse( ERXDevAccess.isFromThisMachine( request( "127.0.0.1", Map.of( "x-webobjects-remote-addr", List.of( "127.0.0.1" ) ) ) ) );
		assertFalse( ERXDevAccess.isFromThisMachine( request( "::1", Map.of( "forwarded", List.of( "for=203.0.113.4" ) ) ) ) );
	}
}
