package er.extensions.routes;

import com.webobjects.appserver.WORequest;

/**
 * Represents an invocation of a route
 */

public class RouteInvocation {

	private final WORequest _request;
	private final String _url;
	private RouteURL _routeURL;
	
	public RouteInvocation( final String url, final WORequest request ) {
		_request = request;
		_url = url;
	}
	
	public RouteURL routeURL() {
		if( _routeURL == null ) {
			_routeURL = RouteURL.create( _url ) ;
		}
		
		return _routeURL;
	}
	
	public WORequest request() {
		return _request;
	}
	
	public String url() {
		return _url;
	}
}