package bookclubs.components;

import com.webobjects.appserver.WOContext;

import bookclubs.BookclubRoutes.DeleteBook;
import bookclubs.data.Library.Book;

public class BookPage extends BaseComponent {

	public Book book;

	public BookPage( final WOContext context ) {
		super( context );
	}

	/**
	 * Forms don't take a route yet, so the form posts to a URL built in Java
	 */
	public String deleteURL() {
		return routes().deleteBook.url( new DeleteBook( club.id(), book ), context() );
	}
}
