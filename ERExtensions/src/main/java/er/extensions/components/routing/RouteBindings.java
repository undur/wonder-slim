package er.extensions.components.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.routing.Linkable;
import er.extensions.routing.Route;


/**
 * What elements taking a route ({@code <wo:route>}, {@code <wo:routeForm>}) share:
 * their {@code to} binding (the route) and its {@code :} parameters become an {@code href} computing the route's URL.
 */

class RouteBindings {

	private static final String ROUTE_KEY = "to";
	private static final String PARAMETER_PREFIX = ":";

	private RouteBindings() {}

	/**
	 * @param tag The element's tag, for error messages ({@code <wo:route>})
	 * @param otherTag The tag for elements not taking a route, for error messages ({@code <wo:link>})
	 * @param urlKeys The element's other bindings for its URL, which a route replaces
	 * @return The associations with {@code to} and the {@code :} parameters replaced by an {@code href} computing the
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
			throw new IllegalArgumentException( "%s needs a 'to' binding: the route it goes to".formatted( tag ) );
		}

		for( final String key : urlKeys ) {
			if( associations.objectForKey( key ) != null ) {
				throw new IllegalArgumentException( "%s takes its URL from its route, so it can't bind '%s'. Otherwise, use %s".formatted( tag, key, otherTag ) );
			}
		}

		final List<String> queryNames = associations.allKeys().stream().filter( key -> key.startsWith( "?" ) ).map( key -> key.substring( 1 ) ).toList();
		final NSMutableDictionary<String, WOAssociation> result = associations.mutableClone();
		result.removeObjectForKey( ROUTE_KEY );
		parameters.keySet().forEach( name -> result.removeObjectForKey( PARAMETER_PREFIX + name ) );
		result.setObjectForKey( new RouteURLAssociation( route, parameters, queryNames, tag ), "href" );
		return result;
	}

	/**
	 * The URL of the bound route, for its bound parameters (host parameters it doesn't bind taken from the request's
	 * host, the rest left empty)
	 */
	private static class RouteURLAssociation extends WOAssociation {

		private final WOAssociation _route;
		private final Map<String, WOAssociation> _parameters;

		/**
		 * The names of the element's {@code ?} bindings, free query parameters
		 */
		private final List<String> _queryNames;
		private final String _tag;

		private RouteURLAssociation( final WOAssociation route, final Map<String, WOAssociation> parameters, final List<String> queryNames, final String tag ) {
			_route = route;
			_parameters = parameters;
			_queryNames = queryNames;
			_tag = tag;
		}

		@Override
		public Object valueInComponent( final WOComponent component ) {
			final Object route = _route.valueInComponent( component );

			if( !(route instanceof Linkable linkable) ) {
				throw new IllegalArgumentException( "The 'to' binding (%s) is %s, not a route".formatted( _route.keyPath(), route == null ? "null" : "a " + route.getClass().getName() ) );
			}

			// A typed route's own parameter as a free query parameter would skip the check a : binding gets
			if( linkable instanceof Route<?> typed ) {
				for( final String name : _queryNames ) {
					if( typed.parameterNames().contains( name ) ) {
						throw new IllegalArgumentException( "%s binds ?%s, a parameter of the route %s: bind it as :%s, so its value is checked".formatted( _tag, name, typed, name ) );
					}
				}
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
