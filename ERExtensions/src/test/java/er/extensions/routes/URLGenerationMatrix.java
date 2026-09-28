package er.extensions.routes;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;

/**
 * The URLs an application generates in answer to a request, for every shape a request can arrive in: short, long
 * (with and without the extension, with an instance number), under a front end's own adaptor path, and without an
 * application name. What's tested is the whole chain an application runs: the request URL canonicalized by
 * {@link ERXRoutingApplication#createRequest}, the context's URL base normalized by {@link ERXRoutingContext}, and the
 * generated URL shortened by it.
 *
 * Each subclass constructs an application of its own, the property set as a deployment would set it. WebObjects keeps
 * the application in a static field, so the most recently constructed one is the one every context sees.
 */
abstract class URLGenerationMatrix {

	static final String NAME = "App";

	static class App extends ERXRoutingApplication {

		@Override
		public WOContext createContextForRequest( final WORequest request ) {
			return new ERXRoutingContext( request );
		}
	}

	/**
	 * @return An application named {@value #NAME}, with short URLs on or off
	 */
	static App application( final boolean shortURLs ) {
		System.setProperty( "WOApplicationName", NAME );
		System.setProperty( "er.extensions.ERXApplication.shortURLs", String.valueOf( shortURLs ) );

		try {
			return new App();
		}
		finally {
			System.clearProperty( "WOApplicationName" );
			System.clearProperty( "er.extensions.ERXApplication.shortURLs" );
		}
	}

	abstract App app();

	/**
	 * @return A fresh context for a request to the given URL, so no URL generated in it creates a session another one would then carry
	 */
	WOContext context( final String requestURL ) {
		return app().createContextForRequest( app().createRequest( "GET", requestURL, "HTTP/1.1", null, null, null ) );
	}

	/**
	 * Asserts the URLs generated in answer to a request: a direct action, a component action (its session ID shown as
	 * {@code SID}), a resource request handler URL and the location WebObjects redirects to when it won't serve the
	 * request itself.
	 */
	void assertURLs( final String requestURL, final String directAction, final String componentAction, final String resource, final String redirect ) {
		assertAll( requestURL,
				() -> assertEquals( directAction, context( requestURL ).directActionURLForActionNamed( "act", null ), "direct action" ),
				() -> assertEquals( componentAction, componentActionURL( requestURL ), "component action" ),
				() -> assertEquals( resource, context( requestURL ).urlWithRequestHandlerKey( "res", "app/x.css", null ), "resource" ),
				() -> assertEquals( redirect, app()._newLocationForRequest( context( requestURL ).request() ), "redirect" ) );
	}

	/**
	 * Asserts the complete URLs generated in answer to a request: a secure direct action, a direct action with the
	 * context generating complete URLs (as a redirect between schemes does), and one asked for with
	 * {@code completeURLWithRequestHandlerKey} (as an absolute link in an email is). The host is shown as {@code host}:
	 * in a test it's the machine's own.
	 */
	void assertCompleteURLs( final String requestURL, final String secureDirectAction, final String completeDirectAction, final String completeURL ) {
		assertAll( requestURL,
				() -> assertEquals( completeURL, withoutHost( context( requestURL ).completeURLWithRequestHandlerKey( "wa", "act", null, false, 0 ) ), "complete URL" ),
				() -> assertEquals( secureDirectAction, withoutHost( context( requestURL ).directActionURLForActionNamed( "act", null, true, 0, false ) ), "secure direct action" ),
				() -> {
					final WOContext context = context( requestURL );
					context.generateCompleteURLs();
					assertEquals( completeDirectAction, withoutHost( context.directActionURLForActionNamed( "act", null ) ), "complete direct action" );
				} );
	}

	private String componentActionURL( final String requestURL ) {
		final WOContext context = context( requestURL );
		final String url = context.componentActionURL();
		return url.replace( context.session().sessionID(), "SID" );
	}

	/**
	 * @return A complete direct action URL asked for with an instance number, the host shown as {@code host}
	 */
	String completeURLForInstance( final String requestURL, final String instanceNumber ) {
		return withoutHost( context( requestURL ).completeURLWithRequestHandlerKey( instanceNumber, "wa", "act", "x=1", false, 0 ) );
	}

	static String withoutHost( final String url ) {
		return url.replaceFirst( "://[^/]+", "://host" );
	}
}
