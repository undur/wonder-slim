package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;

public class ERXRequestRemoteAddressTest {

	private static WORequest request( final String originatingAddress, final Map<String, List<String>> headers ) throws Exception {
		final WORequest request = new WORequest( "GET", "/", "HTTP/1.1", headers, null, null );

		if( originatingAddress != null ) {
			request._setOriginatingAddress( InetAddress.getByName( originatingAddress ) );
		}

		return request;
	}

	@Test
	public void adaptorHeaderComesFirst() throws Exception {
		final Map<String, List<String>> headers = Map.of( "remote_addr", List.of( "10.0.0.5" ), "x-forwarded-for", List.of( "10.0.0.9" ) );
		assertEquals( "10.0.0.5", ERXRequest.remoteAddress( request( "127.0.0.1", headers ) ) );
	}

	@Test
	public void adaptorHeadersInOrder() throws Exception {
		final Map<String, List<String>> headers = Map.of( "remote_host", List.of( "10.0.0.7" ), "x-webobjects-remote-addr", List.of( "10.0.0.6" ) );
		assertEquals( "10.0.0.6", ERXRequest.remoteAddress( request( null, headers ) ) );
	}

	@Test
	public void firstForwardedForAddress() throws Exception {
		final Map<String, List<String>> headers = Map.of( "x-forwarded-for", List.of( "203.0.113.4, 10.0.0.2" ) );
		assertEquals( "203.0.113.4", ERXRequest.remoteAddress( request( "127.0.0.1", headers ) ) );
	}

	@Test
	public void connectionAddressWithoutHeaders() throws Exception {
		assertEquals( "172.16.1.111", ERXRequest.remoteAddress( request( "172.16.1.111", Map.of() ) ) );
	}

	@Test
	public void nullWhenNothingIsKnown() throws Exception {
		assertNull( ERXRequest.remoteAddress( request( null, Map.of() ) ) );
	}
}
