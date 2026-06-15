package er.ajax;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXWOContext;
import er.extensions.appserver.ajax.ERXAjaxApplication;

/**
 * AjaxObserveField performs an Ajax submit (and optional container morph) when a form field changes.
 * If you specify an <code>observeFieldID</code>, that single field is observed; if you also specify
 * an <code>updateContainerID</code>, that container is morphed after the change. If you do NOT
 * specify an <code>observeFieldID</code>, all descendant form fields of this component are observed
 * (and the component renders a wrapper element to find them client-side).
 * <p>
 * This is the AjaxSlim rewrite. The legacy element drove Prototype's
 * <code>Form.Element.Observer</code> + <code>Form.serialize</code>; this one emits a call into the
 * clean runtime in <code>ajaxslim.js</code> - <code>AjaxSlim.ASB.observeField(...)</code> or
 * <code>AjaxSlim.ASB.observeDescendentFields(...)</code> - which binds native
 * <code>change</code>/<code>input</code> listeners with a debounce and serializes the form with
 * <code>FormData</code>/<code>URLSearchParams</code> (no Prototype). <code>fullSubmit</code> is
 * implemented for real: <code>true</code> submits the whole form, <code>false</code> submits only
 * the changed field (partial submit, via the <code>_partialSenderID</code> protocol).
 * <p>
 * <b>Kept bindings:</b> observeFieldID, updateContainerID, action, fullSubmit, observeDelay,
 * observeFieldFrequency (both treated as a debounce now), onBeforeSubmit (gate; return false to
 * deny), onComplete / onSuccess (post-update hooks), id / elementName / class / style (the wrapper,
 * when observing descendants), name.
 * <p>
 * <b>Dropped (vs legacy):</b> the Prototype option dictionary
 * (onLoading/onException/insertion/evalScripts) - the transport that consumed it is gone.
 *
 * @binding observeFieldID the id of the field to observe (omit to observe all descendant fields)
 * @binding updateContainerID the id of the container to morph. Use "_parent" for the nearest one.
 * @binding action the action to call when the observer fires
 * @binding fullSubmit when false (default), only the changed field is submitted (partial); when true,
 *          the whole form is submitted
 * @binding observeDelay the debounce, in seconds, before the submit fires
 * @binding observeFieldFrequency legacy poll interval, in seconds; treated as a debounce
 * @binding onBeforeSubmit JS called before submitting; return false to deny the submit
 * @binding onComplete JavaScript to run after the update/morph completes
 * @binding onSuccess JavaScript to run after a successful update/morph completes
 * @binding id the id of the observe-field wrapper (only used when observing descendant fields)
 * @binding elementName wrapper element name, defaults to "div" (only used when observing descendants)
 * @binding class CSS class of the wrapper (only used when observing descendant fields)
 * @binding style CSS style of the wrapper (only used when observing descendant fields)
 * @binding name the HTML name used as the ajax submit button name (defaults to the element id)
 */
public class AjaxObserveField extends AjaxDynamicElement {

	public AjaxObserveField(String name, NSDictionary<String, WOAssociation> associations, WOElement children) {
		super(name, associations, children);
	}

	@Override
	protected void addRequiredWebResources(WOResponse response, WOContext context) {
		addScriptResourceInHead(context, response, "idiomorph.js");
		addScriptResourceInHead(context, response, "ajaxslim.js");
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		super.appendToResponse(response, context);

		WOComponent component = context.component();
		String observeFieldID = stringValueForBinding("observeFieldID", component);
		String updateContainerID = AjaxUpdateContainer.updateContainerID(this, component);
		boolean fullSubmit = booleanValueForBinding("fullSubmit", false, component);
		boolean observeFieldDescendents;
		if (observeFieldID != null) {
			observeFieldDescendents = false;
		}
		else {
			observeFieldDescendents = true;
			observeFieldID = stringValueForBinding("id", component);
			if (observeFieldID == null) {
				observeFieldID = ERXWOContext.safeIdentifierName(context, false);
			}
			String elementName = stringValueForBinding("elementName", "div", component);
			response.appendContentString("<" + elementName);
			appendTagAttributeToResponse(response, "id", observeFieldID);
			appendTagAttributeToResponse(response, "class", stringValueForBinding("class", component));
			appendTagAttributeToResponse(response, "style", stringValueForBinding("style", component));
			response.appendContentString(">");

			if (hasChildrenElements()) {
				appendChildrenToResponse(response, context);
			}
			response.appendContentString("</" + elementName + ">");
		}

		AjaxUtils.appendScriptHeader(response);
		appendObserverScript(response, context, observeFieldID, observeFieldDescendents, updateContainerID, fullSubmit);
		AjaxUtils.appendScriptFooter(response);
	}

