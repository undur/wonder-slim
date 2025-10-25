package er.extensions.routes;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;

/**
 * Container for the data that gets passed to a RouteHandler for handling a request.
 * It's an "invocation" rather than a "request" merely so we don't have conceptual confusion with the WORequest it wraps. 
 */

public class RouteInvocation {

	private final String _url;
	private final WORequest _request;
	private RouteURL _routeURL;
	
	public RouteInvocation( final String url, final WORequest request ) {
		_url = url;
		_request = request;
	}
	
	public String url() {
		return _url;
	}

	public WORequest request() {
		return _request;
	}
		
	public RouteURL routeURL() {
		if( _routeURL == null ) {
			_routeURL = RouteURL.create( _url ) ;
		}
		
		return _routeURL;
	}

	public WOContext context() {
		return _request.context();
	}
}