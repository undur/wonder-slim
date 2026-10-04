package er.routing.conversion;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Converts route parameters between their URL text and their types, both ways: a value to text for a link, and text
 * back to a value for an invocation. Strings, numbers, booleans, dates and enums are built in, and an application
 * registers converters for its own types, so a route can take an object (a book from its id).
 */

public final class Converters {

	/**
	 * What a converter can see of the request it converts a value for: the request's other parameters, and objects the
	 * application provides ({@link Converters#provide(Class, Function)}, an editing context to fetch in) or the framework
	 * does (its request and context). So a book's converter finds the book in the club the host names.
	 */
	public interface Scope {

		/**
		 * @return A route parameter's text (a path's or a host's), null if the route has none of the name
		 */
		public String parameter( String name );

		/**
		 * @return A route parameter converted to the type (once per request, whatever asks first)
		 */
		public <T> T parameter( String name, Class<T> type );

		/**
		 * @return An object of the type the framework or the application provides for the request, null if none does
		 */
		public <T> T get( Class<T> type );

		/**
		 * No request: converting a link's text, say
		 */
		public static final Scope NONE = new Scope() {
			@Override
			public String parameter( final String name ) {
				return null;
			}

			@Override
			public <T> T parameter( final String name, final Class<T> type ) {
				return null;
			}

			@Override
			public <T> T get( final Class<T> type ) {
				return null;
			}
		};
	}

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
		 * @return The value for the text in the request's scope (its other parameters, objects provided for it), or null
		 *         if there's no such value. A converter needing the scope implements this; the default ignores it.
		 * @throws IllegalArgumentException if the text isn't a value of the type
		 */
		public default T fromString( final String string, final Scope scope ) {
			return fromString( string );
		}

		/**
		 * @return The value's text in a URL
		 */
		public String toString( T value );

		/**
		 * @return true if a value has one text: a route parameter given other text ({@code 007} for 7) is redirected to
		 *         its canonical text, so each value has one URL. False for types written more than one way ({@code 1} and
		 *         {@code 1.0}).
		 */
		public default boolean canonical() {
			return true;
		}

		/**
		 * @return A converter from the two functions, the first seeing the request's scope: a book found in the club the
		 *         host names, an object fetched in the request's editing context. Without a request (a link's text) the
		 *         scope is {@link Scope#NONE}.
		 */
		public static <T> Converter<T> scoped( final BiFunction<String, Scope, T> fromString, final Function<T, String> toString ) {
			return new Converter<>() {
				@Override
				public T fromString( final String string ) {
					return fromString( string, Scope.NONE );
				}

				@Override
				public T fromString( final String string, final Scope scope ) {
					return fromString.apply( string, scope );
				}

				@Override
				public String toString( final T value ) {
					return toString.apply( value );
				}
			};
		}

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

	/**
	 * What the application provides for a request, by type ({@link #provide(Class, Function)})
	 */
	private final Map<Class<?>, Function<Scope, ?>> _provided = new ConcurrentHashMap<>();

	/**
	 * Told of each type registered after the built-in ones, null for nothing
	 */
	private final Consumer<Class<?>> _registered;

	/**
	 * The types with a built-in converter
	 */
	private final Set<Class<?>> _builtIn;

	public Converters() {
		this( null );
	}

	/**
	 * @param registered Told of each type an application registers (not the built-in ones)
	 */
	public Converters( final Consumer<Class<?>> registered ) {
		register( String.class, Converter.of( s -> s, s -> s ) );
		register( Integer.class, Converter.of( Integer::valueOf, String::valueOf ) );
		register( Long.class, Converter.of( Long::valueOf, String::valueOf ) );
		register( Boolean.class, Converter.of( Converters::parseBoolean, String::valueOf ) );
		register( LocalDate.class, Converter.of( Converters::parseDate, LocalDate::toString ) );
		register( Instant.class, Converter.of( Converters::parseInstant, Instant::toString ) );
		register( UUID.class, Converter.of( UUID::fromString, UUID::toString ) );
		register( Double.class, new Converter<>() {
			@Override
			public Double fromString( final String string ) {
				return Double.valueOf( string );
			}

			@Override
			public String toString( final Double value ) {
				return value.toString();
			}

			@Override
			public boolean canonical() {
				return false;
			}
		} );

		_builtIn = Set.copyOf( _converters.keySet() );
		_registered = registered;
	}