	protected void appendObserverScript(WOResponse response, WOContext context, String observeFieldID, boolean observeDescendentFields, String updateContainerID, boolean fullSubmit) {
		WOComponent component = context.component();
		String submitButtonName = nameInContext(context, component, this);
		String observeFieldFrequency = stringValueForBinding("observeFieldFrequency", component);
		String observeDelay = stringValueForBinding("observeDelay", component);

		// partial = the negation of fullSubmit: a partial submit sends only the changed field.
		boolean partial = !fullSubmit;

		response.appendContentString(observeDescendentFields ? "AjaxSlim.ASB.observeDescendentFields(" : "AjaxSlim.ASB.observeField(");
		response.appendContentString(AjaxUtils.quote(updateContainerID));
		response.appendContentString(", ");
		response.appendContentString(AjaxUtils.quote(observeFieldID));
		response.appendContentString(", ");
		response.appendContentString(observeFieldFrequency == null ? "null" : observeFieldFrequency);
		response.appendContentString(", ");
		response.appendContentString(String.valueOf(partial));
		response.appendContentString(", ");
		response.appendContentString(observeDelay == null ? "null" : observeDelay);
		response.appendContentString(", ");
		response.appendContentString(optionsLiteral(component, submitButtonName));
		response.appendContentString(");");
	}

	/**
	 * Builds the options object literal passed to the runtime observer: the ajax submit button name
	 * (so the server invokes this element's action) plus the onBeforeSubmit gate and the
	 * onSuccess / onComplete post-update hooks.
	 */
	protected String optionsLiteral(WOComponent component, String submitButtonName) {
		String onBeforeSubmit = (String) valueForBinding("onBeforeSubmit", component);
		String onSuccess = (String) valueForBinding("onSuccess", component);
		String onComplete = (String) valueForBinding("onComplete", component);
		StringBuilder options = new StringBuilder("{");
		options.append("submitButtonName: " + AjaxUtils.quote(submitButtonName));
		if (onBeforeSubmit != null) {
			options.append(", onBeforeSubmit: function(fieldID) { return (" + onBeforeSubmit + "); }");
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

	public static String nameInContext(WOContext context, WOComponent component, AjaxDynamicElement element) {
		return element.stringValueForBinding("name", context.elementID(), component);
	}

	@Override
	public WOActionResults invokeAction(WORequest request, WOContext context) {
		WOActionResults result = null;
		WOComponent component = context.component();
		String nameInContext = nameInContext(context, component, this);
		boolean shouldHandleRequest = !context.wasActionInvoked() && context.wasFormSubmitted() && nameInContext.equals(ERXAjaxApplication.ajaxSubmitButtonName(request));
		if (shouldHandleRequest) {
			String updateContainerID = AjaxUpdateContainer.updateContainerID(this, component);
			AjaxUpdateContainer.setUpdateContainerID(request, updateContainerID);
			context.setActionInvoked(true);
			result = (WOActionResults) valueForBinding("action", component);
			if (result == null) {
				result = handleRequest(request, context);
			}
			ERXAjaxApplication.enableShouldNotStorePage();
		}
		else {
			result = invokeChildrenAction(request, context);
		}
		return result;
	}

	@Override
	public WOActionResults handleRequest(WORequest request, WOContext context) {
		WOResponse response = AjaxUtils.createResponse(request, context);
		return response;
	}
}
