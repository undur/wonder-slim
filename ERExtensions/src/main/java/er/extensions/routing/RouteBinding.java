package er.extensions.routing;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;

import er.extensions.appserver.ERXWOContext;
import er.routing.conversion.Converters;
import er.routing.matching.PathPattern;
import er.routing.options.Fields;
import er.routing.options.Host;
import er.routing.options.RouteOption;

/**
 * A typed route as one router has it ({@link Route} is the route itself, which links
 * use): its whole pattern and conditions, its record's components, and what it does. Declaring the routes again makes a
 * new one in the new router.
 */

final class RouteBinding<P extends Record> {

	private static final Logger logger = LoggerFactory.getLogger( RouteBinding.class );

	private final PathPattern _path;
	private final Host _host;
	private final Class<P> _parametersClass;
	private final Route.Action<P> _action;
	private final ERXRouter _router;
	private final Converters _converters;
	private final boolean _reportFields;
	private volatile Route.Invalid _whenInvalid;
	private final RecordComponent[] _components;

	/**
	 * For each component, the type of its values if it's a {@code List} (a repeated parameter), null if it isn't
	 */
	private final Class<?>[] _elementTypes;
	private final Constructor<P> _constructor;

	/**
	 * The components' indexes in the order they're converted: the host's parameters, the path's, then the query's, so a
	 * converter looking in the request's other parameters (a club's book) finds those it depends on converted
	 */
	private final int[] _conversionOrder;

	/**
	 * The path and host parameters' names
	 */
	private final List<String> _routeParameterNames;

	RouteBinding( final ERXRouter router, final String pattern, final List<RouteOption> options, final Class<P> parametersClass, final Route.Action<P> action ) {
		_router = router;
		_converters = router.converters();
		_reportFields = options.contains( Fields.REPORTED );
		_path = PathPattern.parse( pattern );
		_host = (Host)options.stream().filter( Host.class::isInstance ).findFirst().orElse( null );
		_parametersClass = Objects.requireNonNull( parametersClass );
		_action = Objects.requireNonNull( action );
		_components = parametersClass.getRecordComponents();

		// A record's component names a wildcard's remainder, so the wildcard needs a name: {path*}
		if( _path.isWildcard() && PathPattern.WILDCARD_PARAMETER.equals( _path.wildcardName() ) ) {
			throw new IllegalArgumentException( "A typed route's wildcard is named for the record's component that takes the remainder ('/files/{path*}'): '%s'".formatted( pattern ) );
		}

		final List<String> routeParameterNames = new ArrayList<>( _path.parameterNames() );

		if( _host != null ) {
			routeParameterNames.addAll( _host.parameterNames() );
		}

		_routeParameterNames = List.copyOf( routeParameterNames );

		final List<String> componentNames = parameterNames();

		// A path parameter is the record's, a group's included, and a host parameter may be left to the host (a link to
		// the request's host)
		for( final String name : _path.parameterNames() ) {
			if( !componentNames.contains( name ) ) {
				throw new IllegalArgumentException( "The route %s has the parameter {%s}, but %s has no component of that name (a group's parameters are the record's too). Its components are %s".formatted( description(), name, parametersClass.getSimpleName(), componentNames ) );
			}
		}

		_elementTypes = new Class<?>[_components.length];

		for( int i = 0; i < _components.length; i++ ) {
			final RecordComponent component = _components[i];

			if( component.getType() == List.class ) {
				_elementTypes[i] = elementType( component );
				continue;
			}

			if( !_converters.converts( component.getType() ) ) {
				throw new IllegalArgumentException( "%s.%s is a %s, which has no converter, so it can't be a route parameter. Register one in the router's converters before declaring the route".formatted( parametersClass.getSimpleName(), component.getName(), component.getType().getSimpleName() ) );
			}

			if( component.getType().isPrimitive() && !_routeParameterNames.contains( component.getName() ) ) {
				throw new IllegalArgumentException( "%s.%s is a query parameter, so it can be absent, and a %s can't be. Use its boxed type".formatted( parametersClass.getSimpleName(), component.getName(), component.getType() ) );
			}
		}

		final List<Integer> order = new ArrayList<>();

		for( final List<String> names : List.of( _host == null ? List.<String>of() : _host.parameterNames(), _path.parameterNames() ) ) {
			for( int i = 0; i < _components.length; i++ ) {
				if( names.contains( _components[i].getName() ) ) {
					order.add( i );
				}
			}
		}

		for( int i = 0; i < _components.length; i++ ) {
			if( !order.contains( i ) ) {
				order.add( i );
			}
		}

		_conversionOrder = order.stream().mapToInt( Integer::intValue ).toArray();

		try {
			_constructor = parametersClass.getDeclaredConstructor( Arrays.stream( _components ).map( RecordComponent::getType ).toArray( Class<?>[]::new ) );
			_constructor.setAccessible( true );
		}
		catch( NoSuchMethodException e ) {
			throw new IllegalStateException( e );
		}
	}

