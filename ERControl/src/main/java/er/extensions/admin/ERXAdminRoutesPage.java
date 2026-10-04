package er.extensions.admin;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXApplication;
import er.extensions.components.ERXComponent;
import er.extensions.foundation.ERXProperties;
import er.extensions.routing.ERXRouter;
import er.extensions.routing.RouteDescription;
import er.routing.matching.PathPattern;
import er.routing.options.CrossSite;
import er.routing.options.Header;
import er.routing.options.Host;
import er.routing.options.Method;
import er.routing.options.RouteCondition;
import er.routing.options.Scheme;
import er.routing.options.TrailingSlash;

/**
 * The application's routes: how the application routes as a whole, then every route by table, in the order they're
 * tried. Each route opens to what applies to it, in words, with the guide's section for each.
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
		settings.add( new Detail( "Fallback", fallback != null
				? "A request no route answers goes to %s first. It may decline (a URL that isn't one of its files, say), passing the request on to not found.".formatted( fallback )
				: "None: a request no route answers goes straight to not found. An application's public folder is served with routes.fallback( new ERXPublicResources() ).",
				fallback != null ? null : "routes.fallback( new ERXPublicResources() );", GUIDE + "fallback-and-not-found" ) );

		final String notFound = router.notFoundAnswer();
		settings.add( new Detail( "Not found", notFound != null
				? "A request nothing else answered goes to %s. Declining passes it on to the server's next handler, with wo-adaptor-jetty.".formatted( notFound )
				: ERXApplication.isDevelopmentModeSafe()
						? "The development pages: a welcome page at / while nothing is mapped there, and a 404 listing why the routes that matched passed the URL on. Deployed, a plain 404."
						: "A plain 404. In development, the development pages instead.",
				null, GUIDE + "fallback-and-not-found" ) );

		settings.add( new Detail( "Trailing slashes", "Ignored unless a route says otherwise: /books and /books/ both match a route for either. A declaration, a group or a route can redirect the other form instead, or refuse it.",
				"ERXRouter.declare( routes -> …, TrailingSlash.REDIRECT );", GUIDE + "trailing-slashes" ) );

		final boolean reload = ERXProperties.booleanForKeyWithDefault( ERXRouter.RELOAD_PROPERTY, ERXApplication.isDevelopmentModeSafe() );
		settings.add( new Detail( "Declared again on change", reload
				? "On: when a class declaring routes changes, the routes are declared again, without a restart."
				: "Off: the routes are declared once, at launch. It's on in development by default (%s).".formatted( ERXRouter.RELOAD_PROPERTY ),
				null, GUIDE + "changing-routes-while-the-application-runs" ) );

		final String publicAddress = ERXProperties.stringForKey( "er.routing.publicAddress" );
		settings.add( new Detail( "Public address", publicAddress != null
				? "%s: complete URLs (an email's links) and links to a route on another host are made with it.".formatted( publicAddress )
				: "Not set: complete URLs take the request's address, and routes on a host relative to the application's (admin.@) need one deployed.",
				publicAddress != null ? null : "er.routing.publicAddress=https://www.example.com", GUIDE + "the-public-address" ) );

		final boolean strict = ERXProperties.booleanForKeyWithDefault( "er.routing.strictPathValues", false );
		settings.add( new Detail( "Path values", strict
				? "Strict: generating a link with a %, \\ or control character in a path value is an error, for a server or adaptor that refuses them."
				: "Encoded: a %, \\ or control character in a path value is encoded (50% is 50%25). Some servers refuse those; er.routing.strictPathValues=true makes generating such a link an error instead.",
				null, GUIDE + "patterns" ) );

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
				tables.add( new Table( "application", "The application's own routes. They come before every plugin's, so one here overrides a plugin's route for the same requests.", application ) );
			}

			byTable.forEach( ( name, rows ) -> tables.add( new Table( name, "A plugin's routes, declared by the framework itself (ERXRouter.declare( \"%s\", … )). A route of the application's for the same requests overrides one here.".formatted( name ), rows ) ) );
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
	 * @return What applies to a route, in words
	 */
	private static List<Detail> details( final RouteDescription route, final PathPattern path ) {
		final List<Detail> details = new ArrayList<>();
		details.add( new Detail( "Matches", matches( path ), null, GUIDE + "patterns" ) );
		details.add( new Detail( "Which one answers", "Where two routes could match, the more specific answers, whatever order they're declared in: a literal path element before a parameter within literal text, that before a whole-element parameter, and that before a wildcard. A route can also decline, passing the request on to the next route that matches.", null, GUIDE + "which-route-answers" ) );
		details.add( parameters( route, path ) );
		details.add( answer( route ) );
		details.add( conditions( route ) );
		details.add( trailingSlash( route, path ) );
		details.add( crossSite( route ) );

		if( route.parametersClass() != null ) {
			details.add( new Detail( "Bad input", route.fieldsReported()
					? "A query parameter or field that doesn't convert is null, and reported to the route (ri.conversionErrors()), so a form can be shown again with what's wrong (Fields.REPORTED)."
					: "A query parameter or field that doesn't convert declines the request, as a wrong URL does, unless the route answers bad input itself (whenInvalid). A form opts into seeing the errors with Fields.REPORTED.",
					null, GUIDE + "bad-input" ) );
		}

		details.add( linking( route, path ) );
		details.add( new Detail( "Table", "application".equals( route.table() )
				? "The application's: it overrides a plugin's route for the same requests."
				: "The plugin table %s: a route of the application's for the same requests overrides it.".formatted( route.table() ),
				null, GUIDE + "tables-and-plugins" ) );
		return details;
	}

	private static String matches( final PathPattern path ) {
		final List<String> parts = new ArrayList<>();

		for( final String name : path.parameterNames() ) {
			if( name.equals( path.optionalParameter() ) ) {
				parts.add( "{%s} is optional: the route matches with or without it, and it's absent from the shorter path".formatted( name ) );
			}
			else if( name.equals( path.wildcardName() ) ) {
				parts.add( "{%s*} takes everything beneath, as one value".formatted( name ) );
			}
			else {
				parts.add( "{%s} is one path element (it never matches an empty one)".formatted( name ) );
			}
		}

		if( path.isWildcard() && PathPattern.WILDCARD_PARAMETER.equals( path.wildcardName() ) ) {
			parts.add( "* takes everything beneath, available as ri.parameter( \"*\" )" );
		}

		final String exact = "Requests whose path is %s.".formatted( path.source() );
		return parts.isEmpty() ? exact + " Paths are case-sensitive." : exact + " " + String.join( "; ", parts ) + ".";
	}

	private static Detail parameters( final RouteDescription route, final PathPattern path ) {
		final List<String> hostParameters = route.conditions().stream().filter( Host.class::isInstance ).flatMap( condition -> ((Host)condition).parameterNames().stream() ).toList();

		if( route.parametersClass() != null ) {
			final List<String> components = new ArrayList<>();

			for( final RecordComponent component : route.parametersClass().getRecordComponents() ) {
				final String name = component.getName();
				final String from = path.parameterNames().contains( name ) ? (name.equals( path.optionalParameter() ) ? "the path, optional" : "the path") : hostParameters.contains( name ) ? "the host" : component.getType() == List.class ? "the query or form, repeated" : "the query or form";
				components.add( "%s %s (%s)".formatted( component.getType().getSimpleName(), name, from ) );
			}

			return new Detail( "Parameters", "A record, %s, converted from the request before the route answers: %s. One that doesn't convert declines the request (the URL is wrong), so the route never sees a value that isn't of its type.".formatted( route.parametersClass().getSimpleName(), String.join( ", ", components ) ), null, GUIDE + "typed-routes" );
		}

		final List<String> names = new ArrayList<>( path.parameterNames() );
		names.addAll( hostParameters );

		if( names.isEmpty() && !path.isWildcard() ) {
			return new Detail( "Parameters", "None. A query string is the request's own (ri.query( \"name\" ) reads a value, converted with ri.query( \"name\", Integer.class ) and the like).", null, GUIDE + "handlers" );
		}

		if( path.isWildcard() && PathPattern.WILDCARD_PARAMETER.equals( path.wildcardName() ) ) {
			names.add( PathPattern.WILDCARD_PARAMETER );
		}

		final String calls = names.stream().map( name -> "ri.parameter( \"" + name + "\" )" ).collect( Collectors.joining( ", " ) );
		return new Detail( "Parameters", "By name, as text: %s. Converted, ri.parameter( \"%s\", Integer.class ) and the like, declining what doesn't convert. A page route sets them on the page's fields or setters of those names, converted to their types.".formatted( calls, names.get( 0 ) ), null, GUIDE + "handlers" );
	}

	private static Detail answer( final RouteDescription route ) {
		if( route.page() ) {
			return new Detail( "Answers with", "A new %s, made in the request's context, its parameters set on it by name. A page's component actions work as on any page.".formatted( route.answer() ), null, GUIDE + "page-routes" );
		}

		return new Detail( "Answers with", "%s: code that answers with a response, a page made in the request's context (ri.page( SomePage.class )), or declines (RouteHandler.DECLINED). Making a page in another context, such as constructing a direct action, loses the session it creates.".formatted( capitalized( route.answer() ) ), null, GUIDE + "handlers" );
	}

	private static Detail conditions( final RouteDescription route ) {
		if( route.conditions().isEmpty() ) {
			return new Detail( "Conditions", "None: any host, any method, any scheme. Routes take them as options: Method.POST, Host.of( \"admin.@\" ), Scheme.HTTPS, Header.of( … ).", null, GUIDE + "conditions" );
		}

		final List<String> words = new ArrayList<>();

		for( final RouteCondition condition : route.conditions() ) {
			words.add( switch( condition ) {
				case Host host -> "only requests to the host %s (another host's request goes on to the next route)".formatted( host.pattern() );
				case Method method -> "only %s requests (another method gets 405 Method Not Allowed)".formatted( method.toString().replace( "Method ", "" ) );
				case Scheme scheme -> "only %s requests".formatted( scheme );
				case Header header -> "only requests with the header %s".formatted( header.toString().replace( "Header ", "" ) );
				default -> condition.toString();
			} );
		}

		return new Detail( "Conditions", capitalized( String.join( "; ", words ) ) + ".", null, GUIDE + (route.conditions().stream().anyMatch( Host.class::isInstance ) ? "routing-by-host" : "conditions") );
	}

	private static Detail trailingSlash( final RouteDescription route, final PathPattern path ) {
		final TrailingSlash policy = route.trailingSlash() == null ? TrailingSlash.IGNORE : route.trailingSlash();
		final String declared = path.source();

		if( declared.equals( "/" ) ) {
			return new Detail( "Trailing slash", "The root has one form only.", null, GUIDE + "trailing-slashes" );
		}

		// An optional parameter's two forms, each in the pattern's trailing slash form
		if( path.optionalParameter() != null ) {
			final List<String> forms = path.forms().stream().map( form -> trailingSlash( route, form ).text() ).toList();
			return new Detail( "Trailing slash", "For each of the two forms. " + String.join( " ", forms ), null, GUIDE + "trailing-slashes" );
		}

		if( path.isWildcard() ) {
			final String prefix = declared.substring( 0, declared.lastIndexOf( '/' ) );
			final String wildcardText = switch( policy ) {
				case IGNORE -> "%s itself matches, with nothing beneath.".formatted( prefix );
				case REDIRECT -> "%s itself is answered with 308 to %s/.".formatted( prefix, prefix );
				case STRICT -> "%s itself doesn't match, only %s/ and what's beneath.".formatted( prefix, prefix );
			};

			return new Detail( "Trailing slash", wildcardText, null, GUIDE + "trailing-slashes" );
		}

		final String other = declared.endsWith( "/" ) ? declared.substring( 0, declared.length() - 1 ) : declared + "/";
		final String text = switch( policy ) {
			case IGNORE -> "Ignored: %s matches as %s does.".formatted( other, declared );
			case REDIRECT -> "Redirected: %s is answered with 308 to %s, keeping the method and the query string.".formatted( other, declared );
			case STRICT -> "Strict: %s doesn't match, only %s does.".formatted( other, declared );
		};

		return new Detail( "Trailing slash", text, null, GUIDE + "trailing-slashes" );
	}

	private static Detail crossSite( final RouteDescription route ) {
		final String text = route.crossSite() == CrossSite.SAME_ORIGIN
				? "Requests that change things (a POST, say) are taken from this application's own pages only: another site's form posting here is refused with 403. An API or a webhook takes them with CrossSite.ALLOWED."
				: "Taken from %s (%s), as an API or a webhook takes them.".formatted( route.crossSite() == CrossSite.ALLOWED ? "any site" : "the application's other hosts", route.crossSite() );
		return new Detail( "Posts from other sites", text, null, GUIDE + "posts-from-other-sites" );
	}

	private static Detail linking( final RouteDescription route, final PathPattern path ) {
		if( path.isWildcard() && PathPattern.WILDCARD_PARAMETER.equals( path.wildcardName() ) ) {
			return new Detail( "Linking", "A wildcard whose remainder has no name has no URL of its own. Name it ({path*}) to link to it.", null, GUIDE + "patterns" );
		}

		if( route.constant() == null ) {
			return new Detail( "Linking", "Nothing links to it by name: it was mapped without a constant (an API's route, a webhook). Give it one, PlainRoute books = Route.plain(), to link to it with a checked link.", "routes.map( \"%s\", Routes.something, … );".formatted( declaredPattern( route ) ), GUIDE + "links" );
		}

		final String key = route.constant().substring( route.constant().lastIndexOf( '.' ) + 1 );
		final String attributes = path.parameterNames().stream().map( name -> " :%s=\"$%s\"".formatted( name, name ) ).collect( Collectors.joining() );
		return new Detail( "Linking", "By its constant, %s: a link is generated from the route, so it's always one the route answers, and its parameters are checked when it renders.%s".formatted( route.constant(), path.optionalParameter() != null ? " A link leaves the optional parameter out when its value is null." : "" ),
				"<wo:route to=\"$routes.%s\"%s>…</wo:route>".formatted( key, attributes ), GUIDE + "in-templates" );
	}

	private static String capitalized( final String text ) {
		return text.isEmpty() ? text : Character.toUpperCase( text.charAt( 0 ) ) + text.substring( 1 );
	}
}
