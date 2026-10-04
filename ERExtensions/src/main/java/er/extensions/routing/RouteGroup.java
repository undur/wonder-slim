package er.extensions.routing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.webobjects.appserver.WOComponent;

import er.routing.conversion.Converters;
import er.routing.matching.PathPattern;
import er.routing.matching.Router;
import er.routing.options.CrossOrigin;
import er.routing.options.CrossSite;
import er.routing.options.Fields;
import er.routing.options.Host;
import er.routing.options.RouteBehavior;
import er.routing.options.RouteCondition;
import er.routing.options.RouteOption;
import er.routing.options.TrailingSlash;


/**
 * Routes sharing a path prefix, conditions and wrapping. A table's routes are its
 * root group ({@link ERXRouter#table(String)}).
 *
 * <pre>
 * routes.map( "/search/{area}", Routes.search, SearchPage.class );
 * routes.map( "/books/{book}", Routes.book, BookPage.class );
 * routes.map( "/hooks/github", hooks::github, Method.POST );
 * routes.group( "/manage", manage -&gt; {
 *     manage.wrap( requireLogin );
 *     manage.map( "/users", Routes.users, UsersPage.class );
 * }, Host.of( "admin.example.com" ) );
 * </pre>
 *
 * A route a link goes to is a constant ({@link Route#of(Class)}, {@link Route#plain()}), which the group gives its
 * pattern. One nothing links to can be mapped without one.
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

	/**
	 * A parameter of the group's ({@link #parameter(String, Class)})
	 */
	private record GroupParameter( String name, Class<?> type ) {}

	/**
	 * The group's own parameters (its parents' are theirs)
	 */
	private final List<GroupParameter> _parameters = new ArrayList<>();

	/**
	 * The first route mapped in the group or a group nested in it, null while there's none
	 */
	private volatile String _firstRoute;

	RouteGroup( final ERXRouter router, final Router<ERXRouter.Mapped>.Table table, final RouteGroup parent, final String prefix, final List<RouteOption> options ) {
		options.forEach( option -> Objects.requireNonNull( option, "A route option is null (a static field read before it was set?)" ) );
		_router = router;
		_table = table;
		_parent = parent;
		_prefix = prefix;
		_conditions = options.stream().filter( RouteCondition.class::isInstance ).map( option -> (RouteCondition)resolved( option ) ).toList();
		_trailingSlash = options.stream().filter( TrailingSlash.class::isInstance ).map( TrailingSlash.class::cast ).findFirst().orElse( null );
		_behaviors = options.stream().filter( RouteBehavior.class::isInstance ).map( RouteBehavior.class::cast ).toList();
	}

	/**
	 * Names the group, so a plugin can map routes into it from its own table ({@link #join(String)})
	 */
	public RouteGroup named( final String name ) {
		_router.undeclared( "the group " + name + ", named" );
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
		_router.undeclared( "a join to the group " + name + ", made" );
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
	 * Makes one of the group's parameters (its prefix's, {@code /orgs/{org}}, or its host's, {@code {club}.@}) the
	 * group's: converted to the type once, before the group's filters run, so one that isn't of the type or names nothing
	 * declines every route in the group (an unknown club's host answers nothing). Its routes' records have it as a
	 * component, and a link gives it, as it gives the path's other parameters; a page may leave it out. A host's
	 * parameter a link leaves out is the request's host.
	 *
	 * <pre>
	 * final RouteGroup club = routes.group( "", Host.of( "{club}.@" ) ).parameter( "club", Club.class );
	 * </pre>
	 *
	 * @return The group
	 */
	public RouteGroup parameter( final String name, final Class<?> type ) {
		_router.undeclared( "the group parameter " + name + ", declared" );
		Objects.requireNonNull( type );

		// Routes mapped before it were given their parameters without it: half applied, so refused
		if( _firstRoute != null ) {
			throw new IllegalStateException( "The group parameter {%s} is declared after the group's route %s: a group's parameters are declared before its routes are mapped".formatted( name, _firstRoute ) );
		}

		final List<String> available = new ArrayList<>( PathPattern.parse( _prefix.isEmpty() ? "/" : _prefix ).parameterNames() );
		_conditions.stream().filter( Host.class::isInstance ).forEach( host -> available.addAll( ((Host)host).parameterNames() ) );

		if( !available.contains( name ) ) {
			throw new IllegalArgumentException( "The group %s has no parameter {%s}: its prefix's and its host's are %s".formatted( _prefix.isEmpty() ? "/" : _prefix, name, available ) );
		}

		if( !_router.converters().converts( type ) ) {
			throw new IllegalArgumentException( "The group parameter {%s} is a %s, which has no converter. Register one in the router's converters before declaring the group".formatted( name, type.getSimpleName() ) );
		}

		_parameters.add( new GroupParameter( name, type ) );
		return this;
	}

	/**
	 * @return The parameters of this group and its parents, outermost first
	 */
	private List<GroupParameter> groupParameters() {
		final List<GroupParameter> all = new ArrayList<>();

		for( RouteGroup group = this; group != null; group = group._parent ) {
			all.addAll( 0, group._parameters );
		}

		return all;
	}

	/**
	 * @return The names of this group's and its parents' parameters
	 */
	List<String> groupParameterNames() {
		return groupParameters().stream().map( GroupParameter::name ).toList();
	}

	/**
	 * Notes a route mapped in this group and those it's nested in, which then take no more parameters
	 */
	private void mapped( final String pattern ) {
		_router.declaredFrom( RouteIdentity.mapper() );

		for( RouteGroup group = this; group != null; group = group._parent ) {
			if( group._firstRoute == null ) {
				group._firstRoute = pattern;
			}
		}
	}

	/**
	 * @return The converters for route parameters, the router's: register an application's own types here, before
	 *         mapping routes taking them
	 */
	public Converters converters() {
		return _router.converters();
	}

	/**
	 * Sets what answers a request no route answered, before the not found handler: the application's {@code public}
	 * folder ({@code new ERXPublicResources()}), say. It may decline, passing the request on to the not found handler.
	 * Set on the application's routes only.
	 *
	 * @return This group
	 */
	public RouteGroup fallback( final RouteHandler fallback ) {
		applicationOnly( "fallback" );
		_router.fallback( fallback );
		return this;
	}

	/**
	 * Sets what answers a request nothing else answered. Without one, the development pages answer in development (a
	 * welcome page at {@code /}, otherwise a 404 saying why the routes that matched passed the URL on), and a plain 404
	 * deployed. One that declines passes the request on (to the next handler in the server, with wo-adaptor-jetty). Set on
	 * the application's routes only.
	 *
	 * @return This group
	 */
	public RouteGroup notFound( final RouteHandler notFound ) {
		applicationOnly( "not found handler" );
		_router.notFound( notFound );
		return this;
	}

	private void applicationOnly( final String what ) {
		_router.undeclared( "the " + what + ", set" );

		if( !_router.isApplication( this ) ) {
			throw new IllegalStateException( "The %s is the application's: set it on the routes ERXRouter.declare( routes -> … ) gives the application, not a group's or a plugin's".formatted( what ) );
		}
	}

	/**
	 * Wraps every route of the group, including those mapped before this call and those of its nested groups. A nested
	 * group's filters run inside its parent's.
	 */
	public RouteGroup wrap( final RouteFilter filter ) {
		_router.undeclared( "a filter on " + (_prefix.isEmpty() ? "/" : _prefix) + ", added" );
		_filters.add( Objects.requireNonNull( filter ) );
		return this;
	}

	/**
	 * Maps a route nothing links to (an API's, a webhook)
	 *
	 * @return The route
	 */
	public PlainRoute map( final String pattern, final RouteHandler handler, final RouteOption... options ) {
		return map( pattern, new PlainRoute( null ), handler, options );
	}

	/**
	 * Maps a page nothing links to
	 */
	public PlainRoute map( final String pattern, final Class<? extends WOComponent> pageClass, final RouteOption... options ) {
		return map( pattern, new PlainRoute( null ), pageClass, options );
	}

	/**
	 * Gives the route its pattern in this group, and what it does
	 *
	 * @return The route
	 */
	public PlainRoute map( final String pattern, final PlainRoute route, final RouteHandler handler, final RouteOption... options ) {
		Objects.requireNonNull( route, "A route constant is null: one declared after the constants it's used with (a static field read before it was set?)" );
		Objects.requireNonNull( handler );

		for( final RouteOption option : options ) {
			if( option instanceof RouteBehavior behavior && !behavior.appliesToPlainRoutes() ) {
				throw new IllegalArgumentException( "The route %s is a plain route, and %s applies to typed routes only".formatted( fullPattern( pattern ), behavior ) );
			}
		}

		final List<RouteOption> allOptions = allOptions( options );
		final Host host = (Host)allOptions.stream().filter( Host.class::isInstance ).findFirst().orElse( null );
		final PlainBinding binding = new PlainBinding( PathPattern.parse( fullPattern( pattern ) ), host, _router.converters() );
		_router.bind( route, binding );
		mapped( fullPattern( pattern ) );
		_router.map( _table, fullPattern( pattern ), new ERXRouter.Mapped( handler, this, route, null, crossSite( allOptions ), crossOrigin( allOptions ), ERXRouter.answerOf( handler ) ), allOptions );
		return route;
	}

	/**
	 * Gives the route its pattern in this group, answered with a new instance of the page, its route parameters (the
	 * path's and the host's) set on it by name: {@code map( "/books/{book}", Routes.book, BookPage.class )} sets
	 * {@code book}, converted to the type of the page's field or setter of that name. A page lacking one is refused here.
	 * Query values aren't set: a page reads those itself, so a URL can't write its fields.
	 *
	 * @return The route
	 */
	public PlainRoute map( final String pattern, final PlainRoute route, final Class<? extends WOComponent> pageClass, final RouteOption... options ) {
		Objects.requireNonNull( pageClass );
		final PathPattern path = PathPattern.parse( fullPattern( pattern ) );
		final Host host = (Host)allOptions( options ).stream().filter( Host.class::isInstance ).findFirst().orElse( null );
		final List<String> names = new ArrayList<>();

		// The host's first, then the path's: a path parameter's converter may look in the host's (a club's book)
		if( host != null ) {
			names.addAll( host.parameterNames() );
		}

		names.addAll( path.parameterNames() );
		return map( pattern, route, new PageSetters( pageClass, names, _router.converters(), fullPattern( pattern ), groupParameterNames() ), options );
	}

	/**
	 * Gives the route its pattern in this group, answered by a new instance of the page, the record's components set on
	 * it by name (as a page route's parameters are). A component that's null (a query parameter absent) isn't set, so the
	 * page keeps its own value. A page lacking a member for a component is refused, except for a group's parameter.
	 *
	 * @return The route
	 */
	public <P extends Record> Route<P> map( final String pattern, final Route<P> route, final Class<? extends WOComponent> pageClass, final RouteOption... options ) {
		Objects.requireNonNull( route, "A route constant is null: one declared after the constants it's used with (a static field read before it was set?)" );

		// A page can't see the fields that didn't convert
		if( allOptions( options ).contains( Fields.REPORTED ) ) {
			throw new IllegalArgumentException( "The route %s reports its fields' errors (Fields.REPORTED), which a page doesn't see: answer it with an action, map( pattern, route, ( %s, invocation ) -> … )".formatted( fullPattern( pattern ), route.parametersClass().getSimpleName().toLowerCase() ) );
		}

		return map( pattern, route, PageSetters.forRecord( pageClass, route.parametersClass(), fullPattern( pattern ), groupParameterNames() ), options );
	}

	/**
	 * Gives the route its pattern in this group, answered by the given action
	 *
	 * @return The route
	 */
	public <P extends Record> Route<P> map( final String pattern, final Route<P> route, final Route.Action<P> action, final RouteOption... options ) {
		Objects.requireNonNull( route, "A route constant is null: one declared after the constants it's used with (a static field read before it was set?)" );
		final List<RouteOption> allOptions = allOptions( options );
		final RouteBinding<P> binding = new RouteBinding<>( _router, fullPattern( pattern ), allOptions, route.parametersClass(), action );
		_router.bind( route, binding );
		mapped( fullPattern( pattern ) );
		_router.map( _table, fullPattern( pattern ), new ERXRouter.Mapped( binding::handle, this, route, route.parametersClass(), crossSite( allOptions ), crossOrigin( allOptions ), ERXRouter.answerOf( action ) ), allOptions );
		return route;
	}

	/**
	 * @return A route nothing links to by a constant, answered by the page, the record's components set on it
	 */
	public <P extends Record> Route<P> map( final String pattern, final Class<P> parametersClass, final Class<? extends WOComponent> pageClass, final RouteOption... options ) {
		return map( pattern, Route.unnamed( parametersClass ), pageClass, options );
	}

	/**
	 * @return A route nothing links to by a constant, invoked by the given action
	 */
	public <P extends Record> Route<P> map( final String pattern, final Class<P> parametersClass, final Route.Action<P> action, final RouteOption... options ) {
		return map( pattern, Route.unnamed( parametersClass ), action, options );
	}

	/**
	 * Redirects an old URL to a route for good ({@code 308}, which a browser and a search engine remember, and which keeps
	 * the method): {@code redirect( "/book/{book}", Routes.book )} answers {@code /book/7} with the route's URL for
	 * {@code book} 7. The old pattern's parameters (and the host's) are the route's by name, and the query string is kept.
	 *
	 * @return The old URL's route
	 */
	public PlainRoute redirect( final String pattern, final Linkable to, final RouteOption... options ) {
		Objects.requireNonNull( to, "A route constant is null: one declared after the constants it's used with (a static field read before it was set?)" );

		// The old URL's parameters are the route's by name: checked once every route has its pattern
		final String fullPattern = fullPattern( pattern );
		final Host host = (Host)allOptions( options ).stream().filter( Host.class::isInstance ).findFirst().orElse( null );
		final List<String> names = new ArrayList<>( PathPattern.parse( fullPattern ).parameterNames() );

		if( host != null ) {
			names.addAll( host.parameterNames() );
		}

		if( to instanceof RouteIdentity<?> identity ) {
			_router.checkOnceDeclared( () -> {
				final List<String> unknown = names.stream().filter( name -> !identity.acceptedNames().contains( name ) ).toList();
				final List<String> missing = identity.requiredNames().stream().filter( name -> !names.contains( name ) ).toList();

				if( !unknown.isEmpty() || !missing.isEmpty() ) {
					throw new IllegalArgumentException( "The redirect from %s to %s doesn't fit it: the route has no parameter %s, and needs %s, which the old URL doesn't have. The old URL's parameters are the route's by name".formatted( fullPattern, identity.name(), unknown, missing ) );
				}
			} );
		}

		return map( pattern, invocation -> {
			final java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
			invocation.parameters().forEach( ( name, value ) -> {
				if( !name.equals( PathPattern.WILDCARD_PARAMETER ) ) {
					values.put( name, value );
				}
			} );

			final String url;

			// A value the route doesn't take (text that isn't one of its type, an object that doesn't exist) means the old
			// URL names nothing: declined, as the route would decline it
			try {
				url = to.url( values, invocation.context() );
			}
			catch( IllegalArgumentException e ) {
				throw new Declined( "the redirect to %s doesn't take its values: %s".formatted( to, e.getMessage() ) );
			}

			final String uri = invocation.request().uri();
			final int query = uri.indexOf( '?' );
			final String location = url + (query == -1 ? "" : (url.contains( "?" ) ? "&" : "?") + uri.substring( query + 1 ));

			final com.webobjects.appserver.WOResponse response = new com.webobjects.appserver.WOResponse();
			response.setStatus( 308 );
			response.setHeader( location, "location" );
			return response;
		}, options );
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
			all.add( resolved( option ) );
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
	 * @return The sites the options take requests that change things from: their level, the same origin by default
	 */
	private static CrossSite crossSite( final List<RouteOption> options ) {
		return options.stream().filter( CrossSite.class::isInstance ).map( CrossSite.class::cast ).findFirst().orElse( CrossSite.SAME_ORIGIN );
	}

	/**
	 * @return The other sites whose scripts may call the route, null for none
	 */
	private static CrossOrigin crossOrigin( final List<RouteOption> options ) {
		return options.stream().filter( CrossOrigin.class::isInstance ).map( CrossOrigin.class::cast ).findFirst().orElse( null );
	}

	/**
	 * @return The option, a host relative to the application's domain ({@code {club}.@}) resolved to the public
	 *         address's host, or {@code localhost} without one
	 */
	private static RouteOption resolved( final RouteOption option ) {
		return option instanceof Host host && host.isRelative() ? host.withDomain( PublicAddress.domain() ) : option;
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

		// The groups' parameters, converted before any filter runs: one that names nothing declines every route in them
		final List<GroupParameter> parameters = groupParameters();

		if( !parameters.isEmpty() ) {
			final RouteHandler filtered = wrapped;
			wrapped = invocation -> {
				parameters.forEach( parameter -> invocation.parameter( parameter.name(), parameter.type() ) );
				return filtered.handle( invocation );
			};
		}

		return wrapped;
	}
}
