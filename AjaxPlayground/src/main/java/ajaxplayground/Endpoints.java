package ajaxplayground;

import com.webobjects.appserver.WOActionResults;

import ajaxplayground.components.scenario.ScenarioRouteLinks;
import er.extensions.experimental.routing.Endpoint;
import er.extensions.experimental.routing.Routable;
import er.extensions.experimental.routing.RouteGroup;
import er.extensions.experimental.routing.RoutedInvocation;
import er.extensions.experimental.routing.core.Host;

/**
 * EXPERIMENTAL (route-links branch). The playground's endpoints, declared from the router's routes, reached by templates
 * as {@code $routes} (see {@code PlaygroundPage.routes()}).
 */
public class Endpoints {

	private static Endpoints _instance;

	public enum Area {
		books, music, films
	}

	/**
	 * Does the route's work itself
	 */
	public record Search( Area area, String q, Boolean more ) implements Routable {

		@Override
		public WOActionResults invoke( final RoutedInvocation invocation ) {
			return show( this, invocation );
		}
	}

	/**
	 * Only data: its route's action is a lambda
	 */
	public record ItemParameters( int id, String tab ) {

		public ItemParameters {
			if( id < 1 ) {
				throw new IllegalArgumentException( "An item id is positive, not " + id );
			}
		}
	}

	/**
	 * A host parameter: the tenant is the host's first label ({tenant}.localhost)
	 */
	public record TenantHome( String tenant, String tab ) implements Routable {

		@Override
		public WOActionResults invoke( final RoutedInvocation invocation ) {
			return show( this, invocation );
		}
	}

	/**
	 * A group's path parameter: the shop comes from the group's prefix (/typed/shops/{shop})
	 */
	public record ShopItem( String shop, int id ) implements Routable {

		@Override
		public WOActionResults invoke( final RoutedInvocation invocation ) {
			return show( this, invocation );
		}
	}

	public final Endpoint<Search> search;
	public final Endpoint<ItemParameters> item;
	public final Endpoint<TenantHome> tenantHome;
	public final Endpoint<ShopItem> shopItem;

	private Endpoints( final RouteGroup routes ) {
		search = routes.endpoint( "/typed/search/{area}", Search.class );
		item = routes.endpoint( "/typed/item/{id}", ItemParameters.class, Endpoints::show );
		tenantHome = routes.endpoint( "/typed/tenant", TenantHome.class, Host.of( "{tenant}.localhost" ) );
		shopItem = routes.group( "/typed/shops/{shop}" ).endpoint( "/items/{id}", ShopItem.class );
	}

	/**
	 * Declares the endpoints in the given routes
	 */
	public static void declare( final RouteGroup routes ) {
		_instance = new Endpoints( routes );
	}

	public static Endpoints instance() {
		return _instance;
	}

	/**
	 * Every endpoint shows the link page, with the parameters it received
	 */
	private static <P> WOActionResults show( final P parameters, final RoutedInvocation invocation ) {
		final ScenarioRouteLinks page = new ScenarioRouteLinks( invocation.context() );
		page.received = parameters.toString();
		return page;
	}
}
