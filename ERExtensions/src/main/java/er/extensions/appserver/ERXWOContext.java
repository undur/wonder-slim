package er.extensions.appserver;

import java.net.MalformedURLException;
import java.net.URL;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOSession;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.appserver.ajax.ERXAjaxContext;
import er.extensions.foundation.ERXMutableURL;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXThreadStorage;

public class ERXWOContext extends ERXAjaxContext {

	private boolean _generateCompleteURLs;
	private boolean _generateCompleteResourceURLs;
	
	private static final String CONTEXT_KEY = "wocontext";
	private static final String CONTEXT_DICTIONARY_KEY = "ERXWOContext.dict";

	/**
	 * Register an observer for resetting currentContext() and contextDictionary() after request dispatch
	 */
	static {
		ERXNotification.ApplicationDidDispatchRequestNotification.addObserver( notification -> {
			ERXWOContext.setCurrentContext(null);
			ERXThreadStorage.removeValueForKey(CONTEXT_DICTIONARY_KEY);			
		});
	}

	public ERXWOContext(WORequest request) {
		super(request);
	}

	/**
	 * @return The existing session if any is given in the form values or URL, or else <code>null</code>
	 * 
	 * Overridden to include a check for the request's session ID
	 */
	public WOSession existingSession() {
		final String requestSessionID = _requestSessionID();

		if (!super.hasSession() && requestSessionID != null) {
			WOApplication.application().restoreSessionWithID(requestSessionID, this);
		}

		return _session();
	}

	/**
	 * @return true if there is an existing session.
	 * 
	 * Overridden to check existingSession() (the request's session ID) as well as our stored session 
	 */
	@Override
	public boolean hasSession() {
		return super.hasSession() || existingSession() != null; 
	}

	/**
	 * Turn on complete resource URL generation.
	 * 
	 * @param generateCompleteResourceURLs if true, resources will generate complete URLs.
	 */
	public void _setGenerateCompleteResourceURLs(boolean generateCompleteResourceURLs) {
		_generateCompleteResourceURLs = generateCompleteResourceURLs;
	}

	/**
	 * @return whether or not resources generate complete URLs
	 */
	public boolean _generatingCompleteResourceURLs() {
		return _generateCompleteResourceURLs;
	}
	
	@Override
	public void generateCompleteURLs() {
		super.generateCompleteURLs();
		_generateCompleteURLs = true; 
	}

	@Override
	public void generateRelativeURLs() {
		super.generateRelativeURLs();
		_generateCompleteURLs = false;
	}
	
	@Override
	public boolean doesGenerateCompleteURLs() {
		return _generateCompleteURLs;
	}

	@Override
	public String _urlWithRequestHandlerKey(String requestHandlerKey, String requestHandlerPath, String queryString, boolean isSecure, int somePort) {
		String url = super._urlWithRequestHandlerKey(requestHandlerKey, requestHandlerPath, queryString, isSecure, somePort);
		url = ERXApplication.erxApplication().rewriteURL(url);
		return url;
	}

	/**
	 * Returns a complete URL for the specified action. Works like
	 * {@link WOContext#directActionURLForActionNamed} but has one extra
	 * parameter to specify whether or not to include the current session ID
	 * in the URL. Convenient if you embed the link for the direct
	 * action into an email message and don't want to keep the session ID in it.
	 * <p>
	 * <code>actionName</code> can be either an action -- "ActionName" -- or
	 * an action on a class -- "ActionClass/ActionName". You can also specify
	 * <code>queryDict</code> to be an NSDictionary which contains form values
	 * as key/value pairs. <code>includeSessionID</code> indicates if you want
	 * to include the session ID in the URL.
	 * 
	 * @param actionName String action name
	 * @param queryDict NSDictionary containing query key/value pairs
	 * @param includeSessionID
	 *            <code>true</code>: to include the session ID (if has one), <br>
	 *            <code>false</code>: not to include the session ID
	 * @return a String containing the URL for the specified action
	 */
	public String directActionURLForActionNamed(String actionName, NSDictionary queryDict, boolean includeSessionID) {
		String url = super.directActionURLForActionNamed(actionName, queryDict);

		if (!includeSessionID) {
			url = stripSessionIDFromURL(url);
		}

		return url;
	}

