package er.extensions.appserver;

import java.util.Map;

import com.webobjects.appserver.WOResourceManager;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;

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
}