package ajaxplayground;

import com.webobjects.appserver.WOActionResults;

import ajaxplayground.components.scenario.ScenarioRouteLinks;
import er.extensions.experimental.routing.Endpoint;
import er.extensions.experimental.routing.Routable;
import er.extensions.routes.RouteInvocation;
import er.extensions.routes.RouteTable;

/**
 * EXPERIMENTAL (route-links branch). The playground's endpoints, reached by templates as {@code $routes}
 * (see {@code PlaygroundPage.routes()}).
 */
public class Endpoints {

	public static final Endpoints INSTANCE = new Endpoints();

	public enum Area {
		books, music, films
	}

	/**
	 * Does the route's work itself
	 */
	public record Search( Area area, String q, Boolean more ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
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

	public final Endpoint<Search> search = Endpoint.of( "/typed/search/{area}", Search.class );

	public final Endpoint<ItemParameters> item = Endpoint.of( "/typed/item/{id}", ItemParameters.class, Endpoints::show );

	private Endpoints() {}

	public void register( final RouteTable routes ) {
		search.mapInto( routes );
		item.mapInto( routes );
	}

	/**
	 * Both routes show the link page, with the parameters they received
	 */
	private static <P> WOActionResults show( final P parameters, final RouteInvocation invocation ) {
		final ScenarioRouteLinks page = new ScenarioRouteLinks( invocation.context() );
		page.received = parameters.toString();
		return page;
	}
}
