package er.extensions.components;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Objects;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WODirectAction;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOSession;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;

import er.extensions.appserver.ERXApplication;
import er.extensions.appserver.ERXResourceManager;
import er.extensions.appserver.ERXResponse;
import er.extensions.appserver.ERXResponseRewriter;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.foundation.ERXExpiringCache;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXStringUtilities;

/**
 * Adds a style sheet to a page. You can either supply a complete URL, a file
 * and framework name or put something in the component content. The content of
 * the component is cached under a "key" binding and then delivered via a direct
 * action, so it doesn't need to get re-rendered too often.
 * 
 * @binding filename name of the style sheet
 * @binding framework name of the framework for the style sheet
 * @binding href url to the style sheet
 * @binding key key to cache the style sheet under when using the component
 *          content. Default is the sessionID. That means, you should *really*
 *          explicitly set a key, when you use more than one ERXStyleSheet using
 *          the component content method within one session
 * @binding inline when <code>true</code>, the generated link tag will be appended inline,
 *          when <code>false</code> it'll be placed in the head of the page, when unset it
 *          will be placed inline for ajax requests and in the head for regular
 *          requests
 * @binding media media name this style sheet is for
 * @property er.extensions.ERXStyleSheet.xhtml (defaults <code>true</code>) if <code>false</code>,
 *           link tags are not closed, which is compatible with older HTML
 */
// FIXME: cache should be able to cache on values of bindings, not a single key
public class ERXStyleSheet extends ERXStatelessComponent {
	/**
	 * Do I need to update serialVersionUID?
	 * See section 5.6 <cite>Type Changes Affecting Serialization</cite> on page 51 of the 
	 * <a href="http://java.sun.com/j2se/1.4/pdf/serial-spec.pdf">Java Object Serialization Spec</a>
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Public constructor
	 * 
	 * @param aContext
	 *            a context
	 */
	public ERXStyleSheet( WOContext aContext ) {
		super( aContext );
	}

	protected static ERXExpiringCache<String, WOResponse> cache( WOSession session ) {
		ERXExpiringCache<String, WOResponse> cache = (ERXExpiringCache<String, WOResponse>)session.objectForKey( "ERXStylesheet.cache" );
		if( cache == null ) {
			cache = new ERXExpiringCache<>( 60 );
			cache.startBackgroundExpiration();
			session.setObjectForKey( cache, "ERXStylesheet.cache" );
		}
		return cache;
	}

	public static class Sheet extends WODirectAction {
		public Sheet( WORequest worequest ) {
			super( worequest );
		}

		@Override
		public WOActionResults performActionNamed( String name ) {
			WOResponse response = ERXStyleSheet.cache( session() ).objectForKey( name );
			String md5 = DeprecatedMD5FromERXStringUtilities.md5Hex( response.contentString(), null );
			String queryMd5 = response.headerForKey( "checksum" );
			if (Objects.equals(md5, queryMd5)) {
				//TODO check for last-whatever time and return not modified if not changed
			}
			return response;
		}
	}

	/**
	 * Returns the complete url to the style sheet.
	 * 
	 * @return style sheet url
	 */
	public String styleSheetUrl() {
		String url = stringValueForBinding("styleSheetUrl");
		url = (url == null ? stringValueForBinding("href") : url);
		if( url == null ) {
			String name = styleSheetName();
			if( name != null ) {
				url = application().resourceManager().urlForResourceNamed( name, styleSheetFrameworkName(), languages(), context().request() );
				if( ERXResourceManager._shouldGenerateCompleteResourceURL( context() ) ) {
					url = ERXResourceManager._completeURLForResource( url, null, context() );
				}
			}
		}
		return url;
	}

	/**
	 * Returns the style sheet framework name either resolved via the binding
	 * <b>framework</b>.
	 * 
	 * @return style sheet framework name
	 */
	public String styleSheetFrameworkName() {
		String result = stringValueForBinding("styleSheetFrameworkName");
		result = (result == null ? stringValueForBinding("framework") : result);
		return result;
	}

