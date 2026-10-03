package er.extensions.appserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver._private.WOKeyValueAssociation;
import com.webobjects.foundation.NSKeyValueCoding;

/**
 * A key path binding ({@code $routes.search}) whose keys may name static members (#172): a {@code public static} field,
 * or a {@code public static} method without arguments, of the class of the object the key is applied to (the component,
 * for the first key), its superclasses or the interfaces it implements. Instance members win: a static one is used only
 * when key-value coding finds no member of that name. So constants on an interface the pages implement are reachable
 * from their templates, and an instance of the interface groups them under a name of their own:
 *
 * <pre>
 * public interface Routes {
 *     Routes routes = new Routes() {};               // $routes.search, clear of the page's own keys
 *     Route&lt;Search&gt; search = Route.of( Search.class ); // $search too, when the page has no member of its own named so
 * }
 * </pre>
 *
 * A static member is read only, so a binding to one can't be set.
 */

public class ERXKeyValueAssociation extends WOKeyValueAssociation {

	/**
	 * By class and key, how to read a static member. A key naming none isn't kept, so a member added while the
	 * application runs (a hot swap) is found.
	 */
	private static final Map<Class<?>, Map<String, Function<Object, Object>>> STATICS = new ConcurrentHashMap<>();

	/**
	 * By class, the keys found to name static members, so they're read without asking key-value coding first
	 */
	private static final Map<Class<?>, Set<String>> STATIC_KEYS = new ConcurrentHashMap<>();

	private final String[] _keys;

	/**
	 * True once a key of the path has named a static member: the path is then followed key by key
	 */
	private volatile boolean _reachesStatics;

	public ERXKeyValueAssociation( final String keyPath ) {
		super( keyPath );
		_keys = keyPath.split( "\\." );
	}

	@Override
	public Object valueInComponent( final WOComponent component ) {

		if( !_reachesStatics ) {
			try {
				return super.valueInComponent( component );
			}
			catch( NSKeyValueCoding.UnknownKeyException e ) {
				if( e.object() == null || staticReader( e.object().getClass(), e.key() ).isEmpty() ) {
					throw e;
				}

				_reachesStatics = true;
			}
		}

		return followed( component );
	}

	/**
	 * @return The path's value, followed key by key, a key naming no member of an object read from a static one
	 */
	private Object followed( final WOComponent component ) {
		Object value = component;

		for( final String key : _keys ) {
			if( value == null ) {
				return null;
			}

			value = valueForKey( value, key );
		}

		return value;
	}

	private static Object valueForKey( final Object object, final String key ) {
		final Set<String> staticKeys = STATIC_KEYS.get( object.getClass() );

		if( staticKeys != null && staticKeys.contains( key ) ) {
			return staticReader( object.getClass(), key ).orElseThrow().apply( null );
		}

		try {
			return NSKeyValueCoding.Utility.valueForKey( object, key );
		}
		catch( NSKeyValueCoding.UnknownKeyException e ) {
			final Optional<Function<Object, Object>> reader = staticReader( object.getClass(), key );

			if( reader.isEmpty() ) {
				throw e;
			}

			STATIC_KEYS.computeIfAbsent( object.getClass(), c -> ConcurrentHashMap.newKeySet() ).add( key );
			return reader.get().apply( null );
		}
	}

	@Override
	public Object clone() {
		return new ERXKeyValueAssociation( keyPath() );
	}

	/**
	 * @return How to read the static member the key names on the class (its superclasses and interfaces), empty for none
	 */
	static Optional<Function<Object, Object>> staticReader( final Class<?> type, final String key ) {
		final Map<String, Function<Object, Object>> byKey = STATICS.computeIfAbsent( type, t -> new ConcurrentHashMap<>() );
		final Function<Object, Object> known = byKey.get( key );

		if( known != null ) {
			return Optional.of( known );
		}

		final Optional<Function<Object, Object>> found = find( type, key );
		found.ifPresent( reader -> byKey.put( key, reader ) );
		return found;
	}

	private static Optional<Function<Object, Object>> find( final Class<?> type, final String key ) {

		for( Class<?> c = type; c != null; c = c.getSuperclass() ) {
			final Optional<Function<Object, Object>> found = declared( c, key );

			if( found.isPresent() ) {
				return found;
			}

			for( final Class<?> i : c.getInterfaces() ) {
				final Optional<Function<Object, Object>> inInterface = find( i, key );

				if( inInterface.isPresent() ) {
					return inInterface;
				}
			}
		}

		return Optional.empty();
	}

	private static Optional<Function<Object, Object>> declared( final Class<?> c, final String key ) {
		try {
			final Field field = c.getDeclaredField( key );

			if( Modifier.isStatic( field.getModifiers() ) && Modifier.isPublic( field.getModifiers() ) ) {
				field.setAccessible( true );
				return Optional.of( ignored -> get( field ) );
			}
		}
		catch( NoSuchFieldException e ) {
			// Then a method, perhaps
		}

		try {
			final Method method = c.getDeclaredMethod( key );

			if( Modifier.isStatic( method.getModifiers() ) && Modifier.isPublic( method.getModifiers() ) ) {
				method.setAccessible( true );
				return Optional.of( ignored -> invoke( method ) );
			}
		}
		catch( NoSuchMethodException e ) {
			// Neither
		}

		return Optional.empty();
	}

	private static Object get( final Field field ) {
		try {
			return field.get( null );
		}
		catch( IllegalAccessException e ) {
			throw new IllegalStateException( e );
		}
	}

	private static Object invoke( final Method method ) {
		try {
			return method.invoke( null );
		}
		catch( ReflectiveOperationException e ) {
			throw new IllegalStateException( e );
		}
	}
}
