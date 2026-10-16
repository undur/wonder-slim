package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;

public class ERXRequestSecureTest {

	private static boolean secure( final Map<String, List<String>> headers ) {
		return ERXRequest.isRequestSecure( new WORequest( "GET", "/", "HTTP/1.1", headers, null, null ) );
	}

	@Test
	public void httpsHeader() {
		assertTrue( secure( Map.of( "https", List.of( "on" ) ) ) );
		assertFalse( secure( Map.of( "https", List.of( "off" ) ) ) );
	}

	@Test
	public void serverPort443() {
		assertTrue( secure( Map.of( "SERVER_PORT", List.of( "443" ) ) ) );
		assertTrue( secure( Map.of( "x-webobjects-server-port", List.of( "443" ) ) ) );
		assertFalse( secure( Map.of( "SERVER_PORT", List.of( "80" ) ) ) );
	}

	@Test
	public void forwardedProto() {
		assertTrue( secure( Map.of( "x-forwarded-proto", List.of( "https" ) ) ) );
		assertFalse( secure( Map.of( "x-forwarded-proto", List.of( "http" ) ) ) );
	}

	@Test
	public void plainRequest() {
		assertFalse( secure( Map.of() ) );
	}

	@Test
	public void fromATrustedFrontEnd() throws Exception {
		final WORequest request = new WORequest( "GET", "/", "HTTP/1.1", Map.of( "https", List.of( "on" ) ), null, null );
		request._setOriginatingAddress( InetAddress.getByName( "10.0.0.2" ) );
		assertTrue( ERXRequest.isRequestSecure( request ) );
	}

	@Test
	public void notFromAnUntrustedConnection() throws Exception {
		final WORequest request = new WORequest( "GET", "/", "HTTP/1.1", Map.of( "https", List.of( "on" ), "x-forwarded-proto", List.of( "https" ) ), null, null );
		request._setOriginatingAddress( InetAddress.getByName( "203.0.113.9" ) );
		assertFalse( ERXRequest.isRequestSecure( request ) );
	}
}
