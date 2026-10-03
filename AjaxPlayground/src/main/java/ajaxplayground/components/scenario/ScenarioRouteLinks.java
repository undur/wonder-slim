package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.Endpoints;
import ajaxplayground.Endpoints.Area;
import ajaxplayground.Endpoints.Search;
import ajaxplayground.components.PlaygroundPage;

/**
 * EXPERIMENTAL (route-links branch). Links to endpoints, from the template and from Java, and shows what a route
 * received when it was invoked.
 */
public class ScenarioRouteLinks extends PlaygroundPage {

	/**
	 * The parameters the route that rendered this page received, as text, null when it wasn't rendered by one
	 */
	public String received;

	public String query = "kaffi & kökur";

	public ScenarioRouteLinks( WOContext context ) {
		super( context );
	}

	public String javaURL() {
		return Endpoints.instance().search.url( new Search( Area.films, "noir", null ), context() );
	}
}