	/**
	 * @return true if fields that don't convert are reported to the route ({@link Fields#REPORTED}), its own option or
	 *         its group's
	 */
	boolean reportsFields() {
		return _reportFields;
	}

	/**
	 * @return The whole path pattern, the group's prefix included
	 */
	/**
	 * @return The names a link gives values for: the record's components, and the host's parameters it leaves out
	 */
	List<String> acceptedNames() {
		final List<String> names = new ArrayList<>( parameterNames() );
		_routeParameterNames.stream().filter( name -> !names.contains( name ) ).forEach( names::add );
		return names;
	}

	/**
	 * @return The names a link must give values for: the path's parameters (the host's can be the request's)
	 */
	List<String> requiredNames() {
		return _path.parameterNames();
	}

	public String pattern() {
		return _path.source();
	}

	public Class<P> parametersClass() {
		return _parametersClass;
	}

	/**
	 * @return The names of the route's parameters, in the record's component order
	 */
	public List<String> parameterNames() {
		return Arrays.stream( _components ).map( RecordComponent::getName ).toList();
	}

	/**
	 * @return The URL of the route with the given parameters, in the current context
	 */
	public String url( final P parameters ) {
		return url( parameters, ERXWOContext.currentContext() );
	}

	/**
	 * @return The URL of the route with the given parameters, generated by the context the way it generates the
	 *         application's other URLs (short, or with the adaptor prefix). Complete, to the route's host, if the route
	 *         has a host other than the request's.
	 */
	public String url( final P parameters, final WOContext context ) {
		final Link link = link( parameters );
		return RouteURLs.url( _path, _host, withHostTexts( link.routeValues(), context ), link.queryValues(), context );
	}

	/**
	 * @return The route values, with the host parameters the record leaves out taken from the context's request
	 */
	private Map<String, String> withHostTexts( final Map<String, String> routeValues, final WOContext context ) {

		if( routeValues.keySet().containsAll( _routeParameterNames ) ) {
			return routeValues;
		}

		final Map<String, String> all = new LinkedHashMap<>( routeValues );
		RouteURLs.withHostParameters( _host, new LinkedHashMap<>( routeValues ), context ).forEach( ( name, value ) -> all.putIfAbsent( name, value instanceof InheritedText inherited ? inherited.text() : String.valueOf( value ) ) );
		return all;
	}

	/**
	 * @return The complete URL of the route with the given parameters, without a request (a background job's email): at
	 *         the application's public address ({@code er.routing.publicAddress}), which must be set
	 */
	public String completeURL( final P parameters ) {
		final Link link = link( parameters );

		if( !link.routeValues().keySet().containsAll( _routeParameterNames ) ) {
			throw new IllegalArgumentException( "%s leaves out the route's parameters %s, which a complete URL outside a request has no request to take from: give them by name, completeURL( values )".formatted( _parametersClass.getSimpleName(), _routeParameterNames.stream().filter( name -> !link.routeValues().containsKey( name ) ).toList() ) );
		}

		return RouteURLs.completeURL( _path, _host, link.routeValues(), link.queryValues() );
	}