	/**
	 * Returns the style sheet name either resolved via the binding <b>filename</b>.
	 * 
	 * @return style sheet name
	 */
	public String styleSheetName() {
		String result = stringValueForBinding("styleSheetName");
		result = (result == null ? stringValueForBinding("filename") : result);
		return result;
	}

	/**
	 * Returns key under which the stylesheet should be placed in the cache. If
	 * no key is given, the session id is used.
	 * 
	 * @return cache key
	 */
	public String styleSheetKey() {
		String result = stringValueForBinding("key");
		if( result == null ) {
			result = session().sessionID();
		}
		return result;
	}

	/**
	 * Specifies on what device the linked document will be displayed.
	 * @return media string
	 */
	public String mediaType() {
		return stringValueForBinding( "media" );
	}

	/**
	 * Returns the languages for the request.
	 * @return requested languages
	 */
	private NSArray<String> languages() {
		if( hasSession() ) {
			return session().languages();
		}
		WORequest request = context().request();
		if( request != null ) {
			return request.browserLanguages();
		}
		return null;
	}

	/**
	 * Appends the &lt;link&gt; tag, either by using the style sheet name and
	 * framework or by using the component content and then generating a link to
	 * it.
	 */
	@Override
	public void appendToResponse( WOResponse originalResponse, WOContext wocontext ) {
		String styleSheetFrameworkName = styleSheetFrameworkName();
		String styleSheetName = styleSheetName();
		boolean isResourceStyleSheet = styleSheetName != null;
		if( isResourceStyleSheet && ERXResponseRewriter.isResourceAddedToHead( wocontext, styleSheetFrameworkName, styleSheetName ) ) {
			// Skip, because this has already been added ... 
			return;
		}
		// default to inline for ajax requests
		boolean inline = booleanValueForBinding( "inline", ERXAjaxApplication.isAjaxRequest( wocontext.request() ) );
		WOResponse response = inline ? originalResponse : new ERXResponse();

		String href = styleSheetUrl();
		if( href == null ) {
			String key = styleSheetKey();
			ERXExpiringCache<String, WOResponse> cache = cache( session() );
			String md5;
			WOResponse cachedResponse = cache.objectForKey( key );
			if( cache.isStale( key ) || ERXApplication.isDevelopmentModeSafe() ) {
				cachedResponse = new ERXResponse();
				super.appendToResponse( cachedResponse, wocontext );
				// appendToResponse above will change the response of
				// "wocontext" to "newresponse". When this happens during an
				// Ajax request, it will lead to backtracking errors on
				// subsequent requests, so restore the original response "r"
				wocontext._setResponse( originalResponse );
				cachedResponse.setHeader( "text/css", "content-type" );
				cache.setObjectForKey( cachedResponse, key );
				md5 = DeprecatedMD5FromERXStringUtilities.md5Hex( cachedResponse.contentString(), null );
				cachedResponse.setHeader( md5, "checksum" );
			}
			md5 = cachedResponse.headerForKey( "checksum" );
			NSDictionary<String, Object> query = new NSDictionary<>( md5, "checksum" );
			href = wocontext.directActionURLForActionNamed( Sheet.class.getName() + "/" + key, query, wocontext.request().isSecure(), 0, false );
		}

		response._appendContentAsciiString( "<link" );

		if (styleSheetName != null && styleSheetName.toLowerCase().endsWith(".less")) {
			response._appendTagAttributeAndValue( "rel", "stylesheet/less", false );
		} else {
			response._appendTagAttributeAndValue( "rel", "stylesheet", false );
		}
		response._appendTagAttributeAndValue( "type", "text/css", false );
		response._appendTagAttributeAndValue( "href", href, false );
		response._appendTagAttributeAndValue( "media", mediaType(), false );

		if( ERXStyleSheet.shouldCloseLinkTags() ) {
			response._appendContentAsciiString( "/>" );
		} else {
			response._appendContentAsciiString( ">" );
		}
		response.appendContentString("\n");
		boolean inserted = true;
		if( !inline ) {
			String stylesheetLink = response.contentString();
			inserted = ERXResponseRewriter.insertInResponseBeforeHead( originalResponse, wocontext, stylesheetLink, ERXResponseRewriter.TagMissingBehavior.Inline );
		}
		if( inserted ) {
			if( isResourceStyleSheet ) {
				ERXResponseRewriter.resourceAddedToHead( wocontext, styleSheetFrameworkName, styleSheetName );
			}
			else if( href != null ) {
				ERXResponseRewriter.resourceAddedToHead( wocontext, null, href );
			}
		}
	}

