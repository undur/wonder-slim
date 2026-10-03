package bookclubs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

import bookclubs.components.BaseComponent;
import bookclubs.components.BookListPage;
import bookclubs.components.BookPage;
import bookclubs.components.ClubPage;
import bookclubs.components.Main;
import bookclubs.components.MemberPage;
import bookclubs.components.NewBookPage;
import bookclubs.components.TextPage;
import bookclubs.data.Library;
import bookclubs.data.Library.Book;
import bookclubs.data.Library.Club;
import bookclubs.data.Library.Sort;
import er.routing.ERXRouter;
import er.routing.CrossOrigin;
import er.routing.CrossSite;
import er.routing.Fields;
import er.routing.Routable;
import er.routing.RouteGroup;
import er.routing.RouteInvocation;
import er.routing.core.Converters.Converter;
import er.routing.core.Host;
import er.routing.core.Method;
import er.routing.core.TrailingSlash;
import er.routing.RouteHandler;

/**
 * Every route of the application: their records and what they do, and the declaration giving the routes ({@link Routes})
 * their patterns.
 *
 * <ul>
 * <li>{@code localhost:1300} lists the clubs.</li>
 * <li>Each club is on its own host, {@code {club}.localhost:1300}: browsers send any name ending in {@code .localhost}
 * to this machine.</li>
 * </ul>
 */
public class BookclubRoutes {

	/**
	 * The host every club's routes answer
	 */
	public static final Host CLUB_HOST = Host.of( "{club}.@" );

	// ---- Records, for the routes with query parameters or form fields ----
	// A club's routes answer its host, {club}.localhost. The host's {club} is a Club, converted by the converter
	// registered for clubs, so an unknown club's host declines before any route is invoked. The records leave it out:
	// it's the group's, and an invocation has it (club( invocation )), as a link takes it from the request.