	/**
	 * A link's values as URL text: the route parameters by name, and the query's name and value pairs in order (a list's
	 * name once for each of its values)
	 */
	private record Link( Map<String, String> routeValues, List<Map.Entry<String, String>> queryValues ) {

		Link() {
			this( new LinkedHashMap<>(), new ArrayList<>() );
		}

		void add( final String name, final String text, final boolean routeParameter ) {
			if( routeParameter ) {
				routeValues.put( name, text );
			}
			else {
				queryValues.add( Map.entry( name, text ) );
			}
		}
	}

	/**
	 * @return The record's values as URL text, without those that are null
	 */
	private Link link( final P parameters ) {
		final Link link = new Link();

		values( parameters ).forEach( ( name, value ) -> {
			if( value instanceof List<?> list ) {
				list.stream().filter( Objects::nonNull ).forEach( element -> link.add( name, _converters.toString( element ), false ) );
			}
			else if( value != null ) {
				link.add( name, _converters.toString( value ), _routeParameterNames.contains( name ) );
			}
		} );

		return link;
	}

	/**
	 * Has the route handle a request with bad input, instead of declining it: a query parameter or field that doesn't
	 * convert (unless the route takes those with its record, {@link Fields#REPORTED}), or values its record's constructor
	 * refuses. A route parameter that doesn't convert still declines: the URL is wrong, and another route may answer it.
	 *
	 * @return The route
	 */
	void whenInvalid( final Route.Invalid handler ) {
		_router.undeclared( "the whenInvalid of " + description() + ", set" );
		_whenInvalid = Objects.requireNonNull( handler );
	}

	/**
	 * @return A redirect to the route with the given parameters ({@code 303 See Other}), in the current context
	 */
	public WOResponse redirect( final P parameters ) {
		return redirect( parameters, ERXWOContext.currentContext() );
	}

	/**
	 * @return A redirect to the route with the given parameters ({@code 303 See Other}): what a form's post answers with
	 */
	public WOResponse redirect( final P parameters, final WOContext context ) {
		return RouteURLs.seeOther( url( parameters, context ) );
	}

	/**
	 * @return The route's URL for parameter values by name, as a link or a form has them, without constructing the
	 *         record: only the route parameters must be there (a host parameter can be the request's), so a form's URL
	 *         doesn't depend on its fields. Each value is checked against its component's type.
	 * @throws IllegalArgumentException for a parameter the route doesn't have, a value of the wrong type, or a route
	 *         parameter that's missing
	 */
	public String url( final Map<String, Object> values, final WOContext context ) {
		final Link link = link( values, context );
		return RouteURLs.url( _path, _host, link.routeValues(), link.queryValues(), context );
	}

	public String completeURL( final Map<String, Object> values ) {
		final Link link = link( values, null );
		return RouteURLs.completeURL( _path, _host, link.routeValues(), link.queryValues() );
	}

