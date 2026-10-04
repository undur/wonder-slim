package er.extensions.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

/**
 * What answers a request no route answered: the fallback, then the not found handler, then passing it on
 */
public class NotFoundTest {

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
		return invocation -> url.equals( invocation.url() ) ? answering( "the file" ).handle( invocation ) : RouteHandler.DECLINED;
	}

	private static WOResponse handle( final ERXRouter router, final String url ) {
		return router.handle( request(), url ).generateResponse();
	}

	@Test
	public void aURLNothingAnswersGetsThePlain404ByDefault() {
		final WOResponse response = handle( new ERXRouter(), "/nothing" );

		assertEquals( 404, response.status() );
		assertEquals( "No route found for URL: /nothing", response.contentString() );
		assertNull( response.userInfoForKey( ERXRouter.UNHANDLED_RESPONSE_KEY ), "The plain 404 is the answer, not passed on" );
	}

	@Test
	public void theNotFoundHandlerIsTheApplications() {
		final ERXRouter router = new ERXRouter();
		router.application().notFound( answering( "our own" ) );

		assertEquals( "our own", handle( router, "/nothing" ).contentString() );

		// A group's or a plugin's routes don't set it: only ApplicationRoutes has notFound() and fallback()
	}

	@Test
	public void aDeclinedURLGoesToTheNextMatchingRoute() {
		final ERXRouter router = new ERXRouter();
		router.application().map( "/things/{name}", invocation -> "known".equals( invocation.parameter( "name" ) ) ? answering( "the first" ).handle( invocation ) : RouteHandler.DECLINED );
		router.application().map( "/things/*", answering( "the second" ) );

		assertEquals( "the first", handle( router, "/things/known" ).contentString() );
		assertEquals( "the second", handle( router, "/things/other" ).contentString() );
	}

	@Test
	public void nullIsNotAnAnswer() {
		final ERXRouter router = new ERXRouter();
		router.application().notFound( invocation -> null );

		assertThrows( IllegalStateException.class, () -> handle( router, "/nothing" ), "A handler declines with RouteHandler.DECLINED, so a null is a mistake" );
	}

	@Test
	public void passingOnIsABare404MarkedUnhandled() {
		final ERXRouter router = new ERXRouter();
		router.application().notFound( invocation -> RouteHandler.DECLINED );

		final WOResponse response = handle( router, "/nothing" );
		assertEquals( 404, response.status() );
		assertNotNull( response.userInfoForKey( ERXRouter.UNHANDLED_RESPONSE_KEY ) );
		assertEquals( "", response.contentString(), "Nothing is generated for a response nobody sees" );
	}

	@Test
	public void aPublicResourceIsServedBeforeNotFound() {
		final ERXRouter router = new ERXRouter();
		router.application().fallback( publicResourceAt( "/robots.txt" ) );

		assertEquals( "the file", handle( router, "/robots.txt" ).contentString() );
		assertEquals( 404, handle( router, "/nothing" ).status(), "What isn't a public resource goes on to not found" );
	}

	@Test
	public void aRouteWinsOverAPublicResource() {
		final ERXRouter router = new ERXRouter();
		router.application().fallback( publicResourceAt( "/robots.txt" ) );
		router.application().map( "/robots.txt", answering( "the route" ) );

		assertEquals( "the route", handle( router, "/robots.txt" ).contentString() );
	}
}
