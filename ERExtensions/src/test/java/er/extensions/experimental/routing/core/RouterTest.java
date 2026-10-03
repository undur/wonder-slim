package er.extensions.experimental.routing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import er.extensions.experimental.routing.core.Router.Matched;
import er.extensions.experimental.routing.core.Router.MethodNotAllowed;
import er.extensions.experimental.routing.core.Router.NoMatch;
import er.extensions.experimental.routing.core.Router.Redirect;

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

		// Not /news itself, under any policy: the wildcard means "beneath"
		assertInstanceOf( NoMatch.class, get( router, "/news" ) );
		assertInstanceOf( NoMatch.class, get( router, "/newsletter" ) );
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
		table( router ).map( "/docs/", TrailingSlash.STRICT, "docs" );

		assertInstanceOf( NoMatch.class, get( router, "/docs" ) );
		assertEquals( List.of( "docs" ), handlers( get( router, "/docs/" ) ) );
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
		routes.map( "/docs/", TrailingSlash.REDIRECT, "docs" );
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
	public void invalidPatterns() {
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "items" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/items//edit" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/items/{id}/{id}" ) );
		assertThrows( IllegalArgumentException.class, () -> PathPattern.parse( "/items/item-{id}" ) );
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
}
