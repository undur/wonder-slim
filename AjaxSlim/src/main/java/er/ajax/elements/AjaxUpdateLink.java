package er.ajax.elements;

import er.ajax.*;

import java.net.MalformedURLException;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;

import er.extensions.appserver.ERXRequest;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.components.ERXComponentUtilities;
import er.extensions.foundation.ERXMutableURL;

/**
 * Updates a region of the page by firing a server action and morphing the result into a target
 * container (<code>updateContainerID</code>) or replacing a region (<code>replaceID</code>).
 * <p>
 * This is the AjaxSlim rewrite. The legacy element emitted <code>new Ajax.Updater(...)</code> /
 * <code>new Ajax.Request(...)</code>; this one emits a call into the clean, fetch-based runtime in
 * <code>ajaxslim.js</code>: <code>AjaxSlim.AUL.update('targetId', 'actionUrl', options)</code> when
 * there is a target, or <code>AjaxSlim.AUL.request('actionUrl', options)</code> when there is not.
 * The runtime does <code>fetch()</code> + Idiomorph morph (reusing the same core as
 * {@link AjaxUpdateContainer}).
 * <p>
 * <b>Kept bindings:</b> action, directActionName, updateContainerID, replaceID, elementName, string,
 * class, style, id, title, accesskey, disabled, button, function, functionName, onClick (a client
 * hook run before the request), onClickBefore (gate), onClickServer (server-returned JS),
 * ignoreActionResponse, and onComplete / onSuccess as post-update JS hooks (run after the morph).
 * <p>
 * <b>Dropped (vs legacy):</b> all Scriptaculous effect / insertion bindings
 * (effect/beforeEffect/afterEffect/*EffectID/*Duration, insertion/*InsertionDuration), and the
 * Prototype-Ajax.Request transport options onLoading/onFailure/onException/evalScripts/asynchronous
 * (the transport that consumed them is gone). onComplete/onSuccess are KEPT, repurposed as
 * "run this JS after the morph completes" hooks on the runtime - the audit showed those are the only
 * callback bindings real apps use.
 *
 * @binding action the action to call when the link executes
 * @binding directActionName the direct action to call when the link executes (requires replaceID)
 * @binding updateContainerID the update container(s) to morph after the action - a single id, a {@code ";"}-joined set, or a {@code List} of ids; {@code "_parent"} targets the nearest enclosing container (see {@link AjaxUpdateContainer#updateContainerID(Object)})
 * @binding replaceID the id of the element whose contents are replaced with the results of this action
 * @binding onComplete JavaScript to run after the update/morph completes
 * @binding onSuccess JavaScript to run after a successful update/morph completes (before onComplete)
 * @binding onClick JavaScript to run on the client before the request is sent
 * @binding onClickBefore if the given expression is false, the click is ignored (e.g. confirm(..))
 * @binding onClickServer JS returned from the server after the action when there is no update
 * @binding ignoreActionResponse if true, the action's response is thrown away
 * @binding function a custom JS function to call, passed the action url
 * @binding functionName if set, the link becomes a named JavaScript function instead of an element
 * @binding elementName the element name to use (defaults to "a")
 * @binding button if true, renders an &lt;input type="button"&gt;
 * @binding string string prepended to the contained elements
 * @binding title title of the link
 * @binding style css style of the link
 * @binding class css class of the link
 * @binding id id of the link
 * @binding accesskey hot key that should trigger the link (optional)
 * @binding disabled boolean defining if the link renders the tag
 */
public class AjaxUpdateLink extends AjaxDynamicElement {

	public AjaxUpdateLink(String name, NSDictionary<String, WOAssociation> associations, WOElement children) {
		super(name, associations, children);
	}

	/**
	 * Builds the JavaScript that fires the request. When generateFunctionWrapper is true the body is
	 * wrapped in <code>function(additionalParams) { ... }</code> for use as a named function.
	 */
	public String onClick(WOContext context, boolean generateFunctionWrapper) {
		WOComponent component = context.component();
		StringBuilder buffer = new StringBuilder();

		String onClick = (String) valueForBinding("onClick", component);
		String onClickBefore = (String) valueForBinding("onClickBefore", component);
		String updateContainerID = AjaxUpdates.updateContainerID(this, component);
		String functionName = (String) valueForBinding("functionName", component);
		String function = (String) valueForBinding("function", component);
		String replaceID = (String) valueForBinding("replaceID", component);

		if (generateFunctionWrapper) {
			buffer.append("function(additionalParams) {");
		}
		if (onClickBefore != null) {
			buffer.append("if (");
			buffer.append(onClickBefore);
			buffer.append(") {");
		}

		WOAssociation directActionNameAssociation = associations().objectForKey("directActionName");
		String actionUrl;
		if (directActionNameAssociation != null) {
			actionUrl = context._directActionURL((String) directActionNameAssociation.valueInComponent(component), ERXComponentUtilities.queryParametersInComponent(associations(), component), ERXRequest.isRequestSecure(context.request()), 0, false).replaceAll("&amp;", "&");
		}
		else {
			actionUrl = AjaxUtils.ajaxComponentActionUrl(context);
		}

		if (replaceID != null) {
			try {
				ERXMutableURL tempActionUrl = new ERXMutableURL(actionUrl);
				tempActionUrl.addQueryParameter(ERXAjaxApplication.KEY_REPLACED, "true");
				actionUrl = tempActionUrl.toExternalForm();
			}
			catch (MalformedURLException e) {
				throw NSForwardException._runtimeExceptionForThrowable(e);
			}
		}

		String actionUrlExpression = "'" + actionUrl + "'";
		if (functionName != null) {
			// A named function receives additionalParams to append to the url at call time.
			actionUrlExpression = actionUrlExpression + " + AjaxSlim.queryString(additionalParams)";
		}

		if (function != null) {
			buffer.append("return " + function + "(" + actionUrlExpression + ")");
		}
		else {
			String options = optionsLiteral(component, replaceID != null);
			String target = replaceID != null ? replaceID : updateContainerID;
			if (target == null) {
				buffer.append("AjaxSlim.AUL.request(" + actionUrlExpression + ", " + options + ")");
			}
			else {
				// A "a;b;c" target is a multi-container update (a single id never contains ';'); AUL.update
				// detects this on the client and routes to updateMany. So no special-casing here.
				buffer.append("AjaxSlim.AUL.update('" + target + "', " + actionUrlExpression + ", " + options + ")");
			}
		}

		if (onClick != null) {
			buffer.append(';');
			buffer.append(onClick);
		}
		if (onClickBefore != null) {
			buffer.append('}');
		}
		if (generateFunctionWrapper) {
			buffer.append('}');
		}

		return buffer.toString();
	}

