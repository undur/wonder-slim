package er.extensions.admin;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXApplication;
import er.extensions.components.ERXComponent;
import er.extensions.foundation.ERXProperties;
import er.extensions.routing.ERXRouter;
import er.extensions.routing.RouteDescription;
import er.routing.matching.PathPattern;
import er.routing.options.CrossSite;
import er.routing.options.Host;
import er.routing.options.Method;
import er.routing.options.RouteCondition;

/**
 * The application's routes: how the application routes as a whole, then every route by table, in the order they're
 * tried. A route with parameters or options of its own opens to them.
 */
public class ERXAdminRoutesPage extends ERXComponent {

	/**
	 * Where the guide's sections are
	 */
	private static final String GUIDE = "https://github.com/undur/wonder-slim/blob/master/docs/ROUTING.md#";

	/**
	 * A setting of the application's routing, or a fact about one route
	 *
	 * @param label What it's about
	 * @param text What it means, in words
	 * @param code Code showing it, null for none
	 * @param guide The guide's section, null for none
	 */
	public record Detail( String label, String text, String code, String guide ) {}

	/**
	 * A route, as the page shows it
	 */
	public record Row( RouteDescription route, String kind, List<String> conditions, List<Detail> details ) {

		public String pattern() {
			return declaredPattern( route );
		}

		public boolean hasDetails() {
			return !details.isEmpty();
		}

		/**
		 * @return The row's class: one with details opens to them
		 */
		public String rowClass() {
			return hasDetails() ? "route-expandable" : null;
		}
	}

	/**
	 * A table's routes
	 */
	public record Table( String name, String about, List<Row> rows ) {}

	public Detail currentDetail;
	public Table currentTable;
	public Row currentRow;
	public String currentCondition;
	public int currentIndex;

	private List<Table> _tables;

	public ERXAdminRoutesPage( final WOContext context ) {
		super( context );
	}

	/**
	 * @return The current route's place in its table, from 1: the order routes are tried in
	 */
	public int currentPosition() {
		return currentIndex + 1;
	}

	private static ERXRouter router() {
		return ERXRouter.defaultRouter();
	}

	public int routeCount() {
		return router().routes().size();
	}

	public int tableCount() {
		return tables().size();
	}

	/**
	 * @return How the application routes as a whole
	 */
	public List<Detail> settings() {
		final List<Detail> settings = new ArrayList<>();
		final ERXRouter router = router();
		final String fallback = router.fallbackAnswer();
		final String notFound = router.notFoundAnswer();
		final String publicAddress = ERXProperties.stringForKey( "er.routing.publicAddress" );
		final boolean reload = ERXProperties.booleanForKeyWithDefault( ERXRouter.RELOAD_PROPERTY, ERXApplication.isDevelopmentModeSafe() );
		final boolean strict = ERXProperties.booleanForKeyWithDefault( "er.routing.strictPathValues", false );

		settings.add( new Detail( "Fallback", fallback != null ? fallback : "None", null, GUIDE + "fallback-and-not-found" ) );
		settings.add( new Detail( "Not found", notFound != null ? notFound : ERXApplication.isDevelopmentModeSafe() ? "Development pages" : "Plain 404", null, GUIDE + "fallback-and-not-found" ) );
		settings.add( new Detail( "Declared again on change", reload ? "On" : "Off", null, GUIDE + "changing-routes-while-the-application-runs" ) );
		settings.add( new Detail( "Public address", publicAddress != null ? publicAddress : "Not set", null, GUIDE + "the-public-address" ) );
		settings.add( new Detail( "Path values", strict ? "Strict" : "Encoded", null, GUIDE + "patterns" ) );
		return settings;
	}

