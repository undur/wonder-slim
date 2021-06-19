package er.extensions.foundation;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSSelector;

import er.extensions.appserver.ERXWOContext;

/**
 * Collection of utilities dealing with threads and processes.
 *
 * @author ak
 * @author david
 */

public class ERXRuntimeUtilities {

	public static NSMutableDictionary<String, Object> informationForBundles() {
		NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
		NSMutableDictionary<String, Object> bundleVersions = new NSMutableDictionary<String, Object>();
		for (Enumeration bundles = NSBundle._allBundlesReally().objectEnumerator(); bundles.hasMoreElements();) {
			NSBundle bundle = (NSBundle) bundles.nextElement();
			String version = ERXProperties.versionStringForFrameworkNamed(bundle.name());
			if (version == null) {
				version = "No version provided";
			}
			bundleVersions.setObjectForKey(version, bundle.name());
		}
		extraInfo.setObjectForKey(bundleVersions, "Bundles");
		return extraInfo;
	}

	public static NSMutableDictionary<String, Object> informationForContext(WOContext context) {
		NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
		if (context != null) {
			if (context.page() != null) {
				extraInfo.setObjectForKey(context.page().name(), "CurrentPage");
				if (context.component() != null) {
					extraInfo.setObjectForKey(context.component().name(), "CurrentComponent");
					if (context.component().parent() != null) {
						extraInfo.setObjectForKey(ERXWOContext.componentPath(context), "CurrentComponentHierarchy");
					}
				}
				// If this is a D2W component, get its D2W-related information
				// from ERDirectToWeb.
				NSSelector d2wSelector = new NSSelector("d2wContext");
				if (d2wSelector.implementedByObject(context.page())) {
					try {
						Class erDirectToWebClazz = Class.forName("er.directtoweb.ERDirectToWeb");
						NSSelector infoSelector = new NSSelector("informationForContext", new Class[] { WOContext.class });
						NSDictionary d2wExtraInfo = (NSDictionary) infoSelector.invoke(erDirectToWebClazz, context);
						extraInfo.addEntriesFromDictionary(d2wExtraInfo);
					}
					catch (Exception e) {
					}
				}
			}
			if (context.request() != null) {
				extraInfo.setObjectForKey(context.request().uri(), "URL");
				if (context.request().headers() != null) {
					NSMutableDictionary<String, Object> headers = new NSMutableDictionary<>();
					for (Object key : context.request().headerKeys()) {
						String value = context.request().headerForKey(key);
						if (value != null) {
							headers.setObjectForKey(value, key.toString());
						}
					}
					extraInfo.setObjectForKey(headers, "Headers");
				}
			}
			if (context.hasSession()) {
				if (context.session().statistics() != null) {
					extraInfo.setObjectForKey(context.session().statistics(), "PreviousPageList");
				}
				extraInfo.setObjectForKey(context.session(), "Session");
			}
		}
		return extraInfo;
	}

	/**
	 * Retrieves the actual cause of an error by unwrapping them as far as
	 * possible, i.e. NSForwardException.originalThrowable(),
	 * InvocationTargetException.getTargetException() or Exception.getCause()
	 * are regarded as actual causes.
	 */
	public static Throwable originalThrowable(Throwable t) {
		if (t instanceof InvocationTargetException) {
			return originalThrowable(((InvocationTargetException) t).getTargetException());
		}
		if (t instanceof NSForwardException) {
			return originalThrowable(((NSForwardException) t).originalException());
		}
		if (t instanceof SQLException) {
			SQLException ex = (SQLException) t;
			if (ex.getNextException() != null) {
				return originalThrowable(ex.getNextException());
			}
		}
		if (t instanceof Exception) {
			Exception ex = (Exception) t;
			if (ex.getCause() != null) {
				return originalThrowable(ex.getCause());
			}
		}
		return t;
	}
}