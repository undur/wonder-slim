package er.extensions;

import java.lang.reflect.InvocationTargetException;

/**
 * Interfaces with the logging implementation. Currently we just delegate
 */

public class ERXLoggingSupport {

	private static final String LOGGING_BRIDGE_CLASS = "er.extensions.logging.ERXTemporaryLoggingBridge";

	public static void configureLoggingWithSystemProperties() {
		ERXLoggingSupport.runLoggingBridgeMethod("configureLoggingWithSystemProperties");
	}

	public static void reInitConsoleAppenders() {
		ERXLoggingSupport.runLoggingBridgeMethod("reInitConsoleAppenders");
	}

	private static void runLoggingBridgeMethod(final String methodName) {
		try {
			final Class<?> bridge = Class.forName(LOGGING_BRIDGE_CLASS);
			bridge.getMethod(methodName, null).invoke(null, null);
		}
		catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			System.out.println("====== NO LOGGING! WARNING! SILENT RUNNING!");
			System.out.println("====== Failed to locate class %s or run method %s on it".formatted(LOGGING_BRIDGE_CLASS, methodName));
			System.out.println("====== NO LOGGING! WARNING! SILENT RUNNING!");
		}
	}
}