package er.routing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import er.routing.core.Router.Matched;
import er.routing.core.Router.MethodNotAllowed;
import er.routing.core.Router.NoMatch;
import er.routing.core.Router.Redirect;

public class RouterTest {

	private static Router<String>.Table table( final Router<String> router ) {
		return router.table( "application" );
	}

	private static Router.Result<String> get( final Router<String> router, final String path ) {
		return router.route( new RouteRequest( "GET", "example.com", path ) );
	}

	/**
	 * @return The handlers of the matched candidates, in the order they're tried
	 */
	private static List<String> handlers( final Router.Result<String> result ) {
		return matched( result ).candidates().stream().map( Router.Candidate::handler ).toList();
	}

	private static Map<String, String> parameters( final Router.Result<String> result ) {
		return matched( result ).candidates().getFirst().parameters();
	}

	@SuppressWarnings("unchecked")
	private static Matched<String> matched( final Router.Result<String> result ) {
		assertInstanceOf( Matched.class, result );
		return (Matched<String>)result;
	}

	@Test
	public void exactAndParameters() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/", "root" );
		routes.map( "/items/{id}", "item" );
		routes.map( "/items/{id}/history/{page}", "history" );

		assertEquals( List.of( "root" ), handlers( get( router, "/" ) ) );
		assertEquals( Map.of( "id", "42" ), parameters( get( router, "/items/42" ) ) );
		assertEquals( Map.of( "id", "42", "page", "3" ), parameters( get( router, "/items/42/history/3" ) ) );
		assertInstanceOf( NoMatch.class, get( router, "/items" ) );
		assertInstanceOf( NoMatch.class, get( router, "/items/42/history" ) );
	}

	@Test
	public void precedenceDoesNotDependOnMappingOrder() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/*", "catchAll" );
		routes.map( "/items/*", "itemsWildcard" );
		routes.map( "/items/{id}", "item" );
		routes.map( "/items/new", "newItem" );

		assertEquals( List.of( "newItem", "item", "itemsWildcard", "catchAll" ), handlers( get( router, "/items/new" ) ) );
		assertEquals( List.of( "item", "itemsWildcard", "catchAll" ), handlers( get( router, "/items/42" ) ) );
		assertEquals( List.of( "itemsWildcard", "catchAll" ), handlers( get( router, "/items/42/history" ) ) );
		assertEquals( List.of( "catchAll" ), handlers( get( router, "/about" ) ) );
	}

	@Test
	public void wildcardMatchesBeneathItsPrefixOnly() {
		final Router<String> router = new Router<>();
		table( router ).map( "/news/*", "news" );

		assertEquals( Map.of( "*", "" ), parameters( get( router, "/news/" ) ) );
		assertEquals( Map.of( "*", "2026/10" ), parameters( get( router, "/news/2026/10" ) ) );
		assertEquals( Map.of( "*", "2026/10/" ), parameters( get( router, "/news/2026/10/" ) ) );

		// /news is the wildcard's other trailing slash form: matched with nothing beneath (the router ignores the slash)
		assertEquals( Map.of( "*", "" ), parameters( get( router, "/news" ) ) );
		assertInstanceOf( NoMatch.class, get( router, "/newsletter" ) );

		// Redirected to /news/, or not matched, as other policies have it
		final Router<String> redirecting = new Router<>();
		table( redirecting ).map( "/news/*", "news", TrailingSlash.REDIRECT );
		assertEquals( new Router.Redirect<String>( "/news/" ), get( redirecting, "/news" ) );

		final Router<String> strict = new Router<>();
		table( strict ).map( "/news/*", "news", TrailingSlash.STRICT );
		assertInstanceOf( NoMatch.class, get( strict, "/news" ) );
	}

	@Test
	public void segmentsAreDecodedAfterSplitting() {
		final Router<String> router = new Router<>();
		table( router ).map( "/files/{name}", "file" );

		assertEquals( Map.of( "name", "a/b" ), parameters( get( router, "/files/a%2Fb" ) ) );
		assertEquals( Map.of( "name", "kaffi og kökur" ), parameters( get( router, "/files/kaffi%20og%20k%C3%B6kur" ) ) );
		assertEquals( Map.of( "name", "a+b" ), parameters( get( router, "/files/a+b" ) ) );
		assertInstanceOf( NoMatch.class, get( router, "/files/%zz" ) );
	}

	@Test
	public void aParameterNeverMatchesAnEmptySegment() {
		final Router<String> router = new Router<>();
		table( router ).map( "/items/{id}/edit", "edit" );

		assertInstanceOf( NoMatch.class, get( router, "/items//edit" ) );
	}

	@Test
	public void pathsAreCaseSensitive() {
		final Router<String> router = new Router<>();
		table( router ).map( "/About", "about" );

		assertInstanceOf( NoMatch.class, get( router, "/about" ) );
	}

	@Test
	public void trailingSlashIgnoredByDefault() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/docs/", "docs" );
		routes.map( "/about", "about" );

		assertEquals( List.of( "docs" ), handlers( get( router, "/docs" ) ) );
		assertEquals( List.of( "docs" ), handlers( get( router, "/docs/" ) ) );
		assertEquals( List.of( "about" ), handlers( get( router, "/about/" ) ) );
	}

	@Test
	public void trailingSlashRedirect() {
		final Router<String> router = new Router<>( TrailingSlash.REDIRECT );
		final var routes = table( router );
		routes.map( "/docs/", "docs" );
		routes.map( "/about", "about" );

		assertEquals( "/docs/", assertInstanceOf( Redirect.class, get( router, "/docs" ) ).path() );
		assertEquals( "/about", assertInstanceOf( Redirect.class, get( router, "/about/" ) ).path() );
		assertEquals( List.of( "docs" ), handlers( get( router, "/docs/" ) ) );
	}

	@Test
	public void trailingSlashStrict() {
		final Router<String> router = new Router<>();
		table( router ).map( "/docs/", "docs", TrailingSlash.STRICT );

		assertInstanceOf( NoMatch.class, get( router, "/docs" ) );
		assertEquals( List.of( "docs" ), handlers( get( router, "/docs/" ) ) );
	}

	@Test
	public void theOtherFormOfAStrictRouteIsNotA405() {
		final Router<String> router = new Router<>( TrailingSlash.STRICT );
		final var routes = table( router );
		routes.map( "/api/books", "list", Method.GET );
		routes.map( "/api/books", "create", Method.POST );

		assertInstanceOf( NoMatch.class, get( router, "/api/books/" ) );
		assertEquals( List.of( "list" ), handlers( get( router, "/api/books" ) ) );
	}

	@Test
	public void oneTrailingSlashPolicyPerRoute() {
		final Router<String> router = new Router<>();

		assertThrows( IllegalArgumentException.class, () -> table( router ).map( "/docs/", "docs", TrailingSlash.STRICT, TrailingSlash.REDIRECT ) );
	}

	@Test
	public void theRootHasOneForm() {
		final Router<String> router = new Router<>( TrailingSlash.STRICT );
		table( router ).map( "/", "root" );

		assertEquals( List.of( "root" ), handlers( get( router, "/" ) ) );
		assertEquals( List.of( "root" ), handlers( get( router, "" ) ) );
	}

	@Test
	public void aMoreSpecificRouteRedirectsBeforeALessSpecificOneMatches() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/docs/", "docs", TrailingSlash.REDIRECT );
		routes.map( "/{page}", "page" );

		assertEquals( "/docs/", assertInstanceOf( Redirect.class, get( router, "/docs" ) ).path() );
	}

	@Test
	public void methods() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/hooks/github", "hook", Method.POST );
		routes.map( "/items/{id}", "item", Method.GET );
		routes.map( "/items/{id}", "updateItem", Method.of( "PUT", "PATCH" ) );

		assertEquals( List.of( "hook" ), handlers( router.route( new RouteRequest( "POST", "example.com", "/hooks/github" ) ) ) );
		assertEquals( Set.of( "POST" ), assertInstanceOf( MethodNotAllowed.class, get( router, "/hooks/github" ) ).allowedMethods() );

		// HEAD is accepted wherever GET is
		assertEquals( List.of( "item" ), handlers( router.route( new RouteRequest( "head", "example.com", "/items/1" ) ) ) );
		assertEquals( List.of( "updateItem" ), handlers( router.route( new RouteRequest( "PATCH", "example.com", "/items/1" ) ) ) );
		assertEquals( Set.of( "GET", "HEAD", "PATCH", "PUT" ), assertInstanceOf( MethodNotAllowed.class, router.route( new RouteRequest( "DELETE", "example.com", "/items/1" ) ) ).allowedMethods() );
	}

	@Test
	public void hosts() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/", "main" );
		routes.map( "/", "tenant", Host.of( "{tenant}.example.com" ) );
		routes.map( "/", "admin", Host.of( "admin.example.com" ) );

		assertEquals( List.of( "admin", "tenant", "main" ), handlers( router.route( new RouteRequest( "GET", "Admin.Example.com:8080", "/" ) ) ) );
		assertEquals( List.of( "tenant", "main" ), handlers( router.route( new RouteRequest( "GET", "acme.example.com", "/" ) ) ) );
		assertEquals( Map.of( "tenant", "acme" ), parameters( router.route( new RouteRequest( "GET", "acme.example.com", "/" ) ) ) );
		assertEquals( List.of( "main" ), handlers( router.route( new RouteRequest( "GET", "example.com", "/" ) ) ) );
		assertEquals( List.of( "main" ), handlers( router.route( new RouteRequest( "GET", null, "/" ) ) ) );
	}

	@Test
	public void aHostMismatchDoesNotCountTowardsA405() {
		final Router<String> router = new Router<>();
		table( router ).map( "/hooks", "hook", Host.of( "admin.example.com" ), Method.POST );

		assertInstanceOf( NoMatch.class, router.route( new RouteRequest( "GET", "example.com", "/hooks" ) ) );
		assertInstanceOf( MethodNotAllowed.class, router.route( new RouteRequest( "GET", "admin.example.com", "/hooks" ) ) );
	}

	@Test
	public void aHostParameterMayNotShareAPathParametersName() {
		final Router<String> router = new Router<>();

		assertThrows( IllegalArgumentException.class, () -> table( router ).map( "/{tenant}", "x", Host.of( "{tenant}.example.com" ) ) );
	}

	@Test
	public void conflictsWithinATableAreRefused() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/items/{id}", "item" );
		routes.map( "/items/{id}", "postItem", Method.POST );
		routes.map( "/items/{id}", "getItem", Method.GET );

		// The same shape, whatever the parameters are called, or the trailing slash
		assertThrows( IllegalArgumentException.class, () -> routes.map( "/items/{itemId}", "other" ) );
		assertThrows( IllegalArgumentException.class, () -> routes.map( "/items/{id}/", "other" ) );

		// Overlapping methods
		assertThrows( IllegalArgumentException.class, () -> routes.map( "/items/{id}", "other", Method.of( "POST", "PUT" ) ) );

		// Different shapes, or conditions that can't both hold, are fine
		routes.map( "/items/new", "newItem" );
		routes.map( "/items/{id}", "putItem", Method.PUT );
	}

	@Test
	public void overridesBetweenTables() {
		final Router<String> router = new Router<>();
		final var application = router.table( "application" );
		final var plugin = router.table( "plugin" );
		plugin.map( "/login", "pluginLogin" );
		plugin.map( "/admin/users", "pluginUsers" );
		application.map( "/login", "applicationLogin" );
		application.map( "/*", "applicationCatchAll" );

		// The application's route wins, and the override is recorded
		assertEquals( List.of( "applicationLogin", "pluginLogin", "applicationCatchAll" ), handlers( get( router, "/login" ) ) );
		assertEquals( 1, router.overrides().size() );
		assertEquals( "applicationLogin", router.overrides().getFirst().route().handler() );

		// Specificity comes first across tables: the application's catch-all doesn't hide the plugin's route
		assertEquals( List.of( "pluginUsers", "applicationCatchAll" ), handlers( get( router, "/admin/users" ) ) );
	}

	@Test
	public void theApplicationsTableRanksFirstWhenCreatedLast() {
		final Router<String> router = new Router<>();
		router.table( "plugin" ).map( "/login", "pluginLogin" );
		router.table( "application", Integer.MIN_VALUE ).map( "/login", "applicationLogin" );

		assertEquals( List.of( "applicationLogin", "pluginLogin" ), handlers( get( router, "/login" ) ) );
		assertEquals( "applicationLogin", router.overrides().getFirst().route().handler() );
	}

	@Test
	public void invalidPatterns() {
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "items" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/items//edit" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/items/{id}/{id}" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/items/{a}-{b}" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/items/{id}*" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/files/{path*}/more" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/files/{path}/{path*}" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/news/*/latest" ) );
		assertThrows( IllegalArgumentException.class, () -> Host.of( "{a}.{a}.example.com" ) );
	}

	@Test
	public void hostForParameters() {
		assertEquals( "acme.example.com", Host.of( "{tenant}.example.com" ).host( Map.of( "tenant", "Acme" ) ) );
		assertTrue( Host.of( "admin.example.com" ).parameterNames().isEmpty() );
	}

	@Test
	public void pathsForParameters() {
		assertEquals( "/", PathPattern.parse( "/" ).path( Map.of() ) );
		assertEquals( "/items/42", PathPattern.parse( "/items/{id}" ).path( Map.of( "id", "42" ) ) );
		assertEquals( "/files/a%2Fb%20c/", PathPattern.parse( "/files/{name}/" ).path( Map.of( "name", "a/b c" ) ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/items/{id}" ).path( Map.of() ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/news/*" ).path( Map.of() ) );

		// The reverse of matching
		final PathPattern pattern = PathPattern.parse( "/files/{name}" );
		assertEquals( Map.of( "name", "kaffi & kökur/2" ), pattern.match( RequestPath.parse( pattern.path( Map.of( "name", "kaffi & kökur/2" ) ) ) ).parameters() );
	}

	@Test
	public void aCatchAllDoesNotSwallowA405() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/api/books", "list", Method.GET );
		routes.map( "/api/books", "create", Method.POST );
		routes.map( "/*", "catchAll" );

		assertEquals( Set.of( "GET", "HEAD", "POST" ), assertInstanceOf( MethodNotAllowed.class, router.route( new RouteRequest( "PUT", "example.com", "/api/books" ) ) ).allowedMethods() );
		assertEquals( List.of( "catchAll" ), handlers( router.route( new RouteRequest( "PUT", "example.com", "/elsewhere" ) ) ) );
	}

	@Test
	public void aRouteOfTheSameShapeStillTakesAnotherMethod() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/books", "create", Method.POST );
		routes.map( "/books/", "list" );
		routes.map( "/*", "catchAll" );

		assertEquals( List.of( "list", "catchAll" ), handlers( get( router, "/books" ) ) );
		assertEquals( List.of( "create", "list", "catchAll" ), handlers( router.route( new RouteRequest( "POST", "example.com", "/books" ) ) ) );
	}

	@Test
	public void twoConditionsOfOneTypeAreRefused() {
		final Router<String> router = new Router<>();
		final var routes = table( router );

		final IllegalArgumentException e = assertThrows( IllegalArgumentException.class, () -> routes.map( "/x", "x", Method.GET, Method.POST ) );
		assertTrue( e.getMessage().contains( "/x" ) );
		assertThrows( IllegalArgumentException.class, () -> routes.map( "/y", "y", Host.of( "a.com" ), Host.of( "b.com" ) ) );
	}

	@Test
	public void hostParameterNamesKeepTheirCase() {
		final Host host = Host.of( "{tenantId}.Example.com" );

		assertEquals( List.of( "tenantId" ), host.parameterNames() );
		assertEquals( "{tenantId}.example.com", host.pattern() );

		final Router<String> router = new Router<>();
		table( router ).map( "/", "tenant", host );
		assertEquals( Map.of( "tenantId", "acme" ), parameters( router.route( new RouteRequest( "GET", "ACME.example.com", "/" ) ) ) );
	}

	@Test
	public void hostValuesAreOneLabel() {
		final Host host = Host.of( "{club}.localhost" );

		assertEquals( "acme-2.localhost", host.host( Map.of( "club", "Acme-2" ) ) );
		assertThrows( IllegalArgumentException.class, () -> host.host( Map.of( "club", "a.b" ) ) );
		assertThrows( IllegalArgumentException.class, () -> host.host( Map.of( "club", "evil.com/x" ) ) );
		assertThrows( IllegalArgumentException.class, () -> host.host( Map.of( "club", "-acme" ) ) );
		assertThrows( IllegalArgumentException.class, () -> host.host( Map.of( "club", "a b" ) ) );
	}

	@Test
	public void hasRouteForClaimsOnlyItsRoutes() {
		final Router<String> router = new Router<>();
		final var routes = table( router );
		routes.map( "/books/{book}", "book" );
		routes.map( "/about/", "about", Method.GET );
		routes.map( "/admin/", "admin", Host.of( "admin.example.com" ) );

		assertTrue( router.hasRouteFor( "/books/2" ) );
		assertTrue( router.hasRouteFor( "/about" ) );

		// A route for one host doesn't claim the path for every host
		assertFalse( router.hasRouteFor( "/admin" ) );
		assertFalse( router.hasRouteFor( "/wonder/admin" ) );
		assertFalse( router.hasRouteFor( "/books" ) );
	}

	@Test
	public void hostPatternsHaveNoPortAndMatchOnlyLabels() {
		assertThrows( IllegalArgumentException.class, () -> Host.of( "localhost:1300" ) );

		final Router<String> router = new Router<>();
		table( router ).map( "/", "club", Host.of( "{club}.localhost" ) );

		assertInstanceOf( NoMatch.class, router.route( new RouteRequest( "GET", "my_club.localhost", "/" ) ) );
		assertEquals( List.of( "club" ), handlers( router.route( new RouteRequest( "GET", "my-club.localhost", "/" ) ) ) );
	}

	@Test
	public void routesForAHostDontClaimPaths() {
		final Router<String> router = new Router<>();
		table( router ).map( "/*", "clubCatchAll", Host.of( "{club}.localhost" ) );

		assertFalse( router.hasRouteFor( "/wonder/admin" ) );
	}

	enum Stray implements RouteOption {
		OPTION
	}

	@Test
	public void anOptionTheRouterDoesntKnowIsRefused() {
		assertThrows( IllegalArgumentException.class, () -> table( new Router<>() ).map( "/x", "x", Stray.OPTION ) );
	}

	@Test
	public void dotSegmentsArentPathElements() {
		final PathPattern members = PathPattern.parse( "/members/{handle}" );

		assertThrows( IllegalArgumentException.class, () -> members.path( Map.of( "handle", ".." ) ) );
		assertThrows( IllegalArgumentException.class, () -> members.path( Map.of( "handle", "." ) ) );
		assertEquals( "/members/...", members.path( Map.of( "handle", "..." ) ) );

		// Servers refuse an encoded %, \ or control character in a path
		assertThrows( IllegalArgumentException.class, () -> members.path( Map.of( "handle", "a\tb" ) ) );
		assertThrows( IllegalArgumentException.class, () -> members.path( Map.of( "handle", "a\u007Fb" ) ) );
		assertThrows( IllegalArgumentException.class, () -> members.path( Map.of( "handle", "50% off" ) ) );
		assertThrows( IllegalArgumentException.class, () -> members.path( Map.of( "handle", "a\\b" ) ) );
	}

	@Test
	public void aPathWithADotSegmentMatchesNothing() {
		final Router<String> router = new Router<>();
		table( router ).map( "/files/*", "files" );

		assertInstanceOf( NoMatch.class, get( router, "/files/x/../y" ) );
		assertInstanceOf( NoMatch.class, get( router, "/files/x/%2E%2E/y" ) );
		assertInstanceOf( NoMatch.class, get( router, "/files/./y" ) );

		// An encoded / in the remainder would carry dot segments past the check
		assertInstanceOf( NoMatch.class, get( router, "/files/..%2F..%2Fetc%2Fpasswd" ) );
		assertInstanceOf( NoMatch.class, get( router, "/files/x/..%2Fy" ) );
		assertInstanceOf( NoMatch.class, get( router, "/files/a%2Fb" ) );
		assertEquals( Map.of( "*", "x/.../y" ), parameters( get( router, "/files/x/.../y" ) ) );
	}

	@Test
	public void aHostRelativeToTheDomain() {
		final Host host = Host.of( "{club}.@" );

		assertTrue( host.isRelative() );
		assertEquals( "{club}.example.com", host.withDomain( "example.com" ).pattern() );
		assertEquals( "example.com", Host.of( "@" ).withDomain( "example.com" ).pattern() );
		assertThrows( IllegalStateException.class, () -> host.host( Map.of( "club", "acme" ) ) );
		assertThrows( IllegalArgumentException.class, () -> Host.of( "@.example.com" ) );
		assertThrows( IllegalArgumentException.class, () -> Host.of( "{club}.x@" ) );
	}

	@Test
	public void schemeAndHeaderConditions() {
		final Router<String> router = new Router<>();
		final Router<String>.Table table = table( router );
		table.map( "/admin", "secure", Scheme.HTTPS );
		table.map( "/api", "v2", Header.of( "X-Api-Version", "2" ) );
		table.map( "/api", "any version" );

		final java.util.function.Function<String, String> v2 = name -> name.equals( "x-api-version" ) ? "2" : null;
		assertInstanceOf( NoMatch.class, router.route( new RouteRequest( "GET", "x", "/admin", false, null ) ) );
		assertInstanceOf( Router.Matched.class, router.route( new RouteRequest( "GET", "x", "/admin", true, null ) ) );
		assertEquals( "v2", ((Router.Matched<String>)router.route( new RouteRequest( "GET", "x", "/api", false, v2 ) )).candidates().getFirst().handler() );
		assertEquals( "any version", ((Router.Matched<String>)router.route( new RouteRequest( "GET", "x", "/api", false, null ) )).candidates().getFirst().handler() );

		// A route on a scheme or a header doesn't claim its path for every request
		assertFalse( router.hasRouteFor( "/admin" ) );
		assertTrue( router.hasRouteFor( "/api" ) );

		// Two routes on the same header and value conflict, on another value they don't
		assertThrows( IllegalArgumentException.class, () -> table.map( "/api", "again", Header.of( "x-api-version", "2" ) ) );
		table.map( "/api", "v3", Header.of( "x-api-version", "3" ) );
	}

	@Test
	public void aParameterWithinAnElement() {
		final Router<String> router = new Router<>();
		final Router<String>.Table table = table( router );
		table.map( "/books/{id}.json", "json" );
		table.map( "/books/{id}", "page" );
		table.map( "/books/new.json", "new" );

		// A literal, then a parameter within literal text, then a whole-element parameter
		assertEquals( "new", ((Router.Matched<String>)get( router, "/books/new.json" )).candidates().getFirst().handler() );
		assertEquals( Map.of( "id", "7" ), parameters( get( router, "/books/7.json" ) ) );
		assertEquals( "page", ((Router.Matched<String>)get( router, "/books/7.json" )).candidates().get( 1 ).handler() );
		assertEquals( Map.of( "id", "7" ), parameters( get( router, "/books/7" ) ) );

		// The parameter isn't empty
		assertEquals( Map.of( "id", ".json" ), parameters( get( router, "/books/.json" ) ) );

		assertEquals( "/books/7.json", PathPattern.parse( "/books/{id}.json" ).path( Map.of( "id", "7" ) ) );
		assertEquals( "/book-a%20b", PathPattern.parse( "/book-{id}" ).path( Map.of( "id", "a b" ) ) );
		assertThrows( IllegalArgumentException.class, () -> table.map( "/books/{other}.json", "again" ) );
	}

	@Test
	public void aNamedWildcard() {
		final PathPattern files = PathPattern.parse( "/files/{path*}" );
		final Router<String> router = new Router<>();
		table( router ).map( "/files/{path*}", "files" );

		assertEquals( List.of( "path" ), files.parameterNames() );
		assertEquals( Map.of( "path", "minutes/2026.txt" ), parameters( get( router, "/files/minutes/2026.txt" ) ) );

		// Its URL from the remainder, each element encoded, dot segments refused
		assertEquals( "/files/minutes/2026%20notes.txt", files.path( Map.of( "path", "minutes/2026 notes.txt" ) ) );
		assertEquals( "/files/minutes/", files.path( Map.of( "path", "minutes/" ) ) );
		assertEquals( "/files/", files.path( Map.of( "path", "" ) ) );
		assertThrows( IllegalArgumentException.class, () -> files.path( Map.of( "path", "../secret" ) ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/files/*" ).path( Map.of( "*", "x" ) ) );
	}

	@Test
	public void parametersWithinLiteralTextAtTheSamePlace() {
		final Router<String> router = new Router<>();
		final Router<String>.Table table = table( router );

		// The more literal text on the same side comes first, whatever the mapping order
		table.map( "/x/{a}.json", "json" );
		table.map( "/x/{a}.min.json", "min" );
		assertEquals( "min", ((Router.Matched<String>)get( router, "/x/app.min.json" )).candidates().getFirst().handler() );
		assertEquals( Map.of( "a", "app" ), parameters( get( router, "/x/app.min.json" ) ) );

		// Literal text on opposite sides: both match /x/pre-7.json, and neither comes first
		assertThrows( IllegalArgumentException.class, () -> table.map( "/x/pre-{a}", "pre" ) );
	}
}
