package er.ajax.elements;

import er.ajax.*;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.components.ERXWOForm;
import er.extensions.foundation.ERXProperties;

/**
 * AjaxSubmitButton behaves like a WOSubmitButton except that it submits its form in the background
 * via Ajax, then morphs the result into a target container (<code>updateContainerID</code>) or
 * replaces a region (<code>replaceID</code>).
 * <p>
 * This is the AjaxSlim rewrite. The legacy element emitted <code>ASB.update</code> /
 * <code>ASB.request</code> backed by Prototype's <code>Ajax.Request</code> +
 * <code>Form.serializeWithoutSubmits</code>; this one emits a call into the clean runtime in
 * <code>ajaxslim.js</code> - <code>AjaxSlim.ASB.update('id', form, options)</code> /
 * <code>AjaxSlim.ASB.request(form, options)</code> - which serializes the form with
 * <code>FormData</code>/<code>URLSearchParams</code>, POSTs via <code>fetch()</code> and morphs.
 * <p>
 * The <code>AJAX_SUBMIT_BUTTON_NAME</code> partial-submit wire contract is preserved: the button's
 * name is passed to the runtime as <code>submitButtonName</code> and appended to the submitted body,
 * exactly as {@link ERXAjaxApplication} expects.
 * <p>
 * <b>Kept bindings:</b> action, name, value, id, class, style, title, tabindex, accesskey, onClick
 * (client hook after the request), onClickBefore (gate), onClickServer (server-returned JS),
 * onComplete / onSuccess (post-update hooks), button, useButtonTag, formName, formSerializer,
 * functionName, showUI, updateContainerID, replaceID, disabled, elementName.
 * <p>
 * <b>Dropped (vs legacy):</b> all Scriptaculous effect / insertion bindings, and the Prototype
 * transport options onLoading/onFailure/evalScripts/asynchronous (the transport is gone).
 *
 * @binding name the HTML name of this submit button (optional)
 * @binding value the HTML value of this submit button (optional)
 * @binding action the action to execute when this button is pressed
 * @binding id the HTML id of this submit button
 * @binding onClick JavaScript to run on the client after the request is sent
 * @binding onClickBefore if the given expression is false, the click is ignored (e.g. confirm(..))
 * @binding onClickServer if the action returns null, this binding's value is returned as JS
 * @binding onComplete JavaScript to run after the update/morph completes
 * @binding onSuccess JavaScript to run after a successful update/morph completes
 * @binding button if false, renders a link instead of a button
 * @binding useButtonTag generate a &lt;button&gt; tag even if the global property is false
 * @binding formName if button is false, the name of the form to submit
 * @binding functionName if set, the button becomes a named JavaScript function instead
 * @binding showUI if functionName is set, the UI defaults to hidden; showUI re-enables it
 * @binding updateContainerID the update container(s) to morph after the action - a single id, a {@code ";"}-joined set, or a {@code List} of ids; {@code "_parent"} targets the nearest enclosing container (see {@link AjaxUpdateContainer#updateContainerID(Object)})
 * @binding replaceID the id of the element whose contents are replaced with the results of this action
 * @binding elementName the element name to use when rendering a link (defaults to "a")
 * @binding disabled if true, the button is disabled (defaults to false)
 * @binding ignoreActionResponse if true, the action's result is ignored and an empty/onClickServer response is returned instead
 * <p>
 * Any other attribute (class, style, title, tabindex, accesskey, data-*, role, aria-*, ...) is passed
 * through verbatim onto the rendered button - it is not a binding this element interprets, so it is not
 * listed above.
 *
 * @property er.extensions.foundation.ERXPatcher.DynamicElementsPatches.SubmitButton.useButtonTag
 */
public class AjaxSubmitButton extends AjaxDynamicElement {
	// MS: If you change this value, make sure to change it in ERXAjaxApplication
	public static final String KEY_AJAX_SUBMIT_BUTTON_NAME = "AJAX_SUBMIT_BUTTON_NAME";
	// MS: If you change this value, make sure to change it in ERXAjaxApplication and in ajaxslim.js
	public static final String KEY_PARTIAL_FORM_SENDER_ID = "_partialSenderID";

	public AjaxSubmitButton(String name, NSDictionary<String, WOAssociation> associations, WOElement children) {
		super(name, associations, children);
	}

