package er.routing;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXWOContext;
import er.routing.core.Converters;
import er.routing.core.Host;
import er.routing.core.PathPattern;
import er.routing.core.RouteCondition;
import er.routing.core.RouteOption;
import er.routing.core.RouteRequest;

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

public final class Route<P extends Record> {

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
	private final RecordComponent[] _components;
	private final Constructor<P> _constructor;

	/**
	 * The path and host parameters' names
	 */
	private final List<String> _routeParameterNames;

	Route( final String pattern, final List<RouteOption> options, final Class<P> parametersClass, final Action<P> action, final Converters converters ) {
		_converters = Objects.requireNonNull( converters );
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
	 * @return The route's parameters built from values by name, as a link element has them, taking host parameters the
	 *         values don't have from the context's request (see below). A value given as a string (a constant in a
	 *         template) is converted to the component's type.
	 */
	public P parameters( final Map<String, Object> values, final WOContext context ) {
		return parameters( withHostParameters( values, context ) );
	}

	/**
	 * @return The values, with the host parameters they don't have taken from the request's host, if it matches the
	 *         route's host pattern: a link within a host doesn't repeat its parameters
	 */
	private Map<String, Object> withHostParameters( final Map<String, Object> values, final WOContext context ) {

		if( _host == null || _host.parameterNames().isEmpty() || context == null || context.request() == null || values.keySet().containsAll( _host.parameterNames() ) ) {
			return values;
		}

		final String requestHost = RequestHost.host( context.request() );

		if( requestHost == null || !(_host.test( new RouteRequest( "GET", requestHost, "/" ) ) instanceof RouteCondition.Satisfied satisfied) ) {
			return values;
		}

		final Map<String, Object> all = new LinkedHashMap<>( values );
		satisfied.parameters().forEach( all::putIfAbsent );
		return all;
	}

	/**
	 * @return The route's parameters built from values by name. A value given as a string (a constant in a template) is
	 *         converted to the component's type.
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
					value = _converters.fromString( string, component.getType() );

					if( value == null ) {
						throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, and there's none for '%s'".formatted( component.getName(), description(), component.getType().getSimpleName(), string ) );
					}
				}
				catch( IllegalArgumentException e ) {
					throw new IllegalArgumentException( "The parameter '%s' of the route %s is a %s, and '%s' isn't one".formatted( component.getName(), description(), component.getType().getSimpleName(), string ), e );
				}
			}

			if( value != null && !Converters.boxed( component.getType() ).isInstance( value ) ) {
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
				strings.put( name, _converters.toString( value ) );
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

		// A context generating complete URLs (an email) gave one already: its host is replaced, keeping its scheme and port
		final int schemeEnd = url.indexOf( "://" );

		if( schemeEnd != -1 ) {
			final int authorityStart = schemeEnd + 3;
			final int pathStart = url.indexOf( '/', authorityStart ) == -1 ? url.length() : url.indexOf( '/', authorityStart );
			return url.substring( 0, authorityStart ) + host + port( url.substring( authorityStart, pathStart ) ) + url.substring( pathStart );
		}

		final String scheme = context.request() != null && context.request().isSecure() ? "https" : "http";
		return scheme + "://" + host + (requestHost == null ? "" : port( requestHost )) + url;
	}

	/**
	 * @return The port of a host as written in a URL or a Host header ({@code :1300}), empty for none
	 */
	private static String port( final String hostAndPort ) {
		final int colon = hostAndPort.lastIndexOf( ':' );
		return colon == -1 || hostAndPort.endsWith( "]" ) ? "" : hostAndPort.substring( colon );
	}

	/**
	 * Invokes the route: the parameters are the router's (path and host) and the request's query values, converted to
	 * their types. A URL whose values don't convert, or that the record refuses (an IllegalArgumentException from its
	 * constructor), is declined.
	 */
	WOActionResults handle( final RouteInvocation invocation ) {
		final Object[] arguments = new Object[_components.length];

		for( int i = 0; i < _components.length; i++ ) {
			final RecordComponent component = _components[i];
			final String name = component.getName();
			final String string = _routeParameterNames.contains( name ) ? invocation.parameter( name ) : invocation.request().stringFormValueForKey( name );

			if( string == null ) {
				continue;
			}

			// A value that isn't one of the type, or names an object that doesn't exist, declines the URL
			try {
				arguments[i] = _converters.fromString( string, component.getType() );
			}
			catch( IllegalArgumentException e ) {
				return RouteHandler.DECLINED;
			}

			if( arguments[i] == null ) {
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
