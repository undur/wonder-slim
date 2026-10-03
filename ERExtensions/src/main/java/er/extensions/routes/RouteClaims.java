package er.extensions.routes;

/**
 * Implemented by a route handler mapped under a broad pattern ({@code /*}) that holds routes of its own, such as a
 * router: it says which URLs it has a route for, so {@link RouteTable#hasRouteFor(String)} answers for its routes rather
 * than for its pattern.
 */

public interface RouteClaims {

	/**
	 * @return true if the handler has a route matching the URL (a path, without a query string), whether or not that
	 *         route answers it when asked
	 */
	public boolean claims( String url );
}
