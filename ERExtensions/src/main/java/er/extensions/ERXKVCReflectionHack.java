package er.extensions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.webobjects.foundation.NSKeyValueCoding;

import sun.misc.Unsafe;

/**
 * Hack that allows KVC to access methods on private classes that implement public interfaces,
 * examples being the List implementations returned by methods like List.of() and Stream.toList().
 */

public class ERXKVCReflectionHack {

	/**
	 * Make our ValueAccessor KVC's default method of resolving keys on plain java objects
	 */
	public static void enable() {
		setFinalStaticField(NSKeyValueCoding.ValueAccessor.class, "_defaultValueAccessor", new AccessGrantingValueAccessor());
		
		// Not using standard logging since we activate this as soon as possible, and that's before the application's logging has been initialized.
		System.out.println("== Enabled " + ERXKVCReflectionHack.class.getSimpleName());
	}

	/**
	 * A ValueAccessor that attempts to open access to methods that initially throw IllegalAccessException upon invocation   
	 */
	private static class AccessGrantingValueAccessor extends NSKeyValueCoding.ValueAccessor {

		@Override
		public Object fieldValue(Object object, Field field) throws IllegalArgumentException, IllegalAccessException {
			return field.get(object);
		}

		@Override
		public void setFieldValue(Object object, Field field, Object value) throws IllegalArgumentException, IllegalAccessException {
			field.set(object, value);
		}

		/**
		 * Used by KVC to invoke a method.
		 * Our change is that if invocation fails with an IllegalAccessException, we try making the method accessible and then attempt re-invocation.
		 * 
		 * Since the Method object passed in should be coming from the KVC cache, invocation should fail only once per key per class.
		 * This means we'll usually be going down the "happy path", i.e. after the first invocation this should work pretty much exactly like KVC's built in default value accessor.
		 */
		@Override
		public Object methodValue(Object object, Method method) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
			try {
				return method.invoke(object);
			}
			catch( IllegalAccessException e ) {
				method.setAccessible(true);
				return method.invoke(object);
			}
		}

		/**
		 * Does exactly the same thing as methodValue()
		 */
		@Override
		public void setMethodValue(Object object, Method method, Object value) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
			try {
				method.invoke(object, value);
			}
			catch( IllegalAccessException e ) {
				method.setAccessible(true);
				method.invoke(object, value);
			}
		}
	}

	/**
	 * A pretty horrifying way to to set NSKeyValueCoding's default value accessor (which is final and static, so needs workarounds to be set).
	 * We can assume that this way of hacking in the value accessor will stop working sometime soon after JDK 25, but by then we'll hopefully have a better solution.
	 * 
	 * @see https://stackoverflow.com/questions/61141836/change-static-final-field-in-java-12
	 * @see https://openjdk.org/jeps/471
	 */
	private static void setFinalStaticField(Class<?> clazz, String fieldName, Object newValue) {
		try {
			// Get Unsafe
			Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			unsafeField.setAccessible(true);
			Unsafe unsafe = (Unsafe) unsafeField.get(null);

			// Get target field
			Field field = clazz.getDeclaredField(fieldName);
			field.setAccessible(true);

			// Override the value in memory
			Object base = unsafe.staticFieldBase(field);
			long offset = unsafe.staticFieldOffset(field);
			unsafe.putObject(base, offset, newValue);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}