	/**
	 * Builds the options object literal passed to AUL.update/request: the post-update hooks
	 * (onSuccess / onComplete) and the replace flag. Each hook is wrapped in a function so it runs in
	 * its own scope after the morph completes.
	 */
	protected String optionsLiteral(WOComponent component, boolean replace) {
		String onSuccess = (String) valueForBinding("onSuccess", component);
		String onComplete = (String) valueForBinding("onComplete", component);
		StringBuilder options = new StringBuilder("{");
		boolean first = true;
		if (replace) {
			options.append("replace: true");
			first = false;
		}
		if (onSuccess != null) {
			if (!first) {
				options.append(", ");
			}
			options.append("onSuccess: function() { " + onSuccess + " }");
			first = false;
		}
		if (onComplete != null) {
			if (!first) {
				options.append(", ");
			}
			options.append("onComplete: function() { " + onComplete + " }");
		}
		options.append("}");
		return options.toString();
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		WOComponent component = context.component();

		boolean disabled = booleanValueForBinding("disabled", false, component);
		Object stringValue = valueForBinding("string", component);
		String functionName = (String) valueForBinding("functionName", component);
		if (functionName == null) {
			String elementName;
			boolean button = booleanValueForBinding("button", false, component);
			if (button) {
				elementName = "input";
			}
			else {
				elementName = (String) valueForBinding("elementName", "a", component);
			}
			boolean isATag = "a".equalsIgnoreCase(elementName);
			boolean renderTags = (!disabled || !isATag);
			if (renderTags) {
				response.appendContentString("<");
				response.appendContentString(elementName);
				response.appendContentString(" ");
				if (button) {
					appendTagAttributeToResponse(response, "type", "button");
				}
				if (isATag) {
					appendTagAttributeToResponse(response, "href", "javascript:void(0);");
				}
				if (!disabled) {
					appendTagAttributeToResponse(response, "onclick", onClick(context, false));
				}
				appendTagAttributeToResponse(response, "title", valueForBinding("title", component));
				appendTagAttributeToResponse(response, "class", valueForBinding("class", component));
				appendTagAttributeToResponse(response, "style", valueForBinding("style", component));
				appendTagAttributeToResponse(response, "id", valueForBinding("id", component));
				appendTagAttributeToResponse(response, "accesskey", valueForBinding("accesskey", component));
				if (button) {
					if (stringValue != null) {
						appendTagAttributeToResponse(response, "value", stringValue);
					}
					if (disabled) {
						response.appendContentString(" disabled");
					}
				}
				response.appendContentString(">");
			}
			if (stringValue != null && !button) {
				response.appendContentHTMLString(stringValue.toString());
			}
			appendChildrenToResponse(response, context);
			if (renderTags) {
				response.appendContentString("</");
				response.appendContentString(elementName);
				response.appendContentString(">");
			}
		}
		else {
			AjaxUtils.appendScriptHeader(response);
			response.appendContentString(functionName);
			response.appendContentString(" = ");
			response.appendContentString(onClick(context, true));
			AjaxUtils.appendScriptFooter(response);
		}
		super.appendToResponse(response, context);
	}

	@Override
	protected void addRequiredWebResources(WOResponse response, WOContext context) {
		addScriptResourceInHead(context, response, "idiomorph.js");
		addScriptResourceInHead(context, response, "ajaxslim.js");
	}

	@Override
	public WOActionResults handleRequest(WORequest request, WOContext context) {
		WOComponent component = context.component();
		boolean disabled = booleanValueForBinding("disabled", false, component);
		// updateContainerID may be a single id or a ";"-joined multi-target set ("a;b;c") - either way it
		// is set as the _u value, and the update pass renders every container it names.
		String updateContainerID = AjaxUpdates.updateContainerID(this, component);
		AjaxUpdates.setUpdateContainerID(request, updateContainerID);
		WOActionResults results = null;
		if (!disabled) {
			results = (WOActionResults) valueForBinding("action", component);
		}

		if (ERXAjaxApplication.isAjaxReplacement(request)) {
			AjaxUtils.setPageReplacementCacheKey(context, (String) valueForBinding("replaceID", component));
		}
		else if (results == null || booleanValueForBinding("ignoreActionResponse", false, component)) {
			String script = (String) valueForBinding("onClickServer", component);
			if (script != null) {
				WOResponse response = AjaxUtils.createResponse(request, context);
				AjaxUtils.appendScriptHeaderIfNecessary(request, response);
				response.appendContentString(script);
				AjaxUtils.appendScriptFooterIfNecessary(request, response);
				results = response;
			}
		}
		else if (updateContainerID != null) {
			AjaxUtils.setPageReplacementCacheKey(context, updateContainerID);
		}

		return results;
	}
}
