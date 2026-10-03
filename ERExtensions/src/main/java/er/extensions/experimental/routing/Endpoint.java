package er.extensions.experimental.routing;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXWOContext;
import er.extensions.experimental.routing.core.Host;
import er.extensions.experimental.routing.core.PathPattern;
import er.extensions.experimental.routing.core.RouteCondition;
import er.extensions.experimental.routing.core.RouteRequest;
import er.extensions.routes.RouteHandler;

/**
 * EXPERIMENTAL (route-links branch). A route whose parameters are the components of a record, see docs/ROUTE_LINKS.md.
 * "Endpoint" is a working name; converging with the existing routes is a later step.
 *
 * <pre>
 * public record Search( String area, String q, Boolean more ) implements Routable {
 *
 *     public WOActionResults invoke( RoutedInvocation invocation ) {
 *         ...
 *     }
 * }
 *
 * public final Endpoint&lt;Search&gt; search = routes.endpoint( "/search/{area}", Search.class );
 * </pre>
 *
 * Declared from a {@link RouteGroup}, which maps it, so an endpoint knows its whole pattern and its conditions from the
 * start. The record's components are its parameters: those named in the path pattern (the group's prefix included) and
 * in a host pattern, and the rest are query parameters. A template links to it with
 * {@code <wo:route route="$routes.search" :area="bork" :q="$someString">}, Java code with
 * {@code routes.search.url( new Search( "bork", someString, null ) )}.
 */

public final class Endpoint<P extends Record> {

	/**
	 * What the route does when invoked, given its parameters
	 */
	@FunctionalInterface
	public interface Action<P> {
		public WOActionResults invoke( P parameters, RoutedInvocation invocation );
	}

	private static final Set<Class<?>> SUPPORTED_TYPES = Set.of( String.class, Integer.class, int.class, Long.class, long.class, Boolean.class, boolean.class, LocalDate.class );

	private final PathPattern _path;
	private final Host _host;
	private final Class<P> _parametersClass;
	private final Action<P> _action;
	private final RecordComponent[] _components;
	private final Constructor<P> _constructor;

	/**
	 * The path and host parameters' names
	 */
	private final List<String> _routeParameterNames;

