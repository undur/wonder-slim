package er.routing;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXWOContext;
import er.routing.core.Converters;
import er.routing.core.Host;
import er.routing.core.PathPattern;
import er.routing.core.RouteOption;

/**
 * EXPERIMENTAL (route-links branch). A route whose parameters are the components of a record, see docs/ROUTE_LINKS.md.
 * "Route" is a working name; converging with the existing routes is a later step.
 *
 * <pre>
 * public record Search( String area, String q, Boolean more ) implements Routable {
 *
 *     public WOActionResults invoke( RouteInvocation invocation ) {
 *         ...
 *     }
 * }
 *
 * public final Route&lt;Search&gt; search = routes.route( "/search/{area}", Search.class );
 * </pre>
 *
 * Declared from a {@link RouteGroup}, which maps it, so a route knows its whole pattern and its conditions from the
 * start. The record's components are its parameters: those named in the path pattern (the group's prefix included) and
 * in a host pattern, and the rest are query parameters. A template links to it with
 * {@code <wo:route route="$routes.search" :area="bork" :q="$someString">}, Java code with
 * {@code routes.search.url( new Search( "bork", someString, null ) )}.
 */

public final class Route<P extends Record> implements Linkable {

	/**
	 * What the route does when invoked, given its parameters
	 */
	@FunctionalInterface
	public interface Action<P> {
		public WOActionResults invoke( P parameters, RouteInvocation invocation );
	}


	private final PathPattern _path;
	private final Host _host;
	private final Class<P> _parametersClass;
	private final Action<P> _action;
	private final Converters _converters;
	private final boolean _reportFields;
	private final RecordComponent[] _components;
	private final Constructor<P> _constructor;

	/**
	 * The path and host parameters' names
	 */
	private final List<String> _routeParameterNames;

