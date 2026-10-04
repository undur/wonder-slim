package er.extensions.routing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;

import er.routing.conversion.Converters;



/**
 * An invocation of a route mapped in an {@link ERXRouter}, with its parameters by
 * name: the path's ({@code {id}}), a host pattern's ({@code {tenant}}) and a wildcard's remainder ({@code *}).
 */

public class RouteInvocation implements Converters.Scope {

	/**
	 * The URL's path, as the routes see it (the application's prefix and base path removed)
	 */
	private final String _url;

	private final WORequest _request;

	private final Map<String, String> _parameters;
	private final Converters _converters;
	private final Map<String, String> _conversionErrors = new LinkedHashMap<>();

	/**
	 * Route parameters converted, by name and type, so each is converted once whatever asks (the route, a converter)
	 */
	private final Map<String, Object> _converted = new java.util.HashMap<>();

	/**
	 * Route parameters being converted, so a converter asking for one that asks for it back fails, rather than loops
	 */
	private final java.util.Set<String> _converting = new java.util.HashSet<>();

	/**
	 * The route invoked, null if the invocation isn't a route's
	 */
	private final Linkable _route;

	/**
	 * Why the route declined the request, null if it didn't say
	 */
	private String _declineReason;

	RouteInvocation( final String url, final WORequest request, final Map<String, String> parameters, final Converters converters ) {
		this( url, request, parameters, converters, null );
	}

	RouteInvocation( final String url, final WORequest request, final Map<String, String> parameters, final Converters converters, final Linkable route ) {
		_url = url;
		_request = request;
		_parameters = Map.copyOf( parameters );
		_converters = converters;
		_route = route;
	}

	/**
	 * @return The URL's path, as the routes see it: the application's prefix and base path removed, without the query
	 *         string ({@code /books/7})
	 */
	public String url() {
		return _url;
	}

	public WORequest request() {
		return _request;
	}

	public WOContext context() {
		return _request.context();
	}

	/**
	 * @return The route invoked (for a filter deciding by route, navigation marking the current page, a log): the constant
	 *         for one a declaration gave a pattern to, which compares by identity ({@code ri.route() == Routes.books})
	 */
	public Linkable route() {
		return _route;
	}

	/**
	 * Notes why the route declined, for the not found page in development
	 */
	void declinedBecause( final String reason ) {
		_declineReason = reason;
	}

	String declineReason() {
		return _declineReason;
	}

	/**
	 * @return The named parameter's value, null if the route has no parameter of that name
	 */
	@Override
	public String parameter( final String name ) {
		return _parameters.get( name );
	}

	/**
	 * @return The named parameter's value converted to the type, by the router's converters
	 * @throws Declined if the value isn't one of the type, or names an object that doesn't exist: the request passes on
	 *         to the next matching route
	 * @throws IllegalArgumentException if the route has no parameter of that name
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> T parameter( final String name, final Class<T> type ) {
		final String string = parameter( name );

		if( string == null ) {
			throw new IllegalArgumentException( "The route has no parameter '%s'. Its parameters are %s".formatted( name, _parameters.keySet() ) );
		}

		final String key = name + ":" + type.getName();

		if( _converted.containsKey( key ) ) {
			return (T)_converted.get( key );
		}

		if( !_converting.add( key ) ) {
			throw new IllegalStateException( "Converting the parameter '%s' needs it converted: a converter asks for a parameter whose converter asks for it back".formatted( name ) );
		}

		final T value;

		try {
			value = _converters.fromString( string, type, this );
		}
		catch( IllegalArgumentException e ) {
			throw new Declined( "'%s' isn't a %s".formatted( string, type.getSimpleName() ) );
		}
		finally {
			_converting.remove( key );
		}

		if( value == null ) {
			throw new Declined( "There's no %s '%s'".formatted( type.getSimpleName(), string ) );
		}

		// One URL per value: other text for it is redirected to its own (007 to 7)
		if( !_converters.isCanonical( string, value ) ) {
			throw new NotCanonical( name, _converters.toString( value ) );
		}

		_converted.put( key, value );
		return value;
	}

	/**
	 * @return The framework's objects for the request (its {@link WOContext}, {@link WORequest}, this invocation), or one
	 *         the application provides ({@link Converters#provide(Class, java.util.function.Function)}), null for none
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <T> T get( final Class<T> type ) {

		if( type == WOContext.class ) {
			return (T)context();
		}

		if( type == WORequest.class ) {
			return (T)request();
		}

		if( type.isInstance( this ) ) {
			return (T)this;
		}

		return provided( type );
	}

	/**
	 * Request userInfo key for the objects provided for the request, by type
	 */
	private static final String PROVIDED_KEY = "er.routing.provided";

	/**
	 * @return The object the application provides of the type, made once per request (the routes a request tries, and the
	 *         converters and handler of each, share it), null if it provides none. Ending it (an editing context's
	 *         disposal) is the application's: the request's page renders after the route has answered.
	 */
	@SuppressWarnings("unchecked")
	private <T> T provided( final Class<T> type ) {
		Map<Class<?>, Object> provided = request() == null ? null : (Map<Class<?>, Object>)request().userInfoForKey( PROVIDED_KEY );

		if( provided == null ) {
			provided = new java.util.HashMap<>();

			if( request() != null ) {
				request().setUserInfoForKey( provided, PROVIDED_KEY );
			}
		}

		if( !provided.containsKey( type ) ) {
			provided.put( type, _converters.provided( type, this ) );
		}

		return (T)provided.get( type );
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
			value = _converters.fromString( string, type, this );
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