	/**
	 * @return The routes by table, the application's first, each table's in the order they're tried
	 */
	public List<Table> tables() {
		if( _tables == null ) {
			final Map<String, List<Row>> byTable = new LinkedHashMap<>();
			final java.util.Set<Object> shown = java.util.Collections.newSetFromMap( new java.util.IdentityHashMap<>() );

			// A route with an optional parameter is mapped as its two forms: shown once, where its first form is tried
			for( final RouteDescription route : router().routes() ) {
				if( shown.add( route.route() ) ) {
					byTable.computeIfAbsent( route.table(), table -> new ArrayList<>() ).add( row( route ) );
				}
			}

			final List<Table> tables = new ArrayList<>();
			final List<Row> application = byTable.remove( "application" );

			if( application != null ) {
				tables.add( new Table( "application", null, application ) );
			}

			byTable.forEach( ( name, rows ) -> tables.add( new Table( name, "plugin", rows ) ) );
			_tables = tables;
		}

		return _tables;
	}

	/**
	 * @return The pattern as declared ({@code /search/{text?}}), not one of the forms it's mapped as
	 */
	public static String declaredPattern( final RouteDescription route ) {
		return route.route() != null ? route.route().pattern() : route.pattern();
	}

	private static Row row( final RouteDescription route ) {
		final PathPattern path = PathPattern.parse( declaredPattern( route ) );
		final List<String> conditions = route.conditions().stream().map( ERXAdminRoutesPage::conditionLabel ).toList();
		final String kind = (route.parametersClass() != null ? "typed" : "plain") + " · " + (route.page() ? "page" : "handler");
		return new Row( route, kind, conditions, details( route, path ) );
	}

	private static String conditionLabel( final RouteCondition condition ) {
		return switch( condition ) {
			case Host host -> "host " + host.pattern();
			case Method method -> method.toString().replace( "Method ", "" );
			default -> condition.toString();
		};
	}

	/**
	 * @return What a route declares beyond its row: its parameters, and the options it sets
	 */
	private static List<Detail> details( final RouteDescription route, final PathPattern path ) {
		final List<Detail> details = new ArrayList<>();
		final String parameters = parameters( route, path );

		if( parameters != null ) {
			details.add( new Detail( "Parameters", parameters, null, null ) );
		}

		if( route.trailingSlash() != null ) {
			details.add( new Detail( "Trailing slash", route.trailingSlash().toString().toLowerCase(), null, GUIDE + "trailing-slashes" ) );
		}

		if( route.crossSite() != CrossSite.SAME_ORIGIN ) {
			details.add( new Detail( "Posts from other sites", route.crossSite() == CrossSite.ALLOWED ? "Allowed" : "From the application's other hosts", null, GUIDE + "posts-from-other-sites" ) );
		}

		if( route.parametersClass() != null && route.fieldsReported() ) {
			details.add( new Detail( "Bad input", "Reported to the route", null, GUIDE + "bad-input" ) );
		}

		return details;
	}

	/**
	 * @return The route's parameters, each with where it comes from, null for none
	 */
	private static String parameters( final RouteDescription route, final PathPattern path ) {
		final List<String> hostParameters = route.conditions().stream().filter( Host.class::isInstance ).flatMap( condition -> ((Host)condition).parameterNames().stream() ).toList();
		final List<String> parameters = new ArrayList<>();

		if( route.parametersClass() != null ) {
			for( final RecordComponent component : route.parametersClass().getRecordComponents() ) {
				parameters.add( "%s %s (%s)".formatted( component.getType().getSimpleName(), component.getName(), source( component.getName(), path, hostParameters ) ) );
			}

			return route.parametersClass().getSimpleName() + ": " + String.join( ", ", parameters );
		}

		for( final String name : path.parameterNames() ) {
			parameters.add( "%s (%s)".formatted( name, source( name, path, hostParameters ) ) );
		}

		hostParameters.forEach( name -> parameters.add( name + " (host)" ) );

		if( path.isWildcard() && PathPattern.WILDCARD_PARAMETER.equals( path.wildcardName() ) ) {
			parameters.add( "* (path, everything beneath)" );
		}

		return parameters.isEmpty() ? null : String.join( ", ", parameters );
	}

	private static String source( final String name, final PathPattern path, final List<String> hostParameters ) {
		if( name.equals( path.optionalParameter() ) ) {
			return "path, optional";
		}

		if( name.equals( path.wildcardName() ) ) {
			return "path, everything beneath";
		}

		if( path.parameterNames().contains( name ) ) {
			return "path";
		}

		return hostParameters.contains( name ) ? "host" : "query or form";
	}
}
