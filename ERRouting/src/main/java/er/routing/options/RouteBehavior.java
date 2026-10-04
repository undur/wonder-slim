package er.routing.options;


/**
 * An option the router's WebObjects layer handles itself, rather than the core's
 * matching: how a typed route treats its fields ({@link Fields}), whether a route takes posts from other sites
 * ({@link CrossSite}). A group's behaviors reach its routes and nested groups, and those of a group a plugin joins, unless
 * a route sets its own of the same type. One that doesn't apply to a kind of route is refused there.
 */

public interface RouteBehavior extends RouteOption {

	/**
	 * @return true if the behavior means something for a plain route, which is refused it otherwise
	 */
	public default boolean appliesToPlainRoutes() {
		return true;
	}
}
