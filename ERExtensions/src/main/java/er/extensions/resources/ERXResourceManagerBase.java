package er.extensions.resources;

import java.util.Map;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;

import er.extensions.appserver.ERXApplication;
import er.extensions.appserver.ERXRequest;
import er.extensions.appserver.ERXWOContext;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXUtilities;

/**
 * Contains base functionality for improved resource management 
 */

public class ERXResourceManagerBase extends WOResourceManager {
	
	/**
	 * Contains mimeTypes registered in the application
	 */
	private static final Map<String, String> _mimeTypes = _additionalMimeTypes();

	/**
	 * Overridden to supply additional mime types that are not present in the JavaWebObjects framework.
	 * 
	 * @param aResourcePath file path of the resource, or just file name of the resource, as only the extension is required
	 * @return HTTP content type for the named resource specified by <code>aResourcePath</code>
	 */
	@Override
	public String contentTypeForResourceNamed(String aResourcePath) {
		String aPathExtension = NSPathUtilities.pathExtension(aResourcePath);

		if(aPathExtension != null && aPathExtension.length() != 0) {
			String mime = _mimeTypes.get(aPathExtension.toLowerCase());

			if(mime != null) {
				return mime;
			}
		}

		return super.contentTypeForResourceNamed(aResourcePath);
	}
	
	/**
	 * Overrides the original implementation appending the additionalMimeTypes to the content types dictionary.
	 *
	 * @return a dictionary containing the original mime types supported along with the additional mime types contributed by this class.
	 */
	@Override
	public NSDictionary _contentTypesDictionary() {
		final NSMutableDictionary d = new NSMutableDictionary<>();
		d.putAll(super._contentTypesDictionary());
		d.putAll(_mimeTypes);
		return d;
	}

	private static Map<String, String> _additionalMimeTypes() {
		return (Map<String, String>)ERXUtilities.readPropertyListFromFileInFramework("AdditionalMimeTypes.plist", "ERExtensions", null, "UTF-8");
	}

	/**
	 * @return true if complete resource URLs should be generated in the given context
	 */
	public static boolean _shouldGenerateCompleteResourceURL(WOContext context) {
		return context instanceof ERXWOContext erxc && erxc._generatingCompleteResourceURLs() && !ERXApplication.erxApplication().rewriteDirectConnectURL();
	}

	/**
	 * Returns a fully qualified URL for the given partial resource URL (i.e. turns /whatever into http://server/whatever).
	 *  
	 * @param url the partial resource URL
	 * @param secure whether or not to generate a secure URL
	 * @param context the current context
	 * 
	 * @return the complete URL
	 */
	public static String _completeURLForResource(String url, Boolean secure, WOContext context) {
		final boolean requestIsSecure = ERXRequest.isRequestSecure(context.request());
		final boolean resourceIsSecure = (secure == null) ? requestIsSecure : secure.booleanValue();
	
		// FIXME: Figure out the exact purpose of this longest written condition on Earth // Hugi 2025-10-05
		if ((resourceIsSecure && ERXProperties.stringForKey("er.extensions.ERXResourceManager.secureResourceUrlPrefix") == null) || (!resourceIsSecure && ERXProperties.stringForKey("er.extensions.ERXResourceManager.resourceUrlPrefix") == null)) {
			final StringBuffer sb = new StringBuffer();
			final String serverPortStr = context.request()._serverPort();
			final int serverPort = (serverPortStr == null) ? 0 : Integer.parseInt(serverPortStr);
			context.request()._completeURLPrefix(sb, resourceIsSecure, serverPort);
			sb.append(url);
			return sb.toString();
		}

		return url;
	}
}