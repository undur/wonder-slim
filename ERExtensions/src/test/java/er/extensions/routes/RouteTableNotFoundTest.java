package er.extensions.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.extensions.routes.RouteTable.PassOnRouteHandler;

public class RouteTableNotFoundTest {

	private static WORequest request() {
		return new WORequest( "GET", "/", "HTTP/1.1", Map.of( "host", List.of( "localhost" ) ), null, null );
	}

	private static RouteHandler answering( final String content ) {
		return invocation -> {
			final WOResponse response = new WOResponse();
			response.setContent( content );
			return response;
		};
	}

	/**
	 * @return A public resources handler that has a file only at the given URL
	 */
	private static RouteHandler publicResourceAt( final String url ) {
		return invocation -> url.equals( invocation.url() ) ? answering( "the file" ).handle( invocation ) : null;
	}

	private static WOResponse handle( final RouteTable routeTable, final String url ) {
		return routeTable.handle( request(), url ).generateResponse();
	}

	@Test
	public void aURLNothingClaimsGetsThePlain404ByDefault() {
		final WOResponse response = handle( new RouteTable(), "/nothing" );

		assertEquals( 404, response.status() );
		assertEquals( "No route found for URL: /nothing", response.contentString() );
		assertNull( response.userInfoForKey( RouteTable.UNHANDLED_RESPONSE_KEY ), "The plain 404 is the answer, not passed on" );
	}

	@Test
	public void theNotFoundHandlerCanBeReplaced() {
		final RouteTable routeTable = new RouteTable();
		routeTable.setNotFoundRouteHandler( answering( "our own" ) );

		assertEquals( "our own", handle( routeTable, "/nothing" ).contentString() );
	}

	@Test
	public void passingOnIsABare404MarkedUnhandled() {
		final RouteTable routeTable = new RouteTable();
		routeTable.setNotFoundRouteHandler( new PassOnRouteHandler() );

		final WOResponse response = handle( routeTable, "/nothing" );
		assertEquals( 404, response.status() );
		assertNotNull( response.userInfoForKey( RouteTable.UNHANDLED_RESPONSE_KEY ) );
		assertEquals( "", response.contentString(), "Nothing is generated for a response nobody sees" );
	}

	@Test
	public void aPublicResourceIsServedBeforeNotFound() {
		final RouteTable routeTable = new RouteTable();
		routeTable.setFallbackRouteHandler( publicResourceAt( "/robots.txt" ) );

		assertEquals( "the file", handle( routeTable, "/robots.txt" ).contentString() );
		assertEquals( 404, handle( routeTable, "/nothing" ).status(), "What isn't a public resource goes on to not found" );
	}

	@Test
	public void aRouteWinsOverAPublicResource() {
		final RouteTable routeTable = new RouteTable();
		routeTable.setFallbackRouteHandler( publicResourceAt( "/robots.txt" ) );
		routeTable.map( "/robots.txt", answering( "the route" ) );

		assertEquals( "the route", handle( routeTable, "/robots.txt" ).contentString() );
	}
}
