package er.extensions.components;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXApplication;
import er.extensions.foundation.ERXProperties;

/**
 * ERXClickToOpenSupport provides the component-side implementation of
 * WOLips.framework's click-to-open support.
 * 
 * @author mschrag
 * @property er.component.clickToOpen boolean that determines if click-to-open
 *           is enabled (only enables in development mode)
 */
public class ERXClickToOpenSupport {
	/**
	 * Boolean that controls whether or not click-to-open support is enabled.
	 */
	private static Boolean _enabled;

	/**
	 * Shared pattern for the click-to-open parser.
	 */
	private static Pattern _tagPattern = Pattern.compile("<([a-z][a-z0-9_\\-]*:)?[a-z][a-z0-9_\\-]*\\s*", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);	

	/**
	 * The marker string that is temporarily written into the buffer for click-to-open support.
	 */
	private static final String _marker = "<<CLICK_TO_OPEN_MARKER>>";
	
	/**
	 * Returns whether or not click-to-open support is enabled.
	 * 
	 * @return whether or not click-to-open support is enabled
	 */
	public static boolean isEnabled() {
		// Just load click-to-open support one time ...
		if (_enabled == null) {
			if (!ERXApplication.isDevelopmentModeSafe()) {
				_enabled = Boolean.FALSE;
			}
			else {
				_enabled = Boolean.valueOf(ERXProperties.booleanForKeyWithDefault("er.component.clickToOpen", false));
			}
		}
		return ERXClickToOpenSupport._enabled.booleanValue();
	}

	/**
	 * Called before super.appendToResponse for click-to-open support.
	 * 
	 * @param response
	 *            the response
	 * @param context
	 *            the context
	 * @param clickToOpenEnabled
	 *            if false, this method is basically a no-op; if true, it
	 *            processes the response
	 */
	public static void preProcessResponse(WOResponse response, WOContext context, boolean clickToOpenEnabled) {
		if (clickToOpenEnabled) {
			response.appendContentString(ERXClickToOpenSupport._marker);
		}
	}

	/**
	 * Called after super.appendToResponse for click-to-open support.
	 * 
	 * @param component
	 *            the component being processed
	 * @param response
	 *            the response
	 * @param context
	 *            the context
	 * @param clickToOpenEnabled
	 *            if false, this method is basically a no-op; if true, it
	 *            processes the response
	 */
	public static void postProcessResponse(Class component, WOResponse response, WOContext context, boolean clickToOpenEnabled) {
		if (clickToOpenEnabled) {
			String contentStr = response.contentString();
			if (contentStr != null) {
				StringBuffer contentStringBuffer = new StringBuffer(contentStr);
				int markerIndex = contentStringBuffer.lastIndexOf(ERXClickToOpenSupport._marker);
				if (markerIndex != -1) {
					contentStringBuffer.delete(markerIndex, markerIndex + ERXClickToOpenSupport._marker.length());
					Matcher tagMatcher = ERXClickToOpenSupport._tagPattern.matcher(contentStringBuffer);
					if (tagMatcher.find(markerIndex)) {
						int attributeOffset = tagMatcher.end();
	
						String componentName = component.getName();
						String componentNameTag = "_componentName";
						if (regionMatches(contentStringBuffer, attributeOffset, componentNameTag, 0, componentNameTag.length())) {
							int openQuoteIndex = contentStringBuffer.indexOf("\"", attributeOffset);
							contentStringBuffer.insert(openQuoteIndex + 1, componentName + ",");
						}
						else {
							contentStringBuffer.insert(attributeOffset, " " + componentNameTag + " = \"" + componentName + "\" ");
						}
					}
					response.setContent(contentStringBuffer.toString());
				}
			}
		}
	}
	
	/**
	 * It's ridiculous that StringBuffer doesn't have a .regionMatches like
	 * String. This is stolen from String and re-implemented on top of
	 * StringBuffer. It's slightly slower than String's because we have to call
	 * charAt instead of just accessing the underlying array, but so be it.
	 * 
	 * @param str
	 *            the StringBuffer to compare a region of
	 * @param toffset
	 *            the starting offset of the sub-region in this string.
	 * @param other
	 *            the string argument.
	 * @param ooffset
	 *            the starting offset of the sub-region in the string argument.
	 * @param len
	 *            the number of characters to compare.
	 * @return <code>true</code> if the specified sub-region of this string
	 *         exactly matches the specified sub-region of the string argument;
	 *         <code>false</code> otherwise.
	 */
	private static boolean regionMatches(StringBuffer str, int toffset, String other, int ooffset, int len) {
		int to = toffset;
		int po = ooffset;
		// Note: toffset, ooffset, or len might be near -1>>>1.
		int count = str.length();
		int otherCount = other.length();
		if ((ooffset < 0) || (toffset < 0) || (toffset > (long) count - len) || (ooffset > (long) otherCount - len)) {
			return false;
		}
		while (len-- > 0) {
			if (str.charAt(to++) != other.charAt(po++)) {
				return false;
			}
		}
		return true;
	}
}
