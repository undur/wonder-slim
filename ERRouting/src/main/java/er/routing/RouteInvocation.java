package er.routing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WORequest;

import er.routing.core.Converters;

/**
 * EXPERIMENTAL (route-links branch). An invocation of a route mapped in an {@link ERXRouter}, with its parameters by
 * name: the path's ({@code {id}}), a host pattern's ({@code {tenant}}) and a wildcard's remainder ({@code *}).
 */

public class RouteInvocation extends er.extensions.routes.RouteInvocation {

	private final Map<String, String> _parameters;
	private final Converters _converters;
	private final Map<String, String> _conversionErrors = new LinkedHashMap<>();

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

		// One URL per value: other text for it is redirected to its own (007 to 7)
		if( !_converters.isCanonical( string, value ) ) {
			throw new NotCanonical( name, _converters.toString( value ) );
		}

		return value;
	}

	/**
	 * @return A query parameter's or form field's value, null if it's absent or empty
	 * @throws Declined if it's given more than once (two fields of the same name): a repeated parameter is a typed route's
	 *         {@code List} component
	 */
	public String query( final String name ) {
		final java.util.List<String> values = request().formValuesForKey( name ) == null ? java.util.List.of() : request().formValuesForKey( name ).stream().filter( String.class::isInstance ).map( String.class::cast ).toList();

		if( values.size() > 1 ) {
			throw new Declined( "The query parameter or field '%s' takes one value, and was given %d: %s".formatted( name, values.size(), values ) );
		}

		return values.isEmpty() || values.getFirst().isEmpty() ? null : values.getFirst();
	}

	/**
	 * @return A query parameter's or form field's value converted to the type, by the router's converters, null if it's
	 *         absent or empty: a plain route's typed query values, as a typed route's record has them
	 * @throws Declined if it isn't one of the type, names an object that doesn't exist, or is given more than once: bad
	 *         input declines, as it does for a typed route
	 */
	public <T> T query( final String name, final Class<T> type ) {
		final String string = query( name );

		if( string == null ) {
			return null;
		}

		final T value;

		try {
			value = _converters.fromString( string, type );
		}
		catch( IllegalArgumentException e ) {
			throw new Declined( "The query parameter or field '%s' is '%s', which isn't a %s".formatted( name, string, type.getSimpleName() ) );
		}

		if( value == null ) {
			throw new Declined( "The query parameter or field '%s' names no %s: '%s'".formatted( name, type.getSimpleName(), string ) );
		}

		return value;
	}

	/**
	 * @return The query parameters or form fields that didn't convert to their components' types (or named objects that
	 *         don't exist), by name, with the text that was given. Those components are null.
	 */
	public Map<String, String> conversionErrors() {
		return Collections.unmodifiableMap( _conversionErrors );
	}

	void addConversionError( final String name, final String text ) {
		_conversionErrors.put( name, text );
	}

	/**
	 * @return A new instance of the page, in the invocation's context
	 */
	@SuppressWarnings("unchecked")
	public <T extends WOComponent> T page( final Class<T> pageClass ) {
		return (T)WOApplication.application().pageWithName( pageClass.getName(), context() );
	}

	/**
	 * @return Every parameter, by name
	 */
	public Map<String, String> parameters() {
		return _parameters;
	}
}
