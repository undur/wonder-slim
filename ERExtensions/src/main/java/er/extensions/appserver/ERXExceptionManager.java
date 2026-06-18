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
import com.webobjects.foundation.NSArray;
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
			return Util.formatExtraInfo(extraInfo);
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
					// Add a focused, readable view of OUR ajax page caches (page-replacement + permanent),
					// grouped by page instance with container keys and entry ages - far more useful when
					// debugging a stale-link / backtrack exception than the flat blob in the session dump.
					if (context.session() instanceof er.extensions.appserver.ajax.ERXAjaxSession ajaxSession) {
						extraInfo.setObjectForKey(ajaxSession.ajaxCacheDiagnostics(), "AjaxPageCache");
					}
				}
			}

			return extraInfo;
		}

		/**
		 * Renders the exception "extra info" dictionary as a human-readable, indented block for the log,
		 * instead of the single-line, quote-and-escape plist form that {@link NSPropertyListSerialization}
		 * produces. Almost everyone reads this straight from the log rather than parsing it, so values are
		 * laid out as {@code key: value}, nested dictionaries/arrays are indented under their key, and a
		 * multi-line string value (e.g. the {@code AjaxPageCache} report) keeps its own line breaks,
		 * indented to sit under its key. The structured dictionary handed to {@code ERXExceptionManager.log}
		 * is unchanged, so anything consuming the raw data still gets it.
		 *
		 * @param extraInfo the extra-info dictionary
		 * @return a multi-line, indented string
		 */
		public static String formatExtraInfo(NSDictionary<?, ?> extraInfo) {
			final StringBuilder out = new StringBuilder();
			appendValue(out, extraInfo, 0);
			return out.toString().stripTrailing();
		}

		private static final String INDENT = "  ";

		@SuppressWarnings("unchecked")
		private static void appendValue(StringBuilder out, Object value, int depth) {
			final String pad = INDENT.repeat(depth);
			if (value instanceof NSDictionary<?, ?> dict) {
				// Keys sorted for stable, scannable output.
				final List<String> keys = new ArrayList<>();
				for (Object k : dict.allKeys()) {
					keys.add(String.valueOf(k));
				}
				Collections.sort(keys);
				for (String key : keys) {
					final Object child = ((NSDictionary<Object, Object>) dict).objectForKey(key);
					if (isScalar(child)) {
						out.append(pad).append(key).append(": ").append(child).append('\n');
					}
					else {
						out.append(pad).append(key).append(":\n");
						appendValue(out, child, depth + 1);
					}
				}
			}
			else if (value instanceof NSArray<?> array) {
				for (Object item : array) {
					if (isScalar(item)) {
						out.append(pad).append("- ").append(item).append('\n');
					}
					else {
						out.append(pad).append("-\n");
						appendValue(out, item, depth + 1);
					}
				}
			}
			else {
				// A scalar (or multi-line string): print each line at this indent so embedded newlines
				// (the AjaxPageCache report, a Session toString) stay readable rather than collapsing.
				final String text = String.valueOf(value);
				for (String line : text.split("\n", -1)) {
					out.append(pad).append(line).append('\n');
				}
			}
		}

		/** A value we render inline as {@code key: value} - a single-line string or a non-collection. */
		private static boolean isScalar(Object value) {
			if (value instanceof NSDictionary || value instanceof NSArray) {
				return false;
			}
			return value == null || !String.valueOf(value).contains("\n");
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