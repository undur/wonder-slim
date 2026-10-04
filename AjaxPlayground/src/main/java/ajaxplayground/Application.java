package ajaxplayground;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXApplication;

/**
 * Minimal WebObjects application that exercises the Ajax framework's components. It is
 * deliberately free of authentication, database access and business dependencies so its pages can
 * be driven directly (by a human or a headless browser) without a login or any setup. It is the
 * canonical test surface for the morph-as-default work and the wider Ajax cleanup.
 */
public class Application extends ERXApplication {

	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory.getLogger( Application.class );

	public static void main( String[] argv ) {
		ERXApplication.main( argv, Application.class );
	}

	public Application() {
		setIncludeCommentsInResponses( false );
		setAllowsConcurrentRequestHandling( true );
		// Off by default; the replay-guard harness starts the app with -DWOPageRefreshOnBacktrackEnabled=true
		// to exercise the repeated-request guard (which is gated on this flag, as in stock WO).
		setPageRefreshOnBacktrackEnabled( Boolean.parseBoolean( System.getProperty( "WOPageRefreshOnBacktrackEnabled", "false" ) ) );

		// No login, so a generous default page cache is plenty and keeps backtracking snappy.
		// Read WOPageCacheSize from a -D system property ourselves (WO's own plumbing only picks it up
		// as a launch argument) - the cache-eviction harnesses start the app with a tiny value to force
		// the instance limit to bite.
		setPageCacheSize( Integer.parseInt( System.getProperty( "WOPageCacheSize", "100" ) ) );

		// Clean, flat URLs for page-to-page navigation (see Routes).
		Routes.register();

		// WebSocket test endpoint (served by WOAdaptorJetty — the app must
		// run with -WOAdaptor WOAdaptorJetty for this to be live). Exercised
		// by the /websocket page, and by modulo's WebSocket tunnel testing.
		com.webobjects.appserver.websocket.WOWebSocketRegistry.register( "/ws/echo", com.webobjects.appserver.websocket.examples.EchoWebSocketHandler.class );
	}
	
	@Override
	public WOResponse dispatchRequest( WORequest request ) {
		final WOResponse response = super.dispatchRequest( request );
		
		// CHECKME: We're temporarily disabling client-side caching on HTML pages
		// Worksaround for something that should really be handled by AjaxSlim. See: https://github.com/undur/wonder-slim/issues/27
		final String contentType = response.headerForKey( "content-type" );

		if( contentType != null && contentType.startsWith( "text/html" ) ) {
			response.disableClientCaching();
		}

		return response;
	}
}
