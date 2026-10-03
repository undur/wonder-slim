package bookclubs.components;

import com.webobjects.appserver.WOContext;

import bookclubs.data.Library.Book;

public class BookPage extends BaseComponent {

	public Book book;

	public BookPage( final WOContext context ) {
		super( context );
	}
}
