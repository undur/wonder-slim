package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;

/**
 * Where a request came from (#67): a front end's headers are believed only from a trusted one
 */
public class ERXRequestOriginTest {

	private static final String FRONT_END = "127.0.0.1";
	private static final String STRANGER = "203.0.113.9";

	private static WORequest request( final String connection, final Map<String, List<String>> headers ) throws Exception {
		final WORequest request = new WORequest( "GET", "/", "HTTP/1.1", headers, null, null );

		if( connection != null ) {
			request._setOriginatingAddress( InetAddress.getByName( connection ) );
		}

		return request;
	}

	@Test
	public void theClientAddressFromATrustedFrontEnd() throws Exception {
		assertEquals( "198.51.100.7", ERXRequestOrigin.clientAddress( request( FRONT_END, Map.of( "x-webobjects-remote-addr", List.of( "198.51.100.7" ), "x-forwarded-for", List.of( "10.9.9.9" ) ) ) ) );

		// The last entry: the one the trusted front end added, not the client's claim at the start
		assertEquals( "198.51.100.7", ERXRequestOrigin.clientAddress( request( FRONT_END, Map.of( "x-forwarded-for", List.of( "6.6.6.6, 198.51.100.7" ) ) ) ) );
		assertEquals( FRONT_END, ERXRequestOrigin.clientAddress( request( FRONT_END, Map.of() ) ) );
	}

	@Test
	public void aStrangersHeadersArentBelieved() throws Exception {
		final Map<String, List<String>> claims = Map.of( "x-webobjects-remote-addr", List.of( "6.6.6.6" ), "x-forwarded-for", List.of( "6.6.6.6" ), "x-forwarded-host", List.of( "evil.example" ), "x-forwarded-proto", List.of( "https" ), "https", List.of( "on" ), "host", List.of( "acme.example.com" ) );
		final WORequest request = request( STRANGER, claims );

		assertEquals( STRANGER, ERXRequestOrigin.clientAddress( request ) );
		assertEquals( "acme.example.com", ERXRequestOrigin.host( request ) );
		assertFalse( ERXRequestOrigin.secure( request ) );

		// Nor when the connection isn't known
		assertFalse( ERXRequestOrigin.secure( request( null, claims ) ) );
		assertNull( ERXRequestOrigin.clientAddress( request( null, claims ) ) );
	}

	@Test
	public void theHostFromATrustedFrontEnd() throws Exception {
		assertEquals( "acme.example.com", ERXRequestOrigin.host( request( FRONT_END, Map.of( "x-webobjects-server-name", List.of( "acme.example.com" ), "x-forwarded-host", List.of( "other.example.com" ), "host", List.of( "localhost:1200" ) ) ) ) );
		assertEquals( "other.example.com", ERXRequestOrigin.host( request( FRONT_END, Map.of( "x-forwarded-host", List.of( "other.example.com" ), "host", List.of( "localhost:1200" ) ) ) ) );
		assertEquals( "localhost:1200", ERXRequestOrigin.host( request( FRONT_END, Map.of( "host", List.of( "localhost:1200" ) ) ) ) );
	}

	@Test
	public void httpsFromATrustedFrontEnd() throws Exception {
		assertTrue( ERXRequestOrigin.secure( request( FRONT_END, Map.of( "https", List.of( "on" ) ) ) ) );
		assertFalse( ERXRequestOrigin.secure( request( FRONT_END, Map.of( "https", List.of( "off" ), "x-forwarded-proto", List.of( "https" ) ) ) ) );
		assertTrue( ERXRequestOrigin.secure( request( FRONT_END, Map.of( "x-forwarded-proto", List.of( "https" ) ) ) ) );
		assertTrue( ERXRequestOrigin.secure( request( FRONT_END, Map.of( "x-webobjects-server-port", List.of( "443" ) ) ) ) );
		assertFalse( ERXRequestOrigin.secure( request( FRONT_END, Map.of() ) ) );
	}

	@Test
	public void trustedNetworks() throws Exception {
		final List<ERXRequestOrigin.Network> networks = ERXRequestOrigin.parse( "10.0.0.0/8, 192.168.1.5, fc00::/7" );

		assertTrue( networks.get( 0 ).contains( InetAddress.getByName( "10.200.3.4" ) ) );
		assertFalse( networks.get( 0 ).contains( InetAddress.getByName( "11.0.0.1" ) ) );
		assertTrue( networks.get( 1 ).contains( InetAddress.getByName( "192.168.1.5" ) ) );
		assertFalse( networks.get( 1 ).contains( InetAddress.getByName( "192.168.1.6" ) ) );
		assertTrue( networks.get( 2 ).contains( InetAddress.getByName( "fd12:3456::1" ) ) );
		assertTrue( networks.get( 0 ).contains( InetAddress.getByName( "::ffff:10.1.2.3" ) ) );

		// A name isn't an address, and isn't looked up
		assertThrows( IllegalArgumentException.class, () -> ERXRequestOrigin.parse( "frontend.example.com" ) );
		assertThrows( IllegalArgumentException.class, () -> ERXRequestOrigin.parse( "10.0.0.0/33" ) );
	}
}
