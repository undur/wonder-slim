package er.extensions.foundation;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;

/**
 * Utilities for use with key value coding. You could instantiate one of these
 * in your app-startup:
 * 
 * <pre>
 * <code>
 * ERXKeyValueCodingUtilities.registerClass(SomeClass.class); 
 * NSKeyValueCodingAdditions statics = ERXKeyValueCodingUtilities.Statics;
 * myValue = statics.valueForKeyPath("SomeClass.SOME_FIELD");
 * </code>
 * </pre>
 * 
 * Also has utilities for getting and private fields and methods on an object.
 * 
 * @author ak
 */

public class ERXKeyValueCodingUtilities {

	public static Object privateValueForKey(Object target, String key) {
		Field field = accessibleFieldForKey(target, key);
		try {
			if (field != null) {
				return field.get(target);
			}
			Method method = accessibleMethodForKey(target, key);
			if (method != null) {
				return method.invoke(target, (Object[]) null);
			}
			throw new NSKeyValueCoding.UnknownKeyException("Key " + key + " not found", target, key);
		}
		catch (IllegalArgumentException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
		catch (IllegalAccessException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
		catch (InvocationTargetException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}

	private static Field accessibleFieldForKey(Object target, String key) {
		Field f = fieldForKey(target, key);
		if (f != null) {
			f.setAccessible(true);
		}
		return f;
	}

	private static Method accessibleMethodForKey(Object target, String key) {
		Method f = methodForKey(target, key);
		if (f != null) {
			f.setAccessible(true);
		}
		return f;
	}

	private static Field fieldForKey(Object target, String key) {
		Field result = null;
		Class c = target.getClass();
		while (c != null) {
			try {
				result = c.getDeclaredField(key);
				if (result != null) {
					return result;
				}
			}
			catch (SecurityException e) {
				throw NSForwardException._runtimeExceptionForThrowable(e);
			}
			catch (NoSuchFieldException e) {
				c = c.getSuperclass();
			}
		}
		return null;
	}

	private static Method methodForKey(Object target, String key) {
		Method result = null;
		Class c = target.getClass();
		while (c != null) {
			try {
				result = c.getDeclaredMethod(key);
				if (result != null) {
					return result;
				}
			}
			catch (SecurityException e) {
				throw NSForwardException._runtimeExceptionForThrowable(e);
			}
			catch (NoSuchMethodException e) {
				c = c.getSuperclass();
			}
		}
		return null;
	}
}