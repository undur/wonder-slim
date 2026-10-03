package er.routing.core;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts route parameters between their URL text and their types, both ways: a value to text for a link, and text
 * back to a value for an invocation. Strings, numbers, booleans, dates and enums are built in, and an application
 * registers converters for its own types, so a route can take an object (a book from its id).
 */

public final class Converters {

	/**
	 * Converts one type
	 */
	public interface Converter<T> {

		/**
		 * @return The value for the text, or null if there's no such value (an object that doesn't exist)
		 * @throws IllegalArgumentException if the text isn't a value of the type
		 */
		public T fromString( String string );

		/**
		 * @return The value's text in a URL
		 */
		public String toString( T value );

		/**
		 * @return A converter from the two functions
		 */
		public static <T> Converter<T> of( final java.util.function.Function<String, T> fromString, final java.util.function.Function<T, String> toString ) {
			return new Converter<>() {
				@Override
				public T fromString( final String string ) {
					return fromString.apply( string );
				}

				@Override
				public String toString( final T value ) {
					return toString.apply( value );
				}
			};
		}
	}

	private final Map<Class<?>, Converter<?>> _converters = new ConcurrentHashMap<>();

	public Converters() {
		register( String.class, Converter.of( s -> s, s -> s ) );
		register( Integer.class, Converter.of( Integer::valueOf, String::valueOf ) );
		register( Long.class, Converter.of( Long::valueOf, String::valueOf ) );
		register( Boolean.class, Converter.of( Converters::parseBoolean, String::valueOf ) );
		register( LocalDate.class, Converter.of( Converters::parseDate, LocalDate::toString ) );
	}

	/**
	 * Registers a converter for a type, replacing the one it had
	 */
	public <T> void register( final Class<T> type, final Converter<T> converter ) {
		_converters.put( Objects.requireNonNull( type ), Objects.requireNonNull( converter ) );
	}

	/**
	 * @return true if values of the type can be converted
	 */
	public boolean converts( final Class<?> type ) {
		return boxed( type ).isEnum() || registered( boxed( type ) ) != null;
	}

	/**
	 * @return The converter registered for the type, its superclasses or its interfaces (a proxy, an entity's subclass,
	 *         an implementation of a registered interface), null for none
	 */
	private Converter<?> registered( final Class<?> type ) {
		for( Class<?> c = type; c != null; c = c.getSuperclass() ) {
			Converter<?> converter = _converters.get( c );

			if( converter == null ) {
				converter = registeredForInterfaces( c );
			}

			if( converter != null ) {
				return converter;
			}
		}

		return null;
	}

	private Converter<?> registeredForInterfaces( final Class<?> type ) {
		for( final Class<?> i : type.getInterfaces() ) {
			Converter<?> converter = _converters.get( i );

			if( converter == null ) {
				converter = registeredForInterfaces( i );
			}

			if( converter != null ) {
				return converter;
			}
		}

		return null;
	}

	/**
	 * @return The value for the text, or null if there's no such value
	 * @throws IllegalArgumentException if the text isn't a value of the type
	 * @throws IllegalStateException if the type has no converter
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T> T fromString( final String string, final Class<T> type ) {
		final Class<?> boxed = boxed( type );

		if( boxed.isEnum() ) {
			return (T)Enum.valueOf( (Class<Enum>)boxed, string );
		}

		return (T)converter( boxed ).fromString( string );
	}

	/**
	 * @return The value's text in a URL
	 * @throws IllegalStateException if the value's type has no converter
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public String toString( final Object value ) {

		if( value instanceof Enum<?> e ) {
			return e.name();
		}

		return ((Converter)converter( value.getClass() )).toString( value );
	}

	private Converter<?> converter( final Class<?> type ) {
		final Converter<?> converter = registered( type );

		if( converter == null ) {
			throw new IllegalStateException( "No converter for %s: register one with Converters.register()".formatted( type.getName() ) );
		}

		return converter;
	}

	/**
	 * @return The boxed type for a primitive one
	 */
	public static Class<?> boxed( final Class<?> type ) {
		if( type == int.class ) {
			return Integer.class;
		}

		if( type == long.class ) {
			return Long.class;
		}

		if( type == boolean.class ) {
			return Boolean.class;
		}

		return type;
	}

	private static Boolean parseBoolean( final String string ) {
		if( !string.equals( "true" ) && !string.equals( "false" ) ) {
			throw new IllegalArgumentException( "Not a boolean: " + string );
		}

		return Boolean.valueOf( string );
	}

	private static LocalDate parseDate( final String string ) {
		try {
			return LocalDate.parse( string );
		}
		catch( DateTimeException e ) {
			throw new IllegalArgumentException( e );
		}
	}
}
