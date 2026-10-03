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
}