	public String safeElementID() {
		return safeIdentifierName(elementID());
	}

	/**
	 * FIXME: Seems to be adding the "-" portnumber prefix when running under a local WS adaptor? Why? // Hugi 2025-10-26
	 */
	@Override
	protected String relativeURLWithRequestHandlerKey(String requestHandlerKey, String requestHandlerPath, String queryString) {
		String result = super.relativeURLWithRequestHandlerKey(requestHandlerKey, requestHandlerPath, queryString);

		if(ERXApplication.isDevelopmentModeSafe() && !WOApplication.application().isDirectConnectEnabled()) {
			final String extension = "." + WOApplication.application().applicationExtension();
			final String replace = extension + "/-" + WOApplication.application().port();

			if(!result.contains(replace) && result.contains(extension)) {
				result = result.replace(extension, replace);
			}
		}

		return result;
	}

	@Override
	public Object clone() {
		ERXWOContext context = (ERXWOContext)super.clone();
		context._setGenerateCompleteResourceURLs(_generateCompleteResourceURLs);
		return context;
	}

	public static WOContext currentContext() {
		return (WOContext) ERXThreadStorage.valueForKey(CONTEXT_KEY);
	}

	public static void setCurrentContext(Object object) {
		ERXThreadStorage.takeValueForKey(object, CONTEXT_KEY);
	}

	public static NSMutableDictionary contextDictionary() {
		NSMutableDictionary contextDictionary = (NSMutableDictionary) ERXThreadStorage.valueForKey(CONTEXT_DICTIONARY_KEY);

		if (contextDictionary == null) {
			contextDictionary = new NSMutableDictionary();
			ERXThreadStorage.takeValueForKey(contextDictionary, CONTEXT_DICTIONARY_KEY);
		}

		return contextDictionary;
	}

	/**
	 * @return A new WOContext created using a dummy WORequest.
	 */
	public static ERXWOContext newContext() {

		final ERXApplication app = ERXApplication.erxApplication();

		// Try to create a URL with a relative path into the application to mimic a real request.
		// We must create a request with a relative URL, as using an absolute URL makes the new 
		// WOContext's URL absolute, and it is then unable to render relative paths. (Long story short.)
		//
		// Note: If you configured the adaptor's WebObjectsAlias to something other than the default, 
		// make sure to also set your WOAdaptorURL property to match.  Otherwise, asking the new context 
		// the path to a direct action or component action URL will give an incorrect result.
		String requestUrl = app.cgiAdaptorURL() + "/" + app.name() + app.applicationExtension();

		try {
			URL url = new URL(requestUrl);
			requestUrl = url.getPath(); // Get just the part of the URL that is relative to the server root.
		}
		catch (MalformedURLException mue) {
			// The above should never fail.  As a last resort, using the empty string will 
			// look funny in the request, but still allow the context to use a relative url.
			requestUrl = "";
		}

		final WORequest dummyRequest = app.createRequest("GET", requestUrl, "HTTP/1.1", null, null, null);

		if (ERXProperties.booleanForKeyWithDefault("er.extensions.ERXApplication.publicHostIsSecure", false)) {
			dummyRequest.setHeader("on", "https");
		}

		return (ERXWOContext) app.createContextForRequest(dummyRequest);
	}

