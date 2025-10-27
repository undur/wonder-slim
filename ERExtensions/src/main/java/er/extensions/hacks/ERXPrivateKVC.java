package er.extensions.hacks;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;

/**
 * Wrapper class for everything related to that single method for obtaining a private field/method value
 * 
 * FIXME: Delete and replace with a couple of methods // Hugi 2025-10-16
 */
public class ERXPrivateKVC {

	public static Object privateValueForKey(Object target, String key) {
		final Field field = accessibleFieldForKey(target, key);

		try {
			if (field != null) {
				return field.get(target);
			}

			final Method method = accessibleMethodForKey(target, key);

			if (method != null) {
				return method.invoke(target, (Object[]) null);
			}

			throw new NSKeyValueCoding.UnknownKeyException("Key " + key + " not found", target, key);
		}
		catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}
	
	private static Field accessibleFieldForKey(Object target, String key) {
		final Field f = fieldForKey(target, key);

		if (f != null) {
			f.setAccessible(true);
		}

		return f;
	}
	
	private static Method accessibleMethodForKey(Object target, String key) {
		final Method f = methodForKey(target, key);

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