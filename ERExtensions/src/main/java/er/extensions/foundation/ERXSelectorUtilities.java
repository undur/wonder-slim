package er.extensions.foundation;

import com.webobjects.foundation.NSSelector;

import er.extensions.ERXConstant;

public class ERXSelectorUtilities {

	/**
	 * Utility that returns a selector you can use with the
	 * NSNotificationCenter.
	 * 
	 * @param methodName
	 * @return A selector suitable for firing a notification
	 */
	public static NSSelector<Void> notificationSelector(String methodName) {
		return new NSSelector<Void>(methodName, ERXConstant.NotificationClassArray);
	}
}