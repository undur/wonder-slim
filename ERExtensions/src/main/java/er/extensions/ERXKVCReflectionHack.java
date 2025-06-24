package er.extensions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import com.webobjects.foundation.NSKeyValueCoding;

import sun.misc.Unsafe;

/**
 * Horrifying hack for accessing methods on java's private inner classes (such
 * as the List implementation returned by Java's List.of())
 */

public class ERXKVCReflectionHack {

	/**
	 * Enable the hack. May the Lord bless you and protect you.
	 */
	public static void enable() {
		setFinalStaticField(NSKeyValueCoding.ValueAccessor.class, "_defaultValueAccessor", new HackedDefaultValueAccessor());
	}

	/**
	 * Our butt-ugly ValueAccessor implementation
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
			// FIXME: We probably want to invoke setAccessible conditionally (for example, based on object type or the method's accessibility). Or do it if IllegalAccessException is thrown? // Hugi 2025-06-24
			method.setAccessible(true);
			return method.invoke(object);
		}

		@Override
		public void setMethodValue(Object object, Method method, Object value) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
			// CHECKME: We might possibly not want to override here.
			// Accessibility errors will primarily cause us grief on
			// immutable objects, and generally, objects we don't mutate // Hugi 2025-06-24
			method.setAccessible(true);
			method.invoke(object, value);
		}
	}

	/**
	 * Horrid hack to allow us to set NSKeyValueCoding's default value accessor
	 * (which is declared final and static)
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

	/**
	 * A little test
	 */
	public static void main(String[] args) throws Throwable {
		enable();
		List<String> names = List.of("Hugi", "Paul", "Marc");
		System.out.println(NSKeyValueCoding.DefaultImplementation.valueForKey(names, "size"));
	}
}