package bookclubs.components;

import com.webobjects.appserver.WOContext;

import bookclubs.BookclubRoutes.CreateBook;

public class NewBookPage extends BaseComponent {

	public String error;

	public NewBookPage( final WOContext context ) {
		super( context );
	}

	/**
	 * Forms don't take a route yet, so the form posts to a URL built in Java. Its fields are the record's other
	 * components (title, author, year), read from the form like query parameters.
	 */
	public String createURL() {
		return routes().createBook.url( new CreateBook( club.id(), null, null, null ), context() );
	}
}
