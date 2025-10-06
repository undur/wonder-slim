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
 * Base functionality for managing resources/generating resource URLs 
 */

public class ERXResourceManagerBase extends WOResourceManager {
	
	/**
	 * Cached content type dictionary, includes both WO's content types and our own.
	 * 
	 * FIXME: Make final once initialization is in order // Hugi 2025-10-06
	 */
	private NSDictionary<String,String> _contentTypes;

	public ERXResourceManagerBase() {
		_contentTypes = super._contentTypesDictionary();
	}

	/**
	 * FIXME A little hack to allow us to initialize the content types after the resource manager's creation. Should happen in constructor // Hugi 2025-10-06
	 */
	public void _initContentTypes() {

		// FIXME: Read a file of the same name from the "app" namespace as well (allowing the user to add/override mimeTypes) // Hugi 2025-10-06
		var _additionalMimeTypes = (Map<String, String>)ERXUtilities.readPropertyListFromFileInFramework("AdditionalMimeTypes.plist", "ERExtensions", null, "UTF-8");
		
		final NSMutableDictionary d = new NSMutableDictionary<>();
		d.putAll(super._contentTypesDictionary());
		d.putAll(_additionalMimeTypes);
		_contentTypes = d.immutableClone();
	}

	/**
	 * Overridden to supply our additional mime types
	 * 
	 * @param resourcePath file path of the resource, or just file name of the resource, as only the extension is required
	 * @return mimetype for the named resource specified by <code>aResourcePath</code>
	 */
	@Override
	public String contentTypeForResourceNamed(String resourcePath) {
	      final String extension = NSPathUtilities.pathExtension(resourcePath);

	      if (extension != null && extension.length() != 0) {
	         final String mimeType = _contentTypes.objectForKey(extension.toLowerCase());
	         
	         if( mimeType != null ) {
	        	 return mimeType;
	         }
	      }

	      // FIXME; I find it highly dubious to return "text/plain" for an unknown mimeType. However this is WO's default and perhaps not worth changing // Hugi 2025-10-06
	      return "text/plain";
	}
	
	/**
	 * @return Dictionary mapping file extensions to mimeTypes. Overridden to include our added mimeTypes
	 */
	@Override
	public NSDictionary _contentTypesDictionary() {
		return _contentTypes;
	}

	/**
	 * @return true if complete resource URLs should be generated in the given context
	 */
	public static boolean _shouldGenerateCompleteResourceURL(WOContext context) {
		return context instanceof ERXWOContext erxc && erxc._generatingCompleteResourceURLs() && !ERXApplication.erxApplication().rewriteDirectConnectURL();
	}

	/**
	 * @return A fully qualified URL for the given partial resource URL (i.e. turns /whatever into http://server/whatever)
	 *  
	 * @param url the partial resource URL
	 * @param secure whether or not to generate a secure URL
	 * @param context the current context
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