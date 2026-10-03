package bookclubs;

import bookclubs.BookclubRoutes.BookView;
import bookclubs.BookclubRoutes.Books;
import bookclubs.BookclubRoutes.ClubText;
import bookclubs.BookclubRoutes.CreateBook;
import bookclubs.BookclubRoutes.DeleteBook;
import bookclubs.BookclubRoutes.MemberView;
import bookclubs.BookclubRoutes.Search;
import er.routing.PlainRoute;
import er.routing.Route;

/**
 * The application's routes: what links go to. {@link BookclubRoutes#declare(er.routing.ERXRouter)} gives each its
 * pattern. The pages implement this, so a template links with {@code <wo:route to="$routes.book" :book="$book">}, and
 * Java code with {@code Routes.book.url( … )}.
 */
public interface Routes {

	/**
	 * The routes under a name of their own in templates ({@code $routes.book}), clear of a page's own keys
	 */
	Routes routes = new Routes() {};

	PlainRoute home = Route.plain();
	PlainRoute clubHome = Route.plain();
	Route<Books> books = Route.of( Books.class );
	Route<BookView> book = Route.of( BookView.class );
	PlainRoute newBook = Route.plain();
	Route<CreateBook> createBook = Route.of( CreateBook.class );
	Route<DeleteBook> deleteBook = Route.of( DeleteBook.class );
	Route<MemberView> member = Route.of( MemberView.class );
	Route<ClubText> clubText = Route.of( ClubText.class );
	PlainRoute about = Route.plain();
	PlainRoute admin = Route.plain();
	PlainRoute danger = Route.plain();
	PlainRoute reset = Route.plain();
	Route<Search> search = Route.of( Search.class );
	PlainRoute rules = Route.plain();

	/**
	 * The guestbook plugin's page: a plugin's route, which the application links to
	 */
	PlainRoute guestbook = GuestbookPlugin.page;
}