	/**
	 * @return The values as URL text, each checked against its component's type, host parameters they don't have taken
	 *         from the context's request (if there's one)
	 */
	private Link link( final Map<String, Object> values, final WOContext context ) {
		final Map<String, Object> all = RouteURLs.withHostParameters( _host, values, context );
		final List<String> unknown = all.keySet().stream().filter( name -> !parameterNames().contains( name ) && !_routeParameterNames.contains( name ) ).toList();

		if( !unknown.isEmpty() ) {
			throw new IllegalArgumentException( "The route %s has no parameter %s. Its parameters are %s".formatted( description(), unknown, parameterNames() ) );
		}

		final Link link = new Link();

		// The host's parameters the record leaves out, as text
		for( final String name : _routeParameterNames ) {
			final Object value = all.get( name );

			if( !parameterNames().contains( name ) ) {
				if( value == null ) {
					throw new IllegalArgumentException( "The route %s needs its parameter '%s'".formatted( description(), name ) );
				}

				link.add( name, switch( value ) {
					case InheritedText inherited -> inherited.text();
					case String string -> string;
					default -> _converters.toString( value );
				}, true );
			}
		}

		for( int i = 0; i < _components.length; i++ ) {
			final String name = _components[i].getName();
			final Object value = all.get( name );

			if( value == null ) {
				if( _routeParameterNames.contains( name ) ) {
					throw new IllegalArgumentException( "The route %s needs its parameter '%s'".formatted( description(), name ) );
				}

				continue;
			}

			if( _elementTypes[i] != null ) {

				// A list takes a collection (an NSArray too), or one value
				final Collection<?> elements = value instanceof Collection<?> collection ? collection : List.of( value );

				for( final Object element : elements ) {
					if( element != null ) {
						link.add( name, text( name, _elementTypes[i], element ), false );
					}
				}

				continue;
			}

			if( value instanceof Collection<?> ) {
				throw new IllegalArgumentException( "The parameter '%s' of the route %s takes one value, and was given several: %s. A repeated parameter is a List".formatted( name, description(), value ) );
			}

			link.add( name, text( name, _components[i].getType(), value ), _routeParameterNames.contains( name ) );
		}

		return link;
	}

	/**
	 * @return The value as URL text, checked against the component's type: a string is converted to the type and back
	 *         (so it's the canonical text), anything else must be of the type
	 */
	private String text( final String name, final Class<?> componentType, final Object value ) {
		final Class<?> type = Converters.boxed( componentType );

		if( value instanceof InheritedText inherited ) {
			return inherited.text();
		}

		// A text parameter takes the text of a value the converters convert (a number, an object with a converter), not
		// just any object's toString(), which would make a wrong binding a garbage URL
		if( type == String.class && !(value instanceof String) ) {
			if( !_converters.converts( value.getClass() ) ) {
				throw new IllegalArgumentException( "The parameter '%s' of the route %s is text, and was given a %s, which has no converter: %s".formatted( name, description(), value.getClass().getSimpleName(), value ) );
			}

			return _converters.toString( value );
		}

		if( value instanceof String string && type != String.class ) {
			final Object converted;

			try {
				converted = _converters.fromString( string, type );
			}
			catch( IllegalArgumentException e ) {
				throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, and '%s' isn't one".formatted( name, description(), type.getSimpleName(), string ), e );
			}

			if( converted == null ) {
				throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, and there's none for '%s'".formatted( name, description(), type.getSimpleName(), string ) );
			}

			return _converters.toString( converted );
		}

		if( !type.isInstance( value ) ) {
			throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, but was given a %s: %s".formatted( name, description(), type.getSimpleName(), value.getClass().getSimpleName(), value ) );
		}

		return _converters.toString( value );
	}

