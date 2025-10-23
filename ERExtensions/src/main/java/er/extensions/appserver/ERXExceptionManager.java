package er.extensions.appserver;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;

/**
 * Keeps track of exceptions caught by ERXApplication.handleException()
 */

public class ERXExceptionManager {

	/**
	 * A class for keeping info about our thrown exception, along with some metadata 
	 */
	public record LoggedException( Throwable throwable, LocalDateTime dateTime, String id, NSDictionary extraInfo ) {
		
		public String extraInfoString() {
			return NSPropertyListSerialization.stringFromPropertyList(extraInfo);
		}
		
		public String stackTraceString() {
			final StringWriter sw = new StringWriter();
			throwable().printStackTrace(new PrintWriter(sw));
			return sw.toString();
		}
	}

	private final List<LoggedException> _loggedExceptions = Collections.synchronizedList(new ArrayList<>());

	/**
	 * @return All exceptions handled by ERXApplication.handleException()
	 */
	public List<LoggedException> loggedExceptions() {
		return _loggedExceptions;
	}

	/**
	 * @return an exception logged with the given data
	 */
	public LoggedException log( Throwable throwable, LocalDateTime dateTime, String id, NSDictionary extraInfo  ) {
		var l = new LoggedException(throwable, dateTime, id, extraInfo );
		_loggedExceptions.add(l);
		return l;
	}
	
	/**
	 * @return A list of the types of thrown exceptions
	 */
	public List<?> exceptionClasses() {
		final List<?> exceptionClasses = _loggedExceptions
				.stream()
				.map(LoggedException::throwable)
				.map(Throwable::getClass)
				.distinct()
				.sorted( Comparator.comparing(Class::getName))
				.toList();

		return exceptionClasses;
	}
	
	public static class Util {

		/**
		 * Puts together a dictionary with a bunch of useful information relative to
		 * the current state when the exception occurred. Potentially added information:
		 * 
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
		 * @param context the current context
		 * @return dictionary containing extra information for the current context.
		 */
		public static NSMutableDictionary extraInformationForExceptionInContext(WOContext context) {
			final NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
			extraInfo.addEntriesFromDictionary(informationForContext(context));
			extraInfo.addEntriesFromDictionary(informationForBundles());
			return extraInfo;
		}

		private static NSMutableDictionary<String, Object> informationForBundles() {
			final NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
			final NSMutableDictionary<String, Object> bundleVersions = new NSMutableDictionary<String, Object>();

			for (Enumeration bundles = NSBundle._allBundlesReally().objectEnumerator(); bundles.hasMoreElements();) {
				NSBundle bundle = (NSBundle) bundles.nextElement();
				String version = versionStringForFrameworkNamed(bundle.name());
				if (version == null) {
					version = "No version provided";
				}
				bundleVersions.setObjectForKey(version, bundle.name());
			}
			extraInfo.setObjectForKey(bundleVersions, "Bundles");

			return extraInfo;
		}

		private static NSMutableDictionary<String, Object> informationForContext(WOContext context) {
			final NSMutableDictionary<String, Object> extraInfo = new NSMutableDictionary<>();
			
			if (context != null) {
				if (context.page() != null) {
					extraInfo.setObjectForKey(context.page().name(), "CurrentPage");
					if (context.component() != null) {
						extraInfo.setObjectForKey(context.component().name(), "CurrentComponent");
						if (context.component().parent() != null) {
							extraInfo.setObjectForKey(ERXWOContext.componentPath(context), "CurrentComponentHierarchy");
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
	     * Returns the version string of the given framework.
	     * It checks <code>CFBundleShortVersionString</code> property 
	     * in the <code>info.plist</code> resource and returns 
	     * a trimmed version of the value.
	     * 
	     * @param frameworkName name
	     * @return version number as string; can be null-string when the framework is not found or the framework doesn't have the value of <code>CFBundleShortVersionString</code> in its <code>info.plist</code> resource.
	     * @see #webObjectsVersion()
	     */ 
		private static String versionStringForFrameworkNamed(String frameworkName) {
	        NSBundle bundle = NSBundle.bundleForName(frameworkName);

	    	if (bundle == null) {
	    		return "";
	    	}

	    	final String dictString = new String(bundle.bytesForResourcePath("Info.plist"));
	    	final NSDictionary versionDictionary = NSPropertyListSerialization.dictionaryForString(dictString);
	    	final String versionString = (String) versionDictionary.objectForKey("CFBundleShortVersionString");
	    	return versionString == null  ?  ""  :  versionString.trim(); // trim() removes the line ending char
	    }
	}
}