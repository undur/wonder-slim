package er.routing.options;

/**
 * EXPERIMENTAL (route-links branch). How a typed route treats query parameters and form fields that don't convert to
 * their components' types (or name objects that don't exist).
 *
 * By default such a request is declined: a URL is input, and a value that isn't one of its type means the URL is wrong,
 * so a route that never looks at its input's errors fails safe. A route taking a form opts in to {@link #REPORTED}:
 * the component is null, and the route hears about it in {@code RouteInvocation.conversionErrors()}, so it can show the
 * form again with what's wrong.
 *
 * <pre>
 * createBook = club.route( "/books", CreateBook.class, BookclubRoutes::createBook, Method.POST, Fields.REPORTED );
 * </pre>
 */

public enum Fields implements RouteBehavior {

	/**
	 * A field that doesn't convert declines the request, or goes to the route's {@code whenInvalid} (the default)
	 */
	DECLINED,

	/**
	 * Fields that don't convert are null, and reported in {@code RouteInvocation.conversionErrors()}
	 */
	REPORTED;

	/**
	 * A plain route reads its own fields
	 */
	@Override
	public boolean appliesToPlainRoutes() {
		return false;
	}
}