	/**
	 * The books, sorted, a page at a time, of the authors chosen: query parameters with types, a repeated one a list
	 */
	public record Books( Sort sort, Integer page, List<String> author ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			final BookListPage list = invocation.page( BookListPage.class ).club( club( invocation ) );
			list.sort = sort == null ? Sort.title : sort;
			list.page = page == null || page < 1 ? 1 : page;
			list.authors = author;
			return list;
		}
	}

	/**
	 * The form's post: the form's fields are the record's components, as query parameters are
	 */
	public record CreateBook( String title, String author, Integer year ) {}

	public record DeleteBook( Book book ) {}

	public record MemberView( String handle ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			final Club club = club( invocation );
			return Library.member( club.id(), handle ).<WOActionResults>map( m -> {
				final MemberPage page = invocation.page( MemberPage.class ).club( club );
				page.member = m;
				return page;
			} ).orElse( RouteHandler.DECLINED );
		}
	}

	/**
	 * A club's page by name ({@code /history}): one it doesn't have declines, and the request passes on to the next
	 * matching route, the club's catch-all
	 */
	public record ClubText( String name ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			final Club club = club( invocation );
			return Library.page( club.id(), name ).<WOActionResults>map( text -> TextPage.create( invocation.context(), club, capitalized( name ), text ) ).orElse( RouteHandler.DECLINED );
		}
	}

	/**
	 * Searching the club's books: the record requires a query, and a request without one gets the route's own answer
	 * ({@code whenInvalid}) instead of a 404
	 */
	public record Search( String q ) implements Routable {

		public Search {
			Objects.requireNonNull( q, "q" );
		}

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			final Club club = club( invocation );
			final String needle = q.toLowerCase();
			final List<String> found = Library.books( club.id(), Sort.title ).stream().filter( b -> b.title().toLowerCase().contains( needle ) || b.author().toLowerCase().contains( needle ) ).map( Book::title ).toList();
			return TextPage.create( invocation.context(), club, "Search", "%d found for '%s': %s".formatted( found.size(), q, found ) );
		}
	}

	/**
	 * @return The club whose host the request is to
	 */
	static Club club( final RouteInvocation invocation ) {
		return invocation.parameter( "club", Club.class );
	}

	/**
	 * Gives the routes ({@link Routes}) their patterns: at startup, and again when this class, the routes or a route's
	 * record changes (in development)
	 */
	public static void declare( final ERXRouter router ) {

		// Clubs and books are route parameters: a club in a URL is its id (a host's first label), a book its number
		router.converters().register( Club.class, Converter.of( id -> Library.club( id ).orElse( null ), Club::id ) );
		// A book is found in the club the host names (the converter's scope has the request's other parameters), so
		// another club's book isn't there: /books/3 on kronan's host declines
		router.converters().register( Book.class, Converter.scoped( ( id, scope ) -> Library.book( Integer.parseInt( id ) ).filter( book -> scope.parameter( "club" ) == null || book.club().equals( scope.parameter( "club" ) ) ).orElse( null ), book -> String.valueOf( book.id() ) ) );

		// The application's table comes first, so its routes override a plugin's
		final RouteGroup routes = router.application();

		// The landing page answers localhost only: on a club's host, / is the club's home
		routes.map( "/", Routes.home, Main.class, Host.of( "@" ) );

		// A group by host alone: every route in it answers {club}.localhost, with "club" a parameter
		final RouteGroup club = routes.group( "", CLUB_HOST ).parameter( "club", Club.class ).named( "club" );

		// Pages: a route parameter ({club}) is set on the page by name
		club.map( "/", Routes.clubHome, ClubPage.class );
		club.route( "/books/", Routes.books, TrailingSlash.REDIRECT );
		club.map( "/books/new", Routes.newBook, NewBookPage.class );
		club.map( "/books/{book}", Routes.book, BookPage.class );

		// An old URL, redirected to the book's for good
		club.redirect( "/book/{book}", Routes.book );
		club.route( "/members/{handle}", Routes.member );
		club.route( "/search", Routes.search ).whenInvalid( ( invocation, reason ) -> TextPage.create( invocation.context(), club( invocation ), "Search", "What are you searching for? Add ?q=… (%s)".formatted( reason.getMessage() ) ).status( 400 ) );
		club.route( "/{name}", Routes.clubText );
		club.map( "/about", Routes.about, ri -> TextPage.create( ri.context(), club( ri ), "About", "%s: %s. (This is the application's /about, overriding the guestbook plugin's.)".formatted( club( ri ).name(), club( ri ).motto() ) ) );

		// Methods: a form posts here, and its fields' conversion errors are reported to the action (Fields.REPORTED). Other methods at /books are redirected to the list at /books/.
		club.route( "/books", Routes.createBook, BookclubRoutes::createBook, Method.POST, Fields.REPORTED );
		club.route( "/books/{book}/delete", Routes.deleteBook, BookclubRoutes::deleteBook, Method.POST );

		// Trailing slashes: /rules redirects to /rules/ (and /books to /books/, above)
		club.map( "/rules/", Routes.rules, ri -> text( 200, "Rules of %s: read the book.".formatted( ri.parameter( "club" ) ) ), TrailingSlash.REDIRECT );

		// A wildcard, named so links can give it: everything beneath /files/ (and /files, redirected there)
		club.map( "/files/{path*}", Routes.file, ri -> text( 200, "The file %s of %s".formatted( ri.parameter( "path" ), ri.parameter( "club" ) ) ), TrailingSlash.REDIRECT );

		// A parameter within a path element: the book as JSON
		club.map( "/books/{book}.json", ri -> jsonResponse( 200, json( ri.parameter( "book", Book.class ) ) ) );

		// The club's catch-all, last in precedence: what nothing else answered, a declined page included
		club.map( "/*", ri -> {
			final Club c = ri.parameter( "club", Club.class );
			return TextPage.create( ri.context(), c, "Not here", "%s has no page at %s.".formatted( c.name(), ri.url() ) ).status( 404 );
		} );

		// Nested groups with filters: the admin filter runs, then the danger filter
		final RouteGroup adminGroup = club.group( "/admin" ).named( "admin" );
		adminGroup.wrap( ( invocation, next ) -> {
			recordFilter( invocation, "admin" );
			return "letmein".equals( invocation.request().stringFormValueForKey( "key" ) ) ? next.handle( invocation ) : text( 403, "Admins only: add ?key=letmein" );
		} );
		adminGroup.map( "/", Routes.admin, ri -> TextPage.create( ri.context(), club( ri ), "Admin", "Filters run, outermost first: " + filtersRun( ri ) ) );

		final RouteGroup dangerGroup = adminGroup.group( "/danger" );
		dangerGroup.wrap( ( invocation, next ) -> {
			recordFilter( invocation, "danger" );
			return next.handle( invocation );
		} );
		dangerGroup.map( "/", Routes.danger, ri -> TextPage.create( ri.context(), club( ri ), "The danger zone", "Filters run, outermost first: " + filtersRun( ri ) ) );
		dangerGroup.map( "/reset", Routes.reset, BookclubRoutes::reset, Method.POST );

		// A JSON API: strict about trailing slashes, and about methods (anything else is 405, with Allow)
		// An API takes posts from other programs, which may send an Origin (a page's form posting to it, a client
		// forwarding one), so its routes allow requests from other sites. A browser's script on another site is CORS's
		// business, which this doesn't touch.
		// A partner's scripts call it from their own site (CORS)
		final RouteGroup api = club.group( "/api", TrailingSlash.STRICT, CrossSite.ALLOWED, CrossOrigin.allow( "https://partner.example" ) );
		api.map( "/books", BookclubRoutes::apiBooks, Method.GET );
		api.map( "/books", BookclubRoutes::apiCreateBook, Method.POST );
		api.map( "/books/{book}", BookclubRoutes::apiBook, Method.GET );
		api.map( "/books/{book}", BookclubRoutes::apiDeleteBook, Method.DELETE );


	}

	// ---- Actions for the data-only records ----

	/**
	 * Post, redirect, get: a new book, then a redirect to its page
	 */
	private static WOActionResults createBook( final CreateBook form, final RouteInvocation invocation ) {

		// The form's fields are checked here, where the form can be shown again with what's wrong
		final String error = invocation.conversionErrors().containsKey( "year" ) ? "The year is a number, not '%s'".formatted( invocation.conversionErrors().get( "year" ) )
				: form.title() == null || form.title().isBlank() || form.author() == null || form.author().isBlank() ? "A book has a title and an author" : null;

		if( error != null ) {
			final NewBookPage page = invocation.page( NewBookPage.class ).club( club( invocation ) );
			page.error = error;
			return page;
		}

		final Club club = club( invocation );
		final Book book = Library.book( club.id(), form.title().strip(), form.author().strip(), form.year() );
		return Routes.book.redirect( Map.of( "book", book ), invocation.context() );
	}

	private static WOActionResults deleteBook( final DeleteBook delete, final RouteInvocation invocation ) {

		if( !Library.removeBook( club( invocation ).id(), delete.book().id() ) ) {
			return RouteHandler.DECLINED;
		}

		return Routes.books.redirect( new Books( null, null, List.of() ), invocation.context() );
	}

	private static WOActionResults reset( final RouteInvocation invocation ) {
		Library.reset();
		return Routes.clubHome.redirect( invocation.context() );
	}

	// ---- The JSON API ----

	private static WOActionResults apiBooks( final RouteInvocation invocation ) {
		final List<String> books = new ArrayList<>();
		Library.books( invocation.parameter( "club" ), Sort.title ).forEach( b -> books.add( json( b ) ) );
		return jsonResponse( 200, "[" + String.join( ",", books ) + "]" );
	}

	/**
	 * A plain route's parameter converted to its type: what doesn't convert declines, without code here
	 */
	private static WOActionResults apiBook( final RouteInvocation invocation ) {
		return jsonResponse( 200, json( invocation.parameter( "book", Book.class ) ) );
	}

	private static WOActionResults apiCreateBook( final RouteInvocation invocation ) {
		final String title = invocation.request().stringFormValueForKey( "title" );
		final String author = invocation.request().stringFormValueForKey( "author" );

		if( title == null || author == null ) {
			return jsonResponse( 400, "{\"error\":\"title and author are required\"}" );
		}

		return jsonResponse( 201, json( Library.book( invocation.parameter( "club" ), title, author, null ) ) );
	}

	private static WOActionResults apiDeleteBook( final RouteInvocation invocation ) {
		final Book book = invocation.parameter( "book", Book.class );
		return Library.removeBook( invocation.parameter( "club" ), book.id() ) ? text( 204, "" ) : RouteHandler.DECLINED;
	}

	// ---- Helpers ----

	private static final String FILTERS_KEY = "bookclubs.filters";

	@SuppressWarnings("unchecked")
	private static void recordFilter( final RouteInvocation invocation, final String name ) {
		List<String> filters = (List<String>)invocation.request().userInfoForKey( FILTERS_KEY );

		if( filters == null ) {
			filters = new ArrayList<>();
			invocation.request().setUserInfoForKey( filters, FILTERS_KEY );
		}

		filters.add( name );
	}

	private static String filtersRun( final RouteInvocation invocation ) {
		return String.valueOf( invocation.request().userInfoForKey( FILTERS_KEY ) );
	}

	static WOResponse text( final int status, final String content ) {
		final WOResponse response = new WOResponse();
		response.setStatus( status );
		response.setHeader( "text/plain; charset=utf-8", "content-type" );
		response.setContent( content );
		return response;
	}

	private static WOResponse jsonResponse( final int status, final String json ) {
		final WOResponse response = text( status, json );
		response.setHeader( "application/json", "content-type" );
		return response;
	}

	private static String json( final Book book ) {
		return "{\"id\":%d,\"title\":%s,\"author\":%s,\"year\":%s}".formatted( book.id(), quoted( book.title() ), quoted( book.author() ), book.year() );
	}

	private static String quoted( final String s ) {
		return "\"" + s.replace( "\\", "\\\\" ).replace( "\"", "\\\"" ) + "\"";
	}

	private static String capitalized( final String s ) {
		return s.isEmpty() ? s : Character.toUpperCase( s.charAt( 0 ) ) + s.substring( 1 );
	}
}
