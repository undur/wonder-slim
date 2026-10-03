package bookclubs.components;

import java.util.List;

import com.webobjects.appserver.WOContext;

import bookclubs.data.Library;
import bookclubs.data.Library.Book;
import bookclubs.data.Library.Sort;

/**
 * The books, sorted, two to a page, of the authors chosen (all, if none are)
 */
public class BookListPage extends BaseComponent {

	private static final int PAGE_SIZE = 2;

	public Sort sort;
	public int page;
	public Book book;

	/**
	 * The authors whose books are shown, all if it's empty: {@code ?author=…&author=…}, a repeated parameter
	 */
	public List<String> authors = List.of();
	public String anAuthor;

	public BookListPage( final WOContext context ) {
		super( context );
	}

	private List<Book> all() {
		return Library.books( club.id(), sort ).stream().filter( b -> authors.isEmpty() || authors.contains( b.author() ) ).toList();
	}

	/**
	 * @return Every author in the club, for the filter
	 */
	public List<String> allAuthors() {
		return Library.books( club.id(), Sort.author ).stream().map( Book::author ).distinct().toList();
	}

	/**
	 * @return {@code checked} for an author whose books are shown, null otherwise (the attribute left out)
	 */
	public String anAuthorChecked() {
		return authors.contains( anAuthor ) ? "checked" : null;
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
