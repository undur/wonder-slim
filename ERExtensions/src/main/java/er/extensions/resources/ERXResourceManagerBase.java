package er.extensions.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSPropertyListSerialization;

import er.extensions.ERXP;
import er.extensions.appserver.ERXRequest;
import er.extensions.appserver.ERXWOContext;

/**
 * Base functionality for managing resources/generating resource URLs 
 */

public class ERXResourceManagerBase extends WOResourceManager {
	
	/**
	 * Content type dictionary, includes both WO's content types and our own.
	 */
	private final NSDictionary<String,String> _contentTypes;

	public ERXResourceManagerBase() {
		final NSMutableDictionary d = new NSMutableDictionary<>();
		d.putAll(super._contentTypesDictionary());
		d.putAll(additionalContentTypesFromBundle("ERExtensions"));
		d.putAll(additionalContentTypesFromBundle("app"));
		_contentTypes = d.immutableClone();
	}

	/**
	 * @return MimeTypes obtained from the file "AdditionalMimeTypes.plist" in the given bundle. Empty map if file not present/empty.
	 *
	 * Read through this resource manager rather than through WOApplication.application().resourceManager(): we are called
	 * from the constructor, and the application's resource manager is the object under construction.
	 */
	private Map<String,String> additionalContentTypesFromBundle( final String bundleName ) {
		try( final InputStream stream = inputStreamForResourceNamed( "AdditionalMimeTypes.plist", bundleName, null )) {

			if( stream == null ) {
				return Collections.emptyMap();
			}

			final Map<String, String> m = (Map<String, String>)NSPropertyListSerialization.propertyListFromString( new String( stream.readAllBytes(), StandardCharsets.UTF_8 ) );
			return m != null ? m : Collections.emptyMap();
		}
		catch( IOException e ) {
			throw new UncheckedIOException( e );
		}
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
	 * @return true if complete resource URLs should be generated in the given context.
	 *
	 * Wonder also answered false while its development-only direct-connect
	 * rewrite was on, because that rewrite matched the relative form of the
	 * application prefix and a complete URL would have escaped it. That rewrite
	 * is gone; short URLs remove the prefix from complete URLs as well (see
	 * ERXShortURLs.shorten), so the context's setting is the only input now.
	 */
	public static boolean _shouldGenerateCompleteResourceURL(WOContext context) {
		return context instanceof ERXWOContext erxc && erxc._generatingCompleteResourceURLs();
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
		if ((resourceIsSecure && ERXP.SECURE_RESOURCE_URL_PREFIX.stringValue() == null) || (!resourceIsSecure && ERXP.RESOURCE_URL_PREFIX.stringValue() == null)) {
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