	Endpoint( final String pattern, final List<RouteCondition> conditions, final Class<P> parametersClass, final Action<P> action ) {
		_path = PathPattern.parse( pattern );
		_host = (Host)conditions.stream().filter( Host.class::isInstance ).findFirst().orElse( null );
		_parametersClass = Objects.requireNonNull( parametersClass );
		_action = Objects.requireNonNull( action );
		_components = parametersClass.getRecordComponents();

		if( _path.isWildcard() ) {
			throw new IllegalArgumentException( "An endpoint's pattern has no wildcard: '%s'".formatted( pattern ) );
		}

		final List<String> routeParameterNames = new ArrayList<>( _path.parameterNames() );

		if( _host != null ) {
			routeParameterNames.addAll( _host.parameterNames() );
		}

		_routeParameterNames = List.copyOf( routeParameterNames );

		final List<String> componentNames = parameterNames();

		for( final String name : _routeParameterNames ) {
			if( !componentNames.contains( name ) ) {
				throw new IllegalArgumentException( "The endpoint %s has the parameter {%s}, but %s has no component of that name. Its components are %s".formatted( description(), name, parametersClass.getSimpleName(), componentNames ) );
			}
		}

		for( final RecordComponent component : _components ) {
			if( !SUPPORTED_TYPES.contains( component.getType() ) && !component.getType().isEnum() ) {
				throw new IllegalArgumentException( "%s.%s is a %s, which a route parameter can't be. Use one of String, Integer, Long, Boolean, LocalDate or an enum".formatted( parametersClass.getSimpleName(), component.getName(), component.getType().getSimpleName() ) );
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
	 * @return The route's parameters built from values by name, as a link element has them. A value given as a string
	 *         (a constant in a template) is converted to the component's type.
	 */
	public P parameters( final Map<String, Object> values ) {
		final List<String> unknown = values.keySet().stream().filter( name -> !parameterNames().contains( name ) ).toList();

		if( !unknown.isEmpty() ) {
			throw new IllegalArgumentException( "The route %s has no parameter %s. Its parameters are %s".formatted( description(), unknown, parameterNames() ) );
		}

		final Object[] arguments = new Object[_components.length];

		for( int i = 0; i < _components.length; i++ ) {
			final RecordComponent component = _components[i];
			Object value = values.get( component.getName() );

			if( value instanceof String string && component.getType() != String.class ) {
				try {
					value = fromString( string, component.getType() );
				}
				catch( IllegalArgumentException e ) {
					throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, and '%s' isn't one".formatted( component.getName(), description(), component.getType().getSimpleName(), string ), e );
				}
			}

			if( value != null && !boxed( component.getType() ).isInstance( value ) ) {
				throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, but was given a %s: %s".formatted( component.getName(), description(), component.getType().getSimpleName(), value.getClass().getSimpleName(), value ) );
			}

			if( value == null && component.getType().isPrimitive() ) {
				throw new IllegalArgumentException( "The route %s needs its parameter '%s'".formatted( description(), component.getName() ) );
			}

			arguments[i] = value;
		}

		return construct( arguments );
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
		Objects.requireNonNull( context, "A route URL is generated in a context, and there's none" );

		final Map<String, String> strings = new LinkedHashMap<>();
		values( parameters ).forEach( ( name, value ) -> {
			if( value != null ) {
				strings.put( name, toString( value ) );
			}
		} );

		final String query = strings.entrySet().stream()
				.filter( e -> !_routeParameterNames.contains( e.getKey() ) )
				.map( e -> URLEncoder.encode( e.getKey(), StandardCharsets.UTF_8 ) + "=" + URLEncoder.encode( e.getValue(), StandardCharsets.UTF_8 ) )
				.collect( Collectors.joining( "&" ) );

		final String url = RouteURLs.url( _path.path( strings ), query.isEmpty() ? null : query, context );

		if( _host == null ) {
			return url;
		}

		// To the route's host, unless the request is already there. FIXME: Assumes the same scheme and port as the request's
		final String host = _host.host( strings );
		final String requestHost = context.request() == null ? null : RequestHost.host( context.request() );

		if( requestHost != null && new RouteRequest( "GET", requestHost, "/" ).host().equals( host ) ) {
			return url;
		}

		final int colon = requestHost == null ? -1 : requestHost.lastIndexOf( ':' );
		final String port = colon == -1 || requestHost.endsWith( "]" ) ? "" : requestHost.substring( colon );
		final String scheme = context.request() != null && context.request().isSecure() ? "https" : "http";
		return scheme + "://" + host + port + url;
	}

	/**
	 * Invokes the route: the parameters are the router's (path and host) and the request's query values, converted to
	 * their types. A URL whose values don't convert, or that the record refuses (an IllegalArgumentException from its
	 * constructor), is declined.
	 */
	WOActionResults handle( final RoutedInvocation invocation ) {
		final Object[] arguments = new Object[_components.length];

		for( int i = 0; i < _components.length; i++ ) {
			final RecordComponent component = _components[i];
			final String name = component.getName();
			final String string = _routeParameterNames.contains( name ) ? invocation.parameter( name ) : invocation.request().stringFormValueForKey( name );

			try {
				arguments[i] = string == null ? null : fromString( string, component.getType() );
			}
			catch( IllegalArgumentException e ) {
				return RouteHandler.DECLINED;
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

	private static String toString( final Object value ) {
		return value instanceof Enum<?> e ? e.name() : value.toString();
	}

	/**
	 * @throws IllegalArgumentException if the string isn't a value of the type
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Object fromString( final String string, final Class<?> type ) {
		final Class<?> boxed = boxed( type );

		if( boxed == String.class ) {
			return string;
		}

		if( boxed == Integer.class ) {
			return Integer.valueOf( string );
		}

		if( boxed == Long.class ) {
			return Long.valueOf( string );
		}

		if( boxed == Boolean.class ) {
			if( !string.equals( "true" ) && !string.equals( "false" ) ) {
				throw new IllegalArgumentException( "Not a boolean: " + string );
			}

			return Boolean.valueOf( string );
		}

		if( boxed == LocalDate.class ) {
			try {
				return LocalDate.parse( string );
			}
			catch( DateTimeException e ) {
				throw new IllegalArgumentException( e );
			}
		}

		if( boxed.isEnum() ) {
			return Enum.valueOf( (Class<Enum>)boxed, string );
		}

		throw new IllegalArgumentException( "Unsupported type " + type );
	}

	private static Class<?> boxed( final Class<?> type ) {
		if( type == int.class ) {
			return Integer.class;
		}

		if( type == long.class ) {
			return Long.class;
		}

		if( type == boolean.class ) {
			return Boolean.class;
		}

		return type;
	}

	/**
	 * @return The pattern, and the host pattern if there is one
	 */
	private String description() {
		return _host == null ? _path.source() : _path.source() + " (" + _host.pattern() + ")";
	}

	@Override
	public String toString() {
		return "Endpoint " + description() + " (" + _parametersClass.getSimpleName() + ")";
	}
}
