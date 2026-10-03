package er.routing.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Matches requests to routes. Plain Java, so it can serve more than one framework: requests are strings
 * ({@link RouteRequest}), and a route's handler is whatever the framework using it routes to ({@code H}).
 *
 * <h3>Tables</h3>
 *
 * Routes are mapped in tables, ranked in the order they're created: the application's first, then each plugin's, in
 * dependency order. Within a table, two routes with the same path shape whose conditions overlap are a conflict,
 * refused when mapped. Between tables, the same route is an override: the higher ranked table's route comes first,
 * and the override is recorded ({@link #overrides()}).
 *
 * <h3>Precedence</h3>
 *
 * Specificity first, across all tables: a literal segment before a parameter, a parameter before a wildcard. For the
 * same path shape, a route with more specific conditions before one with less. Then the table's rank, then the order
 * routes were mapped in.
 *
 * <h3>Outcomes</h3>
 *
 * See {@link Result}: the matching routes in precedence order, or {@code 405}, or a redirect to the declared trailing
 * slash form, or no match.
 */

public final class Router<H> {

	private final TrailingSlash _trailingSlash;
	private final List<Table> _tables = new ArrayList<>();
	private final List<RouteOverride<H>> _overrides = new ArrayList<>();
	private List<Entry<H>> _sorted = List.of();
	private int _mappedCount;

	/**
	 * @param trailingSlash The trailing slash policy for routes that don't set their own
	 */
	public Router( final TrailingSlash trailingSlash ) {
		_trailingSlash = Objects.requireNonNull( trailingSlash );
	}

	/**
	 * A router whose routes ignore the trailing slash unless they set their own policy
	 */
	public Router() {
		this( TrailingSlash.IGNORE );
	}

	/**
	 * @return A new table, ranked below the tables created before it
	 */
	public Table table( final String name ) {
		return table( name, _tables.size() );
	}

	/**
	 * @return A new table of the given rank: a lower rank comes first. The application's table ranks first, whenever
	 *         it's created ({@link Integer#MIN_VALUE}).
	 */
	public Table table( final String name, final int rank ) {
		final Table table = new Table( name, rank );
		_tables.add( table );
		return table;
	}

	/**
	 * A mapped route
	 *
	 * @param trailingSlash The route's own trailing slash policy, null for the router's
	 */
	public record Entry<H>( PathPattern path, List<RouteCondition> conditions, TrailingSlash trailingSlash, H handler, String table, int rank, int order ) {

		@Override
		public String toString() {
			return path + (conditions.isEmpty() ? "" : " " + conditions) + " (" + table + ")";
		}
	}

	/**
	 * A route of a higher ranked table overriding one of a lower ranked table
	 */
	public record RouteOverride<H>( Entry<H> route, Entry<H> overridden ) {}

	/**
	 * @return The overrides between tables, for logging them
	 */
	public List<RouteOverride<H>> overrides() {
		return Collections.unmodifiableList( _overrides );
	}

	/**
	 * @return Every route, in precedence order
	 */
	public List<Entry<H>> routes() {
		return _sorted;
	}

	/**
	 * A table of routes
	 */
	public final class Table {

		private final String _name;
		private final int _rank;
		private final List<Entry<H>> _routes = new ArrayList<>();

		private Table( final String name, final int rank ) {
			_name = Objects.requireNonNull( name );
			_rank = rank;
		}

		public String name() {
			return _name;
		}

		/**
		 * Maps a route
		 *
		 * @param options The route's conditions, and its own trailing slash policy if it has one (the router's otherwise)
		 * @throws IllegalArgumentException if the route conflicts with one of this table's, a host parameter has the
		 *         name of a path parameter, or the options name two trailing slash policies
		 */
		public Entry<H> map( final String pattern, final H handler, final RouteOption... options ) {
			TrailingSlash trailingSlash = null;
			final List<RouteCondition> conditionList = new ArrayList<>();

			for( final RouteOption option : options ) {
				switch( Objects.requireNonNull( option, "A route option is null" ) ) {
					case RouteCondition condition -> conditionList.add( condition );
					case TrailingSlash policy -> {
						if( trailingSlash != null ) {
							throw new IllegalArgumentException( "The route %s has two trailing slash policies, %s and %s".formatted( pattern, trailingSlash, policy ) );
						}

						trailingSlash = policy;
					}
					default -> throw new IllegalArgumentException( "Unknown route option " + option );
				}
			}

			final PathPattern path = PathPattern.parse( pattern );

			for( final RouteCondition condition : conditionList ) {
				if( condition instanceof Host host ) {
					for( final String name : host.parameterNames() ) {
						if( path.parameterNames().contains( name ) ) {
							throw new IllegalArgumentException( "The route %s has a host parameter and a path parameter named '%s'".formatted( pattern, name ) );
						}
					}
				}
			}

			final Entry<H> route = new Entry<>( path, List.copyOf( conditionList ), trailingSlash, Objects.requireNonNull( handler ), _name, _rank, _mappedCount++ );

			for( final Entry<H> existing : _routes ) {
				if( sameRoute( route, existing ) ) {
					throw new IllegalArgumentException( "The route %s conflicts with %s: they match the same requests".formatted( route, existing ) );
				}
			}

			for( final Table table : _tables ) {
				if( table != this ) {
					for( final Entry<H> existing : table._routes ) {
						if( sameRoute( route, existing ) ) {
							_overrides.add( _rank < table._rank ? new RouteOverride<>( route, existing ) : new RouteOverride<>( existing, route ) );
						}
					}
				}
			}

			_routes.add( route );
			sort();
			return route;
		}
	}

	/**
	 * @return true if two routes match the same requests: the same path shape, the same types of conditions, and each
	 *         condition overlapping its counterpart
	 */
	private static boolean sameRoute( final Entry<?> a, final Entry<?> b ) {

		if( !a.path().shape().equals( b.path().shape() ) ) {
			return false;
		}

		final Map<Class<?>, RouteCondition> aConditions = byType( a.conditions() );
		final Map<Class<?>, RouteCondition> bConditions = byType( b.conditions() );

		if( !aConditions.keySet().equals( bConditions.keySet() ) ) {
			return false;
		}

		for( final Map.Entry<Class<?>, RouteCondition> entry : aConditions.entrySet() ) {
			if( !entry.getValue().overlaps( bConditions.get( entry.getKey() ) ) ) {
				return false;
			}
		}

		return true;
	}

	private static Map<Class<?>, RouteCondition> byType( final List<RouteCondition> conditions ) {
		final Map<Class<?>, RouteCondition> byType = new HashMap<>();

		for( final RouteCondition condition : conditions ) {
			if( byType.put( condition.getClass(), condition ) != null ) {
				throw new IllegalArgumentException( "A route has one condition of each type, and has two of %s".formatted( condition.getClass().getSimpleName() ) );
			}
		}

		return byType;
	}

	private void sort() {
		final List<Entry<H>> all = new ArrayList<>();
		_tables.forEach( table -> all.addAll( table._routes ) );
		all.sort( PRECEDENCE );
		_sorted = List.copyOf( all );
	}

	private static final Comparator<Entry<?>> PRECEDENCE = ( a, b ) -> {
		int difference = PathPattern.comparePrecedence( a.path(), b.path() );

		if( difference == 0 ) {
			difference = Integer.compare( specificity( b ), specificity( a ) );
		}

		if( difference == 0 ) {
			difference = Integer.compare( a.rank(), b.rank() );
		}

		return difference != 0 ? difference : Integer.compare( a.order(), b.order() );
	};

	private static int specificity( final Entry<?> route ) {
		return route.conditions().stream().mapToInt( RouteCondition::specificity ).sum();
	}

	/**
	 * The outcome of routing a request
	 */
	public sealed interface Result<H> {}

	/**
	 * Routes matching the request, in precedence order. The first that answers wins, and one that declines passes the
	 * request to the next.
	 */
	public record Matched<H>( List<Candidate<H>> candidates ) implements Result<H> {}

	/**
	 * A route matching a request, with its parameters: the path's and the conditions' (a host pattern's)
	 */
	public record Candidate<H>( Entry<H> entry, Map<String, String> parameters ) {

		public H handler() {
			return entry.handler();
		}
	}

	/**
	 * A path matched, but no route at it accepts the request's method: {@code 405}, with {@code Allow}
	 */
	public record MethodNotAllowed<H>( Set<String> allowedMethods ) implements Result<H> {}

	/**
	 * The path matched a route with the redirect policy, but only in the other trailing slash form: {@code 308} to the
	 * declared form
	 *
	 * @param path The path in the declared form, encoded, without a query string
	 */
	public record Redirect<H>( String path ) implements Result<H> {}

	/**
	 * Nothing matched
	 */
	public record NoMatch<H>() implements Result<H> {}

	/**
	 * @return The outcome of routing the request
	 */
	public Result<H> route( final RouteRequest request ) {
		final RequestPath path = RequestPath.parse( request.path() );

		if( path == null ) {
			return new NoMatch<>();
		}

		final List<Candidate<H>> candidates = new ArrayList<>();
		final Set<String> allowed = new TreeSet<>();

		// The shape of the most specific route that matched the path but not the method, while no route has matched yet
		String notAllowedShape = null;

		for( final Entry<H> route : _sorted ) {
			final PathPattern.Match match = route.path().match( path );

			if( match == null ) {
				continue;
			}

			// A more specific route is there, but not for this method: less specific routes (a catch-all) don't get the
			// request, so it's answered with 405. Routes of the same shape still do.
			if( notAllowedShape != null && candidates.isEmpty() && !notAllowedShape.equals( route.path().shape() ) ) {
				break;
			}

			final TrailingSlash policy = route.trailingSlash() != null ? route.trailingSlash() : _trailingSlash;

			// Under the strict policy, the other trailing slash form isn't the route's path at all
			if( !match.exactForm() && policy == TrailingSlash.STRICT ) {
				continue;
			}

			final Map<String, String> parameters = new LinkedHashMap<>( match.parameters() );
			final Set<String> routeAllows = new TreeSet<>();
			boolean here = true;
			boolean accepted = true;

			for( final RouteCondition condition : route.conditions() ) {
				switch( condition.test( request ) ) {
					case RouteCondition.Satisfied satisfied -> parameters.putAll( satisfied.parameters() );
					case RouteCondition.NotHere notHere -> here = false;
					case RouteCondition.NotAllowed notAllowed -> {
						accepted = false;
						routeAllows.addAll( notAllowed.allowedMethods() );
					}
				}
			}

			if( !here ) {
				continue;
			}

			// The route is there, but not for this method: it counts towards a 405
			if( !accepted ) {
				allowed.addAll( routeAllows );

				if( notAllowedShape == null && candidates.isEmpty() ) {
					notAllowedShape = route.path().shape();
				}

				continue;
			}

			if( !match.exactForm() ) {
				if( policy == TrailingSlash.REDIRECT ) {
					// A more specific route wants the request in its declared form, unless an earlier route took it as it is
					if( candidates.isEmpty() ) {
						return new Redirect<>( path.withTrailingSlash( route.path().hasTrailingSlash() ) );
					}

					continue;
				}
			}

			candidates.add( new Candidate<>( route, Collections.unmodifiableMap( parameters ) ) );
		}

		if( !candidates.isEmpty() ) {
			return new Matched<>( List.copyOf( candidates ) );
		}

		if( !allowed.isEmpty() ) {
			return new MethodNotAllowed<>( Collections.unmodifiableSet( allowed ) );
		}

		return new NoMatch<>();
	}
}
