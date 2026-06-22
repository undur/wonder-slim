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
		setPageRefreshOnBacktrackEnabled( false );

		// No login, so a generous default page cache is plenty and keeps backtracking snappy.
		setPageCacheSize( 100 );

		// Clean, flat URLs for page-to-page navigation (see Routes).
		Routes.register();
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