	/**
	 * Returns whether or not XHTML link tags should be used. If false, then
	 * link tags will not be closed, which is more compatible with certain
	 * browser parsers. Set the 'er.extensions.ERXStyleSheet.xhtml' to control
	 * this property.
	 * 
	 * @return true of link tags should be closed, false otherwise
	 */
	public static boolean shouldCloseLinkTags() {
		return ERXProperties.booleanForKeyWithDefault( "er.extensions.ERXStyleSheet.xhtml", true );
	}
	
	@Deprecated
	private static class DeprecatedMD5FromERXStringUtilities {

		@Deprecated
		private static final char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

		/**
		 * Converts a byte array to hex string.
		 * 
		 * @param block
		 *            byte array
		 * @return hex string
		 */
		@Deprecated
		private static String byteArrayToHexString(byte[] block) {
			int len = block.length;
			StringBuilder buf = new StringBuilder(2 * len);
			for (int i = 0; i < len; ++i) {
				int high = ((block[i] & 0xf0) >> 4);
				int low = (block[i] & 0x0f);
				buf.append(HEX_CHARS[high]);
				buf.append(HEX_CHARS[low]);
			}
			return buf.toString();
		}
		
		/**
		 * Generate an MD5 hash from a String.
		 *
		 * @param str
		 *            the string to hash
		 * @param encoding
		 *            MD5 operates on byte arrays, so we need to know the encoding
		 *            to getBytes as
		 * @return the MD5 sum of the bytes
		 * 
		 * FIXME: Replace with standard Java methods
		 */
		@Deprecated
		private static byte[] md5(String str, String encoding) {
			byte[] bytes;
			if (str == null) {
				bytes = new byte[0];
			}
			else {
				try {
					if (encoding == null) {
						encoding = "UTF-8";
					}
					bytes = md5(new ByteArrayInputStream(str.getBytes(encoding)));
				}
				catch (UnsupportedEncodingException e) {
					throw NSForwardException._runtimeExceptionForThrowable(e);
				}
				catch (IOException e) {
					throw NSForwardException._runtimeExceptionForThrowable(e);
				}
			}
			return bytes;
		}

		/**
		 * Generate an MD5 hash from an input stream.
		 *
		 * @param in
		 *            the input stream to sum
		 * @return the MD5 sum of the bytes in file
		 * @exception IOException
		 *                if the input stream could not be read
		 *                
		 * FIXME: Replace with Java methods
		 */
		@Deprecated
		private static byte[] md5(InputStream in) throws IOException {
			try {
				java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
				byte[] buf = new byte[50 * 1024];
				int numRead;

				while ((numRead = in.read(buf)) != -1) {
					md5.update(buf, 0, numRead);
				}
				return md5.digest();
			}
			catch (java.security.NoSuchAlgorithmException e) {
				throw new NSForwardException(e);
			}
		}

		/**
		 * Generate an MD5 hash as hex from a String.
		 *
		 * @param str
		 *            the string to hash
		 * @param encoding
		 *            MD5 operates on byte arrays, so we need to know the encoding
		 *            to getBytes as
		 * @return the MD5 sum of the bytes in a hex string
		 * 
		 * FIXME: Replace with Java methods
		 */
		@Deprecated
		private static String md5Hex(String str, String encoding) {
			String hexStr;
			if (str == null) {
				hexStr = null;
			}
			else {
				hexStr = byteArrayToHexString(md5(str, encoding));
			}
			return hexStr;
		}
	}
}