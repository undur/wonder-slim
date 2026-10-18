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
		assertEquals( "10.0.0.7", ERXRequest.remoteAddress( request( null, headers ) ) );
	}

	/**
	 * As a request through mod_WebObjects arrives when the client sent x-webobjects-remote-addr itself: Apache sets
	 * remote_addr and remote_host, and passes the client's header on (#218)
	 */
	@Test
	public void throughApacheTheClientCantChooseItsAddress() throws Exception {
		final Map<String, List<String>> headers = Map.of( "remote_addr", List.of( "198.51.100.66" ), "remote_host", List.of( "198.51.100.66" ), "x-webobjects-remote-addr", List.of( "6.6.6.6" ), "x-webobjects-adaptor-version", List.of( "Apache" ) );
		assertEquals( "198.51.100.66", ERXRequest.remoteAddress( request( "127.0.0.1", headers ) ) );
	}

	/**
	 * As a request through mod_WebObjects arrives when the client sent remote_addr itself: two values, in no reliable
	 * order (#219)
	 */
	@Test
	public void severalAddressesInAnAdaptorHeaderAreUnknown() throws Exception {
		assertNull( ERXRequest.remoteAddress( request( "127.0.0.1", Map.of( "remote_addr", List.of( "6.6.6.6", "198.51.100.66" ) ) ) ) );
		assertNull( ERXRequest.remoteAddress( request( "127.0.0.1", Map.of( "remote_addr", List.of( "198.51.100.66", "6.6.6.6" ) ) ) ) );
		assertNull( ERXRequest.remoteAddress( request( "127.0.0.1", Map.of( "remote_addr", List.of( "6.6.6.6, 198.51.100.66" ) ) ) ), "joined into one value" );
	}

	/**
	 * Apache's own remote_addr, alone, is read before a remote_host the client doubled up
	 */
	@Test
	public void aSingleAddressInTheFirstHeaderIsTheAddress() throws Exception {
		assertEquals( "198.51.100.66", ERXRequest.remoteAddress( request( "127.0.0.1", Map.of( "remote_addr", List.of( "198.51.100.66" ), "remote_host", List.of( "6.6.6.6", "198.51.100.66" ) ) ) ) );
	}

	@Test
	public void theSameAddressTwiceIsThatAddress() throws Exception {
		assertEquals( "198.51.100.66", ERXRequest.remoteAddress( request( "127.0.0.1", Map.of( "remote_addr", List.of( "198.51.100.66", "198.51.100.66" ) ) ) ) );
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

	/**
	 * A front end on this machine connects from one of its addresses when the application listens on a public one
	 */
	@Test
	public void localIsThisMachinesAddresses() throws Exception {
		final ERXTrustedFrontEnds local = ERXTrustedFrontEnds.parse( "local" );

		for( final InetAddress address : ERXTrustedFrontEnds.localAddresses() ) {
			assertEquals( true, local.contains( address ), address.toString() );
			assertEquals( true, ERXTrustedFrontEnds.parse( ERXTrustedFrontEnds.DEFAULT ).contains( address ), address.toString() );
		}

		assertEquals( false, local.contains( InetAddress.getByName( "203.0.113.9" ) ) );
	}

	@Test
	public void fromThisMachine() throws Exception {
		assertEquals( true, ERXRequest.isFromThisMachine( request( "127.0.0.1", Map.of() ) ) );
		assertEquals( true, ERXRequest.isFromThisMachine( request( "::1", Map.of() ) ) );

		for( final InetAddress address : ERXTrustedFrontEnds.localAddresses() ) {
			final WORequest request = new WORequest( "GET", "/", "HTTP/1.1", Map.of(), null, null );
			request._setOriginatingAddress( address );
			assertEquals( true, ERXRequest.isFromThisMachine( request ), address.toString() );
		}
	}

	@Test
	public void notFromThisMachine() throws Exception {
		assertEquals( false, ERXRequest.isFromThisMachine( request( "203.0.113.9", Map.of() ) ) );
		assertEquals( false, ERXRequest.isFromThisMachine( request( "10.0.0.2", Map.of() ) ), "a private network is other machines" );
		assertEquals( false, ERXRequest.isFromThisMachine( request( null, Map.of() ) ), "a request the application built itself" );
	}

	@Test
	public void throughAFrontEndOnThisMachine() throws Exception {
		assertEquals( false, ERXRequest.isFromThisMachine( request( "127.0.0.1", Map.of( "x-webobjects-adaptor-version", List.of( "Apache" ) ) ) ) );
		assertEquals( false, ERXRequest.isFromThisMachine( request( "127.0.0.1", Map.of( "x-forwarded-for", List.of( "198.51.100.66" ) ) ) ) );
		assertEquals( false, ERXRequest.isFromThisMachine( request( "127.0.0.1", Map.of( "forwarded", List.of( "for=198.51.100.66" ) ) ) ) );
		assertEquals( false, ERXRequest.isFromThisMachine( request( "127.0.0.1", Map.of( "x-real-ip", List.of( "198.51.100.66" ) ) ) ) );
	}

	@Test
	public void trustedFrontEndsRefusesWhatIsntAnAddress() {
		assertThrows( IllegalArgumentException.class, () -> ERXTrustedFrontEnds.parse( "proxy.example.com" ) );
		assertThrows( IllegalArgumentException.class, () -> ERXTrustedFrontEnds.parse( "10.0.0.0/33" ) );
	}
}
