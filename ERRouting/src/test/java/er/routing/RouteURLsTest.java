package er.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
