package er.extensions.foundation;

import java.util.Enumeration;

import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
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

	/**
	 * Puts together a dictionary with a bunch of useful information relative to
	 * the current state when the exception occurred. Potentially added
	 * information:
	 * <ol>
	 * <li>the current page name</li>
	 * <li>the current component</li>
	 * <li>the complete hierarchy of nested components</li>
	 * <li>the requested uri</li>
	 * <li>the D2W page configuration</li>
	 * <li>the previous page list (from the WOStatisticsStore)</li>
	 * </ol>
	 * Also, in case the top-level exception was a EOGeneralAdaptorException,
	 * then you also get the failed ops and the sql exception.
	 * 
	 * @param e
	 *            exception
	 * @param context
	 *            the current context
	 * @return dictionary containing extra information for the current context.
	 */
	public static NSMutableDictionary extraInformationForExceptionInContext(Exception e, WOContext context) {
		NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
		extraInfo.addEntriesFromDictionary(ERXRuntimeUtilities.informationForContext(context));
		extraInfo.addEntriesFromDictionary(ERXRuntimeUtilities.informationForBundles());
		return extraInfo;
	}

	private static NSMutableDictionary<String, Object> informationForBundles() {
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

	private static NSMutableDictionary<String, Object> informationForContext(WOContext context) {
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
}