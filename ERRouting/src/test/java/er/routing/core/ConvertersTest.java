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

	interface Named {
		String name();
	}

	record Person( String name ) implements Named {}

	@Test
	public void aConverterForAnInterface() {
		final Converters converters = new Converters();
		converters.register( Named.class, Converter.of( Person::new, Named::name ) );

		assertTrue( converters.converts( Person.class ) );
		assertEquals( "sol", converters.toString( new Person( "sol" ) ) );
	}

	@Test
	public void lenientParsingAndCanonicalText() {
		final Converters converters = new Converters();

		// Parsing takes the common forms
		assertEquals( 7, converters.fromString( "007", Integer.class ) );
		assertEquals( java.time.Instant.parse( "2026-10-03T17:00:00Z" ), converters.fromString( "2026-10-03T17:00:00.000Z", java.time.Instant.class ) );
		assertEquals( java.util.UUID.fromString( "a2a0e1a4-2b6c-4a35-9b1e-0f1d2c3b4a59" ), converters.fromString( "A2A0E1A4-2B6C-4A35-9B1E-0F1D2C3B4A59", java.util.UUID.class ) );
		assertEquals( true, converters.fromString( "on", Boolean.class ) );

		// One text per value, for route parameters
		assertFalse( converters.isCanonical( "007", 7 ) );
		assertFalse( converters.isCanonical( "+5", 5 ) );
		assertTrue( converters.isCanonical( "7", 7 ) );

		// Decimals are written more than one way
		assertTrue( converters.isCanonical( "1", 1.0 ) );
	}

	@Test
	public void moreBuiltInTypes() {
		final Converters converters = new Converters();
		final java.util.UUID uuid = java.util.UUID.randomUUID();

		assertEquals( uuid, converters.fromString( uuid.toString(), java.util.UUID.class ) );
		assertEquals( java.time.Instant.parse( "2026-10-03T12:00:00Z" ), converters.fromString( "2026-10-03T12:00:00Z", java.time.Instant.class ) );
		assertThrows( IllegalArgumentException.class, () -> converters.fromString( "not-a-uuid", java.util.UUID.class ) );
	}

	enum Kind {
		PLAIN,
		FANCY {
			@Override
			public String toString() {
				return "fancy";
			}
		}
	}

	@Test
	public void anEnumConstantWithABody() {
		final Converters converters = new Converters();

		assertTrue( converters.converts( Kind.FANCY.getClass() ) );
		assertEquals( "FANCY", converters.toString( Kind.FANCY ) );
	}
}
