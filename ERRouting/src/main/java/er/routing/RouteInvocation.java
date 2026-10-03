package er.routing;

import java.util.Map;

import com.webobjects.appserver.WORequest;

/**
 * EXPERIMENTAL (route-links branch). An invocation of a route mapped in an {@link ERXRouter}, with its parameters by
 * name: the path's ({@code {id}}), a host pattern's ({@code {tenant}}) and a wildcard's remainder ({@code *}).
 */

public class RouteInvocation extends er.extensions.routes.RouteInvocation {

	private final Map<String, String> _parameters;

	public RouteInvocation( final String url, final WORequest request, final Map<String, String> parameters ) {
		super( url, request );
		_parameters = Map.copyOf( parameters );
	}

	/**
	 * @return The named parameter's value, null if the route has no parameter of that name
	 */
	public String parameter( final String name ) {
		return _parameters.get( name );
	}

	/**
	 * @return Every parameter, by name
	 */
	public Map<String, String> parameters() {
		return _parameters;
	}
}