	/**
	 * The binding names this element interprets itself - either to drive its behaviour
	 * ({@code action}, {@code button}, the {@code on*} callbacks, ...) or to emit as a specific,
	 * computed tag attribute ({@code name} becomes the submit name, {@code value} the label, ...). Every
	 * OTHER author-supplied binding is passed through verbatim onto the rendered tag by
	 * {@link #appendPassthroughAttributes} - so {@code data-*}, {@code aria-*}, {@code role}, {@code lang},
	 * ... placed on a {@code <wo:AjaxSubmitButton>} land on the button instead of being silently dropped.
	 * <p>
	 * Note this list deliberately EXCLUDES the plain HTML attributes the element used to emit by hand
	 * ({@code class}, {@code style}, {@code id}, {@code tabindex}, {@code title}) - those now flow through
	 * the generic passthrough like any other attribute. ({@code accesskey} stays handled: it is emitted
	 * only in the button branch, so leaving it as-is keeps rendering identical.)
	 */
	private static final NSArray<String> HANDLED_BINDINGS = new NSArray<>(new String[] {
		"action", "name", "value", "disabled", "accesskey", "elementName", "button", "useButtonTag",
		"showUI", "functionName", "formName", "formSerializer", "updateContainerID", "replaceID",
		"ignoreActionResponse", "onClick", "onClickBefore", "onClickServer", "onComplete", "onSuccess"
	});

	/**
	 * @return the binding names this element handles itself and must NOT pass through onto the tag.
	 */
	protected NSArray<String> handledBindingNames() {
		return HANDLED_BINDINGS;
	}

	/**
	 * Emit every author-supplied binding this element does not handle itself as a tag attribute, so
	 * arbitrary HTML attributes (data-*, aria-*, role, class, style, id, ...) land on the rendered button.
	 *
	 * @param response the response being built
	 * @param component the current component (for evaluating each association)
	 */
	protected void appendPassthroughAttributes(WOResponse response, WOComponent component) {
		NSArray<String> handled = handledBindingNames();
		for (String name : associations().allKeys()) {
			if (handled.containsObject(name)) {
				continue;
			}
			WOAssociation association = associations().objectForKey(name);
			Object value = association.valueInComponent(component);
			appendTagAttributeToResponse(response, name, value);
		}
	}

	public boolean disabledInComponent(WOComponent component) {
		return booleanValueForBinding("disabled", false, component);
	}

	public String nameInContext(WOContext context, WOComponent component) {
		return (String) valueForBinding("name", context.elementID(), component);
	}

	/**
	 * Builds the options object literal passed to ASB.update/request: the ajax submit button name
	 * (so the server invokes this button's action in a multiple-submit form), the replace flag, and
	 * the onSuccess / onComplete post-update hooks.
	 */
	protected String optionsLiteral(WOContext context, WOComponent component, boolean replace) {
		String submitButtonName = nameInContext(context, component);
		String onSuccess = (String) valueForBinding("onSuccess", component);
		String onComplete = (String) valueForBinding("onComplete", component);
		StringBuilder options = new StringBuilder("{");
		options.append("submitButtonName: " + AjaxUtils.quote(submitButtonName));
		if (replace) {
			options.append(", replace: true");
		}
		if (onSuccess != null) {
			options.append(", onSuccess: function() { " + onSuccess + " }");
		}
		if (onComplete != null) {
			options.append(", onComplete: function() { " + onComplete + " }");
		}
		options.append("}");
		return options.toString();
	}

