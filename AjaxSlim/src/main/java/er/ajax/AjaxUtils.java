package er.ajax;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXResponseRewriter;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.appserver.ajax.ERXAjaxSession;

/**
 * Utility methods shared by the AjaxSlim elements.
 * <p>
 * This is a trimmed port of the legacy {@code er.ajax.AjaxUtils}: the resource-injection,
 * response-construction, script-header/footer and {@code shouldHandleRequest} plumbing the slim
 * core actually needs. The legacy JSON array helpers ({@code arrayValueForObject} and friends,
 * which dragged in {@code org.json}) and the {@code updateDomElement}/{@code AjaxValue} DOM-poke
 * helpers were dropped - nothing in the slim core uses them.
 *
 * @property er.extensions.ERXResponseRewriter.javascriptTypeAttribute
 */
public class AjaxUtils {

	/** The framework AjaxSlim's web-server resources (ajaxslim.js, idiomorph.js) live in. */
	public static final String FRAMEWORK = "AjaxSlim";

	/**
	 * If the value is null, this returns "null", otherwise it returns '[value]'.
	 * @param value the value to quote
	 * @return the quoted value or "null"
	 */
	public static String quote(String value) {
		return value == null ? "null" : "'" + value.replaceAll("'", "\\\\'") + "'";
	}

	/**
	 * Converts a value (typically a DOM id) into a token safe to use as part of a JavaScript
	 * identifier. DOM ids may legally contain '-' and start with a digit (e.g. UUIDs), which are
	 * illegal in a JS identifier.
	 *
	 * @param value the value to make identifier-safe
	 * @return the value with every character outside [A-Za-z0-9_$] replaced by '_'
	 */
	public static String jsSafeIdentifier(String value) {
		return value == null ? null : value.replaceAll("[^a-zA-Z0-9_$]", "_");
	}

	/**
	 * Return whether or not the given request is an Ajax request.
	 *
	 * @param request the request the check
	 * @return <code>true</code> if it is an Ajax request
	 */
	public static boolean isAjaxRequest(WORequest request) {
		return ERXAjaxApplication.isAjaxRequest(request);
	}

	public static void setPageReplacementCacheKey(WOContext _context, String _key) {
		_context.request().setHeader(_key, ERXAjaxSession.PAGE_REPLACEMENT_CACHE_LOOKUP_KEY);
	}

	/**
	 * Creates a response for the given context (which can be null), sets the charset to UTF-8, the connection to
	 * keep-alive and flags it as a Ajax request by adding an AJAX_REQUEST_KEY header.
	 *
	 * @param request the current request
	 * @param context the current context
	 * @return a new Ajax response
	 */
	public static AjaxResponse createResponse(WORequest request, WOContext context) {
		AjaxResponse response = null;
		if (context != null && context.response() != null) {
			WOResponse existingResponse = context.response();
			if (existingResponse instanceof AjaxResponse) {
				response = (AjaxResponse) existingResponse;
			}
			else {
				response = new AjaxResponse(request, context);
				response.setHeaders(existingResponse.headers());
				response.setUserInfo(existingResponse.userInfo());
				response.appendContentString(existingResponse.contentString());
			}
		}
		if (response == null) {
			response = new AjaxResponse(request, context);
			response.setHeader("text/plain; charset=utf-8", "content-type");
		}
		if (context != null) {
			context._setResponse(response);
		}

		response.setHeader("Connection", "keep-alive");
		response.setHeader(ERXAjaxSession.DONT_STORE_PAGE, ERXAjaxSession.DONT_STORE_PAGE);
		return response;
	}

	/**
	 * Adds a script tag with a correct resource URL in the HTML head tag if it isn't already present in the response.
	 *
	 * @param context the context
	 * @param response the response to write into
	 * @param framework the framework that contains the file
	 * @param fileName the name of the javascript file to add
	 */
	public static void addScriptResourceInHead(WOContext context, WOResponse response, String framework, String fileName) {
		ERXResponseRewriter.addScriptResourceInHead(response, context, framework, fileName);
	}

	/**
	 * Calls {@link #addScriptResourceInHead(WOContext, WOResponse, String, String)} with {@value #FRAMEWORK} as framework.
	 *
	 * @param context the context
	 * @param response the response to write into
	 * @param fileName the name of the javascript file to add
	 */
	public static void addScriptResourceInHead(WOContext context, WOResponse response, String fileName) {
		AjaxUtils.addScriptResourceInHead(context, response, FRAMEWORK, fileName);
	}

	/**
	 * Calls {@link er.extensions.appserver.ERXResponseRewriter#addStylesheetResourceInHead(WOResponse, WOContext, String, String)}.
	 *
	 * @param context the context
	 * @param response the response to write into
	 * @param framework the framework that contains the file
	 * @param fileName the name of the CSS file to add
	 */
	public static void addStylesheetResourceInHead(WOContext context, WOResponse response, String framework, String fileName) {
		ERXResponseRewriter.addStylesheetResourceInHead(response, context, framework, fileName);
	}

	/**
	 * Calls {@link #addStylesheetResourceInHead(WOContext, WOResponse, String, String)} with {@value #FRAMEWORK} as framework.
	 *
	 * @param context the context
	 * @param response the response to write into
	 * @param fileName the name of the CSS file to add
	 */
	public static void addStylesheetResourceInHead(WOContext context, WOResponse response, String fileName) {
		AjaxUtils.addStylesheetResourceInHead(context, response, FRAMEWORK, fileName);
	}

