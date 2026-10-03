package bookclubs.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The book clubs, their books, members and pages. In memory: the application is about its routes, not its storage.
 */
public class Library {

	public record Club( String id, String name, String motto ) {}

	public record Book( int id, String title, String author, Integer year ) {}

	public record Member( String handle, String name, String favourite ) {}

	public enum Sort {
		title, author, year
	}

	private static final Map<String, Club> CLUBS = new LinkedHashMap<>();
	private static final Map<String, List<Book>> BOOKS = new LinkedHashMap<>();
	private static final Map<String, List<Member>> MEMBERS = new LinkedHashMap<>();
	private static final Map<String, Map<String, String>> PAGES = new LinkedHashMap<>();
	private static final AtomicInteger NEXT_ID = new AtomicInteger( 1 );

	static {
		reset();
	}

	/**
	 * Puts the clubs back the way they started
	 */
	public static synchronized void reset() {
		CLUBS.clear();
		BOOKS.clear();
		MEMBERS.clear();
		PAGES.clear();

		club( new Club( "acme", "Acme Readers", "One chapter a week, no spoilers" ) );
		book( "acme", "The Left Hand of Darkness", "Ursula K. Le Guin", 1969 );
		book( "acme", "Kindred", "Octavia E. Butler", 1979 );
		book( "acme", "Independent People", "Halldór Laxness", 1934 );
		member( "acme", new Member( "hugi", "Hugi", "Independent People" ) );
		member( "acme", new Member( "sol", "Sól", "Kindred" ) );
		page( "acme", "history", "Founded in a kitchen in 2019, over coffee that was too strong." );

		club( new Club( "kronan", "Krónan Book Club", "We read whatever's on sale" ) );
		book( "kronan", "Njáls saga", "Unknown", 1280 );
		book( "kronan", "The Fish Can Sing", "Halldór Laxness", 1957 );
		member( "kronan", new Member( "gudrun", "Guðrún", "Njáls saga" ) );
		page( "kronan", "history", "Started in the queue at the till." );
	}

	private static void club( final Club club ) {
		CLUBS.put( club.id(), club );
		BOOKS.put( club.id(), new ArrayList<>() );
		MEMBERS.put( club.id(), new ArrayList<>() );
		PAGES.put( club.id(), new LinkedHashMap<>() );
	}

	private static void member( final String club, final Member member ) {
		MEMBERS.get( club ).add( member );
	}

	private static void page( final String club, final String name, final String text ) {
		PAGES.get( club ).put( name, text );
	}

	public static synchronized List<Club> clubs() {
		return List.copyOf( CLUBS.values() );
	}

	public static synchronized Optional<Club> club( final String id ) {
		return Optional.ofNullable( CLUBS.get( id ) );
	}

	public static synchronized List<Book> books( final String club, final Sort sort ) {
		final Comparator<Book> comparator = switch( sort ) {
			case title -> Comparator.comparing( Book::title );
			case author -> Comparator.comparing( Book::author );
			case year -> Comparator.comparing( Book::year, Comparator.nullsLast( Comparator.naturalOrder() ) );
		};

		return BOOKS.getOrDefault( club, List.of() ).stream().sorted( comparator ).toList();
	}

	public static synchronized Optional<Book> book( final String club, final int id ) {
		return BOOKS.getOrDefault( club, List.of() ).stream().filter( b -> b.id() == id ).findFirst();
	}

	public static synchronized Book book( final String club, final String title, final String author, final Integer year ) {
		final Book book = new Book( NEXT_ID.getAndIncrement(), title, author, year );
		BOOKS.get( club ).add( book );
		return book;
	}

	public static synchronized boolean removeBook( final String club, final int id ) {
		return BOOKS.getOrDefault( club, new ArrayList<>() ).removeIf( b -> b.id() == id );
	}

	public static synchronized List<Member> members( final String club ) {
		return List.copyOf( MEMBERS.getOrDefault( club, List.of() ) );
	}

	public static synchronized Optional<Member> member( final String club, final String handle ) {
		return MEMBERS.getOrDefault( club, List.of() ).stream().filter( m -> m.handle().equals( handle ) ).findFirst();
	}

	public static synchronized Optional<String> page( final String club, final String name ) {
		return Optional.ofNullable( PAGES.getOrDefault( club, Map.of() ).get( name ) );
	}
}
