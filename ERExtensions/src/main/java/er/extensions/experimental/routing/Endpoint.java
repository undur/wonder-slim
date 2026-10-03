package er.extensions.experimental.routing;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

import er.extensions.appserver.ERXApplication;
import er.extensions.appserver.ERXWOContext;
import er.extensions.routes.ERXShortURLs;
import er.extensions.routes.RouteHandler;
import er.extensions.routes.RouteInvocation;
import er.extensions.routes.RouteTable;

/**
 * EXPERIMENTAL (route-links branch). A route whose parameters are the components of a record, see docs/ROUTE_LINKS.md.
 * Kept apart from the routes package on purpose: "Endpoint" is a working name, and converging with the existing routes
 * is a later step.
 *
 * <pre>
 * public record Search( String area, String q, Boolean more ) implements Routable {
 *
 *     public WOActionResults invoke( RouteInvocation invocation ) {
 *         ...
 *     }
 * }
 *
 * public final Endpoint&lt;Search&gt; search = Endpoint.of( "/search/{area}", Search.class );
 * </pre>
 *
 * A record that's only data gets its action as a lambda instead: {@link #of(String, Class, Action)}.
 *
 * Components named in the pattern ({@code {area}}) are path parameters, the others query parameters. Mapped with {@link #mapInto(RouteTable)}. A template links
 * to it with {@code <wo:link route="$routes.search" :area="bork" :q="$someString">}, Java code with
 * {@code routes.search.url( new Search( "bork", someString, null ) )}.
 */

public final class Endpoint<P extends Record> implements RouteHandler {

	/**
	 * What the route does when invoked, given its parameters
	 */
	@FunctionalInterface
	public interface Action<P> {
		public WOActionResults invoke( P parameters, RouteInvocation invocation );
	}

	private static final Set<Class<?>> SUPPORTED_TYPES = Set.of( String.class, Integer.class, int.class, Long.class, long.class, Boolean.class, boolean.class, LocalDate.class );

	private final String _pattern;
	private final Class<P> _parametersClass;
	private final Action<P> _action;
	private final RecordComponent[] _components;
	private final Constructor<P> _constructor;
	private final List<String> _patternSegments;
	private final List<String> _pathParameterNames;

