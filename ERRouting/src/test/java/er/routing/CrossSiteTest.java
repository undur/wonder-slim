package er.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSMutableDictionary;

/**
 * Which requests are refused as coming from another site
 */
public class CrossSiteTest {

	private static final String HOST = "acme.localhost:1300";

	private static WORequest request( final String method, final Map<String, String> headers ) {
		final NSMutableDictionary<String, NSArray<String>> all = new NSMutableDictionary<>();
		headers.forEach( ( name, value ) -> all.setObjectForKey( new NSArray<>( value ), name ) );
		return new WORequest( method, "/books", "HTTP/1.1", all, null, null );
	}

	private static boolean refused( final String method, final Map<String, String> headers ) {
		return CrossSite.refused( request( method, headers ), HOST, null );
	}

	@Test
	public void aPostFromAnotherSiteIsRefused() {
		assertTrue( refused( "POST", Map.of( "sec-fetch-site", "cross-site" ) ) );
		assertTrue( refused( "POST", Map.of( "origin", "https://evil.example" ) ) );
		assertTrue( refused( "POST", Map.of( "origin", "null" ) ) );

		// Another club is another site, though it's the same site to the browser
		assertTrue( refused( "POST", Map.of( "sec-fetch-site", "same-site" ) ) );
		assertTrue( refused( "POST", Map.of( "origin", "http://kronan.localhost:1300" ) ) );
	}

	@Test
	public void aPostFromTheSamePageOrNoPageIsTaken() {
		assertFalse( refused( "POST", Map.of( "sec-fetch-site", "same-origin" ) ) );
		assertFalse( refused( "POST", Map.of( "sec-fetch-site", "none" ) ) );
		assertFalse( refused( "POST", Map.of( "origin", "http://ACME.localhost:1300" ) ) );

		// The scheme isn't compared: behind a front end terminating TLS, the application sees http
		assertFalse( refused( "POST", Map.of( "origin", "https://acme.localhost:1300" ) ) );

		// Not from a browser page: curl, a server's webhook
		assertFalse( refused( "POST", Map.of() ) );
	}

	@Test
	public void readingIsNeverRefused() {
		assertFalse( refused( "GET", Map.of( "sec-fetch-site", "cross-site" ) ) );
		assertFalse( refused( "HEAD", Map.of( "origin", "https://evil.example" ) ) );
	}

	@Test
	public void thePublicAddressIsTheApplicationsOwn() {
		final WORequest request = request( "POST", Map.of( "origin", "https://bookclubs.example.com" ) );

		assertFalse( CrossSite.refused( request, "localhost:1300", PublicAddress.parse( "https://bookclubs.example.com" ) ) );
		assertTrue( CrossSite.refused( request, "localhost:1300", null ) );
	}
}
