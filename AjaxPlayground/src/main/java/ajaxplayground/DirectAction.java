package ajaxplayground;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import ajaxplayground.components.Main;
import er.extensions.appserver.ERXDirectAction;
import er.extensions.routes.RouteRequestHandler;

/**
 * Entry points that create a session, so playground pages (which contain stateful Ajax components)
 * can be reached directly by URL - by a human or a headless browser - without first walking through
 * a component-action link. A bare component URL (/wo/ScenarioFocus) has no session and would hit a
 * "session has timed out" page; these direct actions avoid that.
 */
public class DirectAction extends ERXDirectAction {

	public DirectAction( WORequest request ) {
		super( request );
	}

	@Override
	public WOActionResults defaultAction() {
		return pageWithName( Main.class );
	}

	/**
	 * Open any playground page by name: /wa/page?name=ScenarioFocus. The name is resolved against
	 * the component packages, so callers pass the simple class name. Falls back to the index for an
	 * unknown / missing name rather than erroring.
	 */
	public WOActionResults pageAction() {
		String name = request().stringFormValueForKey( "name" );
		if( name == null || name.isBlank() ) {
			return pageWithName( Main.class );
		}
		try {
			return pageWithName( name );
		}
		catch( Exception e ) {
			return pageWithName( Main.class );
		}
	}

	/**
	 * Echoes RouteRequestHandler.routePath() for this request, for the URL-shape probe: a page served by a
	 * direct action describes itself by its short path whatever shape the request arrived in.
	 */
	public WOActionResults routePathAction() {
		final WOResponse response = new WOResponse();
		response.setHeader( "text/plain", "content-type" );
		response.setContent( RouteRequestHandler.routePath( request() ) );
		return response;
	}
}
