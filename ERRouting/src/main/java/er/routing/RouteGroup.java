package er.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;

import er.routing.core.Host;
import er.routing.core.PathPattern;
import er.routing.core.RouteCondition;
import er.routing.core.RouteOption;
import er.routing.core.Router;
import er.routing.core.TrailingSlash;

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
 *
 * Route&lt;Search&gt; search = routes.route( "/search/{area}", Search.class );
 * </pre>
 *
 * Routes and groups take options ({@link RouteOption}): conditions, and a trailing slash policy. A group's conditions
 * apply to its routes and nested groups, as does its policy unless a route or nested group sets its own.
 */

public final class RouteGroup {

	private final ERXRouter _router;
	private final Router<ERXRouter.Mapped>.Table _table;
	private final RouteGroup _parent;
	private final String _prefix;
	private final List<RouteCondition> _conditions;

	/**
	 * The group's trailing slash policy (its own or its parent's), null for the router's
	 */
	private final TrailingSlash _trailingSlash;

	/**
	 * The group's behaviors (its own or its parents'), one of each type
	 */
	private final List<RouteBehavior> _behaviors;
	private final List<RouteFilter> _filters = new ArrayList<>();

	RouteGroup( final ERXRouter router, final Router<ERXRouter.Mapped>.Table table, final RouteGroup parent, final String prefix, final List<RouteOption> options ) {
		options.forEach( option -> Objects.requireNonNull( option, "A route option is null (a static field read before it was set?)" ) );
		_router = router;
		_table = table;
		_parent = parent;
		_prefix = prefix;
		_conditions = options.stream().filter( RouteCondition.class::isInstance ).map( RouteCondition.class::cast ).toList();
		_trailingSlash = options.stream().filter( TrailingSlash.class::isInstance ).map( TrailingSlash.class::cast ).findFirst().orElse( null );
		_behaviors = options.stream().filter( RouteBehavior.class::isInstance ).map( RouteBehavior.class::cast ).toList();
	}

	/**
	 * Names the group, so a plugin can map routes into it from its own table ({@link #join(String)})
	 */
	public RouteGroup named( final String name ) {
		_router.name( name, this );
		return this;
	}

	/**
	 * @return The named group, for mapping routes into from this group's table: they share its prefix, conditions,
	 *         trailing slash policy and filters, while staying this table's routes, so the overrides between tables keep
	 *         working. A plugin maps into the application's groups this way. Only the named group's prefix and options
	 *         apply, not this group's.
	 */
	public RouteGroup join( final String name ) {
		return joined( _router.namedGroup( name ) );
	}

	/**
	 * Joins the named group as {@link #join(String)} does, once it's named: a plugin starts before the application
	 * declares its groups, so the body maps its routes in the group when the application names it (or now, if it has).
	 * A group never named fails the application's startup (see {@link ERXRouter#checkJoins()}).
	 */
	public void join( final String name, final Consumer<RouteGroup> body ) {
		_router.whenNamed( name, named -> body.accept( joined( named ) ) );
	}

