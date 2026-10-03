package er.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

/**
 * EXPERIMENTAL (route-links branch). What elements taking a route ({@code <wo:route>}, {@code <wo:routeForm>}) share:
 * their {@code route} binding and its {@code :} parameters become an {@code href} computing the route's URL.
 */

class RouteBindings {

	private static final String ROUTE_KEY = "route";
	private static final String PARAMETER_PREFIX = ":";

	private RouteBindings() {}

	/**
	 * @param tag The element's tag, for error messages ({@code <wo:route>})
	 * @param otherTag The tag for elements not taking a route, for error messages ({@code <wo:link>})
	 * @param urlKeys The element's other bindings for its URL, which a route replaces
	 * @return The associations with {@code route} and the {@code :} parameters replaced by an {@code href} computing the
	 *         route's URL
	 */
	static NSDictionary<String, WOAssociation> withRouteURL( final NSDictionary<String, WOAssociation> associations, final String tag, final String otherTag, final List<String> urlKeys ) {
		final WOAssociation route = associations.objectForKey( ROUTE_KEY );
		final Map<String, WOAssociation> parameters = new LinkedHashMap<>();

		for( final String key : associations.allKeys() ) {
			if( key.startsWith( PARAMETER_PREFIX ) ) {
				parameters.put( key.substring( PARAMETER_PREFIX.length() ), associations.objectForKey( key ) );
			}
		}

		if( route == null ) {
			throw new IllegalArgumentException( "%s needs a 'route' binding: the route it goes to".formatted( tag ) );
		}

		for( final String key : urlKeys ) {
			if( associations.objectForKey( key ) != null ) {
				throw new IllegalArgumentException( "%s takes its URL from its route, so it can't bind '%s'. Otherwise, use %s".formatted( tag, key, otherTag ) );
			}
		}

		final NSMutableDictionary<String, WOAssociation> result = associations.mutableClone();
		result.removeObjectForKey( ROUTE_KEY );
		parameters.keySet().forEach( name -> result.removeObjectForKey( PARAMETER_PREFIX + name ) );
		result.setObjectForKey( new RouteURLAssociation( route, parameters ), "href" );
		return result;
	}

	/**
	 * The URL of the bound route, for its bound parameters (host parameters it doesn't bind taken from the request's
	 * host, the rest left empty)
	 */
	private static class RouteURLAssociation extends WOAssociation {

		private final WOAssociation _route;
		private final Map<String, WOAssociation> _parameters;

		private RouteURLAssociation( final WOAssociation route, final Map<String, WOAssociation> parameters ) {
			_route = route;
			_parameters = parameters;
		}

		@Override
		public Object valueInComponent( final WOComponent component ) {
			final Object route = _route.valueInComponent( component );

			if( !(route instanceof Linkable linkable) ) {
				throw new IllegalArgumentException( "The 'route' binding (%s) is %s, not a route".formatted( _route.keyPath(), route == null ? "null" : "a " + route.getClass().getName() ) );
			}

			final Map<String, Object> values = new LinkedHashMap<>();
			_parameters.forEach( ( name, association ) -> values.put( name, association.valueInComponent( component ) ) );

			// An attribute's value, and WO doesn't escape it
			return linkable.url( values, component.context() ).replace( "&", "&amp;" );
		}

		@Override
		public String keyPath() {
			return _route.keyPath();
		}

		@Override
		public String bindingInComponent( final WOComponent component ) {
			return _route.bindingInComponent( component );
		}
	}
}
