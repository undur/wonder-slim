package er.extensions.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WOActionResults;

import er.routing.conversion.Converters;
import er.routing.options.CrossSite;
import er.routing.options.Fields;
import er.routing.options.Host;
import er.routing.options.Method;
import er.routing.options.RouteOption;
import er.routing.options.TrailingSlash;



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
	public void theApplicationsOptionsReachEveryRouteItDeclares() {
		final ERXRouter router = new ERXRouter();
		final ApplicationRoutes routes = router.application( TrailingSlash.REDIRECT );
		routes.map( "/books/", NOTHING );
		routes.group( "/api" ).map( "/items", NOTHING );
		routes.map( "/strict", NOTHING, TrailingSlash.STRICT );

		assertEquals( TrailingSlash.REDIRECT, entry( router, "/books/" ).trailingSlash() );
		assertEquals( TrailingSlash.REDIRECT, entry( router, "/api/items" ).trailingSlash() );
		assertEquals( TrailingSlash.STRICT, entry( router, "/strict" ).trailingSlash() );

		// They're still the application's routes: what only those take, they take
		routes.notFound( invocation -> null );
		routes.fallback( invocation -> RouteHandler.DECLINED );
	}

	public record Bookcase( String name ) {}

	@Test
	public void aConvertersTypeHasOneOwner() {
		final ERXRouter router = new ERXRouter();
		final Converters.Converter<Bookcase> bookcases = Converters.Converter.of( Bookcase::new, Bookcase::name );

		// A plugin registers its own types, and registering one again is its own business
		router.declaringAs( "the table library", () -> {
			router.table( "library" ).converters().register( Bookcase.class, bookcases );
			router.table( "library" ).converters().register( Bookcase.class, bookcases );
		} );

		// Another owner can't take the type over, and a plugin can't replace a built-in converter
		final IllegalStateException taken = assertThrows( IllegalStateException.class, () -> router.declaringAs( "the application", () -> router.application().converters().register( Bookcase.class, bookcases ) ) );
		assertTrue( taken.getMessage().contains( "the table library" ), taken.getMessage() );
		assertThrows( IllegalStateException.class, () -> router.declaringAs( "the table guestbook", () -> router.table( "guestbook" ).converters().register( String.class, Converters.Converter.of( s -> s, s -> s ) ) ) );

		// The application may
		router.declaringAs( "the application", () -> router.application().converters().register( Integer.class, Converters.Converter.of( Integer::valueOf, String::valueOf ) ) );
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
		final NullPointerException e = assertThrows( NullPointerException.class, () -> new ERXRouter().application().map( "/p", NOTHING, (er.routing.options.RouteOption)null ) );

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

		routes.map( "/items/{id}", Item.class, ( item, invocation ) -> null );

		// A pattern parameter the record lacks, a type without a converter, a query parameter that can't be absent
		assertThrows( IllegalArgumentException.class, () -> routes.map( "/items/{itemId}/more", Item.class, ( item, invocation ) -> null ) );
		assertThrows( IllegalArgumentException.class, () -> routes.map( "/threads", WithAnObject.class, ( item, invocation ) -> null ) );
		assertThrows( IllegalArgumentException.class, () -> routes.map( "/pages", PrimitiveQuery.class, ( item, invocation ) -> null ) );
	}

	public record Cartoon( Integer cartoon ) {}

	public record PrimitiveCartoon( int cartoon ) {}

	@Test
	public void anOptionalParametersComponentIsNullWhenAbsent() {
		final ERXRouter router = new ERXRouter();
		final java.util.concurrent.atomic.AtomicReference<Cartoon> received = new java.util.concurrent.atomic.AtomicReference<>();
		final Route<Cartoon> cartoons = router.application().map( "/cartoons/{cartoon?}", Cartoon.class, ( cartoon, invocation ) -> {
			received.set( cartoon );
			return null;
		} );

		cartoons.binding().handle( new RouteInvocation( "/cartoons", null, Map.of(), router.converters(), cartoons ) );
		assertEquals( new Cartoon( null ), received.get() );

		cartoons.binding().handle( new RouteInvocation( "/cartoons/7", null, Map.of( "cartoon", "7" ), router.converters(), cartoons ) );
		assertEquals( new Cartoon( 7 ), received.get() );

		// It can be absent, so a primitive can't hold it
		assertThrows( IllegalArgumentException.class, () -> router.application().map( "/strips/{cartoon?}", PrimitiveCartoon.class, ( cartoon, invocation ) -> null ) );
	}

	@Test
	public void aLinksValuesAreChecked() {
		final Route<Item> item = new ERXRouter().application().map( "/items/{id}", Item.class, ( i, invocation ) -> null );

		// Unknown parameter, missing route parameter, wrong type, text that isn't one of the type
		assertThrows( IllegalArgumentException.class, () -> item.url( Map.of( "id", 1, "colour", "red" ), null ) );
		assertThrows( IllegalArgumentException.class, () -> item.url( Map.of( "tab", "history" ), null ) );
		assertThrows( IllegalArgumentException.class, () -> item.url( Map.of( "id", 1.5 ), null ) );
		assertThrows( IllegalArgumentException.class, () -> item.url( Map.of( "id", "abc" ), null ) );
	}

	@Test
	public void routesAreDescribed() {
		final ERXRouter router = new ERXRouter();
		final Route<Search> search = router.application().map( "/search", Search.class, ( s, invocation ) -> null );
		final PlainRoute about = router.table( "plugin" ).map( "/about", NOTHING, Method.GET );

		final RouteDescription searchDescription = router.routes().stream().filter( d -> d.pattern().equals( "/search" ) ).findFirst().orElseThrow();
		assertEquals( search, searchDescription.route() );
		assertEquals( Search.class, searchDescription.parametersClass() );

		assertEquals( CrossSite.SAME_ORIGIN, searchDescription.crossSite() );
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

		assertTrue( forms.map( "/book", Form.class, ( f, invocation ) -> null, Method.POST ).reportsFields() );
		assertFalse( router.application().map( "/plain-form", Form.class, ( f, invocation ) -> null ).reportsFields() );

		// A plain route reads its own fields, so the option means nothing there
		assertThrows( IllegalArgumentException.class, () -> router.application().map( "/p", NOTHING, Fields.REPORTED ) );
	}

	@Test
	public void aTextParameterTakesOnlyConvertibleValues() {
		final Route<Member> member = new ERXRouter().application().map( "/members/{handle}", Member.class, ( m, invocation ) -> null );

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
		final Route<Query> search = router.application().map( "/search", Query.class, ( query, invocation ) -> found );

		// Without whenInvalid, a record that refuses its values declines
		assertEquals( RouteHandler.DECLINED, search.binding().handle( invocation( "/search", Map.of(), router ) ) );

		final WOActionResults asked = () -> null;
		assertEquals( search, search.whenInvalid( ( invocation, reason ) -> {
			reasons.add( reason.getMessage() );
			return asked;
		} ) );

		assertEquals( asked, search.binding().handle( invocation( "/search", Map.of(), router ) ) );
		assertEquals( List.of( "Absent: [q]" ), reasons );
		assertEquals( found, search.binding().handle( invocation( "/search", Map.of( "q", "dune" ), router ) ) );

		// An empty value is absent, for text too (?q=)
		assertEquals( asked, search.binding().handle( invocation( "/search", Map.of( "q", "" ), router ) ) );
	}

	public record Listing( Integer page ) {}

	@Test
	public void aFieldThatDoesntConvertGoesToWhenInvalid() {
		final ERXRouter router = new ERXRouter();
		final List<String> reasons = new ArrayList<>();
		final WOActionResults asked = () -> null;
		final Route<Listing> listing = router.application().map( "/list", Listing.class, ( l, invocation ) -> null );

		assertEquals( RouteHandler.DECLINED, listing.binding().handle( invocation( "/list", Map.of( "page", "abc" ), router ) ) );

		listing.whenInvalid( ( invocation, reason ) -> {
			reasons.add( reason.getMessage() );
			return asked;
		} );

		assertEquals( asked, listing.binding().handle( invocation( "/list", Map.of( "page", "abc" ), router ) ) );
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
		final Route<Shelf> shelf = router.application().map( "/{club}/shelf", Shelf.class, ( s, invocation ) -> {
			got.add( s );
			return () -> null;
		} );

		final RouteInvocation invocation = new RouteInvocation( "/acme/shelf", invocationWithLists( "/acme/shelf", Map.of( "genres", List.of( "novel", "drama" ), "years", List.of( "1934", "" ) ), router ).request(), Map.of( "club", "acme" ), router.converters() );
		shelf.binding().handle( invocation );
		assertEquals( new Shelf( "acme", List.of( Genre.novel, Genre.drama ), List.of( 1934 ), null ), got.getFirst() );

		// None is an empty list
		shelf.binding().handle( new RouteInvocation( "/acme/shelf", invocationWithLists( "/acme/shelf", Map.of(), router ).request(), Map.of( "club", "acme" ), router.converters() ) );
		assertEquals( List.of(), got.get( 1 ).genres() );

		// A value that isn't one declines, as does a second value for one
		assertEquals( RouteHandler.DECLINED, shelf.binding().handle( new RouteInvocation( "/acme/shelf", invocationWithLists( "/acme/shelf", Map.of( "genres", List.of( "novel", "opera" ) ), router ).request(), Map.of( "club", "acme" ), router.converters() ) ) );
		assertEquals( RouteHandler.DECLINED, shelf.binding().handle( new RouteInvocation( "/acme/shelf", invocationWithLists( "/acme/shelf", Map.of( "q", List.of( "a", "b" ) ), router ).request(), Map.of( "club", "acme" ), router.converters() ) ) );
	}

	@Test
	public void aListsLinkTakesOnlyItsTypesValues() {
		final Route<Shelf> shelf = new ERXRouter().application().map( "/{club}/shelf", Shelf.class, ( s, invocation ) -> null );

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

		assertThrows( IllegalArgumentException.class, () -> routes.map( "/tags/{tags}", ListInPath.class, ( r, invocation ) -> null ) );
		assertThrows( IllegalArgumentException.class, () -> routes.map( "/raw", RawList.class, ( r, invocation ) -> null ) );
		assertThrows( IllegalArgumentException.class, () -> routes.map( "/threads", ListOfObjects.class, ( r, invocation ) -> null ) );
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
		assertEquals( CrossSite.ALLOWED, entry( router, "/api/hooks" ).crossSite() );

		// A route sets its own, back to the default
		api.map( "/strict", NOTHING, Method.POST, CrossSite.SAME_ORIGIN );
		assertEquals( CrossSite.SAME_ORIGIN, entry( router, "/api/strict" ).crossSite() );

		final RouteGroup forms = router.application().group( "/forms", Fields.REPORTED );
		assertFalse( forms.map( "/plain", Form.class, ( f, invocation ) -> null, Fields.DECLINED ).reportsFields() );
	}

	@Test
	public void aHostRelativeToTheDomainIsResolved() {
		final ERXRouter router = new ERXRouter();
		router.application().group( "", Host.of( "{club}.@" ) ).map( "/books", NOTHING );

		// Without a public address, the domain is localhost
		assertEquals( "{club}.localhost", ((Host)entry( router, "/books" ).conditions().getFirst()).pattern() );
	}

	public static class ItemPage extends com.webobjects.appserver.WOComponent {
		public Integer id;

		public ItemPage( final com.webobjects.appserver.WOContext context ) {
			super( context );
		}

		public ItemPage tenant( final String tenant ) {
			return this;
		}
	}

	@Test
	public void aPageRouteSetsItsParametersOnThePage() {
		final RouteGroup tenant = new ERXRouter().application().group( "", Host.of( "{tenant}.example.com" ) );

		// {id} on the field, {tenant} through the fluent setter
		tenant.map( "/items/{id}", ItemPage.class );

		// A parameter the page has no member for is refused when declared
		final IllegalArgumentException e = assertThrows( IllegalArgumentException.class, () -> tenant.map( "/items/{id}/{part}", ItemPage.class ) );
		assertTrue( e.getMessage().contains( "{part}" ), e.getMessage() );
	}

	public record Order( Integer id, String note ) {}

	@Test
	public void aRecordLeavesOutTheGroupsHostParameters() {
		final RouteGroup tenant = new ERXRouter().application().group( "", Host.of( "{tenant}.example.com" ) );
		final Route<Order> order = tenant.map( "/orders/{id}", Order.class, ( o, invocation ) -> null );

		// By name, the host's parameter is given; outside a request a record's URL can't take it from one
		assertThrows( IllegalArgumentException.class, () -> order.url( Map.of( "id", 7 ), null ) );
		final IllegalArgumentException e = assertThrows( IllegalArgumentException.class, () -> order.completeURL( new Order( 7, null ) ) );
		assertTrue( e.getMessage().contains( "tenant" ), e.getMessage() );

		// A path parameter is the record's
		assertThrows( IllegalArgumentException.class, () -> tenant.map( "/orders/{id}/{line}", Order.class, ( o, invocation ) -> null ) );
	}

	@Test
	public void aPlainRouteReadsTypedQueryValues() {
		final ERXRouter router = new ERXRouter();
		final RouteInvocation invocation = invocationWithLists( "/list", Map.of( "page", List.of( "3" ), "bad", List.of( "x" ), "twice", List.of( "1", "2" ), "empty", List.of( "" ) ), router );

		assertEquals( 3, invocation.query( "page", Integer.class ) );
		assertNull( invocation.query( "absent", Integer.class ) );
		assertNull( invocation.query( "empty", Integer.class ) );
		assertThrows( Declined.class, () -> invocation.query( "bad", Integer.class ) );
		assertThrows( Declined.class, () -> invocation.query( "twice", Integer.class ) );
	}

	public record Shelf2( Integer n ) {}

	@Test
	public void aConverterSeesTheRequestsScope() {
		final ERXRouter router = new ERXRouter();
		final List<String> clubsAsked = new ArrayList<>();
		router.converters().register( Shelf2.class, er.routing.conversion.Converters.Converter.scoped( ( text, scope ) -> {
			clubsAsked.add( scope.parameter( "club" ) );
			return scope.get( String.class ) == null && "acme".equals( scope.parameter( "club" ) ) ? new Shelf2( Integer.valueOf( text ) ) : null;
		}, shelf -> String.valueOf( shelf.n() ) ) );

		final RouteInvocation acme = new RouteInvocation( "/shelves/2", invocationWithLists( "/shelves/2", Map.of(), router ).request(), Map.of( "club", "acme", "shelf", "2" ), router.converters() );
		final RouteInvocation kronan = new RouteInvocation( "/shelves/2", invocationWithLists( "/shelves/2", Map.of(), router ).request(), Map.of( "club", "kronan", "shelf", "2" ), router.converters() );

		// Converted once per request, and in the request's club only
		assertEquals( new Shelf2( 2 ), acme.parameter( "shelf", Shelf2.class ) );
		assertEquals( new Shelf2( 2 ), acme.parameter( "shelf", Shelf2.class ) );
		assertThrows( Declined.class, () -> kronan.parameter( "shelf", Shelf2.class ) );
		assertEquals( List.of( "acme", "kronan" ), clubsAsked );

		// The application provides objects for the scope
		router.converters().provide( StringBuilder.class, scope -> new StringBuilder( scope.parameter( "club" ) ) );
		assertEquals( "acme", acme.get( StringBuilder.class ).toString() );
	}

	public record Buggy( String q ) {

		public Buggy {
			q.length();
		}
	}

	@Test
	public void aBugsNullPointerExceptionIsntARefusal() {
		final ERXRouter router = new ERXRouter();
		final Route<Buggy> buggy = router.application().map( "/buggy", Buggy.class, ( b, invocation ) -> null );
		buggy.whenInvalid( ( invocation, reason ) -> () -> null );

		// Not "absent": a 500, though the route answers bad input itself
		assertThrows( NullPointerException.class, () -> buggy.binding().handle( invocation( "/buggy", Map.of(), router ) ) );
	}

	@Test
	public void anInvocationKnowsItsRoute() {
		final ERXRouter router = new ERXRouter();
		final PlainRoute about = router.application().map( "/about", NOTHING );
		final RouteInvocation invocation = new RouteInvocation( "/about", null, Map.of(), router.converters(), about );

		assertSame( about, invocation.route() );
	}

	@Test
	public void anInvocationsRouteKnowsItsWholePattern() {
		final ERXRouter router = new ERXRouter();
		final PlainRoute books = router.application().group( "/library" ).map( "/books/*", NOTHING );
		final RouteInvocation invocation = new RouteInvocation( "/library/books/a/b", null, Map.of(), router.converters(), books );

		assertEquals( "/library/books/*", invocation.route().pattern() );
	}

	public static class OverloadedPage extends com.webobjects.appserver.WOComponent {

		public OverloadedPage( final com.webobjects.appserver.WOContext context ) {
			super( context );
		}

		public void setId( final Integer id ) {}

		public void id( final String id ) {}
	}

	@Test
	public void aPageWithOverloadsForAParameterIsRefused() {
		final IllegalArgumentException e = assertThrows( IllegalArgumentException.class, () -> new ERXRouter().application().map( "/items/{id}", OverloadedPage.class ) );
		assertTrue( e.getMessage().contains( "several methods" ), e.getMessage() );
	}

	@Test
	public void anObjectProvidedIsMadeOncePerRequest() {
		final ERXRouter router = new ERXRouter();
		final List<Integer> made = new ArrayList<>();
		router.converters().provide( StringBuilder.class, scope -> {
			made.add( 1 );
			return new StringBuilder();
		} );

		final com.webobjects.appserver.WORequest request = invocationWithLists( "/x", Map.of(), router ).request();
		final RouteInvocation first = new RouteInvocation( "/x", request, Map.of(), router.converters() );
		final RouteInvocation second = new RouteInvocation( "/x", request, Map.of(), router.converters() );

		assertSame( first.get( StringBuilder.class ), second.get( StringBuilder.class ) );
		assertEquals( 1, made.size() );
	}

	public record Org( String id ) {}

	public record Project( Org org, Integer project ) {}

	public record OrphanProject( Integer project ) {}

	public static class ProjectPage extends com.webobjects.appserver.WOComponent {
		public Integer project;

		public ProjectPage( final com.webobjects.appserver.WOContext context ) {
			super( context );
		}
	}

	@Test
	public void aGroupsParameter() {
		final ERXRouter router = new ERXRouter();
		router.converters().register( Org.class, er.routing.conversion.Converters.Converter.of( id -> id.equals( "acme" ) ? new Org( id ) : null, Org::id ) );
		final RouteGroup org = router.application().group( "/orgs/{org}" ).parameter( "org", Org.class );

		// A record has it, as it has the path's other parameters, so a link gives it; a page may leave it out
		final Route<Project> project = org.map( "/projects/{project}", Project.class, ( p, invocation ) -> () -> null );
		org.map( "/projects/{project}/page", ProjectPage.class );
		final IllegalArgumentException orphan = assertThrows( IllegalArgumentException.class, () -> org.map( "/orphans/{project}", OrphanProject.class, ( p, invocation ) -> () -> null ) );
		assertTrue( orphan.getMessage().contains( "{org}" ), orphan.getMessage() );

		// An unknown one declines every route of the group, before its filters
		final List<String> filtered = new ArrayList<>();
		org.wrap( ( invocation, next ) -> {
			filtered.add( "filter" );
			return next.handle( invocation );
		} );
		final RouteHandler handler = org.wrapped( invocation -> () -> null );
		assertThrows( Declined.class, () -> handler.handle( new RouteInvocation( "/orgs/nope/projects/1", invocationWithLists( "/x", Map.of(), router ).request(), Map.of( "org", "nope", "project", "1" ), router.converters() ) ) );
		assertTrue( filtered.isEmpty() );

		// A link without it fails, rather than taking one from the request
		assertThrows( IllegalArgumentException.class, () -> project.completeURL( new Project( null, 1 ) ) );

		// Only the prefix's and the host's parameters are a group's
		assertThrows( IllegalArgumentException.class, () -> router.application().group( "/teams" ).parameter( "team", String.class ) );
	}

	public record OrgListing( Org org, String sort, Integer page ) {}

	public static class OrgListingPage extends com.webobjects.appserver.WOComponent {
		public String sort;
		public Integer page;

		public OrgListingPage( final com.webobjects.appserver.WOContext context ) {
			super( context );
		}
	}

	public static class SortlessPage extends com.webobjects.appserver.WOComponent {
		public Integer page;

		public SortlessPage( final com.webobjects.appserver.WOContext context ) {
			super( context );
		}
	}

	public static class MistypedPage extends com.webobjects.appserver.WOComponent {
		public String sort;
		public String page;

		public MistypedPage( final com.webobjects.appserver.WOContext context ) {
			super( context );
		}
	}

	@Test
	public void aTypedRoutesPage() {
		final ERXRouter router = new ERXRouter();
		router.converters().register( Org.class, er.routing.conversion.Converters.Converter.of( Org::new, Org::id ) );
		final RouteGroup org = router.application().group( "/orgs/{org}" ).parameter( "org", Org.class );

		// The page has a member for each component, the group's parameter apart
		org.map( "/listing", OrgListing.class, OrgListingPage.class );

		// A component the page has no member for, or one of another type, is refused
		final IllegalArgumentException missing = assertThrows( IllegalArgumentException.class, () -> org.map( "/sortless", OrgListing.class, SortlessPage.class ) );
		assertTrue( missing.getMessage().contains( "OrgListing.sort" ), missing.getMessage() );
		final IllegalArgumentException mistyped = assertThrows( IllegalArgumentException.class, () -> org.map( "/mistyped", OrgListing.class, MistypedPage.class ) );
		assertTrue( mistyped.getMessage().contains( "OrgListing.page" ), mistyped.getMessage() );

		// A page doesn't see the fields that didn't convert
		assertThrows( IllegalArgumentException.class, () -> org.map( "/reported", OrgListing.class, OrgListingPage.class, Fields.REPORTED ) );
	}

	@Test
	public void aGroupParameterIsDeclaredBeforeTheGroupsRoutes() {
		final RouteGroup org = new ERXRouter().application().group( "/orgs/{org}" );
		org.group( "/teams" ).map( "/", NOTHING );

		assertThrows( IllegalStateException.class, () -> org.parameter( "org", String.class ) );
	}
}
