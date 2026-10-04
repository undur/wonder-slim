package er.extensions.routing;

import java.util.List;

import er.routing.matching.Router;
import er.routing.options.RouteOption;

/**
 * The application's routes, as {@link ERXRouter#declare(java.util.function.Consumer)} hands them over: a group, and what
 * only the application sets (what answers a request no route answered).
 */

public final class ApplicationRoutes extends RouteGroup {

	ApplicationRoutes( final ERXRouter router, final Router<ERXRouter.Mapped>.Table table, final RouteGroup parent, final List<RouteOption> options ) {
		super( router, table, parent, "", options );
	}

	/**
	 * Sets what answers a request no route answered, before the not found handler: the application's {@code public}
	 * folder ({@code new ERXPublicResources()}), say. It may decline, passing the request on to the not found handler.
	 *
	 * @return These routes
	 */
	public ApplicationRoutes fallback( final RouteHandler fallback ) {
		router().undeclared( "the fallback, set" );
		router().fallback( fallback );
		return this;
	}

	/**
	 * Sets what answers a request nothing else answered. Without one, the development pages answer in development (a
	 * welcome page at {@code /}, otherwise a 404 saying why the routes that matched passed the URL on), and a plain 404
	 * deployed. One that declines passes the request on (to the next handler in the server, with wo-adaptor-jetty).
	 *
	 * @return These routes
	 */
	public ApplicationRoutes notFound( final RouteHandler notFound ) {
		router().undeclared( "the not found handler, set" );
		router().notFound( notFound );
		return this;
	}
}
