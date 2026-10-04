package er.routing;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.webobjects.foundation.NSKeyValueCoding;

/**
 * EXPERIMENTAL (route-links branch). An interface's route constants by name, for templates. Key-value coding doesn't read
 * static fields, so the pages' interface gives templates its constants through one of these:
 *
 * <pre>
 * public interface Routes {
 *
 * 	default Object routes() {
 * 		return RouteKeys.of( Routes.class );
 * 	}
 *
 * 	PlainRoute book = Route.plain();
 * }
 * </pre>
 *
 * and a template links with {@code <wo:route to="$routes.book" …>}.
 */
public final class RouteKeys implements NSKeyValueCoding {

	private final Class<?> _type;

	private RouteKeys( final Class<?> type ) {
		_type = type;
	}

	/**
	 * @return The public static fields of the type (and its interfaces), by name
	 */
	public static RouteKeys of( final Class<?> type ) {
		return new RouteKeys( type );
	}

	@Override
	public Object valueForKey( final String key ) {
		try {
			final Field field = _type.getField( key );

			if( Modifier.isStatic( field.getModifiers() ) ) {
				return field.get( null );
			}
		}
		catch( NoSuchFieldException | IllegalAccessException e ) {
			// Not a constant: unknown, below
		}

		throw new NSKeyValueCoding.UnknownKeyException( "%s has no constant named '%s'".formatted( _type.getName(), key ), this, key );
	}

	@Override
	public void takeValueForKey( final Object value, final String key ) {
		throw new UnsupportedOperationException( "Route constants are read only: '%s'".formatted( key ) );
	}

	@Override
	public String toString() {
		return "RouteKeys " + _type.getName();
	}
}