	/**
	 * Removes session ID query key/value pair from the given URL string.
	 * 
	 * @param url String URL
	 * @return a String with the session ID removed
	 */
	private static String stripSessionIDFromURL(String url) {

		if (url == null) {
			return null;
		}

		String sessionIdKey = WOApplication.application().sessionIdKey();
		int len = 1;
		int startpos = url.indexOf("?" + sessionIdKey);
		if (startpos < 0) {
			startpos = url.indexOf("&" + sessionIdKey);
		}
		if (startpos < 0) {
			startpos = url.indexOf("&amp;" + sessionIdKey);
			len = 5;
		}

		if (startpos >= 0) {
			int endpos = url.indexOf('&', startpos + len);
			if (endpos < 0)
				url = url.substring(0, startpos);
			else {
				int endLen = len;
				if (len == 1 && url.indexOf("&amp;") >= 0) {
					endLen = 5;
				}
				url = url.substring(0, startpos + len) + url.substring(endpos + endLen);
			}
		}

		return url;
	}

	/**
	 * Debugging help, returns the path to current component as a list of component names.
	 * 
	 * @param context the current context
	 * @return an array of component names
	 */
	public static NSArray<String> componentPath(WOContext context) {
		final NSMutableArray<String> result = new NSMutableArray<>();

		if (context != null) {
			WOComponent component = context.component();
			while (component != null) {
				if (component.name() != null) {
					result.insertObjectAtIndex(component.name(), 0);
				}
				component = component.parent();
			}
		}

		return result;
	}

	private static final String SAFE_IDENTIFIER_NAME_KEY = "ERXWOContext.safeIdentifierName";

	/**
	 * Returns a safe identifier for the current component.  If willCache is true, your
	 * component should cache the identifier name so that it does not change.  In this case,
	 * your component will be given an incrementing counter value that is unique on the 
	 * current page.  If willCache is false (because you cannot cache the value), the 
	 * identifier returned will be based on the context.elementID().  While unique on the
	 * page at any point in time, be aware that structural changes to the page can 
	 * cause the elementID of your component to change. 
	 * 
	 * @param context the WOContext
	 * @param willCache if true, you should cache the resulting value in your component
	 * @return a safe identifier name
	 */
	public static String safeIdentifierName(WOContext context, boolean willCache) {
		String safeIdentifierName;

		if (willCache) {
			final NSMutableDictionary<String, Object> pageUserInfo = ERXResponseRewriter.pageUserInfo(context);
			Integer counter = (Integer) pageUserInfo.objectForKey(SAFE_IDENTIFIER_NAME_KEY);

			if (counter == null) {
				counter = Integer.valueOf(0);
			}
			else {
				counter = Integer.valueOf(counter.intValue() + 1);
			}

			pageUserInfo.setObjectForKey(counter, SAFE_IDENTIFIER_NAME_KEY);
			safeIdentifierName = safeIdentifierName(counter.toString());
		}
		else {
			safeIdentifierName = safeIdentifierName("e_" + context.elementID());	
		}

		return safeIdentifierName;
	}
	
	/**
	 * Converts source to be suitable for use as an identifier in JavaScript.
	 * prefix is prefixed to source if the first character of source is not
	 * suitable to start an identifier (e.g. a number). Any characters in source
	 * that are not allowed in an identifier are replaced with replacement.
	 * 
	 * @see Character#isJavaIdentifierStart(char)
	 * @see Character#isJavaIdentifierPart(char)
	 * 
	 * @param source String to make into a identifier name
	 * @param prefix String to prefix source with to make it a valid identifier name
	 * @param replacement character to use to replace characters in source that are no allowed in an identifier name
	 * @return source converted to a name suitable for use as an identifier in JavaScript
	 */
	private static String safeIdentifierName(String source, String prefix, char replacement) {
		final StringBuilder b = new StringBuilder();

		// Add prefix if source does not start with valid character
		if (source == null || source.length() == 0 || !Character.isJavaIdentifierStart(source.charAt(0))) {
			b.append(prefix);
		}

		b.append(source);

		for (int i = 0; i < b.length(); i++) {
			char c = b.charAt(i);

			if (!Character.isJavaIdentifierPart(c)) {
				b.setCharAt(i, replacement);
			}
		}

		return b.toString();
	}

