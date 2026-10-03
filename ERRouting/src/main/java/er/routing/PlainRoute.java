package er.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.webobjects.appserver.WOContext;

import er.routing.core.Converters;
import er.routing.core.Host;
import er.routing.core.PathPattern;

/**
 * EXPERIMENTAL (route-links branch). A route mapped with a handler ({@link RouteGroup#map(String, RouteHandler,
 * er.routing.core.RouteOption...)}), for links, forms and redirects to it:
 *
 * <pre>
 * guestbook = club.map( "/guestbook", ri -&gt; …, Method.GET );
 * return seeOther( guestbook.url( context ) );
 * </pre>
 *
 * Its parameters are its pattern's and its host's, given by name and converted by the router's converters. Query
 * parameters are added to a link with {@code ?} attributes.
 */

public final class PlainRoute implements Linkable {

	private final PathPattern _path;
	private final Host _host;
	private final Converters _converters;
	private final List<String> _routeParameterNames;

	PlainRoute( final PathPattern path, final Host host, final Converters converters ) {
		_path = Objects.requireNonNull( path );
		_host = host;
		_converters = Objects.requireNonNull( converters );

		final List<String> names = new ArrayList<>( path.parameterNames() );

		if( host != null ) {
			names.addAll( host.parameterNames() );
		}

		_routeParameterNames = List.copyOf( names );
	}

	public String pattern() {
		return _path.source();
	}

	/**
	 * @throws IllegalArgumentException for a missing route parameter, or a wildcard route (which has no URL of its own)
	 */
	@Override
	public String url( final Map<String, Object> values, final WOContext context ) {
		return RouteURLs.url( _path, _host, strings( values, context ), List.of(), context );
	}

	@Override
	public String completeURL( final Map<String, Object> values ) {
		return RouteURLs.completeURL( _path, _host, strings( values, null ), List.of() );
	}

	/**
	 * @return The values as URL text, host parameters they don't have taken from the context's request (if there's one)
	 */
	private Map<String, String> strings( final Map<String, Object> values, final WOContext context ) {
		final List<String> unknown = values.keySet().stream().filter( name -> !_routeParameterNames.contains( name ) ).toList();

		// Free query parameters are ?-attributes on a link, so a name that isn't a parameter is a mistake, not a query
		if( !unknown.isEmpty() ) {
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
		return "PlainRoute " + _path + (_host == null ? "" : " (" + _host.pattern() + ")");
	}
}
