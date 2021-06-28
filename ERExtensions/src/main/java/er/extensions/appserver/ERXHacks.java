package er.extensions.appserver;

import java.lang.reflect.Field;

public class ERXHacks {

	/**
	 * On every startup of a WOApplication _PBXProjectWatcher._sendXMLToPB() gets invoked, in
	 * an attempt to communicate with ProjectBuilder, an IDE which no longer exists.
	 * Disabling this request shaves about a second of application startup time.
	 */
	public static void disablePBXProjectWatcher() {
		try {
			Field field = com.webobjects._ideservices._PBXProjectWatcher.class.getDeclaredField("_communicationDisabled");
			field.setAccessible(true);
			field.set(null, true);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}