    /**
     * Convenience method to call safeIdentifierName(source, prefix, '_')
     * 
     * @see #safeIdentifierName(String, String, char)
     * 
     * @param source String to make into a identifier name
     * @param prefix String to prefix source with to make it a valid identifier name
     * @return source converted to a name suitable for use as an identifier in JavaScript
     */
    public static String safeIdentifierName(String source, String prefix) {
    	return safeIdentifierName(source, prefix, '_');
    }

	/**
	 * Convenience method to call safeIdentifierName(source, "_", '_')
	 *
	 * @param source String to make into a identifier name
	 * @return source converted to a name suitable for use as an identifier in JavaScript
	 */
	public static String safeIdentifierName(String source) {
		return safeIdentifierName(source, "_", '_');
	}

	/**
	 * Generates direct action URLs with support for various overrides.
	 * 
	 * @param context the context to generate the URL within
	 * @param directActionName the direct action name
	 * @param queryParameters the query parameters to append (or <code>null</code>)
	 * @param secure <code>true</code> = https, <code>false</code> = http, <code>null</code> = same as request
	 * @param includeSessionID if <code>false</code>, removes session ID from query parameters
	 * 
	 * @return the constructed direct action URL
	 */
	public static String directActionUrl(WOContext context, String directActionName, NSDictionary<String, Object> queryParameters, Boolean secure, boolean includeSessionID) {
		return directActionUrl(context, null, null, null, directActionName, queryParameters, secure, includeSessionID);
	}

	/**
	 * Generates direct action URLs with support for various overrides.
	 * 
	 * @param context the context to generate the URL within
	 * @param host the host name for the URL (or <code>null</code> for default)
	 * @param port the port number of the URL (or <code>null</code> for default)
	 * @param path the custom path prefix (or <code>null</code> for none)
	 * @param directActionName the direct action name
	 * @param queryParameters the query parameters to append (or <code>null</code>)
	 * @param secure <code>true</code> = https, <code>false</code> = http, <code>null</code> = same as request
	 * @param includeSessionID if <code>false</code>, removes session ID from query parameters
	 * 
	 * @return the constructed direct action URL
	 */
	public static String directActionUrl(WOContext context, String host, Integer port, String path, String directActionName, NSDictionary<String, Object> queryParameters, Boolean secure, boolean includeSessionID) {
		boolean completeUrls;

		boolean currentlySecure = ERXRequest.isRequestSecure(context.request());
		boolean secureBool = (secure == null) ? currentlySecure : secure.booleanValue();

		if (host == null && currentlySecure == secureBool && port == null) {
			completeUrls = true;
		}
		else {
			completeUrls = context.doesGenerateCompleteURLs();
		}

		if (!completeUrls) {
			context.generateCompleteURLs();
		}

		String url;
		try {
			ERXMutableURL mu = new ERXMutableURL();
			boolean customPath = (path != null && path.length() > 0);
			if (!customPath) {
				mu.setURL(context._directActionURL(directActionName, queryParameters, secureBool, 0, false));
				if (!includeSessionID) {
					mu.removeQueryParameter(WOApplication.application().sessionIdKey());
				}
			}
			else {
				if (secureBool) {
					mu.setProtocol("https");
				}
				else {
					mu.setProtocol("http");
				}
				mu.setHost(context.request()._serverName());
				mu.setPath(path + directActionName);
				mu.setQueryParameters(queryParameters);
				if (includeSessionID && context.session().storesIDsInURLs()) {
					mu.setQueryParameter(WOApplication.application().sessionIdKey(), context.session().sessionID());
				}
			}

			if (port != null) {
				mu.setPort(port);
			}

			if (host != null && host.length() > 0) {
				mu.setHost(host);
				if (mu.protocol() == null) {
					if (secureBool) {
						mu.setProtocol("https");
					}
					else {
						mu.setProtocol("http");
					}
				}
			}

			url = mu.toExternalForm();
		}
		catch (MalformedURLException e) {
			throw new RuntimeException("Failed to create url for direct action '" + directActionName + "'.", e);
		}
		finally {
			if (!completeUrls) {
				context.generateRelativeURLs();
			}
		}

		return url;
	}
}