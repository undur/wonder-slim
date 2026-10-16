package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
	public void lastForwardedForAddress() throws Exception {
		final Map<String, List<String>> headers = Map.of( "x-forwarded-for", List.of( "198.51.100.66, 203.0.113.4" ) );
		assertEquals( "203.0.113.4", ERXRequest.remoteAddress( request( "127.0.0.1", headers ) ), "the first entry is whatever the client sent" );
	}

	@Test
	public void forwardedForSkipsTrustedProxies() throws Exception {
		final Map<String, List<String>> headers = Map.of( "x-forwarded-for", List.of( "198.51.100.66, 203.0.113.4, 10.0.0.2" ) );
		assertEquals( "203.0.113.4", ERXRequest.remoteAddress( request( "127.0.0.1", headers ) ) );
	}

	@Test
	public void forwardedForAcrossSeveralHeaders() throws Exception {
		final Map<String, List<String>> headers = Map.of( "x-forwarded-for", List.of( "198.51.100.66", "203.0.113.4" ) );
		assertEquals( "203.0.113.4", ERXRequest.remoteAddress( request( "127.0.0.1", headers ) ) );
	}

	@Test
	public void forwardedForOnlyTrustedAddresses() throws Exception {
		final Map<String, List<String>> headers = Map.of( "x-forwarded-for", List.of( "192.168.1.20, 10.0.0.2" ) );
		assertEquals( "192.168.1.20", ERXRequest.remoteAddress( request( "127.0.0.1", headers ) ), "a client on the private network" );
	}

	@Test
	public void headersFromAnUntrustedConnectionAreIgnored() throws Exception {
		final Map<String, List<String>> headers = Map.of( "x-webobjects-remote-addr", List.of( "6.6.6.6" ), "x-forwarded-for", List.of( "6.6.6.6" ) );
		assertEquals( "203.0.113.9", ERXRequest.remoteAddress( request( "203.0.113.9", headers ) ) );
	}

	@Test
	public void connectionAddressWithoutHeaders() throws Exception {
		assertEquals( "172.16.1.111", ERXRequest.remoteAddress( request( "172.16.1.111", Map.of() ) ) );
	}

	@Test
	public void nullWhenNothingIsKnown() throws Exception {
		assertNull( ERXRequest.remoteAddress( request( null, Map.of() ) ) );
	}

	@Test
	public void trustedFrontEnds() throws Exception {
		final ERXTrustedFrontEnds defaults = ERXTrustedFrontEnds.parse( ERXTrustedFrontEnds.DEFAULT );
		assertEquals( true, defaults.contains( InetAddress.getByName( "127.0.0.1" ) ) );
		assertEquals( true, defaults.contains( InetAddress.getByName( "::1" ) ) );
		assertEquals( true, defaults.contains( InetAddress.getByName( "10.1.2.3" ) ) );
		assertEquals( true, defaults.contains( InetAddress.getByName( "172.31.0.1" ) ) );
		assertEquals( true, defaults.contains( InetAddress.getByName( "192.168.0.1" ) ) );
		assertEquals( true, defaults.contains( InetAddress.getByName( "fd12::1" ) ) );
		assertEquals( false, defaults.contains( InetAddress.getByName( "172.32.0.1" ) ) );
		assertEquals( false, defaults.contains( InetAddress.getByName( "203.0.113.9" ) ) );
		assertEquals( false, defaults.contains( InetAddress.getByName( "2001:db8::1" ) ) );

		final ERXTrustedFrontEnds configured = ERXTrustedFrontEnds.parse( "203.0.113.0/25, 198.51.100.7" );
		assertEquals( true, configured.contains( InetAddress.getByName( "203.0.113.127" ) ) );
		assertEquals( false, configured.contains( InetAddress.getByName( "203.0.113.128" ) ) );
		assertEquals( true, configured.contains( InetAddress.getByName( "198.51.100.7" ) ) );
		assertEquals( false, configured.contains( InetAddress.getByName( "127.0.0.1" ) ), "only what's listed" );

		assertEquals( true, ERXTrustedFrontEnds.parse( "all" ).contains( InetAddress.getByName( "203.0.113.9" ) ) );
	}

	@Test
	public void trustedFrontEndsRefusesWhatIsntAnAddress() {
		assertThrows( IllegalArgumentException.class, () -> ERXTrustedFrontEnds.parse( "proxy.example.com" ) );
		assertThrows( IllegalArgumentException.class, () -> ERXTrustedFrontEnds.parse( "10.0.0.0/33" ) );
	}
}
