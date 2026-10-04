package er.routing.options;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;


/**
 * Which other sites' scripts may call a route
 */
public class CrossOriginTest {

	private static RouteRequest request( final String method, final Map<String, String> headers ) {
		return new RouteRequest( method, "acme.localhost", "/api/books", false, headers::get );
	}

	@Test
	public void anOriginIsAllowedByName() {
		final CrossOrigin partner = CrossOrigin.allow( "https://Partner.example:443" );

		assertTrue( partner.allows( request( "GET", Map.of( "origin", "https://partner.example:443" ) ) ) );
		assertFalse( partner.allows( request( "GET", Map.of( "origin", "https://evil.example" ) ) ) );
		assertFalse( partner.allows( request( "GET", Map.of() ) ) );
		assertFalse( CrossOrigin.ANY.allows( request( "GET", Map.of( "origin", "null" ) ) ) );
		assertTrue( CrossOrigin.ANY.allows( request( "GET", Map.of( "origin", "https://anyone.example" ) ) ) );
	}

	@Test
	public void aPreflightIsOptionsWithARequestedMethod() {
		assertTrue( CrossOrigin.isPreflight( request( "OPTIONS", Map.of( "origin", "https://partner.example", "access-control-request-method", "POST" ) ) ) );
		assertFalse( CrossOrigin.isPreflight( request( "OPTIONS", Map.of( "origin", "https://partner.example" ) ) ) );
		assertFalse( CrossOrigin.isPreflight( request( "POST", Map.of( "origin", "https://partner.example", "access-control-request-method", "POST" ) ) ) );
	}

	@Test
	public void anyOriginHasNoCredentials() {
		assertThrows( IllegalStateException.class, CrossOrigin.ANY::withCredentials );
		CrossOrigin.allow( "https://partner.example" ).withCredentials();
		assertThrows( IllegalArgumentException.class, () -> CrossOrigin.allow( "partner.example" ) );
		assertThrows( IllegalArgumentException.class, () -> CrossOrigin.allow( "https://partner.example/path" ) );
	}

	@Test
	public void onlyNamedOriginsMayPost() {
		assertTrue( CrossOrigin.allow( "https://partner.example" ).waivesCrossSite() );
		assertFalse( CrossOrigin.ANY.waivesCrossSite() );
	}
}
