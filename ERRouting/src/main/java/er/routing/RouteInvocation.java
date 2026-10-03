package er.routing;

import java.util.Map;

import com.webobjects.appserver.WORequest;

import er.routing.core.Converters;

/**
 * EXPERIMENTAL (route-links branch). An invocation of a route mapped in an {@link ERXRouter}, with its parameters by
 * name: the path's ({@code {id}}), a host pattern's ({@code {tenant}}) and a wildcard's remainder ({@code *}).
 */

public class RouteInvocation extends er.extensions.routes.RouteInvocation {

	private final Map<String, String> _parameters;
	private final Converters _converters;
	private final Map<String, String> _conversionErrors = new java.util.LinkedHashMap<>();

	public RouteInvocation( final String url, final WORequest request, final Map<String, String> parameters, final Converters converters ) {
		super( url, request );
		_parameters = Map.copyOf( parameters );
		_converters = converters;
	}

	/**
	 * @return The named parameter's value, null if the route has no parameter of that name
	 */
	public String parameter( final String name ) {
		return _parameters.get( name );
	}

	/**
	 * @return The named parameter's value converted to the type, by the router's converters
	 * @throws Declined if the value isn't one of the type, or names an object that doesn't exist: the request passes on
	 *         to the next matching route
	 * @throws IllegalArgumentException if the route has no parameter of that name
	 */
	public <T> T parameter( final String name, final Class<T> type ) {
		final String string = parameter( name );

		if( string == null ) {
			throw new IllegalArgumentException( "The route has no parameter '%s'. Its parameters are %s".formatted( name, _parameters.keySet() ) );
		}

		final T value;

		try {
			value = _converters.fromString( string, type );
		}
		catch( IllegalArgumentException e ) {
			throw new Declined( "'%s' isn't a %s".formatted( string, type.getSimpleName() ) );
		}

		if( value == null ) {
			throw new Declined( "There's no %s '%s'".formatted( type.getSimpleName(), string ) );
		}

		return value;
	}

	/**
	 * @return The query parameters or form fields that didn't convert to their components' types (or named objects that
	 *         don't exist), by name, with the text that was given. Those components are null.
	 */
	public Map<String, String> conversionErrors() {
		return java.util.Collections.unmodifiableMap( _conversionErrors );
	}

	void addConversionError( final String name, final String text ) {
		_conversionErrors.put( name, text );
	}

	/**
	 * @return Every parameter, by name
	 */
	public Map<String, String> parameters() {
		return _parameters;
	}
}
