package er.extensions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;

import com.webobjects.foundation.NSKeyValueCoding;

import sun.misc.Unsafe;

/**
 * Horrifying hack for accessing methods on java's private inner classes (such as the List implementation returned by Java's List.of()) 
 */

public class ERXKVCReflectionHack {

	/**
	 * Enable the hack. May the Lord bless you and protect you.
	 */
	public static void enable() {
		try {
			setFinalStaticField( NSKeyValueCoding.ValueAccessor.class, "_defaultValueAccessor", new HackedDefaultValueAccessor() );
		}
		catch( Exception e ) {
			throw new RuntimeException( e );
		}
	}

	/**
	 * Horrid hack to allow us to set a value on the static final field containing NSKeyValueCoding's default value accessor
	 */
	private static void setFinalStaticField( Class<?> clazz, String fieldName, Object newValue ) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		// Get Unsafe
		Field unsafeField = Unsafe.class.getDeclaredField( "theUnsafe" );
		unsafeField.setAccessible( true );
		Unsafe unsafe = (Unsafe)unsafeField.get( null );

		// Get target field
		Field field = clazz.getDeclaredField( fieldName );
		field.setAccessible( true );

		// Override the value in memory
		Object base = unsafe.staticFieldBase( field );
		long offset = unsafe.staticFieldOffset( field );
		unsafe.putObject( base, offset, newValue );
	}

	/**
	 * Our butt-ugly ValueAccessor implementation
	 */
	public static class HackedDefaultValueAccessor extends NSKeyValueCoding.ValueAccessor {

		@Override
		public Object fieldValue( Object object, Field field ) throws IllegalArgumentException, IllegalAccessException {
			return field.get( object );
		}

		@Override
		public void setFieldValue( Object object, Field field, Object value ) throws IllegalArgumentException, IllegalAccessException {
			field.set( object, value );
		}

		@Override
		public Object methodValue( Object object, Method method ) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
			try {
				return method.invoke( object );
			}
			catch( IllegalAccessException e ) {
				// If we receive an IllegalAccessException, we try to resolve the value using our
				// FIXME: We might actually want to (a) use our own cache for keys accessed in this way or (b) try to insert our own accessor into KVC's cache? // Hugi 2025-06-24
				method = accessibleMethod( object, method.getName() );
				return method.invoke( object );
			}
		}

		@Override
		public void setMethodValue( Object object, Method method, Object value ) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
			// CHECKME: We might possible want to override this. Don't think so though. Accessibility errors will primarily cause us grief on immutable objects, and generally, objects we don't mutate
			method.invoke( object, value );
		}
	}

	/**
	 * The hack from NG used to locate a readable method of the given name on the given object.
	 */
	private static Method accessibleMethod( final Object object, final String methodName, Class<?>... signature ) {
		Objects.requireNonNull( object );
		Objects.requireNonNull( methodName );

		Class<?> currentClass = object.getClass();

		try {
			while( currentClass != null ) {
				final Method classMethod = currentClass.getMethod( methodName, signature );

				final boolean canAccess;

				// FIXME: Unlike KVC we're allowing static invocation. Contemplate. // Hugi 2024-06-14
				if( Modifier.isStatic( classMethod.getModifiers() ) ) {
					canAccess = classMethod.canAccess( null );
				}
				else {
					canAccess = classMethod.canAccess( object );
				}

				if( canAccess ) {
					// Method exists and is accessible on the object's class
					// This is the happy path, where we'll immediately end up in 99% of cases
					return classMethod;
				}

				// Here come the dragons...

				// The class doesn't have an accessible method definition. What about the interfaces?
				// FIXME: We're missing a check on "parent interfaces", i.e. interfaces that these interfaces inherit from // Hugi 2022-10-21
				for( Class<?> interfaceClass : currentClass.getInterfaces() ) {
					try {
						final Method interfaceMethod = interfaceClass.getMethod( methodName, signature );

						if( interfaceMethod.canAccess( object ) ) {
							return interfaceMethod;
						}
					}
					catch( NoSuchMethodException interfaceException ) {
						// Failure to locate methods in interfaces are to be expected. If no interfaces contain the method, we've already failed anyway.
					}
				}

				// Now let's try the whole thing again for the superclass
				currentClass = currentClass.getSuperclass();
			}

			// The method exists, but no accessible implementation was found. Tough luck.
			return null;
		}
		catch( NoSuchMethodException methodException ) {
			// We'll end up here immediately if the method doesn't exist on the first try.
			// If the method doesn't exist on the original class we're dead whatever we do regardless of accessibility, so just return immediately
			return null;
		}
	}

	/**
	 * A little test
	 */
	public static void main( String[] args ) throws Throwable {
		enable();
		List<String> names = List.of( "Hugi", "Paul", "Marc" );
		System.out.println( NSKeyValueCoding.DefaultImplementation.valueForKey( names, "size" ) );
	}
}