package er.extensions.experimental.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;

import er.extensions.experimental.routing.core.RouteCondition;
import er.extensions.experimental.routing.core.Router;
import er.extensions.experimental.routing.core.TrailingSlash;

/**
 * EXPERIMENTAL (route-links branch). Routes sharing a path prefix, conditions and wrapping. A table's routes are its
 * root group ({@link ERXRouter#table(String)}).
 *
 * <pre>
 * routes.map( "/items/{id}", ri -&gt; ItemPage.page( ri, ri.parameter( "id" ) ) );
 * routes.map( "/hooks/github", hooks::github, Method.POST );
 * routes.group( "/manage", manage -&gt; {
 *     manage.wrap( requireLogin );
 *     manage.map( "/users", Users.class );
 * }, Host.of( "admin.example.com" ) );
 * </pre>
 */

public final class RouteGroup {

	private final ERXRouter _router;
	private final Router<ERXRouter.Mapped>.Table _table;
	private final RouteGroup _parent;
	private final String _prefix;
	private final List<RouteCondition> _conditions;
	private final List<RouteFilter> _filters = new ArrayList<>();

	RouteGroup( final ERXRouter router, final Router<ERXRouter.Mapped>.Table table, final RouteGroup parent, final String prefix, final List<RouteCondition> conditions ) {
		_router = router;
		_table = table;
		_parent = parent;
		_prefix = prefix;
		_conditions = List.copyOf( conditions );
	}

	/**
	 * Wraps every route of the group, including those mapped before this call and those of its nested groups. A nested
	 * group's filters run inside its parent's.
	 */
	public RouteGroup wrap( final RouteFilter filter ) {
		_filters.add( Objects.requireNonNull( filter ) );
		return this;
	}

	/**
	 * Maps a route
	 */
	public void map( final String pattern, final RoutedHandler handler, final RouteCondition... conditions ) {
		map( pattern, null, handler, conditions );
	}

	/**
	 * Maps a route with its own trailing slash policy
	 */
	public void map( final String pattern, final TrailingSlash trailingSlash, final RoutedHandler handler, final RouteCondition... conditions ) {
		_router.map( _table, fullPattern( pattern ), trailingSlash, new ERXRouter.Mapped( handler, this ), allConditions( conditions ) );
	}

	/**
	 * Maps a route to a page
	 */
	public void map( final String pattern, final Class<? extends WOComponent> pageClass, final RouteCondition... conditions ) {
		map( pattern, invocation -> WOApplication.application().pageWithName( pageClass.getName(), invocation.context() ), conditions );
	}

	/**
	 * @return A nested group, after the body has mapped its routes
	 */
	public RouteGroup group( final String prefix, final Consumer<RouteGroup> body, final RouteCondition... conditions ) {

		if( !prefix.startsWith( "/" ) || prefix.endsWith( "/" ) ) {
			throw new IllegalArgumentException( "A group's prefix starts with '/' and doesn't end with one: '%s'".formatted( prefix ) );
		}

		final RouteGroup group = new RouteGroup( _router, _table, this, _prefix + prefix, allConditions( conditions ) );
		body.accept( group );
		return group;
	}

	/**
	 * @return The group's prefix and the pattern: {@code /manage} and {@code /users} give {@code /manage/users}, and
	 *         {@code /} gives {@code /manage/}
	 */
	String fullPattern( final String pattern ) {
		if( !pattern.startsWith( "/" ) ) {
			throw new IllegalArgumentException( "A route's pattern starts with '/': '%s'".formatted( pattern ) );
		}

		return _prefix + pattern;
	}

	/**
	 * @return The group's conditions (its parents' included) and the given ones
	 */
	List<RouteCondition> allConditions( final RouteCondition... conditions ) {
		final List<RouteCondition> all = new ArrayList<>( _conditions );
		all.addAll( List.of( conditions ) );
		return all;
	}

	/**
	 * @return The handler wrapped in the filters of this group and its parents, outermost first
	 */
	RoutedHandler wrapped( final RoutedHandler handler ) {
		RoutedHandler wrapped = handler;

		for( RouteGroup group = this; group != null; group = group._parent ) {
			for( int i = group._filters.size() - 1; i >= 0; i-- ) {
				final RouteFilter filter = group._filters.get( i );
				final RoutedHandler next = wrapped;
				wrapped = invocation -> filter.filter( invocation, next );
			}
		}

		return wrapped;
	}
}
