package er.extensions.routing;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WODirectAction;
import com.webobjects.appserver.WORequest;

import er.extensions.routes.RouteRequestHandler;

/**
 * The direct action {@link RouteRequestHandler} invokes: passes the request on to the application's router
 */
public class RouteAction extends WODirectAction {

	public RouteAction( final WORequest request ) {
		super( request );
	}

	@Override
	public WOActionResults defaultAction() {
		return ERXRouter.handleRequest( request(), RouteRequestHandler.routePath( request() ) );
	}

	/**
	 * Legacy entry for front ends that pass the route URL in the {@code url} query parameter, or in the
	 * {@code redirect_url} header Apache's 404 handler sets
	 */
	public WOActionResults handlerAction() {
		String url = request().stringFormValueForKey( "url" );

		if( url == null ) {
			url = request().headerForKey( "redirect_url" );
		}

		return ERXRouter.handleRequest( request(), url );
	}
}