	Route( final String pattern, final List<RouteOption> options, final Class<P> parametersClass, final Action<P> action, final Converters converters ) {
		_converters = Objects.requireNonNull( converters );
		_reportFields = options.contains( Fields.REPORTED );
		_path = PathPattern.parse( pattern );
		_host = (Host)options.stream().filter( Host.class::isInstance ).findFirst().orElse( null );
		_parametersClass = Objects.requireNonNull( parametersClass );
		_action = Objects.requireNonNull( action );
		_components = parametersClass.getRecordComponents();

		if( _path.isWildcard() ) {
			throw new IllegalArgumentException( "A route's pattern has no wildcard: '%s'".formatted( pattern ) );
		}

		final List<String> routeParameterNames = new ArrayList<>( _path.parameterNames() );

		if( _host != null ) {
			routeParameterNames.addAll( _host.parameterNames() );
		}

		_routeParameterNames = List.copyOf( routeParameterNames );

		final List<String> componentNames = parameterNames();

		for( final String name : _routeParameterNames ) {
			if( !componentNames.contains( name ) ) {
				throw new IllegalArgumentException( "The route %s has the parameter {%s}, but %s has no component of that name. Its components are %s".formatted( description(), name, parametersClass.getSimpleName(), componentNames ) );
			}
		}

		for( final RecordComponent component : _components ) {
			if( !_converters.converts( component.getType() ) ) {
				throw new IllegalArgumentException( "%s.%s is a %s, which has no converter, so it can't be a route parameter. Register one in the router's converters before declaring the route".formatted( parametersClass.getSimpleName(), component.getName(), component.getType().getSimpleName() ) );
			}

			if( component.getType().isPrimitive() && !_routeParameterNames.contains( component.getName() ) ) {
				throw new IllegalArgumentException( "%s.%s is a query parameter, so it can be absent, and a %s can't be. Use its boxed type".formatted( parametersClass.getSimpleName(), component.getName(), component.getType() ) );
			}
		}

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
		final Map<String, String> strings = new LinkedHashMap<>();
		values( parameters ).forEach( ( name, value ) -> {
			if( value != null ) {
				strings.put( name, _converters.toString( value ) );
			}
		} );

		return RouteURLs.url( _path, _host, strings, _routeParameterNames, context );
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
	@Override
	public String url( final Map<String, Object> values, final WOContext context ) {
		final Map<String, Object> all = RouteURLs.withHostParameters( _host, values, context );
		final List<String> unknown = all.keySet().stream().filter( name -> !parameterNames().contains( name ) ).toList();

		if( !unknown.isEmpty() ) {
			throw new IllegalArgumentException( "The route %s has no parameter %s. Its parameters are %s".formatted( description(), unknown, parameterNames() ) );
		}

		final Map<String, String> strings = new LinkedHashMap<>();

		for( final RecordComponent component : _components ) {
			final Object value = all.get( component.getName() );

			if( value == null ) {
				if( _routeParameterNames.contains( component.getName() ) ) {
					throw new IllegalArgumentException( "The route %s needs its parameter '%s'".formatted( description(), component.getName() ) );
				}

				continue;
			}

			strings.put( component.getName(), text( component, value ) );
		}

		return RouteURLs.url( _path, _host, strings, _routeParameterNames, context );
	}

	/**
	 * @return The value as URL text, checked against the component's type: a string is converted to the type and back
	 *         (so it's the canonical text), anything else must be of the type
	 */
	private String text( final RecordComponent component, final Object value ) {
		final Class<?> type = Converters.boxed( component.getType() );

		if( value instanceof InheritedText inherited ) {
			return inherited.text();
		}

		// A text parameter takes the text of a value the converters convert (a number, an object with a converter), not
		// just any object's toString(), which would make a wrong binding a garbage URL
		if( type == String.class && !(value instanceof String) ) {
			if( !_converters.converts( value.getClass() ) ) {
				throw new IllegalArgumentException( "The parameter '%s' of the route %s is text, and was given a %s, which has no converter: %s".formatted( component.getName(), description(), value.getClass().getSimpleName(), value ) );
			}

			return _converters.toString( value );
		}

		if( value instanceof String string && type != String.class ) {
			final Object converted;

			try {
				converted = _converters.fromString( string, type );
			}
			catch( IllegalArgumentException e ) {
				throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, and '%s' isn't one".formatted( component.getName(), description(), type.getSimpleName(), string ), e );
			}

			if( converted == null ) {
				throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, and there's none for '%s'".formatted( component.getName(), description(), type.getSimpleName(), string ) );
			}

			return _converters.toString( converted );
		}

		if( !type.isInstance( value ) ) {
			throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, but was given a %s: %s".formatted( component.getName(), description(), type.getSimpleName(), value.getClass().getSimpleName(), value ) );
		}

		return _converters.toString( value );
	}

	/**
	 * Invokes the route: the parameters are the router's (path and host) and the request's query values or form fields,
	 * converted to their types. A route parameter that doesn't convert declines the request, as does a record whose
	 * constructor refuses its values (an IllegalArgumentException). A query parameter or field that doesn't convert is
	 * null, and reported in {@link RouteInvocation#conversionErrors()}, so the route can answer a form with its errors.
	 */
	WOActionResults handle( final RouteInvocation invocation ) {
		final Object[] arguments = new Object[_components.length];

		for( int i = 0; i < _components.length; i++ ) {
			final RecordComponent component = _components[i];
			final String name = component.getName();
			final String string = _routeParameterNames.contains( name ) ? invocation.parameter( name ) : invocation.request().stringFormValueForKey( name );

			if( string == null || (string.isEmpty() && component.getType() != String.class) ) {
				continue;
			}

			try {
				arguments[i] = _converters.fromString( string, component.getType() );
			}
			catch( IllegalArgumentException e ) {
				arguments[i] = null;
			}

			if( _routeParameterNames.contains( name ) ) {

				// A route parameter that isn't one of the type, or names an object that doesn't exist, means the URL is wrong
				if( arguments[i] == null ) {
					return RouteHandler.DECLINED;
				}

				// One URL per value: other text for it is redirected to its own (007 to 7)
				if( !_converters.isCanonical( string, arguments[i] ) ) {
					throw new NotCanonical( name, _converters.toString( arguments[i] ) );
				}
			}
			else if( arguments[i] == null ) {

				// A query parameter or field that doesn't convert declines, unless the route takes the errors (a form)
				if( !_reportFields ) {
					return RouteHandler.DECLINED;
				}

				invocation.addConversionError( name, string );
			}
		}

		final P parameters;

		// A URL is user input: values the record refuses decline it, as values that don't convert do
		try {
			parameters = construct( arguments );
		}
		catch( IllegalArgumentException e ) {
			return RouteHandler.DECLINED;
		}

		return _action.invoke( parameters, invocation );
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
	private String description() {
		return _host == null ? _path.source() : _path.source() + " (" + _host.pattern() + ")";
	}

	@Override
	public String toString() {
		return "Route " + description() + " (" + _parametersClass.getSimpleName() + ")";
	}
}