	/**
	 * Calls {@link er.extensions.appserver.ERXResponseRewriter#addScriptCodeInHead(WOResponse, WOContext, String)}.
	 *
	 * @param response the response to write into
	 * @param context the context
	 * @param script the javascript code to insert
	 */
	public static void addScriptCodeInHead(WOResponse response, WOContext context, String script) {
		ERXResponseRewriter.addScriptCodeInHead(response, context, script);
	}

	public static boolean shouldHandleRequest(WORequest request, WOContext context, String containerID) {
		String elementID = context.elementID();
		String senderID = context.senderID();
		// The requested update target. With a single target this is one id (unchanged); with the
		// multi-target feature (updateContainerID="a;b;c") it is a ";"-separated set, and this AUC
		// renders its content if its id is a MEMBER of that set. isRequestedUpdateContainer() handles
		// both - for the single case it is exactly the old `containerID.equals(updateContainerID)`.
		boolean targetedContainer = false;
		if (containerID != null && AjaxResponse.isAjaxUpdatePass(request)) {
			targetedContainer = AjaxUpdateProtocol.isRequestedUpdateContainer(request, containerID);
		}
		boolean shouldHandleRequest = elementID != null && (elementID.equals(senderID) || targetedContainer || elementID.equals(ERXAjaxApplication.ajaxSubmitButtonName(request)));
		return shouldHandleRequest;
	}

	/**
	 * Returns an {@link er.ajax.AjaxResponse} with the given javascript as the body of the response.
	 *
	 * @param javascript the javascript to send
	 * @param context the context
	 * @return a new response
	 */
	public static WOResponse javascriptResponse(String javascript, WOContext context) {
		WORequest request = context.request();
		AjaxResponse response = AjaxUtils.createResponse(request, context);
		AjaxUtils.appendScriptHeaderIfNecessary(request, response);
		response.appendContentString(javascript);
		AjaxUtils.appendScriptFooterIfNecessary(request, response);
		return response;
	}

	/**
	 * Shortcut for appendScript.
	 *
	 * @param context the context
	 * @param script the script to append
	 */
	public static void appendScript(WOContext context, String script) {
		AjaxUtils.appendScript(context.request(), context.response(), script);
	}

	/**
	 * Appends the given javascript to the response, surrounding it in a script header/footer if necessary.
	 *
	 * @param request the request
	 * @param response the response
	 * @param script the script to append
	 */
	public static void appendScript(WORequest request, WOResponse response, String script) {
		AjaxUtils.appendScriptHeaderIfNecessary(request, response);
		response.appendContentString(script);
		AjaxUtils.appendScriptFooterIfNecessary(request, response);
	}

	public static void appendScriptHeaderIfNecessary(WORequest request, WOResponse response) {
		if (AjaxUpdateProtocol.hasUpdateContainerID(request)) {
			AjaxUtils.appendScriptHeader(response);
		}
		else {
			response.setHeader("text/javascript", "Content-Type");
		}
	}

	public static void appendScriptHeader(WOResponse response) {
		ERXResponseRewriter.appendScriptTagOpener(response);
	}

	public static void appendScriptFooterIfNecessary(WORequest request, WOResponse response) {
		if (AjaxUpdateProtocol.hasUpdateContainerID(request)) {
			AjaxUtils.appendScriptFooter(response);
		}
	}

	public static void appendScriptFooter(WOResponse response) {
		ERXResponseRewriter.appendScriptTagCloser(response);
	}

	/**
	 * Returns an Ajax component action url. Using an ajax component action url guarantees that caching during your
	 * ajax request will be handled appropriately.
	 *
	 * @param context the context of the request
	 * @return an ajax request url.
	 */
	public static String ajaxComponentActionUrl(WOContext context) {
		String actionUrl = context.componentActionURL();
		if (AjaxRequestHandler.useAjaxRequestHandler()) {
			actionUrl = actionUrl.replaceFirst("/" + WOApplication.application().componentRequestHandlerKey() + "/", "/" + AjaxRequestHandler.AjaxRequestHandlerKey + "/");
		}
		return actionUrl;
	}

	public static void appendTagAttributeAndValue(WOResponse response, WOContext context, WOComponent component, NSDictionary<String, WOAssociation> associations, String name) {
		AjaxUtils.appendTagAttributeAndValue(response, context, component, associations, name, null);
	}

	public static void appendTagAttributeAndValue(WOResponse response, WOContext context, WOComponent component, NSDictionary<String, WOAssociation> associations, String name, String appendValue) {
		AjaxUtils.appendTagAttributeAndValue(response, context, component, name, associations.objectForKey(name), appendValue);
	}

	public static void appendTagAttributeAndValue(WOResponse response, WOContext context, WOComponent component, String name, WOAssociation association) {
		AjaxUtils.appendTagAttributeAndValue(response, context, component, name, association, null);
	}

	public static void appendTagAttributeAndValue(WOResponse response, WOContext context, WOComponent component, String name, WOAssociation association, String appendValue) {
		if (association != null || appendValue != null) {
			String value = null;
			if (association != null) {
				value = (String) association.valueInComponent(component);
			}
			if (value == null || value.length() == 0) {
				value = appendValue;
			}
			else if (appendValue != null && appendValue.length() > 0) {
				if (!value.endsWith(";")) {
					value += ";";
				}
				value += appendValue;
			}
			if (value != null) {
				response._appendTagAttributeAndValue(name, value, true);
			}
		}
	}
}
