package er.extensions.appserver.ajax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WOComponentRequestHandler;

import er.extensions.appserver.ERXWOContext;

/**
 * An Ajax request whose session has expired is answered so the client knows, whatever the application's
 * handleSessionRestorationErrorInContext answers (see {@link ERXAjaxApplication#dispatchRequest(WORequest)})
 */
public class ERXAjaxSessionExpiryTest {

	/**
	 * An application whose expiry handling redirects, as applications commonly do, or answers WebObjects' own expiry page
	 */
	static class App extends ERXAjaxApplication {

		String redirectTo;

		App() {
			// As AjaxSlim registers its handler, which is a component request handler
			registerRequestHandler( new WOComponentRequestHandler(), "ajax" );
		}

		@Override
		public WOContext createContextForRequest( final WORequest request ) {
			return new ERXWOContext( request );
		}

		@Override
		public WOResponse handleSessionRestorationErrorInContext( final WOContext context ) {
			if( redirectTo == null ) {
				return super.handleSessionRestorationErrorInContext( context );
			}

			final WOResponse response = new WOResponse();
			response.setStatus( 302 );
			response.setHeader( redirectTo, "location" );
			response.addCookie( new com.webobjects.appserver.WOCookie( "login", "renewed" ) );
			return response;
		}
	}

	private static final String EXPIRED_SESSION_ACTION = "/cgi-bin/WebObjects/App.woa/wo/expiredSessionID/0.1";

	private static App app;

	@BeforeAll
	static void createApplication() {
		System.setProperty( "WOApplicationName", "App" );

		try {
			app = new App();
		}
		finally {
			System.clearProperty( "WOApplicationName" );
		}
	}

	@AfterEach
	void reset() {
		app.redirectTo = null;
	}

	private static WOResponse dispatch( final String url, final boolean ajax ) {
		final Map<String, List<String>> headers = ajax ? Map.of( "x-requested-with", List.of( "XMLHttpRequest" ) ) : Map.of();
		return app.dispatchRequest( app.createRequest( "GET", url, "HTTP/1.1", headers, null, null ) );
	}

	@Test
	public void anAjaxRequestGetsTheApplicationsRedirectAsAHeader() {
		app.redirectTo = "/start";
		final WOResponse response = dispatch( EXPIRED_SESSION_ACTION, true );

		assertEquals( 403, response.status() );
		assertEquals( "true", response.headerForKey( ERXAjaxApplication.SESSION_EXPIRED_HEADER ) );
		assertEquals( "/start", response.headerForKey( ERXAjaxApplication.SESSION_EXPIRED_LOCATION_HEADER ) );
		assertNull( response.headerForKey( "location" ), "a redirect the browser would follow silently" );
		assertTrue( response.headerForKey( "set-cookie" ).contains( "login=renewed" ), "the application's cookies are kept" );
	}

	@Test
	public void anAjaxHandlerRequestToo() {
		app.redirectTo = "/start";
		final WOResponse response = dispatch( "/cgi-bin/WebObjects/App.woa/ajax/expiredSessionID/0.1", true );

		assertEquals( 403, response.status() );
		assertEquals( "/start", response.headerForKey( ERXAjaxApplication.SESSION_EXPIRED_LOCATION_HEADER ) );
	}

	@Test
	public void anAjaxRequestGetsWebObjectsExpiryPageAsAnError() {
		final WOResponse response = dispatch( EXPIRED_SESSION_ACTION, true );

		assertEquals( 403, response.status() );
		assertEquals( "true", response.headerForKey( ERXAjaxApplication.SESSION_EXPIRED_HEADER ) );
		assertNull( response.headerForKey( ERXAjaxApplication.SESSION_EXPIRED_LOCATION_HEADER ) );
	}

	@Test
	public void aPageRequestIsAnsweredAsTheApplicationDecided() {
		app.redirectTo = "/start";
		final WOResponse response = dispatch( EXPIRED_SESSION_ACTION, false );

		assertEquals( 302, response.status() );
		assertEquals( "/start", response.headerForKey( "location" ) );
		assertNull( response.headerForKey( ERXAjaxApplication.SESSION_EXPIRED_HEADER ) );
	}

	@Test
	public void onlyPageRestoringRequestsCount() {
		assertTrue( ERXAjaxApplication.restoresPage( app.createRequest( "GET", "/wo/x/0.1", "HTTP/1.1", null, null, null ) ) );
		assertTrue( ERXAjaxApplication.restoresPage( app.createRequest( "GET", "/ajax/x/0.1", "HTTP/1.1", null, null, null ) ) );
		assertFalse( ERXAjaxApplication.restoresPage( app.createRequest( "GET", "/wa/x", "HTTP/1.1", null, null, null ) ) );
		assertFalse( ERXAjaxApplication.restoresPage( app.createRequest( "GET", "/about", "HTTP/1.1", null, null, null ) ) );
	}
}
