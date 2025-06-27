package er.extensions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.webobjects.foundation.NSKeyValueCoding;

import sun.misc.Unsafe;

/**
 * Hack that allows KVC to access methods on private inner classes implementing public classes, such as the List implementation returned by Java's List.of()
 * 
 * CHECKME: Rename to something a little more descriptive // Hugi 2025-06-25
 */

public class ERXKVCReflectionHack {

	/**
	 * Make our ValueAccessor KVC's default method of resolving keys on plain java objects
	 */
	public static void enable() {
		setFinalStaticField(NSKeyValueCoding.ValueAccessor.class, "_defaultValueAccessor", new AccessGrantingValueAccessor());
		
		// CHECKME: We're not using standard logging since we're activating this before the application's logging has been set up. Not an ideal situation // Hugi 2025-06-25
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
		 * KVC will use this to invoke the method represented by a key.
		 * Our change is that if invocation fails with an IllegalAccessException, we try to make the method accessible and then attempt reinvocation.
		 * Since the method object we're working with comes from the KVC cache, invocation should fail only once per key per class.
		 * This means we'll usually be going down the "happy path" so this should work pretty much like KVC's built in default value accessor.
		 * 
		 * CHECKME: Perform additional checks before granting access? This might be a bit of an accessibility carte blanche // Hugi 2025-06-24
		 * CHECKME: Keep a record of classes/keys we do this for // Hugi 2025-06-25
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
	 * We can assume that this way of hacking in the value accessor will stop working in the somewhat near future, but by then we'll hopefully have a better solution.
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