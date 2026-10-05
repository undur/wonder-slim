package er.extensions.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WOContext;

import er.extensions.routes.URLGenerationMatrix;

/**
 * A route's URL with short URLs ontrue, whatever shape the request arrived in
 */
public class RouteURLShortTest extends URLGenerationMatrix {

	private static App app;

	@BeforeAll
	static void createApplication() {
		app = application( true );
	}

	@Override
	protected App app() {
		return app;
	}

	@Test
	public void routeURLs() {
		final ApplicationRoutes routes = new ERXRouter().application();
		final PlainRoute book = routes.map( "/books/{book}", invocation -> null );
		final PlainRoute books = routes.map( "/books/", invocation -> null );
		final PlainRoute front = routes.map( "/", invocation -> null );

		for( final String request : List.of( "/about", "/cgi-bin/WebObjects/App.woa/wa/x", "/cgi-bin/WebObjects/App.woa/3/about", "/Apps/WebObjects/App.woa/wa/x", "/cgi-bin/WebObjects/" ) ) {
			assertEquals( "/books/7", book.url( Map.of( "book", 7 ), context( request ) ), request );
			assertEquals( "/books/a%20b", book.url( Map.of( "book", "a b" ), context( request ) ), request );
			assertEquals( "/books/", books.url( context( request ) ), request );
			assertEquals( "/", front.url( context( request ) ), request );
		}

		final WOContext complete = context( "/about" );
		complete.generateCompleteURLs();
		final String url = book.url( Map.of( "book", 7 ), complete );
		assertTrue( url.startsWith( "http://" ) && url.endsWith( "/books/7" ) && !url.contains( "/route/" ), url );
	}
}
