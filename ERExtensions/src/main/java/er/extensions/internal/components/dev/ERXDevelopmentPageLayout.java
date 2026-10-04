package er.extensions.internal.components.dev;

import java.util.List;

import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;
import er.extensions.routing.ERXRouter;
import er.extensions.routing.RouteDescription;

/**
 * The page around {@link ERXWelcomePage} and {@link ERXRouteNotFoundPage}: the look, and the list of mapped routes.
 *
 * @binding title the page's title
 */
public class ERXDevelopmentPageLayout extends ERXComponent {

	public String title;
	public RouteDescription currentRoute;

	public ERXDevelopmentPageLayout( final WOContext context ) {
		super( context );
	}

	/**
	 * @return The router's routes, in precedence order
	 */
	public List<RouteDescription> routes() {
		return ERXRouter.defaultRouter().routes();
	}

	public boolean hasRoutes() {
		return !routes().isEmpty();
	}

	/**
	 * @return What the current route answers with (its page's name, or what kind of handler it is), its conditions, and
	 *         the table it's in when it's a plugin's
	 */
	public String currentRouteTarget() {
		final StringBuilder b = new StringBuilder( currentRoute.answer() );

		// Its conditions tell apart routes at the same path (GET and POST)
		currentRoute.conditions().forEach( condition -> b.append( ", " ).append( condition ) );

		if( !"application".equals( currentRoute.table() ) ) {
			b.append( " (" ).append( currentRoute.table() ).append( ")" );
		}

		return b.toString();
	}
}
