package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;

import er.extensions.appserver.ajax.ERXAjaxApplication;

/**
 * The port complete URLs use: a web server's header only from a trusted front end (#215)
 */
public class ERXRequestPortTest {

	static class App extends ERXAjaxApplication {}

	@BeforeAll
	static void createApplication() {
		System.setProperty( "WOApplicationName", "App" );

		try {
			new App();
		}
		finally {
			System.clearProperty( "WOApplicationName" );
		}
	}

	private static String port( final String originatingAddress ) throws Exception {
		final Map<String, List<String>> headers = Map.of( "x-webobjects-adaptor-version", List.of( "Modulo" ), "x-webobjects-server-port", List.of( "443" ) );
		final ERXRequest request = new ERXRequest( "GET", "/", "HTTP/1.1", headers, null, null );
		request._setOriginatingAddress( InetAddress.getByName( originatingAddress ) );
		return request._serverPort();
	}

	@Test
	public void fromATrustedFrontEnd() throws Exception {
		assertEquals( "443", port( "10.0.0.2" ) );
	}

	@Test
	public void notFromAnUntrustedConnection() throws Exception {
		assertNotEquals( "443", port( "203.0.113.9" ) );
	}
}
