package er.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import er.routing.core.RouteRequest;

/**
 * Which requests are refused as coming from another site
 */
public class CrossSiteTest {

	private static final String HOST = "acme.localhost:1300";

	private static RouteRequest request( final String method, final Map<String, String> headers ) {
		return new RouteRequest( method, HOST, "/books", false, headers::get );
	}

	private static boolean refused( final String method, final Map<String, String> headers ) {
		return CrossSite.SAME_ORIGIN.refuses( request( method, headers ), HOST, null, host -> false );
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
		final RouteRequest request = request( "POST", Map.of( "origin", "https://bookclubs.example.com" ) );

		assertFalse( CrossSite.SAME_ORIGIN.refuses( request, "localhost:1300", PublicAddress.parse( "https://bookclubs.example.com" ), host -> false ) );
		assertTrue( CrossSite.SAME_ORIGIN.refuses( request, "localhost:1300", null, host -> false ) );
	}

	@Test
	public void theBrowsersVerdictDecidesForTheSameOrigin() {

		// The same host and port, another scheme: an http page posting over https
		final RouteRequest otherScheme = request( "POST", Map.of( "sec-fetch-site", "same-site", "origin", "http://" + HOST ) );

		assertTrue( CrossSite.SAME_ORIGIN.refuses( otherScheme, HOST, null, host -> false ) );
		assertFalse( CrossSite.OWN_HOSTS.refuses( otherScheme, HOST, null, host -> false ) );
	}

	@Test
	public void ownHostsTakesTheApplicationsOtherHosts() {
		final java.util.function.Predicate<String> ownHost = host -> host.endsWith( ".localhost" ) || host.equals( "localhost" );
		final RouteRequest fromLanding = request( "POST", Map.of( "sec-fetch-site", "same-site", "origin", "http://localhost:1300" ) );
		final RouteRequest fromElsewhere = request( "POST", Map.of( "sec-fetch-site", "cross-site", "origin", "https://evil.example" ) );
		final RouteRequest sameSiteWithoutOrigin = request( "POST", Map.of( "sec-fetch-site", "same-site" ) );

		assertTrue( CrossSite.SAME_ORIGIN.refuses( fromLanding, HOST, null, ownHost ) );
		assertFalse( CrossSite.OWN_HOSTS.refuses( fromLanding, HOST, null, ownHost ) );
		assertTrue( CrossSite.OWN_HOSTS.refuses( fromElsewhere, HOST, null, ownHost ) );
		assertTrue( CrossSite.OWN_HOSTS.refuses( sameSiteWithoutOrigin, HOST, null, ownHost ) );
		assertFalse( CrossSite.ALLOWED.refuses( fromElsewhere, HOST, null, ownHost ) );
	}
}
