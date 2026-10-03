package bookclubs.components;

import java.util.List;

import com.webobjects.appserver.WOContext;

import bookclubs.data.Library;
import bookclubs.data.Library.Book;
import bookclubs.data.Library.Member;
import bookclubs.data.Library.Sort;

public class ClubPage extends BaseComponent {

	public Book book;
	public Member member;

	public ClubPage( final WOContext context ) {
		super( context );
	}

	public List<Book> books() {
		return Library.books( club.id(), Sort.year );
	}

	public List<Member> members() {
		return Library.members( club.id() );
	}
}
