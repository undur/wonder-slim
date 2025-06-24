package er.extensions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.webobjects.foundation.NSKeyValueCoding;

import sun.misc.Unsafe;

/**
 * Hack for allowing KVC to access methods on private inner classes (such as the List implementation returned by Java's List.of())
 */

public class ERXKVCReflectionHack {

	/**
	 * Enable the hack. May the Lord bless you and protect you.
	 */
	public static void enable() {
		setFinalStaticField(NSKeyValueCoding.ValueAccessor.class, "_defaultValueAccessor", new HackedDefaultValueAccessor());
	}

	/**
	 * Our butt-ugly ValueAccessor implementation, which will try to open access to the method
	 */
	public static class HackedDefaultValueAccessor extends NSKeyValueCoding.ValueAccessor {

		@Override
		public Object fieldValue(Object object, Field field) throws IllegalArgumentException, IllegalAccessException {
			return field.get(object);
		}

		@Override
		public void setFieldValue(Object object, Field field, Object value) throws IllegalArgumentException, IllegalAccessException {
			field.set(object, value);
		}

		@Override
		public Object methodValue(Object object, Method method) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {

			// If invocation fails, make method accessible and attempt reinvocation
			// Since the method object we're working with comes from the KVC cache, invocation should fail only once per key per class.
			// That makes me breathe a little easier WRT the induced performance penalty.
			try {
				return method.invoke(object);
			}
			catch( IllegalAccessException e ) {
				// FIXME: Perform additional checks before granting access? This might a bit of an accessibility carte blanche // Hugi 2025-06-24
				method.setAccessible(true);
				return method.invoke(object);
			}
		}

		@Override
		public void setMethodValue(Object object, Method method, Object value) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {

			// @See same logic in methodValue()
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
	 * A pretty horrifying way to to set NSKeyValueCoding's default value accessor (which is final and static, so needs workarounds to be set)
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