	/**
	 * Builds the JS that performs the background submit (used both as an inline onclick and as the
	 * body of a named function). formReference is the JS expression that resolves to the form.
	 */
	protected String onClick(WOContext context, String formReference) {
		WOComponent component = context.component();
		StringBuilder buffer = new StringBuilder();

		String onClickBefore = (String) valueForBinding("onClickBefore", component);
		if (onClickBefore != null) {
			buffer.append("if (");
			buffer.append(onClickBefore);
			buffer.append(") {");
		}

		String updateContainerID = AjaxUpdateProtocol.updateContainerID(this, component);
		String replaceID = (String) valueForBinding("replaceID", component);
		String target = (updateContainerID == null) ? replaceID : updateContainerID;
		boolean replace = replaceID != null && updateContainerID == null;

		if (target != null) {
			buffer.append("AjaxSlim.ASB.update(" + AjaxUtils.quote(target) + ", " + formReference + ", ");
		}
		else {
			buffer.append("AjaxSlim.ASB.request(" + formReference + ", ");
		}
		buffer.append(optionsLiteral(context, component, replace));
		buffer.append(')');

		String onClick = (String) valueForBinding("onClick", component);
		if (onClick != null) {
			buffer.append(';');
			buffer.append(onClick);
		}
		if (onClickBefore != null) {
			buffer.append('}');
		}
		return buffer.toString();
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		WOComponent component = context.component();

		String functionName = (String) valueForBinding("functionName", null, component);
		String formName = (String) valueForBinding("formName", component);
		boolean showUI = (functionName == null || booleanValueForBinding("showUI", false, component));
		boolean showButton = showUI && booleanValueForBinding("button", true, component);
		boolean useButtonTagBinding = booleanValueForBinding("useButtonTag", false, component);
		String formReference;
		if ((!showButton || functionName != null) && formName == null) {
			formName = ERXWOForm.formName(context, null);
			if (formName == null) {
				throw new WODynamicElementCreationException("If button = false or functionName is not null, the containing form must have an explicit name.");
			}
		}
		if (formName == null) {
			formReference = "this.form";
		}
		else {
			formReference = "document." + formName;
		}

		String onClick = onClick(context, formReference);

		if (functionName != null) {
			AjaxUtils.appendScriptHeader(response);
			response.appendContentString(functionName + " = function() { " + onClick + " }\n");
			AjaxUtils.appendScriptFooter(response);
		}
		if (showUI) {
			boolean disabled = disabledInComponent(component);
			String elementName = (String) valueForBinding("elementName", "a", component);
			boolean useButtonTag = useButtonTagBinding || ERXProperties.booleanForKeyWithDefault("er.extensions.foundation.ERXPatcher.DynamicElementsPatches.SubmitButton.useButtonTag", false);

			if (showButton) {
				elementName = useButtonTag ? "button" : "input";
				response.appendContentString("<" + elementName + " ");
				appendTagAttributeToResponse(response, "type", "button");
				String name = nameInContext(context, component);
				appendTagAttributeToResponse(response, "name", name);
				appendTagAttributeToResponse(response, "value", valueForBinding("value", component));
				appendTagAttributeToResponse(response, "accesskey", valueForBinding("accesskey", component));
				if (disabled) {
					appendTagAttributeToResponse(response, "disabled", "disabled");
				}
			}
			else {
				boolean isATag = "a".equalsIgnoreCase(elementName);
				if (isATag) {
					response.appendContentString("<a href = \"javascript:void(0)\" ");
				}
				else {
					response.appendContentString("<" + elementName + " ");
				}
			}
			// Pass through every author-supplied binding this element does not handle itself - class,
			// style, id, tabindex, title, plus arbitrary data-*, aria-*, role, ... - onto the rendered
			// button, instead of forwarding only a hand-picked few. Behavioural bindings and the
			// computed attributes (name/value/onclick/...) are excluded via handledBindingNames().
			appendPassthroughAttributes(response, component);
			if (functionName == null) {
				appendTagAttributeToResponse(response, "onclick", onClick);
			}
			else {
				appendTagAttributeToResponse(response, "onclick", functionName + "()");
			}
			if (showButton && !useButtonTag) {
				response.appendContentString(" />");
			}
			else {
				response.appendContentString(">");
				if (hasChildrenElements()) {
					appendChildrenToResponse(response, context);
				}
				else {
					// No children and no value must not render the literal "null" as the button label
					// (appendContentString does a bare string append). Escaped, matching how
					// AjaxUpdateLink renders its string label.
					Object label = valueForBinding("value", component);
					if (label != null) {
						response.appendContentHTMLString(label.toString());
					}
				}
				response.appendContentString("</" + elementName + ">");
			}
		}
		super.appendToResponse(response, context);
	}

	@Override
	protected void addRequiredWebResources(WOResponse response, WOContext context) {
		addScriptResourceInHead(context, response, "idiomorph.js");
		addScriptResourceInHead(context, response, "ajaxslim.js");
	}

	@Override
	public WOActionResults invokeAction(WORequest request, WOContext context) {
		WOActionResults result = null;
		WOComponent component = context.component();

		String nameInContext = nameInContext(context, component);
		boolean shouldHandleRequest = (!disabledInComponent(component) && context.wasFormSubmitted()) && ((context.isMultipleSubmitForm() && nameInContext.equals(request.formValueForKey(KEY_AJAX_SUBMIT_BUTTON_NAME))) || !context.isMultipleSubmitForm());
		if (shouldHandleRequest) {
			String updateContainerID = AjaxUpdateProtocol.updateContainerID(this, component);
			AjaxUpdateProtocol.setUpdateContainerID(request, updateContainerID);
			context.setActionInvoked(true);
			result = handleRequest(request, context);
			ERXAjaxApplication.enableShouldNotStorePage();
		}

		return result;
	}

	@Override
	public WOActionResults handleRequest(WORequest request, WOContext context) {
		WOComponent component = context.component();
		WOActionResults result = (WOActionResults) valueForBinding("action", component);

		if (ERXAjaxApplication.isAjaxReplacement(request)) {
			AjaxUtils.setPageReplacementCacheKey(context, (String) valueForBinding("replaceID", component));
		}
		else if (result == null || booleanValueForBinding("ignoreActionResponse", false, component)) {
			WOResponse response = AjaxUtils.createResponse(request, context);
			String onClickServer = (String) valueForBinding("onClickServer", component);
			if (onClickServer != null) {
				AjaxUtils.appendScriptHeaderIfNecessary(request, response);
				response.appendContentString(onClickServer);
				AjaxUtils.appendScriptFooterIfNecessary(request, response);
			}
			result = response;
		}
		else {
			String updateContainerID = AjaxUpdateProtocol.updateContainerID(this, component);
			if (updateContainerID != null) {
				AjaxUtils.setPageReplacementCacheKey(context, updateContainerID);
			}
		}

		return result;
	}
}
