package er.extensions.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import er.extensions.routes.ERXRoutingApplication;
import er.extensions.routes.URLGenerationMatrix;

/**
 * A route's URL with short URLs, for an application served beneath a base path
 */
public class RouteURLBasePathTest extends URLGenerationMatrix {

	private static App app;

	@BeforeAll
	static void createApplication() {
		System.setProperty( ERXRoutingApplication.BASE_PATH_PROPERTY, "/App" );
		app = application( true );
	}

	@AfterAll
	static void forgetBasePath() {
		System.clearProperty( ERXRoutingApplication.BASE_PATH_PROPERTY );
	}

	@Override
	protected App app() {
		return app;
	}

	@Test
	public void routeURLsAreBeneathTheBasePath() {
		final ApplicationRoutes routes = new ERXRouter().application();
		final PlainRoute book = routes.map( "/books/{book}", invocation -> null );
		final PlainRoute books = routes.map( "/books/", invocation -> null );
		final PlainRoute front = routes.map( "/", invocation -> null );

		for( final String request : List.of( "/App/about", "/cgi-bin/WebObjects/App.woa/wa/x", "/cgi-bin/WebObjects/App.woa/3/about" ) ) {
			assertEquals( "/App/books/7", book.url( Map.of( "book", 7 ), context( request ) ), request );
			assertEquals( "/App/books/", books.url( context( request ) ), request );
			assertEquals( "/App/", front.url( context( request ) ), request );
		}
	}
}