	/**
	 * Invokes the route: the parameters are the router's (path and host) and the request's query values or form fields,
	 * converted to their types. A route parameter that doesn't convert declines the request. Bad input otherwise (a query
	 * parameter or field that doesn't convert, values the record's constructor refuses) declines it too, or goes to the
	 * route's {@code whenInvalid}; with {@link Fields#REPORTED}, a field that doesn't convert is null instead, reported in
	 * {@link RouteInvocation#conversionErrors()}, so the route can answer a form with its errors.
	 */
	WOActionResults handle( final RouteInvocation invocation ) {
		final Object[] arguments = new Object[_components.length];

		for( final int i : _conversionOrder ) {
			final RecordComponent component = _components[i];
			final String name = component.getName();

			if( _routeParameterNames.contains( name ) ) {
				final String string = invocation.parameter( name );

				if( string == null || (string.isEmpty() && component.getType() != String.class) ) {
					continue;
				}

				// Converted once per request (a group's parameter is converted before its filters), redirected to its own text
				// if it's other text for the value (007 to 7). One that isn't of the type, or names nothing, means the URL is
				// wrong.
				try {
					arguments[i] = invocation.parameter( name, component.getType() );
				}
				catch( Declined e ) {
					return declined( invocation, "its parameter '%s' is '%s', which isn't a %s, or names none".formatted( name, string, component.getType().getSimpleName() ) );
				}

				continue;
			}

			final List<String> texts = formValues( invocation, name );

			// A repeated parameter: every value, an empty list for none
			if( _elementTypes[i] != null ) {
				final List<Object> values = new ArrayList<>();

				for( final String text : texts ) {
					// An empty value is no value, as an empty field is, text included
					if( text.isEmpty() ) {
						continue;
					}

					final Object value = convert( text, _elementTypes[i], invocation );

					if( value == null ) {
						final WOActionResults refused = badInput( invocation, name, text, "The query parameter or field '%s' has the value '%s', which isn't a %s, or names none".formatted( name, text, _elementTypes[i].getSimpleName() ) );

						if( refused != null ) {
							return refused;
						}

						continue;
					}

					values.add( value );
				}

				arguments[i] = List.copyOf( values );
				continue;
			}

			// Several values for one is a mistake (two fields of the same name), not a choice to make silently
			if( texts.size() > 1 ) {
				final WOActionResults refused = badInput( invocation, name, String.join( ", ", texts ), "The query parameter or field '%s' takes one value, and was given %d: %s. A repeated parameter is a List".formatted( name, texts.size(), texts ) );

				if( refused != null ) {
					return refused;
				}

				continue;
			}

			final String string = texts.isEmpty() ? null : texts.getFirst();

			// An empty value (?q=, a field left empty) is absent, for text too
			if( string == null || string.isEmpty() ) {
				continue;
			}

			arguments[i] = convert( string, component.getType(), invocation );

			if( arguments[i] == null ) {
				final WOActionResults refused = badInput( invocation, name, string, "The query parameter or field '%s' is '%s', which isn't a %s, or names none".formatted( name, string, component.getType().getSimpleName() ) );

				if( refused != null ) {
					return refused;
				}
			}
		}

		final P parameters;

		// A URL is user input: values the record refuses (or a value it requires that's absent) decline it, as values that
		// don't convert do, unless the route handles that itself
		try {
			parameters = construct( arguments );
		}
		catch( IllegalArgumentException | NullPointerException e ) {

			// A NullPointerException is a refusal (a value it requires) only from Objects.requireNonNull called by the
			// record's constructor: anywhere else it's a bug, which isn't passed off as the request's
			if( e instanceof NullPointerException && !thrownByRequireNonNull( e ) ) {
				throw e;
			}

			final RuntimeException reason = withAbsentNamed( e, arguments );
			return _whenInvalid != null ? _whenInvalid.invoke( invocation, reason ) : declined( invocation, "%s refused its values: %s".formatted( _parametersClass.getSimpleName(), reason.getMessage() ) );
		}

		return _action.invoke( parameters, invocation );
	}

	/**
	 * @return true if the exception comes from {@code Objects.requireNonNull…} called by the record's constructor
	 */
	private boolean thrownByRequireNonNull( final RuntimeException e ) {
		final StackTraceElement[] trace = e.getStackTrace();

		for( int i = 0; i + 1 < trace.length; i++ ) {
			if( trace[i].getClassName().equals( "java.util.Objects" ) && trace[i].getMethodName().startsWith( "requireNonNull" ) ) {
				final StackTraceElement caller = trace[i + 1];

				// Objects.requireNonNull may call itself (the overload with a message), so the first caller outside it
				if( caller.getClassName().equals( "java.util.Objects" ) ) {
					continue;
				}

				return caller.getClassName().equals( _parametersClass.getName() ) && caller.getMethodName().equals( "<init>" );
			}
		}

		return false;
	}

	/**
	 * @return The reason the record refused its values: a NullPointerException without a message (from
	 *         {@code Objects.requireNonNull( q )}) is given one naming the components that were absent
	 */
	private RuntimeException withAbsentNamed( final RuntimeException e, final Object[] arguments ) {

		if( !(e instanceof NullPointerException) || e.getMessage() != null ) {
			return e;
		}

		final List<String> absent = new ArrayList<>();

		for( int i = 0; i < _components.length; i++ ) {
			if( arguments[i] == null ) {
				absent.add( _components[i].getName() );
			}
		}

		final NullPointerException named = new NullPointerException( "Absent: " + absent );
		named.initCause( e );
		return named;
	}

