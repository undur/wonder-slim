package er.ajax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXRedirect;
import er.extensions.appserver.ERXResponseRewriter;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.appserver.ajax.ERXAjaxSession;
import er.extensions.formatters.ERXNumberFormatter;
import er.extensions.formatters.ERXTimestampFormatter;

public class AjaxUtils {

	private final static Logger log = LoggerFactory.getLogger(AjaxUtils.class);

	/**
	 * If the value is null, this returns "null", otherwise it returns '[value]'.
	 * 
	 * @param value the value to quote
	 * @return the quoted value or "null"
	 */
	public static String quote(String value) {
		return value == null ? "null" : "'" + value.replaceAll("'", "\\\\'") + "'";
	}

	/**
	 * Return whether or not the given request is an Ajax request.
	 * 
	 * @param request the request the check
	 * @return if it an Ajax request the <code>true</code> 
	 */
	public static boolean isAjaxRequest(WORequest request) {
		return ERXAjaxApplication.isAjaxRequest(request);
	}

	public static void setPageReplacementCacheKey(WOContext _context, String _key) {
		_context.request().setHeader(_key, ERXAjaxSession.PAGE_REPLACEMENT_CACHE_LOOKUP_KEY);
	}

	/**
	 * Creates a response for the given context (which can be null), sets the charset to UTF-8, the connection to
	 * keep-alive and flags it as a Ajax request by adding an AJAX_REQUEST_KEY header. You can check this header in the
	 * session to decide if you want to save the request or not.
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
		// Encode using UTF-8, although We are actually ASCII clean as all
		// unicode data is JSON escaped using backslash u. This is less data
		// efficient for foreign character sets but it is needed to support
		// naughty browsers such as Konqueror and Safari which do not honour the
		// charset set in the response
		
		response.setHeader("Connection", "keep-alive");
		response.setHeader(ERXAjaxSession.DONT_STORE_PAGE, ERXAjaxSession.DONT_STORE_PAGE);
		return response;
	}

	public static boolean shouldHandleRequest(WORequest request, WOContext context, String containerID) {
		String elementID = context.elementID();
		String senderID = context.senderID();
		String updateContainerID = null;
		if (containerID != null) {
			if (AjaxResponse.isAjaxUpdatePass(request)) {
				updateContainerID = AjaxUpdateContainer.updateContainerID(request);
			}
		}
		boolean shouldHandleRequest = elementID != null && (elementID.equals(senderID) || (containerID != null && containerID.equals(updateContainerID)) || elementID.equals(ERXAjaxApplication.ajaxSubmitButtonName(request)));
		return shouldHandleRequest;
	}

	/**
	 * Returns an {@link er.ajax.AjaxResponse} with the given javascript as the body of the response.
	 * 
	 * @param context the context
	 * @param javascript the javascript to send
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
		if (AjaxUpdateContainer.hasUpdateContainerID(request)) {
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
		if (AjaxUpdateContainer.hasUpdateContainerID(request)) {
			AjaxUtils.appendScriptFooter(response);
		}
	}

	public static void appendScriptFooter(WOResponse response) {
		ERXResponseRewriter.appendScriptTagCloser(response);
	}

	/**
	 * Returns an Ajax component action url. Using an ajax component action urls guarantees that caching during your ajax request will be handled appropriately.
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

	/**
	 * Creates (or modifies if already created) an AjaxResponse to redirect to the passed component with a component action.
	 * Anything previously written to the AjaxResponse is preserved.
	 *
	 * @param component full page WOComponent instance to redirect to
	 */
	public static void redirectTo(WOComponent component) {
		WOContext context = component.context();
        ERXRedirect redirect = (ERXRedirect)component.pageWithName(ERXRedirect.class.getName());
        redirect.setComponent(component);
        redirect.appendToResponse(AjaxUtils.createResponse(context.request(), context), context);
	}
	
	/**
	 * <code>updateDomElement</code> appends JavaScript code to the given
	 * AjaxResponse that updates the content of the DOM Element with the given
	 * ID to the specified value. Useful if you want to update multiple small
	 * regions on a page with a single request, e.g. when an AjaxObserveField
	 * triggers an action.
	 * <p>
	 * This method is also available on instances. The example
	 * below uses the method on AjaxResponse.
	 * 
	 * <code><pre>
	 * public WOActionResults cartItemChanged() {
	 * 	ShoppingCart cart; // assume this exists
	 * 	ShoppingCartItem item; // assume this exists
	 * 	AjaxResponse response = AjaxUtils.createResponse(context().request(), context());
	 * 	response.appendScriptHeaderIfNecessary();
	 * 	response.updateDomElement(&quot;orderAmount_&quot; + item.primaryKey(), item.orderAmount(), &quot;#,##0.&quot;, null, null);
	 * 	response.updateDomElement(&quot;price_&quot; + item.primaryKey(), item.priceForOrderAmount(), &quot;#,##0.00&quot;, null, null);
	 * 	response.updateDomElement(&quot;shoppingCartPrice&quot;, cart.totalPrice(), &quot;#,##0.00&quot;, null, null);
	 * 	response.appendScriptFooterIfNecessary();
	 * 	return response;
	 * }
	 * </code></pre>
	 * 
	 * @see AjaxResponse#updateDomElement(String, Object, String, String, String)
	 * @see AjaxResponse#updateDomElement(String, Object)
	 * 
	 * @param response The response to append the JavaScript to
	 * @param id ID of the DOM element to update
	 * @param value The new value
	 * @param numberFormat optional number format to format the value with
	 * @param dateFormat optional date format to format the value with
	 * @param valueWhenEmpty string to use when value is null
	 */
	public static void updateDomElement(WOResponse response, String id, Object value, String numberFormat, String dateFormat, String valueWhenEmpty) {
		if (numberFormat != null && dateFormat != null)
			throw new IllegalArgumentException("You can only specify a numberFormat or a dateFormat, not both.");

		if (value == null && valueWhenEmpty != null) {
			value = valueWhenEmpty;
		}
		else {
			try {
				if (numberFormat != null) {
					value = ERXNumberFormatter.numberFormatterForPattern(numberFormat).format(value);
				}
				if (dateFormat != null) {
					value = ERXTimestampFormatter.dateFormatterForPattern(dateFormat).format(value);
				}
			}
			catch (Exception e) {
				log.error("Could not parse value '{}'", value, e);
				value = null;
			}
		}

		response.appendContentString("Element.update('" + id + "'," + AjaxValue.javaScriptEscaped(value) + ");");
	}

}
