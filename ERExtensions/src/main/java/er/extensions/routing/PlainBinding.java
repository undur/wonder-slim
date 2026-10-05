package er.extensions.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.webobjects.appserver.WOContext;

import er.routing.conversion.Converters;
import er.routing.matching.PathPattern;
import er.routing.options.Host;



/**
 * A plain route as one router has it ({@link PlainRoute} is the route itself, which
 * links use): its whole pattern and host, for its URLs.
 */

final class PlainBinding {

	private final PathPattern _path;
	private final Host _host;
	private final Converters _converters;
	private final List<String> _routeParameterNames;

	PlainBinding( final PathPattern path, final Host host, final Converters converters ) {
		_path = Objects.requireNonNull( path );
		_host = host;
		_converters = Objects.requireNonNull( converters );

		final List<String> names = new ArrayList<>( path.parameterNames() );

		if( host != null ) {
			names.addAll( host.parameterNames() );
		}

		_routeParameterNames = List.copyOf( names );
	}

	/**
	 * @return The names a link gives values for: the path's parameters and the host's
	 */
	List<String> acceptedNames() {
		return _routeParameterNames;
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

	/**
	 * @throws IllegalArgumentException for a missing route parameter, or a wildcard route (which has no URL of its own)
	 */
	public String url( final Map<String, Object> values, final WOContext context ) {
		return RouteURLs.url( _path, _host, strings( values, context ), List.of(), context );
	}

	public String completeURL( final Map<String, Object> values ) {
		return RouteURLs.completeURL( _path, _host, strings( values, null ), List.of() );
	}

	/**
	 * @return The values as URL text, host parameters they don't have taken from the context's request (if there's one)
	 */
	private Map<String, String> strings( final Map<String, Object> values, final WOContext context ) {
		// Free query parameters are ?-attributes on a link, so a name that isn't a parameter is a mistake, not a query
		if( !_routeParameterNames.containsAll( values.keySet() ) ) {
			final List<String> unknown = values.keySet().stream().filter( name -> !_routeParameterNames.contains( name ) ).toList();
			throw new IllegalArgumentException( "The route %s has no parameter %s. Its parameters are %s".formatted( _path, unknown, _routeParameterNames ) );
		}

		final Map<String, String> strings = new LinkedHashMap<>();
		RouteURLs.withHostParameters( _host, values, context ).forEach( ( name, value ) -> {
			if( value != null ) {
				strings.put( name, switch( value ) {
					case InheritedText inherited -> inherited.text();
					case String string -> string;
					default -> _converters.toString( value );
				} );
			}
		} );

		return strings;
	}

	@Override
	public String toString() {
		return _path + (_host == null ? "" : " (" + _host.pattern() + ")");
	}
}
