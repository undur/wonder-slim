package er.ajax;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

/**
 * An invisible form submit button included as the first element in an Ajax-submitted form so that
 * pressing Enter performs the action bound to this button (the form's "default" action). It renders
 * a real <code>type="submit"</code> input positioned off-screen, whose onclick does the background
 * Ajax submit and then returns false to cancel the native form post.
 * <p>
 * This is the AjaxSlim rewrite (subclass of {@link AjaxSubmitButton}). It reuses the parent's
 * <code>AjaxSlim.ASB.update</code> / <code>request</code> runtime call. The legacy element's IE&lt;9
 * keypress workaround (which relied on Prototype's <code>Event.observe</code> / <code>$$</code> /
 * <code>fireEvent</code>) is <b>dropped</b> - modern browsers fire the default submit button's click
 * on Enter natively, which is exactly what we want.
 *
 * @binding name the HTML name of this submit button (optional)
 * @binding value the HTML value of this submit button (optional)
 * @binding action the action to execute when this button is pressed
 * @binding id the HTML id of this submit button
 * @binding class the HTML class of this submit button
 * @binding accesskey hot key that should trigger the button (optional)
 * @binding disabled if true, the button does not handle the form submission on the server
 * @binding onClick JavaScript to run on the client after the request is sent
 * @binding onClickBefore if the given expression is false, the click is ignored (e.g. confirm(..))
 * @binding onClickServer if the action returns null, this binding's value is returned as JS
 * @binding ignoreActionResponse if true, the action's result is ignored and an empty/onClickServer response is returned instead
 * @binding onComplete JavaScript to run after the update/morph completes
 * @binding onSuccess JavaScript to run after a successful update/morph completes
 * @binding formName the name of the form to submit (defaults to the containing form)
 * @binding replaceID the id of an element to replace with the action's response (alternative to updateContainerID)
 * @binding updateContainerID the update container(s) to morph after the action - a single id, a {@code ";"}-joined set, or a {@code List} of ids; {@code "_parent"} targets the nearest enclosing container (see {@link AjaxUpdateContainer#updateContainerID(Object)})
 */
public class AjaxDefaultSubmitButton extends AjaxSubmitButton {

	public AjaxDefaultSubmitButton(String name, NSDictionary<String, WOAssociation> associations, WOElement children) {
		super(name, associations, children);
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		WOComponent component = context.component();

		String formName = (String) valueForBinding("formName", component);
		String formReference = "this.form";
		if (formName != null) {
			formReference = "document." + formName;
		}

		// Reuse the parent's update/request emission, then cancel the native submit so only the
		// background Ajax submit runs.
		String onClick = onClick(context, formReference) + "; return false;";

		response.appendContentString("<input ");
		appendTagAttributeToResponse(response, "tabindex", "");
		appendTagAttributeToResponse(response, "type", "submit");

		String name = nameInContext(context, component);
		appendTagAttributeToResponse(response, "name", name);
		appendTagAttributeToResponse(response, "value", valueForBinding("value", component));
		appendTagAttributeToResponse(response, "accesskey", valueForBinding("accesskey", component));
		appendTagAttributeToResponse(response, "class", valueForBinding("class", component));
		appendTagAttributeToResponse(response, "style", "position:absolute;left:-10000px");
		appendTagAttributeToResponse(response, "id", valueForBinding("id", component));
		appendTagAttributeToResponse(response, "onclick", onClick);

		response.appendContentString(" />");

		// Ensure the runtime resources are present (the parent adds them in its appendToResponse,
		// which we don't call here since we render our own markup).
		addRequiredWebResources(response, context);
	}
}
