package er.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	private static RouteDescription entry( final ERXRouter router, final String pattern ) {
		return router.routes().stream().filter( e -> e.pattern().equals( pattern ) ).findFirst().orElseThrow();
	}

	@Test
	public void aGroupsPrefixConditionsAndPolicyReachItsRoutes() {
		final ERXRouter router = new ERXRouter();
		final RouteGroup api = router.application().group( "/api", Host.of( "{club}.example.com" ), TrailingSlash.STRICT );
		api.map( "/books", NOTHING, Method.GET );
		api.map( "/loose/", NOTHING, TrailingSlash.IGNORE );

		final RouteDescription books = entry( router, "/api/books" );
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

		final RouteDescription moderate = entry( router, "/admin/moderate" );
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

		// Deferred, it fails the check made once the application has launched
		final ERXRouter router = new ERXRouter();
		router.table( "plugin" ).join( "nowhere", group -> group.map( "/x", NOTHING ) );
		assertThrows( IllegalStateException.class, router::checkJoins );

		// It fails again, every time, until the group is named
		assertThrows( IllegalStateException.class, router::checkJoins );

		router.application().group( "/somewhere" ).named( "nowhere" );
		router.checkJoins();
	}

	@Test
	public void aNullOptionSaysWhatsWrong() {
		final NullPointerException e = assertThrows( NullPointerException.class, () -> new ERXRouter().application().map( "/p", NOTHING, (er.routing.core.RouteOption)null ) );

		assertTrue( e.getMessage().contains( "route option is null" ) );
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
	public void routesAreDescribed() {
		final ERXRouter router = new ERXRouter();
		final Route<Search> search = router.application().route( "/search", Search.class, ( s, invocation ) -> null );
		final PlainRoute about = router.table( "plugin" ).map( "/about", NOTHING, Method.GET );

		final RouteDescription searchDescription = router.routes().stream().filter( d -> d.pattern().equals( "/search" ) ).findFirst().orElseThrow();
		assertEquals( search, searchDescription.route() );
		assertEquals( Search.class, searchDescription.parametersClass() );

		assertFalse( searchDescription.crossSiteAllowed() );
		assertFalse( searchDescription.fieldsReported() );

		final RouteDescription aboutDescription = router.routes().stream().filter( d -> d.pattern().equals( "/about" ) ).findFirst().orElseThrow();
		assertEquals( about, aboutDescription.route() );
		assertNull( aboutDescription.parametersClass() );
		assertEquals( "plugin", aboutDescription.table() );
	}

	@Test
	public void aPlainRoutesLinkTakesOnlyItsParameters() {
		final PlainRoute item = new ERXRouter().application().map( "/items/{id}", NOTHING );

		assertThrows( IllegalArgumentException.class, () -> item.url( Map.of( "id", 1, "colour", "red" ), null ) );
	}

	public record Form( String title, Integer year ) {}

	public record Member( String handle ) {}

	@Test
	public void aGroupsFieldsOptionReachesItsTypedRoutes() {
		final ERXRouter router = new ERXRouter();
		final RouteGroup forms = router.application().group( "/forms", Fields.REPORTED );

		assertTrue( forms.route( "/book", Form.class, ( f, invocation ) -> null, Method.POST ).reportsFields() );
		assertFalse( router.application().route( "/plain-form", Form.class, ( f, invocation ) -> null ).reportsFields() );

		// A plain route reads its own fields, so the option means nothing there
		assertThrows( IllegalArgumentException.class, () -> router.application().map( "/p", NOTHING, Fields.REPORTED ) );
	}

	@Test
	public void aTextParameterTakesOnlyConvertibleValues() {
		final Route<Member> member = new ERXRouter().application().route( "/members/{handle}", Member.class, ( m, invocation ) -> null );

		assertThrows( IllegalArgumentException.class, () -> member.url( Map.of( "handle", new Object() ), null ) );
	}

	public record Query( String q ) {

		public Query {
			java.util.Objects.requireNonNull( q );
		}
	}

	/**
	 * A request whose form values are given, since reading them from a URI needs a running application
	 */
	private static RouteInvocation invocation( final String path, final Map<String, String> formValues, final ERXRouter router ) {
		final Map<String, List<String>> lists = new java.util.HashMap<>();
		formValues.forEach( ( name, value ) -> lists.put( name, List.of( value ) ) );
		return invocationWithLists( path, lists, router );
	}

	private static RouteInvocation invocationWithLists( final String path, final Map<String, List<String>> formValues, final ERXRouter router ) {
		final com.webobjects.appserver.WORequest request = new com.webobjects.appserver.WORequest( "GET", path, "HTTP/1.1", null, null, null ) {
			@Override
			public com.webobjects.foundation.NSArray<Object> formValuesForKey( final String key ) {
				return formValues.containsKey( key ) ? new com.webobjects.foundation.NSArray<>( formValues.get( key ).toArray() ) : null;
			}
		};

		return new RouteInvocation( path, request, Map.of(), router.converters() );
	}

	@Test
	public void aRouteHandlesARecordItCantBuild() {
		final ERXRouter router = new ERXRouter();
		final WOActionResults found = () -> null;
		final List<String> reasons = new ArrayList<>();
		final Route<Query> search = router.application().route( "/search", Query.class, ( query, invocation ) -> found );

		// Without whenInvalid, a record that refuses its values declines
		assertEquals( RouteHandler.DECLINED, search.handle( invocation( "/search", Map.of(), router ) ) );

		final WOActionResults asked = () -> null;
		assertEquals( search, search.whenInvalid( ( invocation, reason ) -> {
			reasons.add( reason.getMessage() );
			return asked;
		} ) );

		assertEquals( asked, search.handle( invocation( "/search", Map.of(), router ) ) );
		assertEquals( List.of( "Absent: [q]" ), reasons );
		assertEquals( found, search.handle( invocation( "/search", Map.of( "q", "dune" ), router ) ) );

		// An empty value is absent, for text too (?q=)
		assertEquals( asked, search.handle( invocation( "/search", Map.of( "q", "" ), router ) ) );
	}

	public record Listing( Integer page ) {}

	@Test
	public void aFieldThatDoesntConvertGoesToWhenInvalid() {
		final ERXRouter router = new ERXRouter();
		final List<String> reasons = new ArrayList<>();
		final WOActionResults asked = () -> null;
		final Route<Listing> listing = router.application().route( "/list", Listing.class, ( l, invocation ) -> null );

		assertEquals( RouteHandler.DECLINED, listing.handle( invocation( "/list", Map.of( "page", "abc" ), router ) ) );

		listing.whenInvalid( ( invocation, reason ) -> {
			reasons.add( reason.getMessage() );
			return asked;
		} );

		assertEquals( asked, listing.handle( invocation( "/list", Map.of( "page", "abc" ), router ) ) );
		assertTrue( reasons.getFirst().contains( "'page' is 'abc'" ) );
	}

	public enum Genre {
		novel,
		poetry,
		drama
	}

	public record Shelf( String club, List<Genre> genres, List<Integer> years, String q ) {}

	@Test
	public void aListTakesARepeatedParameter() {
		final ERXRouter router = new ERXRouter();
		final List<Shelf> got = new ArrayList<>();
		final Route<Shelf> shelf = router.application().route( "/{club}/shelf", Shelf.class, ( s, invocation ) -> {
			got.add( s );
			return () -> null;
		} );

		final RouteInvocation invocation = new RouteInvocation( "/acme/shelf", invocationWithLists( "/acme/shelf", Map.of( "genres", List.of( "novel", "drama" ), "years", List.of( "1934", "" ) ), router ).request(), Map.of( "club", "acme" ), router.converters() );
		shelf.handle( invocation );
		assertEquals( new Shelf( "acme", List.of( Genre.novel, Genre.drama ), List.of( 1934 ), null ), got.getFirst() );

		// None is an empty list
		shelf.handle( new RouteInvocation( "/acme/shelf", invocationWithLists( "/acme/shelf", Map.of(), router ).request(), Map.of( "club", "acme" ), router.converters() ) );
		assertEquals( List.of(), got.get( 1 ).genres() );

		// A value that isn't one declines, as does a second value for one
		assertEquals( RouteHandler.DECLINED, shelf.handle( new RouteInvocation( "/acme/shelf", invocationWithLists( "/acme/shelf", Map.of( "genres", List.of( "novel", "opera" ) ), router ).request(), Map.of( "club", "acme" ), router.converters() ) ) );
		assertEquals( RouteHandler.DECLINED, shelf.handle( new RouteInvocation( "/acme/shelf", invocationWithLists( "/acme/shelf", Map.of( "q", List.of( "a", "b" ) ), router ).request(), Map.of( "club", "acme" ), router.converters() ) ) );
	}

	@Test
	public void aListsLinkTakesOnlyItsTypesValues() {
		final Route<Shelf> shelf = new ERXRouter().application().route( "/{club}/shelf", Shelf.class, ( s, invocation ) -> null );

		// By name: a collection, or one value
		assertThrows( IllegalArgumentException.class, () -> shelf.url( Map.of( "club", "acme", "genres", List.of( "novel", "opera" ) ), null ) );
		assertThrows( IllegalArgumentException.class, () -> shelf.url( Map.of( "club", "acme", "q", List.of( "a", "b" ) ), null ) );
	}

	public record ListInPath( List<String> tags ) {}

	public record RawList( @SuppressWarnings("rawtypes") List tags ) {}

	public record ListOfObjects( List<Thread> threads ) {}

	@Test
	public void aListIsAQueryParameterOfAConvertibleType() {
		final RouteGroup routes = new ERXRouter().application();

		assertThrows( IllegalArgumentException.class, () -> routes.route( "/tags/{tags}", ListInPath.class, ( r, invocation ) -> null ) );
		assertThrows( IllegalArgumentException.class, () -> routes.route( "/raw", RawList.class, ( r, invocation ) -> null ) );
		assertThrows( IllegalArgumentException.class, () -> routes.route( "/threads", ListOfObjects.class, ( r, invocation ) -> null ) );
	}

	@Test
	public void aGroupsBehaviorsReachItsRoutes() {
		final ERXRouter router = new ERXRouter();
		final RouteGroup api = router.application().group( "/api", CrossSite.ALLOWED ).named( "api" );
		api.map( "/hooks", NOTHING, Method.POST );
		router.table( "plugin" ).join( "api" ).map( "/more", NOTHING, Method.POST );

		assertEquals( List.of( CrossSite.ALLOWED ), api.allOptions().stream().filter( CrossSite.class::isInstance ).toList() );
		assertTrue( router.application().group( "/other" ).allOptions().stream().noneMatch( CrossSite.class::isInstance ) );
		assertTrue( router.table( "plugin" ).join( "api" ).allOptions().contains( CrossSite.ALLOWED ) );
		assertTrue( entry( router, "/api/hooks" ).crossSiteAllowed() );
	}
}
