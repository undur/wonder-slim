package bookclubs;

import java.util.ArrayList;
import java.util.List;

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
import er.routing.PlainRoute;
import er.routing.Route;
import er.routing.Routable;
import er.routing.RouteGroup;
import er.routing.RouteInvocation;
import er.routing.core.Converters.Converter;
import er.routing.core.Host;
import er.routing.core.Method;
import er.routing.core.TrailingSlash;
import er.routing.RouteHandler;

/**
 * Every route of the application. Templates reach the endpoints as {@code $routes} (see {@link bookclubs.components.BaseComponent#routes()}).
 *
 * <ul>
 * <li>{@code localhost:1300} lists the clubs.</li>
 * <li>Each club is on its own host, {@code {club}.localhost:1300}: browsers send any name ending in {@code .localhost}
 * to this machine.</li>
 * </ul>
 */
public class BookclubRoutes {

	private static BookclubRoutes _instance;

	/**
	 * The host every club's routes answer
	 */
	public static final Host CLUB_HOST = Host.of( "{club}.localhost" );

	// ---- The landing page, on localhost ----

	public record Home() implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			return page( Main.class, invocation.context() );
		}
	}

	// ---- A club's pages, on {club}.localhost ----

	public record ClubHome( String club ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			return Library.club( club ).<WOActionResults>map( c -> page( ClubPage.class, invocation.context() ).club( c ) ).orElse( RouteHandler.DECLINED );
		}
	}

	/**
	 * The books, sorted, a page at a time: query parameters with types, absent ones null
	 */
	public record Books( String club, Sort sort, Integer page ) implements Routable {

		public Books {
			if( page != null && page < 1 ) {
				throw new IllegalArgumentException( "Pages start at 1" );
			}
		}

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			return Library.club( club ).<WOActionResults>map( c -> {
				final BookListPage page = BookclubRoutes.page( BookListPage.class, invocation.context() ).club( c );
				page.sort = sort == null ? Sort.title : sort;
				page.page = this.page == null ? 1 : this.page;
				return page;
			} ).orElse( RouteHandler.DECLINED );
		}
	}

	/**
	 * A book, converted from its id in the URL by the converter registered for books. An id that isn't a book's declines
	 * before the route is invoked, and so does another club's book (here), so the club's own not found page answers.
	 */
	public record BookView( String club, Book book ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			final Club c = Library.club( club ).orElse( null );

			if( c == null || !book.club().equals( club ) ) {
				return RouteHandler.DECLINED;
			}

			final BookPage page = page( BookPage.class, invocation.context() ).club( c );
			page.book = book;
			return page;
		}
	}

	/**
	 * The form for a new book. A literal segment ("new") comes before the parameter ({book}), whatever the mapping order.
	 */
	public record NewBook( String club ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			return Library.club( club ).<WOActionResults>map( c -> page( NewBookPage.class, invocation.context() ).club( c ) ).orElse( RouteHandler.DECLINED );
		}
	}

	/**
	 * The form's post: the form's fields are the record's components, as query parameters are
	 */
	public record CreateBook( String club, String title, String author, Integer year ) {}

	public record DeleteBook( String club, Book book ) {}

	public record MemberView( String club, String handle ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			final Club c = Library.club( club ).orElse( null );

			if( c == null ) {
				return RouteHandler.DECLINED;
			}

			return Library.member( club, handle ).<WOActionResults>map( m -> {
				final MemberPage page = page( MemberPage.class, invocation.context() ).club( c );
				page.member = m;
				return page;
			} ).orElse( RouteHandler.DECLINED );
		}
	}

	/**
	 * A club's page by name ({@code /history}): one it doesn't have declines, and the request passes on to the next
	 * matching route, the club's catch-all
	 */
	public record ClubText( String club, String name ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			final Club c = Library.club( club ).orElse( null );
			final String text = Library.page( club, name ).orElse( null );

			if( c == null || text == null ) {
				return RouteHandler.DECLINED;
			}

			return TextPage.create( invocation.context(), c, capitalized( name ), text );
		}
	}

	/**
	 * Overrides the guestbook plugin's {@code /about}: the same route in a higher ranked table
	 */
	public record About( String club ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			return Library.club( club ).<WOActionResults>map( c -> TextPage.create( invocation.context(), c, "About", "%s: %s. (This is the application's /about, overriding the guestbook plugin's.)".formatted( c.name(), c.motto() ) ) ).orElse( RouteHandler.DECLINED );
		}
	}

	public record Admin( String club ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			return Library.club( club ).<WOActionResults>map( c -> TextPage.create( invocation.context(), c, "Admin", "Filters run, outermost first: " + filtersRun( invocation ) ) ).orElse( RouteHandler.DECLINED );
		}
	}

	public record Danger( String club ) implements Routable {

		@Override
		public WOActionResults invoke( final RouteInvocation invocation ) {
			return Library.club( club ).<WOActionResults>map( c -> TextPage.create( invocation.context(), c, "The danger zone", "Filters run, outermost first: " + filtersRun( invocation ) ) ).orElse( RouteHandler.DECLINED );
		}
	}

	public record Reset( String club ) {}

	public final Route<Home> home;
	public final Route<ClubHome> clubHome;
	public final Route<Books> books;
	public final Route<BookView> book;
	public final Route<NewBook> newBook;
	public final Route<CreateBook> createBook;
	public final Route<DeleteBook> deleteBook;
	public final Route<MemberView> member;
	public final Route<ClubText> clubText;
	public final Route<About> about;
	public final Route<Admin> admin;
	public final Route<Danger> danger;
	public final Route<Reset> reset;
	public final PlainRoute rules;
	public final GuestbookPlugin guestbook;

	private BookclubRoutes() {
		final ERXRouter router = ERXRouter.defaultRouter();

		// Books are route parameters: a book in a URL is its id
		router.converters().register( Book.class, Converter.of( id -> Library.book( Integer.parseInt( id ) ).orElse( null ), book -> String.valueOf( book.id() ) ) );

		// The application's table comes first, so its routes override a plugin's
		final RouteGroup routes = router.application();

		// The landing page answers localhost only: on a club's host, / is the club's home
		home = routes.route( "/", Home.class, Host.of( "localhost" ) );

		// A group by host alone: every route in it answers {club}.localhost, with "club" a parameter
		final RouteGroup club = routes.group( "", CLUB_HOST ).named( "club" );

		clubHome = club.route( "/", ClubHome.class );
		books = club.route( "/books/", Books.class, TrailingSlash.REDIRECT );
		newBook = club.route( "/books/new", NewBook.class );
		book = club.route( "/books/{book}", BookView.class );
		member = club.route( "/members/{handle}", MemberView.class );
		clubText = club.route( "/{name}", ClubText.class );
		about = club.route( "/about", About.class );

		// Methods: a form posts here. Other methods at /books are redirected to the list at /books/.
		createBook = club.route( "/books", CreateBook.class, BookclubRoutes::createBook, Method.POST );
		deleteBook = club.route( "/books/{book}/delete", DeleteBook.class, BookclubRoutes::deleteBook, Method.POST );

		// Trailing slashes: /rules redirects to /rules/ (and /books to /books/, above)
		rules = club.map( "/rules/", ri -> text( 200, "Rules of %s: read the book.".formatted( ri.parameter( "club" ) ) ), TrailingSlash.REDIRECT );

		// A wildcard: everything beneath /files/
		club.map( "/files/*", ri -> text( 200, "The file %s of %s".formatted( ri.parameter( "*" ), ri.parameter( "club" ) ) ) );

		// The club's catch-all, last in precedence: what nothing else answered, a declined page included
		club.map( "/*", ri -> Library.club( ri.parameter( "club" ) ).<WOActionResults>map( c -> TextPage.create( ri.context(), c, "Not here", "%s has no page at %s.".formatted( c.name(), ri.url() ) ).status( 404 ) ).orElse( RouteHandler.DECLINED ) );

		// Nested groups with filters: the admin filter runs, then the danger filter
		final RouteGroup adminGroup = club.group( "/admin" ).named( "admin" );
		adminGroup.wrap( ( invocation, next ) -> {
			recordFilter( invocation, "admin" );
			return "letmein".equals( invocation.request().stringFormValueForKey( "key" ) ) ? next.handle( invocation ) : text( 403, "Admins only: add ?key=letmein" );
		} );
		admin = adminGroup.route( "/", Admin.class );

		final RouteGroup dangerGroup = adminGroup.group( "/danger" );
		dangerGroup.wrap( ( invocation, next ) -> {
			recordFilter( invocation, "danger" );
			return next.handle( invocation );
		} );
		danger = dangerGroup.route( "/", Danger.class );
		reset = dangerGroup.route( "/reset", Reset.class, BookclubRoutes::reset, Method.POST );

		// A JSON API: strict about trailing slashes, and about methods (anything else is 405, with Allow)
		final RouteGroup api = club.group( "/api", TrailingSlash.STRICT );
		api.map( "/books", BookclubRoutes::apiBooks, Method.GET );
		api.map( "/books", BookclubRoutes::apiCreateBook, Method.POST );
		api.map( "/books/{book}", BookclubRoutes::apiBook, Method.GET );
		api.map( "/books/{book}", BookclubRoutes::apiDeleteBook, Method.DELETE );

		// A plugin's table, ranked below the application's
		guestbook = new GuestbookPlugin( router.table( "guestbook" ) );
	}

	/**
	 * Declares the routes, at startup
	 */
	public static void declare() {
		_instance = new BookclubRoutes();
	}

	public static BookclubRoutes instance() {
		return _instance;
	}

	// ---- Actions for the data-only records ----

	/**
	 * Post, redirect, get: a new book, then a redirect to its page
	 */
	private static WOActionResults createBook( final CreateBook form, final RouteInvocation invocation ) {

		if( Library.club( form.club() ).isEmpty() ) {
			return RouteHandler.DECLINED;
		}

		// The form's fields are checked here, where the form can be shown again with what's wrong
		final String error = invocation.conversionErrors().containsKey( "year" ) ? "The year is a number, not '%s'".formatted( invocation.conversionErrors().get( "year" ) )
				: form.title() == null || form.title().isBlank() || form.author() == null || form.author().isBlank() ? "A book has a title and an author" : null;

		if( error != null ) {
			final NewBookPage page = page( NewBookPage.class, invocation.context() ).club( Library.club( form.club() ).get() );
			page.error = error;
			return page;
		}

		final Book book = Library.book( form.club(), form.title().strip(), form.author().strip(), form.year() );
		return seeOther( instance().book.url( new BookView( form.club(), book ), invocation.context() ) );
	}

	private static WOActionResults deleteBook( final DeleteBook delete, final RouteInvocation invocation ) {

		if( !Library.removeBook( delete.club(), delete.book().id() ) ) {
			return RouteHandler.DECLINED;
		}

		return seeOther( instance().books.url( new Books( delete.club(), null, null ), invocation.context() ) );
	}

	private static WOActionResults reset( final Reset reset, final RouteInvocation invocation ) {
		Library.reset();
		return seeOther( instance().clubHome.url( new ClubHome( reset.club() ), invocation.context() ) );
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
		final Book book = invocation.parameter( "book", Book.class );
		return book.club().equals( invocation.parameter( "club" ) ) ? jsonResponse( 200, json( book ) ) : RouteHandler.DECLINED;
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

	@SuppressWarnings("unchecked")
	private static <T extends BaseComponent> T page( final Class<T> pageClass, final WOContext context ) {
		return (T)WOApplication.application().pageWithName( pageClass.getName(), context );
	}

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

	static WOResponse seeOther( final String url ) {
		final WOResponse response = new WOResponse();
		response.setStatus( 303 );
		response.setHeader( url, "location" );
		return response;
	}

	private static String capitalized( final String s ) {
		return s.isEmpty() ? s : Character.toUpperCase( s.charAt( 0 ) ) + s.substring( 1 );
	}
}