	/**
	 * @return true if the type's converter is one of the built-in ones (strings, numbers, booleans, dates, UUIDs), whether
	 *         or not it has been replaced since
	 */
	public boolean isBuiltIn( final Class<?> type ) {
		return _builtIn.contains( boxed( type ) );
	}

	/**
	 * Registers a converter for a type, replacing the one it had
	 */
	public <T> void register( final Class<T> type, final Converter<T> converter ) {
		_converters.put( Objects.requireNonNull( type ), Objects.requireNonNull( converter ) );

		if( _registered != null ) {
			_registered.accept( type );
		}
	}

	/**
	 * Provides an object for a request's scope, which converters see: {@code provide( EOEditingContext.class, scope ->
	 * session( scope ).defaultEditingContext() )}
	 */
	public <T> void provide( final Class<T> type, final Function<Scope, T> provider ) {
		_provided.put( Objects.requireNonNull( type ), Objects.requireNonNull( provider ) );

		if( _registered != null ) {
			_registered.accept( type );
		}
	}

	/**
	 * @return The object the application provides of the type for the scope, null if it provides none
	 */
	@SuppressWarnings("unchecked")
	public <T> T provided( final Class<T> type, final Scope scope ) {
		final Function<Scope, ?> provider = _provided.get( type );
		return provider == null ? null : (T)provider.apply( scope );
	}

	/**
	 * @return true if values of the type can be converted
	 */
	public boolean converts( final Class<?> type ) {
		return Enum.class.isAssignableFrom( boxed( type ) ) || registered( boxed( type ) ) != null;
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
	public <T> T fromString( final String string, final Class<T> type ) {
		return fromString( string, type, Scope.NONE );
	}

	/**
	 * @return The value for the text, converted in the request's scope (see {@link Converter#fromString(String, Scope)}),
	 *         or null if there's no such value
	 * @throws IllegalArgumentException if the text isn't a value of the type
	 * @throws IllegalStateException if the type has no converter
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T> T fromString( final String string, final Class<T> type, final Scope scope ) {
		final Class<?> boxed = boxed( type );

		if( Enum.class.isAssignableFrom( boxed ) ) {
			final Class<? extends Enum> enumType = (Class<? extends Enum>)(boxed.isEnum() ? boxed : (Class<?>)boxed.getSuperclass());
			return (T)Enum.valueOf( enumType, string );
		}

		return (T)converter( boxed ).fromString( string, scope == null ? Scope.NONE : scope );
	}

	/**
	 * @return true if the text is the value's text, or the value's type is written more than one way ({@link
	 *         Converter#canonical()} is false): a route parameter given other text ({@code 007} for 7) is answered with a
	 *         redirect to the URL with the canonical text, so each value has one URL
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean isCanonical( final String text, final Object value ) {

		if( value instanceof Enum<?> e ) {
			return text.equals( e.name() );
		}

		final Converter converter = converter( value.getClass() );
		return !converter.canonical() || text.equals( converter.toString( value ) );
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

	/**
	 * @return true for {@code true}, and for {@code on}, what a checkbox without a value posts; false for {@code false}
	 */
	private static Boolean parseBoolean( final String string ) {
		if( string.equalsIgnoreCase( "true" ) || string.equalsIgnoreCase( "on" ) ) {
			return true;
		}

		if( string.equalsIgnoreCase( "false" ) ) {
			return false;
		}

		throw new IllegalArgumentException( "Not a boolean: " + string );
	}

	private static Instant parseInstant( final String string ) {
		try {
			return Instant.parse( string );
		}
		catch( DateTimeException e ) {
			throw new IllegalArgumentException( e );
		}
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