	/**
	 * A query parameter or field that doesn't convert goes to the route's whenInvalid, or declines, unless the route
	 * takes the errors with its record (Fields.REPORTED, a form)
	 *
	 * @return The answer to the request, null if the route takes the error (reported, its value null or left out)
	 */
	private WOActionResults badInput( final RouteInvocation invocation, final String name, final String text, final String reason ) {

		if( _reportFields ) {
			invocation.addConversionError( name, text );
			return null;
		}

		return _whenInvalid != null ? _whenInvalid.invoke( invocation, new IllegalArgumentException( reason ) ) : declined( invocation, reason );
	}

	/**
	 * @return The value for the text, null if it isn't one of the type or names nothing
	 */
	private Object convert( final String text, final Class<?> type, final RouteInvocation invocation ) {
		try {
			return _converters.fromString( text, type, invocation );
		}
		catch( IllegalArgumentException e ) {
			return null;
		}
	}

	/**
	 * @return The request's values for a query parameter or field, in order, empty for none (a file's content isn't one)
	 */
	private static List<String> formValues( final RouteInvocation invocation, final String name ) {
		final NSArray<Object> values = invocation.request().formValuesForKey( name );
		return values == null ? List.of() : values.stream().filter( String.class::isInstance ).map( String.class::cast ).toList();
	}

	/**
	 * @return The type of a List component's values (a repeated parameter)
	 * @throws IllegalArgumentException if it's a route parameter (which has one value), or isn't a list of a type with a
	 *         converter
	 */
	private Class<?> elementType( final RecordComponent component ) {

		if( _routeParameterNames.contains( component.getName() ) ) {
			throw new IllegalArgumentException( "%s.%s is a List, and a route parameter has one value: a repeated parameter is a query parameter".formatted( _parametersClass.getSimpleName(), component.getName() ) );
		}

		if( component.getGenericType() instanceof ParameterizedType list && list.getActualTypeArguments()[0] instanceof Class<?> type && _converters.converts( type ) ) {
			return type;
		}

		throw new IllegalArgumentException( "%s.%s is a %s: a repeated parameter is a List of a type with a converter (List<String>, List<Genre>)".formatted( _parametersClass.getSimpleName(), component.getName(), component.getGenericType().getTypeName() ) );
	}

	/**
	 * @return {@link RouteHandler#DECLINED}, logged (at debug) with why, for "why is this a 404"
	 */
	private WOActionResults declined( final RouteInvocation invocation, final String reason ) {
		logger.debug( "The route {} declined {}: {}", description(), invocation.url(), reason );
		invocation.declinedBecause( reason );
		return RouteHandler.DECLINED;
	}

	private P construct( final Object[] arguments ) {
		try {
			return _constructor.newInstance( arguments );
		}
		catch( InvocationTargetException e ) {
			if( e.getCause() instanceof RuntimeException runtimeException ) {
				throw runtimeException;
			}

			throw new IllegalStateException( e.getCause() );
		}
		catch( ReflectiveOperationException e ) {
			throw new IllegalStateException( e );
		}
	}

	private Map<String, Object> values( final P parameters ) {
		final Map<String, Object> values = new LinkedHashMap<>();

		for( final RecordComponent component : _components ) {
			try {
				values.put( component.getName(), component.getAccessor().invoke( parameters ) );
			}
			catch( ReflectiveOperationException e ) {
				throw new IllegalStateException( e );
			}
		}

		return values;
	}

	/**
	 * @return The pattern, and the host pattern if there is one
	 */
	String description() {
		return _host == null ? _path.source() : _path.source() + " (" + _host.pattern() + ")";
	}

	@Override
	public String toString() {
		return description() + " (" + _parametersClass.getSimpleName() + ")";
	}
}
