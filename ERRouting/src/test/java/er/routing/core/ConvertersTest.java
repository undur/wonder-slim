package er.routing.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import er.routing.core.Converters.Converter;

public class ConvertersTest {

	enum Sort {
		title, author
	}

	record Book( int id, String title ) {}

	@Test
	public void builtIn() {
		final Converters converters = new Converters();

		assertEquals( 42, converters.fromString( "42", int.class ) );
		assertEquals( 42L, converters.fromString( "42", Long.class ) );
		assertEquals( true, converters.fromString( "true", boolean.class ) );
		assertEquals( LocalDate.of( 2026, 10, 3 ), converters.fromString( "2026-10-03", LocalDate.class ) );
		assertEquals( Sort.author, converters.fromString( "author", Sort.class ) );
		assertEquals( "author", converters.toString( Sort.author ) );
		assertEquals( "2026-10-03", converters.toString( LocalDate.of( 2026, 10, 3 ) ) );
	}

	@Test
	public void valuesThatArentOfTheType() {
		final Converters converters = new Converters();

		assertThrows( IllegalArgumentException.class, () -> converters.fromString( "abc", Integer.class ) );
		assertThrows( IllegalArgumentException.class, () -> converters.fromString( "yes", Boolean.class ) );
		assertThrows( IllegalArgumentException.class, () -> converters.fromString( "2026-13-01", LocalDate.class ) );
		assertThrows( IllegalArgumentException.class, () -> converters.fromString( "price", Sort.class ) );
	}

	@Test
	public void anApplicationsOwnType() {
		final Converters converters = new Converters();
		final Map<Integer, Book> books = Map.of( 1, new Book( 1, "Kindred" ) );

		assertFalse( converters.converts( Book.class ) );
		converters.register( Book.class, Converter.of( id -> books.get( Integer.valueOf( id ) ), book -> String.valueOf( book.id() ) ) );
		assertTrue( converters.converts( Book.class ) );

		assertEquals( "Kindred", converters.fromString( "1", Book.class ).title() );
		assertEquals( "1", converters.toString( new Book( 1, "Kindred" ) ) );

		// No such book, and not a book's id
		assertNull( converters.fromString( "2", Book.class ) );
		assertThrows( IllegalArgumentException.class, () -> converters.fromString( "x", Book.class ) );
	}

	@Test
	public void aTypeWithoutAConverter() {
		assertThrows( IllegalStateException.class, () -> new Converters().fromString( "1", Book.class ) );
	}
}
