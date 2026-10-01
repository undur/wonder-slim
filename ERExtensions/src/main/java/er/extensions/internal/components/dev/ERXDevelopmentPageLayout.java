package er.extensions.internal.components.dev;

import java.util.List;

import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;
import er.extensions.routes.RouteTable;
import er.extensions.routes.RouteTable.ComponentClassRouteHandler;
import er.extensions.routes.RouteTable.Route;

/**
 * The page around {@link ERXWelcomePage} and {@link ERXRouteNotFoundPage}: the look, and the list of mapped routes.
 *
 * @binding title the page's title
 */
public class ERXDevelopmentPageLayout extends ERXComponent {

	public String title;
	public Route currentRoute;

	public ERXDevelopmentPageLayout( final WOContext context ) {
		super( context );
	}

	public List<Route> routes() {
		return RouteTable.defaultRouteTable().routes();
	}

	public boolean hasRoutes() {
		return !routes().isEmpty();
	}

	/**
	 * @return What the current route answers with: its component's name, or what kind of handler it is
	 */
	public String currentRouteTarget() {

		if( currentRoute.routeHandler() instanceof ComponentClassRouteHandler handler ) {
			return handler.componentClass().getSimpleName();
		}

		final Class<?> handlerClass = currentRoute.routeHandler().getClass();
		return handlerClass.isHidden() || handlerClass.isAnonymousClass() ? "a handler" : handlerClass.getSimpleName();
	}
}
