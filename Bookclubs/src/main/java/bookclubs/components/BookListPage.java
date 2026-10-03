package bookclubs.components;

import java.util.List;

import com.webobjects.appserver.WOContext;

import bookclubs.data.Library;
import bookclubs.data.Library.Book;
import bookclubs.data.Library.Sort;

/**
 * The books, sorted, two to a page
 */
public class BookListPage extends BaseComponent {

	private static final int PAGE_SIZE = 2;

	public Sort sort;
	public int page;
	public Book book;

	public BookListPage( final WOContext context ) {
		super( context );
	}

	private List<Book> all() {
		return Library.books( club.id(), sort );
	}

	public List<Book> books() {
		final List<Book> all = all();
		final int from = Math.min( (page - 1) * PAGE_SIZE, all.size() );
		return all.subList( from, Math.min( from + PAGE_SIZE, all.size() ) );
	}

	public boolean hasPrevious() {
		return page > 1;
	}

	public boolean hasNext() {
		return page * PAGE_SIZE < all().size();
	}

	public Integer previousPage() {
		return page - 1;
	}

	public Integer nextPage() {
		return page + 1;
	}
}
