package er.extensions.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;


/**
 * Links to another host: what a link to a route on a host pattern becomes
 */
public class RouteURLsTest {

	@Test
	public void aRelativeURLBecomesCompleteToTheHost() {
		assertEquals( "http://acme.localhost:1300/books/2", RouteURLs.toHost( "/books/2", "acme.localhost", "localhost:1300", false ) );
		assertEquals( "https://acme.example.com/books/2", RouteURLs.toHost( "/books/2", "acme.example.com", "example.com", true ) );
		assertEquals( "http://acme.example.com/", RouteURLs.toHost( "/", "acme.example.com", null, false ) );
	}

	@Test
	public void aCompleteURLGetsTheHostInsteadOfAnotherOne() {

		// A context generating complete URLs (an email): the host is replaced, the scheme and port kept
		assertEquals( "https://acme.example.com/books/2?tab=history", RouteURLs.toHost( "https://example.com/books/2?tab=history", "acme.example.com", null, false ) );
		assertEquals( "http://acme.localhost:1300/books/2", RouteURLs.toHost( "http://localhost:1300/books/2", "acme.localhost", "localhost:1300", true ) );
		assertEquals( "http://acme.localhost:1300", RouteURLs.toHost( "http://localhost:1300", "acme.localhost", null, false ) );
	}

	@Test
	public void aCompleteURLAlwaysGetsTheRoutesHost() {

		// On the route's own host, a relative URL stays relative, and a complete one (an email) gets the route's host
		// rather than the machine's
		assertEquals( "/books/", RouteURLs.toRouteHost( "/books/", "acme.localhost", "acme.localhost:1300", false ) );
		assertEquals( "http://acme.localhost:1300/books/", RouteURLs.toRouteHost( "http://hugi-macbook.local:1300/books/", "acme.localhost", "acme.localhost:1300", false ) );
		assertEquals( "http://kronan.localhost:1300/", RouteURLs.toRouteHost( "/", "kronan.localhost", "acme.localhost:1300", false ) );
	}

	@Test
	public void thePublicAddressIsASchemeHostAndPort() {
		assertEquals( new PublicAddress.Origin( "https", "bookclubs.example.com", -1 ), PublicAddress.parse( "https://BookClubs.example.com" ) );
		assertEquals( new PublicAddress.Origin( "https", "bookclubs.example.com", -1 ), PublicAddress.parse( "https://bookclubs.example.com:443/" ) );
		assertEquals( "http://localhost:1300", PublicAddress.parse( "http://localhost:1300" ).origin() );

		// A base path is #51's, and the rest isn't an address
		for( final String wrong : new String[] { "bookclubs.example.com", "ftp://example.com", "https://example.com/app", "https://example.com?x=1", "https://user@example.com" } ) {
			assertThrows( IllegalStateException.class, () -> PublicAddress.parse( wrong ), wrong );
		}
	}

	@Test
	public void aRoutesHostGetsThePublicAddresssSchemeAndPort() {
		final PublicAddress.Origin publicAddress = PublicAddress.parse( "https://bookclubs.example.com" );

		// Behind a front end terminating TLS, the request is plain http on the application's own port
		assertEquals( "https://kronan.bookclubs.example.com/books/", RouteURLs.toRouteHost( "/books/", "kronan.bookclubs.example.com", "acme.bookclubs.example.com:1300", false, publicAddress ) );
		assertEquals( "https://kronan.bookclubs.example.com/books/?sort=author", RouteURLs.toRouteHost( "http://my-macbook.local:1300/books/?sort=author", "kronan.bookclubs.example.com", null, false, publicAddress ) );

		// On the route's own host, relative stays relative
		assertEquals( "/books/", RouteURLs.toRouteHost( "/books/", "acme.bookclubs.example.com", "acme.bookclubs.example.com", false, publicAddress ) );
	}

	@Test
	public void aCompleteURLWithoutARequest() {
		final PublicAddress.Origin publicAddress = PublicAddress.parse( "https://bookclubs.example.com" );
		final er.routing.matching.PathPattern path = er.routing.matching.PathPattern.parse( "/books/{book}" );
		final java.util.List<java.util.Map.Entry<String, String>> query = java.util.List.of( java.util.Map.entry( "author", "A B" ), java.util.Map.entry( "author", "C" ) );

		assertEquals( "https://bookclubs.example.com/books/2?author=A+B&author=C", RouteURLs.completeURL( path, null, java.util.Map.of( "book", "2" ), query, publicAddress, null, "" ) );
		assertEquals( "https://bookclubs.example.com/cgi-bin/WebObjects/App.woa/route/books/2", RouteURLs.completeURL( path, null, java.util.Map.of( "book", "2" ), java.util.List.of(), publicAddress, "/cgi-bin/WebObjects/App.woa", "" ) );
		assertEquals( "https://kronan.example.com/books/2", RouteURLs.completeURL( path, er.routing.options.Host.of( "{club}.example.com" ), java.util.Map.of( "book", "2", "club", "kronan" ), java.util.List.of(), publicAddress, null, "" ) );
	}

	@Test
	public void aCompleteURLBeneathTheBasePath() {
		final PublicAddress.Origin publicAddress = PublicAddress.parse( "https://example.com" );
		assertEquals( "https://example.com/shop/books/2", RouteURLs.completeURL( er.routing.matching.PathPattern.parse( "/books/{book}" ), null, java.util.Map.of( "book", "2" ), java.util.List.of(), publicAddress, null, "/shop" ) );
	}
}