	private RouteGroup joined( final RouteGroup named ) {
		final List<RouteOption> options = new ArrayList<>( named._conditions );

		if( named._trailingSlash != null ) {
			options.add( named._trailingSlash );
		}

		options.addAll( named._behaviors );

		// The named group is the parent, so its filters (and its parents') wrap the routes mapped here
		return new RouteGroup( _router, _table, named, named._prefix, options );
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
	 *
	 * @return The route, for links, forms and redirects to it
	 */
	public PlainRoute map( final String pattern, final RouteHandler handler, final RouteOption... options ) {

		for( final RouteOption option : options ) {
			if( option instanceof RouteBehavior behavior && !behavior.appliesToPlainRoutes() ) {
				throw new IllegalArgumentException( "The route %s is a plain route, and %s applies to typed routes only".formatted( fullPattern( pattern ), behavior ) );
			}
		}

		final List<RouteOption> allOptions = allOptions( options );
		final Host host = (Host)allOptions.stream().filter( Host.class::isInstance ).findFirst().orElse( null );
		final PlainRoute route = new PlainRoute( PathPattern.parse( fullPattern( pattern ) ), host, _router.converters() );
		_router.map( _table, fullPattern( pattern ), new ERXRouter.Mapped( handler, this, route, null, allOptions.contains( CrossSite.ALLOWED ) ), allOptions );
		return route;
	}

	/**
	 * Maps a route to a page
	 */
	public PlainRoute map( final String pattern, final Class<? extends WOComponent> pageClass, final RouteOption... options ) {
		return map( pattern, invocation -> WOApplication.application().pageWithName( pageClass.getName(), invocation.context() ), options );
	}

	/**
	 * @return A typed route mapped in this group, invoked by its parameter record's own {@link Routable#invoke(RouteInvocation)}
	 */
	public <P extends Record & Routable> Route<P> route( final String pattern, final Class<P> parametersClass, final RouteOption... options ) {
		return route( pattern, parametersClass, ( parameters, invocation ) -> parameters.invoke( invocation ), options );
	}

	/**
	 * @return A typed route mapped in this group, invoked by the given action: for a parameter record that's only data,
	 *         or one of several routes taking the same parameters
	 */
	public <P extends Record> Route<P> route( final String pattern, final Class<P> parametersClass, final Route.Action<P> action, final RouteOption... options ) {
		final List<RouteOption> allOptions = allOptions( options );
		final Route<P> route = new Route<>( fullPattern( pattern ), allOptions, parametersClass, action, _router.converters() );
		_router.map( _table, fullPattern( pattern ), new ERXRouter.Mapped( route::handle, this, route, parametersClass, allOptions.contains( CrossSite.ALLOWED ) ), allOptions );
		return route;
	}

	/**
	 * @return A nested group, for mapping its routes and declaring its routes afterwards
	 */
	public RouteGroup group( final String prefix, final RouteOption... options ) {
		return group( prefix, group -> {}, options );
	}

	/**
	 * @return A nested group, after the body has mapped its routes
	 */
	public RouteGroup group( final String prefix, final Consumer<RouteGroup> body, final RouteOption... options ) {

		if( !prefix.isEmpty() && (!prefix.startsWith( "/" ) || prefix.endsWith( "/" )) ) {
			throw new IllegalArgumentException( "A group's prefix is empty (a group by its conditions alone), or starts with '/' and doesn't end with one: '%s'".formatted( prefix ) );
		}

		final RouteGroup group = new RouteGroup( _router, _table, this, _prefix + prefix, allOptions( options ) );
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
	 * @return The group's conditions (its parents' included) and the given options' conditions, and the given options'
	 *         trailing slash policy, or the group's if they don't name one
	 */
	List<RouteOption> allOptions( final RouteOption... options ) {
		final List<RouteOption> all = new ArrayList<>( _conditions );
		boolean ownPolicy = false;

		for( final RouteOption option : options ) {
			Objects.requireNonNull( option, "A route option is null (a static field read before it was set?)" );
			all.add( option );
			ownPolicy |= option instanceof TrailingSlash;
		}

		if( !ownPolicy && _trailingSlash != null ) {
			all.add( _trailingSlash );
		}

		// The group's behaviors reach its routes, unless a route sets its own of the type (a plain route ignores those that
		// don't apply to it)
		for( final RouteBehavior behavior : _behaviors ) {
			if( all.stream().noneMatch( option -> type( option ) == type( behavior ) ) ) {
				all.add( behavior );
			}
		}

		return all;
	}

	/**
	 * @return The option's type: an enum constant's enum (a constant with a body is a subclass of it)
	 */
	private static Class<?> type( final RouteOption option ) {
		return option instanceof Enum<?> constant ? constant.getDeclaringClass() : option.getClass();
	}

	/**
	 * @return The handler wrapped in the filters of this group and its parents, outermost first
	 */
	RouteHandler wrapped( final RouteHandler handler ) {
		RouteHandler wrapped = handler;

		for( RouteGroup group = this; group != null; group = group._parent ) {
			for( int i = group._filters.size() - 1; i >= 0; i-- ) {
				final RouteFilter filter = group._filters.get( i );
				final RouteHandler next = wrapped;
				wrapped = invocation -> filter.filter( invocation, next );
			}
		}

		return wrapped;
	}
}
