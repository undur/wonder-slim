package er.extensions.components;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
import er.extensions.appserver.ERXResponseRewriter;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.foundation.ERXExpiringCache;
import er.extensions.resources.ERXResourceManagerBase;

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
 */

// FIXME: cache should be able to cache on values of bindings, not a single key
// FIXME: Shouldn't this be a dynamic element rather than a component? // Hugi 2022-03-12

public class ERXStyleSheet extends ERXStatelessComponent {

	public ERXStyleSheet( WOContext aContext ) {
		super( aContext );
	}

	private static ERXExpiringCache<String, WOResponse> cache( WOSession session ) {
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
			return ERXStyleSheet.cache( session() ).objectForKey( name );
		}
	}

	/**
	 * @return Returns the complete url to the style sheet.
	 */
	private String styleSheetUrl() {
		String url = stringValueForBinding("styleSheetUrl");
		
		if( url == null ) {
			url = stringValueForBinding("href");
		}

		if( url == null ) {
			String name = styleSheetName();
			if( name != null ) {
				url = application().resourceManager().urlForResourceNamed( name, styleSheetFrameworkName(), languages(), context().request() );
				if( ERXResourceManagerBase._shouldGenerateCompleteResourceURL( context() ) ) {
					url = ERXResourceManagerBase._completeURLForResource( url, null, context() );
				}
			}
		}

		return url;
	}

	/**
	 * @return The style sheet framework name either resolved via the binding <b>framework</b>.
	 */
	private String styleSheetFrameworkName() {
		String result = stringValueForBinding("styleSheetFrameworkName");
		result = (result == null ? stringValueForBinding("framework") : result);
		return result;
	}

	/**
	 * @return The style sheet name either resolved via the binding <b>filename</b>.
	 */
	private String styleSheetName() {
		String result = stringValueForBinding("styleSheetName");
		result = (result == null ? stringValueForBinding("filename") : result);
		return result;
	}

	/**
	 * @return key under which the stylesheet should be placed in the cache. If no key is given, the session id is used.
	 */
	private String styleSheetKey() {
		String result = stringValueForBinding("key");

		if( result == null ) {
			result = session().sessionID();
		}

		return result;
	}

	/**
	 * @return value of the [media] binding
	 */
	private String mediaType() {
		return stringValueForBinding( "media" );
	}

	/**
	 * @return The languages for the request.
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
	 * Appends the &lt;link&gt; tag, either by using the style sheet name and framework or by using the component content and then generating a link to it.
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
		WOResponse response = inline ? originalResponse : new WOResponse();

		String href = styleSheetUrl();
		if( href == null ) {
			String key = styleSheetKey();
			ERXExpiringCache<String, WOResponse> cache = cache( session() );
			String md5;
			WOResponse cachedResponse = cache.objectForKey( key );
			if( cache.isStale( key ) || ERXApplication.isDevelopmentModeSafe() ) {
				cachedResponse = new WOResponse();
				super.appendToResponse( cachedResponse, wocontext );
				// appendToResponse above will change the response of
				// "wocontext" to "newresponse". When this happens during an
				// Ajax request, it will lead to backtracking errors on
				// subsequent requests, so restore the original response "r"
				wocontext._setResponse( originalResponse );
				cachedResponse.setHeader( "text/css", "content-type" );
				cache.setObjectForKey( cachedResponse, key );
				md5 = md5Hex( cachedResponse.contentString() );
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
		response._appendContentAsciiString( "/>" );
//		response.appendContentString("\n"); // FIXME: Disabling this experimentally. In a propertly formatted HTML document, this stylesheet will be in it's own line anyway // Hugi 2025-06-23
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
	 * Partial rip from ERX StringUtilities
	 */
	@Deprecated
	private static String md5Hex(String str) {
		Objects.requireNonNull(str);

		try {
			MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
			byte[] buf = new byte[50 * 1024];
			int numRead;

			final ByteArrayInputStream in = new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));

			while ((numRead = in.read(buf)) != -1) {
				md5.update(buf, 0, numRead);
			}

			return HexFormat.of().formatHex(md5.digest() );
		}
		catch (java.security.NoSuchAlgorithmException | IOException e) {
			throw new NSForwardException(e);
		}
	}
}