package er.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WOActionResults;

import er.routing.core.Host;
import er.routing.core.Method;
import er.routing.core.Router;
import er.routing.core.TrailingSlash;

/**
 * The router's WebObjects side, without a running application: groups, joins, filters, and typed routes' declarations
 * and links
 */
public class RouteGroupTest {

	private static final RouteHandler NOTHING = invocation -> null;

	private static Router.Entry<?> entry( final ERXRouter router, final String pattern ) {
		return router.routes().stream().filter( e -> e.path().source().equals( pattern ) ).findFirst().orElseThrow();
	}

	@Test
	public void aGroupsPrefixConditionsAndPolicyReachItsRoutes() {
		final ERXRouter router = new ERXRouter();
		final RouteGroup api = router.application().group( "/api", Host.of( "{club}.example.com" ), TrailingSlash.STRICT );
		api.map( "/books", NOTHING, Method.GET );
		api.map( "/loose/", NOTHING, TrailingSlash.IGNORE );

		final Router.Entry<?> books = entry( router, "/api/books" );
		assertEquals( TrailingSlash.STRICT, books.trailingSlash() );
		assertEquals( 2, books.conditions().size() );

		// A route's own policy wins over its group's
		assertEquals( TrailingSlash.IGNORE, entry( router, "/api/loose/" ).trailingSlash() );
	}

	@Test
	public void aRouteCantAddAConditionOfATypeItsGroupHas() {
		final RouteGroup get = new ERXRouter().application().group( "/read", Method.GET );

		assertThrows( IllegalArgumentException.class, () -> get.map( "/x", NOTHING, Method.POST ) );
	}

	@Test
	public void filtersRunOutermostFirst() {
		final List<String> ran = new ArrayList<>();
		final RouteGroup outer = new ERXRouter().application().group( "/admin" );
		final RouteGroup inner = outer.group( "/danger" );
		inner.wrap( ( invocation, next ) -> { ran.add( "inner" ); return next.handle( invocation ); } );
		outer.wrap( ( invocation, next ) -> { ran.add( "outer" ); return next.handle( invocation ); } );

		inner.wrapped( invocation -> { ran.add( "route" ); return null; } ).handle( null );

		assertEquals( List.of( "outer", "inner", "route" ), ran );
	}

	@Test
	public void aFilterCanAnswerItself() {
		final RouteGroup admin = new ERXRouter().application().group( "/admin" );
		final WOActionResults refused = () -> null;
		admin.wrap( ( invocation, next ) -> refused );

		assertEquals( refused, admin.wrapped( invocation -> { throw new AssertionError( "The route runs" ); } ).handle( null ) );
	}

	@Test
	public void aPluginJoinsANamedGroup() {
		final List<String> ran = new ArrayList<>();
		final ERXRouter router = new ERXRouter();

		// The plugin joins before the application names the group
		router.table( "plugin" ).join( "admin", admin -> {
			final PlainRoute moderate = admin.map( "/moderate", NOTHING );
			assertEquals( "/admin/moderate", moderate.pattern() );
		} );
		assertTrue( router.routes().isEmpty() );

		final RouteGroup admin = router.application().group( "/admin", Host.of( "admin.example.com" ) ).named( "admin" );
		admin.wrap( ( invocation, next ) -> { ran.add( "admin filter" ); return next.handle( invocation ); } );

		final Router.Entry<?> moderate = entry( router, "/admin/moderate" );
		assertEquals( "plugin", moderate.table() );
		assertEquals( 1, moderate.conditions().size() );

		// The application's filter wraps the plugin's route
		final RouteGroup joined = router.table( "other" ).join( "admin" );
		joined.wrapped( invocation -> null ).handle( null );
		assertEquals( List.of( "admin filter" ), ran );
	}

	@Test
	public void joiningAGroupNeverNamedFails() {
		assertThrows( IllegalArgumentException.class, () -> new ERXRouter().table( "plugin" ).join( "nowhere" ) );
	}

	@Test
	public void theApplicationsTableRanksFirst() {
		final ERXRouter router = new ERXRouter();
		router.table( "plugin" ).map( "/login", NOTHING );
		router.application().map( "/login", NOTHING );

		assertEquals( "application", router.routes().getFirst().table() );
	}

	public record Search( String q, Integer page ) {}

	public record Item( int id, String tab ) {}

	public record WithAnObject( Thread thread ) {}

	public record PrimitiveQuery( int page ) {}

	@Test
	public void aTypedRoutesRecordIsCheckedWhenDeclared() {
		final RouteGroup routes = new ERXRouter().application();

		routes.route( "/items/{id}", Item.class, ( item, invocation ) -> null );

		// A pattern parameter the record lacks, a type without a converter, a query parameter that can't be absent
		assertThrows( IllegalArgumentException.class, () -> routes.route( "/items/{itemId}/more", Item.class, ( item, invocation ) -> null ) );
		assertThrows( IllegalArgumentException.class, () -> routes.route( "/threads", WithAnObject.class, ( item, invocation ) -> null ) );
		assertThrows( IllegalArgumentException.class, () -> routes.route( "/pages", PrimitiveQuery.class, ( item, invocation ) -> null ) );
	}

	@Test
	public void aLinksValuesAreChecked() {
		final Route<Item> item = new ERXRouter().application().route( "/items/{id}", Item.class, ( i, invocation ) -> null );

		// Unknown parameter, missing route parameter, wrong type, text that isn't one of the type
		assertThrows( IllegalArgumentException.class, () -> item.url( Map.of( "id", 1, "colour", "red" ), null ) );
		assertThrows( IllegalArgumentException.class, () -> item.url( Map.of( "tab", "history" ), null ) );
		assertThrows( IllegalArgumentException.class, () -> item.url( Map.of( "id", 1.5 ), null ) );
		assertThrows( IllegalArgumentException.class, () -> item.url( Map.of( "id", "abc" ), null ) );
	}

	@Test
	public void parametersFromValues() {
		final Route<Search> search = new ERXRouter().application().route( "/search", Search.class, ( s, invocation ) -> null );

		assertEquals( new Search( "kaffi", 2 ), search.parameters( Map.of( "q", "kaffi", "page", "2" ) ) );
		assertNull( search.parameters( Map.of() ).page() );
	}
}
