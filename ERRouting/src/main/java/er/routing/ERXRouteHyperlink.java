package er.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOElement;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.components.patches.ERXWOHyperlink;

/**
 * EXPERIMENTAL (route-links branch). A link to an {@link Route}, as {@code <wo:route>}, see docs/ROUTE_LINKS.md.
 *
 * <pre>
 * &lt;wo:route route="$routes.search" :area="bork" :q="$someString"&gt;Search&lt;/wo:route&gt;
 * </pre>
 *
 * {@code route} binds an {@link Route}, and each {@code :name} binding one of its parameters. The URL is the
 * route's URL for those parameters: an unknown parameter, a missing path parameter or a value of the wrong type is an
 * error when the link renders. {@code ?} bindings are added to the query as on any hyperlink. The URL comes from the
 * route only, so {@code href}, {@code action}, {@code pageName} and the direct action bindings aren't accepted.
 */

public class ERXRouteHyperlink extends ERXWOHyperlink {

	private static final String ROUTE_KEY = "route";
	private static final String PARAMETER_PREFIX = ":";
	private static final List<String> URL_KEYS = List.of( "href", "action", "directActionName", "actionClass", "pageName" );

	public ERXRouteHyperlink( final String name, final NSDictionary associations, final WOElement template ) {
		super( name, withRouteURL( associations ), template );
	}

	/**
	 * @return The associations with {@code route} and the {@code :} parameters replaced by an {@code href} computing the
	 *         route's URL
	 */
	private static NSDictionary<String, WOAssociation> withRouteURL( final NSDictionary<String, WOAssociation> associations ) {
		final WOAssociation route = associations.objectForKey( ROUTE_KEY );
		final Map<String, WOAssociation> parameters = new LinkedHashMap<>();

		for( final String key : associations.allKeys() ) {
			if( key.startsWith( PARAMETER_PREFIX ) ) {
				parameters.put( key.substring( PARAMETER_PREFIX.length() ), associations.objectForKey( key ) );
			}
		}

		if( route == null ) {
			throw new IllegalArgumentException( "<wo:route> needs a 'route' binding: the route to link to" );
		}

		for( final String key : URL_KEYS ) {
			if( associations.objectForKey( key ) != null ) {
				throw new IllegalArgumentException( "<wo:route> takes its URL from its route, so it can't bind '%s'. For other links, use <wo:link>".formatted( key ) );
			}
		}

		final NSMutableDictionary<String, WOAssociation> result = associations.mutableClone();
		result.removeObjectForKey( ROUTE_KEY );
		parameters.keySet().forEach( name -> result.removeObjectForKey( PARAMETER_PREFIX + name ) );
		result.setObjectForKey( new RouteURLAssociation( route, parameters ), "href" );
		return result;
	}

	/**
	 * The URL of the bound route, for its bound parameters
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

			if( !(route instanceof Route<?> endpoint) ) {
				throw new IllegalArgumentException( "The 'route' binding (%s) is %s, not an Route".formatted( _route.keyPath(), route == null ? "null" : "a " + route.getClass().getName() ) );
			}

			final Map<String, Object> values = new LinkedHashMap<>();
			_parameters.forEach( ( name, association ) -> values.put( name, association.valueInComponent( component ) ) );
			return url( endpoint, values, component );
		}

		private static <P extends Record> String url( final Route<P> route, final Map<String, Object> values, final WOComponent component ) {
			return route.url( route.parameters( values ), component.context() );
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
