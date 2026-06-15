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

/**
 * Renders a trigger (a button) that opens its inline content in a modal dialog.
 *
 * <p>
 * This is a slim, native rebuild of the legacy AjaxModalContainer. The legacy element wrapped the
 * iBox lightbox (Prototype-based): it relocated the content node to <code>&lt;body&gt;</code>, drew its
 * own overlay, and supported iframe / direct-action / ajax content loading plus skins and locking.
 * AjaxSlim uses the platform's native <code>&lt;dialog&gt;</code> instead, which provides the backdrop
 * (<code>::backdrop</code>), focus trapping, and Esc-to-close for free, and - crucially - keeps the
 * content in place in the DOM (no relocation, so no morph collision and no <code>data-morph-ignore</code>
 * relocation hack). The content is the element's inline children, rendered normally, so forms/links
 * inside the dialog submit and navigate exactly as they would anywhere else.
 * </p>
 *
 * <p>
 * Only the inline-content use is supported (the one real-world usage): a labelled button that pops its
 * children in a dialog with a close button. The legacy iframe (<code>href</code>),
 * <code>directActionName</code>, ajax-fetched (<code>ajax</code>/<code>action</code>) content modes and
 * the <code>skin</code>/<code>locked</code>/<code>secure</code> bindings are intentionally dropped - they
 * were unused.
 * </p>
 *
 * @binding label the text on the trigger button
 * @binding closeLabel the text on the dialog's close button (default "Close")
 * @binding title optional title shown at the top of the dialog (and as the button's title attribute)
 * @binding class CSS class(es) for the trigger button
 * @binding style inline style for the trigger button
 * @binding id optional id for the trigger button
 * @binding open if true, the dialog is open on initial render
 */
public class AjaxModalContainer extends AjaxDynamicElement {

	public AjaxModalContainer(String name, NSDictionary<String, WOAssociation> associations, WOElement template) {
		super(name, associations, template);
	}

	@Override
	protected void addRequiredWebResources(WOResponse response, WOContext context) {
		addScriptResourceInHead(context, response, "ajaxslim-modal.js");
		addStylesheetResourceInHead(context, response, "ajaxslim-modal.css");
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		WOComponent component = context.component();

		String triggerID = (String) valueForBinding("id", component);
		if (triggerID == null) {
			triggerID = ERXWOContext.safeIdentifierName(context, false);
		}
		String dialogID = triggerID + "_dialog";

		boolean open = booleanValueForBinding("open", false, component);
		Object title = valueForBinding("title", component);
		Object closeLabel = valueForBinding("closeLabel", "Close", component);

		// The trigger button.
		response.appendContentString("<button type=\"button\"");
		appendTagAttributeToResponse(response, "id", triggerID);
		appendTagAttributeToResponse(response, "class", valueForBinding("class", component));
		appendTagAttributeToResponse(response, "style", valueForBinding("style", component));
		appendTagAttributeToResponse(response, "title", title);
		appendTagAttributeToResponse(response, "data-ajaxslim-modal-open", dialogID);
		response.appendContentString(">");
		response.appendContentString(escape(valueForBinding("label", "", component).toString()));
		response.appendContentString("</button>");

		// The native dialog holding the inline content.
		response.appendContentString("<dialog class=\"ajaxslim-modal\"");
		appendTagAttributeToResponse(response, "id", dialogID);
		if (open) {
			// rendered-open: the JS opens it on init (showModal can't run before the node is connected)
			appendTagAttributeToResponse(response, "data-ajaxslim-modal-openonload", "true");
		}
		response.appendContentString(">");

		response.appendContentString("<div class=\"ajaxslim-modal-body\">");
		if (title != null) {
			response.appendContentString("<h3 class=\"ajaxslim-modal-title\">");
			response.appendContentString(escape(title.toString()));
			response.appendContentString("</h3>");
		}
		appendChildrenToResponse(response, context);
		response.appendContentString("</div>");

		response.appendContentString("<form method=\"dialog\" class=\"ajaxslim-modal-footer\">");
		response.appendContentString("<button type=\"submit\" class=\"ajaxslim-modal-close\">");
		response.appendContentString(escape(closeLabel.toString()));
		response.appendContentString("</button>");
		response.appendContentString("</form>");

		response.appendContentString("</dialog>");

		super.appendToResponse(response, context);
	}

	/**
	 * This element renders only client-side behaviour (open/close via native &lt;dialog&gt;); it does
	 * not handle ajax requests of its own.
	 */
	@Override
	public WOActionResults handleRequest(WORequest request, WOContext context) {
		return null;
	}

	private static String escape(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