	private Endpoint( final String pattern, final Class<P> parametersClass, final Action<P> action ) {
		_pattern = Objects.requireNonNull( pattern );
		_parametersClass = Objects.requireNonNull( parametersClass );
		_action = Objects.requireNonNull( action );
		_components = parametersClass.getRecordComponents();
		_patternSegments = List.of( pattern.split( "/", -1 ) );
		_pathParameterNames = _patternSegments.stream().filter( Endpoint::isParameterSegment ).map( s -> s.substring( 1, s.length() - 1 ) ).toList();

		if( !pattern.startsWith( "/" ) || pattern.contains( "*" ) ) {
			throw new IllegalArgumentException( "An endpoint's pattern starts with '/' and has no wildcard: '%s'".formatted( pattern ) );
		}

		final List<String> componentNames = parameterNames();

		for( final String name : _pathParameterNames ) {
			if( !componentNames.contains( name ) ) {
				throw new IllegalArgumentException( "The pattern '%s' has the parameter {%s}, but %s has no component of that name. Its components are %s".formatted( pattern, name, parametersClass.getSimpleName(), componentNames ) );
			}
		}

		for( final RecordComponent component : _components ) {
			if( !SUPPORTED_TYPES.contains( component.getType() ) && !component.getType().isEnum() ) {
				throw new IllegalArgumentException( "%s.%s is a %s, which a route parameter can't be. Use one of String, Integer, Long, Boolean, LocalDate or an enum".formatted( parametersClass.getSimpleName(), component.getName(), component.getType().getSimpleName() ) );
			}

			if( component.getType().isPrimitive() && !_pathParameterNames.contains( component.getName() ) ) {
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
	 * @return A route invoked by its parameter record's own {@link Routable#invoke(RouteInvocation)}
	 */
	public static <P extends Record & Routable> Endpoint<P> of( final String pattern, final Class<P> parametersClass ) {
		return new Endpoint<>( pattern, parametersClass, ( parameters, invocation ) -> parameters.invoke( invocation ) );
	}

	/**
	 * @return A route invoked by the given action, for a parameter record that's only data (or one route of several
	 *         taking the same parameters)
	 */
	public static <P extends Record> Endpoint<P> of( final String pattern, final Class<P> parametersClass, final Action<P> action ) {
		return new Endpoint<>( pattern, parametersClass, action );
	}

	/**
	 * Maps the endpoint in the route table, under the part of its pattern before its first parameter
	 * ({@code /search/{area}} as {@code /search/*}): the route table matches exact paths and prefixes, and the endpoint
	 * declines a URL beneath that prefix that doesn't match its whole pattern.
	 */
	public void mapInto( final RouteTable routes ) {
		final int firstParameter = _pattern.indexOf( '{' );
		routes.map( firstParameter == -1 ? _pattern : _pattern.substring( 0, firstParameter ) + "*", this );
	}

	/**
	 * @return true if the URL (a path without a query string) matches the pattern: equal segments, and one non-empty
	 *         path element for each parameter
	 */
	private boolean matches( final String[] urlSegments ) {

		if( urlSegments.length != _patternSegments.size() ) {
			return false;
		}

		for( int i = 0; i < urlSegments.length; i++ ) {
			final String patternSegment = _patternSegments.get( i );

			if( isParameterSegment( patternSegment ) ? urlSegments[i].isEmpty() : !patternSegment.equals( urlSegments[i] ) ) {
				return false;
			}
		}

		return true;
	}

	public String pattern() {
		return _pattern;
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

	private static boolean isParameterSegment( final String segment ) {
		return segment.length() > 2 && segment.startsWith( "{" ) && segment.endsWith( "}" );
	}

	/**
	 * @return The route's parameters built from values by name, as a link element has them. A value given as a string
	 *         (a constant in a template) is converted to the component's type.
	 */
	public P parameters( final Map<String, Object> values ) {
		final List<String> unknown = values.keySet().stream().filter( name -> !parameterNames().contains( name ) ).toList();

		if( !unknown.isEmpty() ) {
			throw new IllegalArgumentException( "The route %s has no parameter %s. Its parameters are %s".formatted( _pattern, unknown, parameterNames() ) );
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
					throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, and '%s' isn't one".formatted( component.getName(), _pattern, component.getType().getSimpleName(), string ), e );
				}
			}

			if( value != null && !boxed( component.getType() ).isInstance( value ) ) {
				throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, but was given a %s: %s".formatted( component.getName(), _pattern, component.getType().getSimpleName(), value.getClass().getSimpleName(), value ) );
			}

			if( value == null && component.getType().isPrimitive() ) {
				throw new IllegalArgumentException( "The route %s needs its parameter '%s'".formatted( _pattern, component.getName() ) );
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
	 *         application's other URLs (short, or with the adaptor prefix)
	 */
	public String url( final P parameters, final WOContext context ) {
		Objects.requireNonNull( context, "A route URL is generated in a context, and there's none" );
		final String[] pathAndQuery = pathAndQuery( parameters );
		return withoutRouteKey( context.urlWithRequestHandlerKey( ERXShortURLs.ROUTE_KEY, pathAndQuery[0].substring( 1 ), pathAndQuery[1] ) );
	}

	/**
	 * @return The path ({@code /search/bork}) and query string ({@code q=kaffi&more=true}, null for none) for the parameters
	 */
	private String[] pathAndQuery( final P parameters ) {
		final Map<String, Object> values = values( parameters );
		final List<String> path = new ArrayList<>();

		for( final String segment : _patternSegments ) {
			if( isParameterSegment( segment ) ) {
				final String name = segment.substring( 1, segment.length() - 1 );
				final Object value = values.get( name );

				if( value == null ) {
					throw new IllegalArgumentException( "The route %s needs its path parameter '%s'".formatted( _pattern, name ) );
				}

				path.add( URLEncoder.encode( toString( value ), StandardCharsets.UTF_8 ).replace( "+", "%20" ) );
			}
			else {
				path.add( segment );
			}
		}

		final String query = values.entrySet().stream()
				.filter( e -> !_pathParameterNames.contains( e.getKey() ) && e.getValue() != null )
				.map( e -> URLEncoder.encode( e.getKey(), StandardCharsets.UTF_8 ) + "=" + URLEncoder.encode( toString( e.getValue() ), StandardCharsets.UTF_8 ) )
				.collect( Collectors.joining( "&" ) );

		return new String[] { String.join( "/", path ), query.isEmpty() ? null : query };
	}

	/**
	 * Routes travel under the route request handler key, which a short URL leaves out ({@code /route/search/bork} is
	 * {@code /search/bork}). FIXME: Belongs in ERXShortURLs, as the reverse of canonicalize(), if this is adopted
	 */
	private static String withoutRouteKey( final String url ) {

		if( !ERXApplication.erxApplication().shortURLs() ) {
			return url;
		}

		final int schemeEnd = url.indexOf( "://" );
		final int pathStart = schemeEnd == -1 ? 0 : url.indexOf( '/', schemeEnd + 3 );
		final String routePrefix = "/" + ERXShortURLs.ROUTE_KEY;

		if( pathStart == -1 || !url.startsWith( routePrefix, pathStart ) ) {
			return url;
		}

		final int afterKey = pathStart + routePrefix.length();

		if( afterKey == url.length() || url.charAt( afterKey ) == '?' ) {
			return url.substring( 0, pathStart ) + "/" + url.substring( afterKey );
		}

		return url.charAt( afterKey ) == '/' ? url.substring( 0, pathStart ) + url.substring( afterKey ) : url;
	}

	/**
	 * Invokes the route: the parameters are read from the URL and converted to their types. A URL whose values don't
	 * convert, or that the record refuses (an IllegalArgumentException from its constructor), is declined.
	 */
	@Override
	public WOActionResults handle( final RouteInvocation invocation ) {
		final String[] urlSegments = invocation.url().split( "/", -1 );

		if( !matches( urlSegments ) ) {
			return DECLINED;
		}
		final Map<String, String> strings = new LinkedHashMap<>();

		for( int i = 0; i < _patternSegments.size(); i++ ) {
			final String segment = _patternSegments.get( i );

			if( isParameterSegment( segment ) ) {
				strings.put( segment.substring( 1, segment.length() - 1 ), URLDecoder.decode( urlSegments[i].replace( "+", "%2B" ), StandardCharsets.UTF_8 ) );
			}
		}

		final Object[] arguments = new Object[_components.length];

		for( int i = 0; i < _components.length; i++ ) {
			final RecordComponent component = _components[i];
			final String string = strings.containsKey( component.getName() ) ? strings.get( component.getName() ) : invocation.request().stringFormValueForKey( component.getName() );

			try {
				arguments[i] = string == null ? null : fromString( string, component.getType() );
			}
			catch( IllegalArgumentException e ) {
				return DECLINED;
			}
		}

		final P parameters;

		// A URL is user input: values the record refuses decline it, as values that don't convert do
		try {
			parameters = construct( arguments );
		}
		catch( IllegalArgumentException e ) {
			return DECLINED;
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
			catch( java.time.DateTimeException e ) {
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

	@Override
	public String toString() {
		return "Endpoint " + _pattern + " (" + _parametersClass.getSimpleName() + ")";
	}
}
