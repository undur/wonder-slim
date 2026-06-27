package er.ajax.elements;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

import er.ajax.AjaxUtils;
import er.extensions.appserver.ERXMarkerClassAssociation;
import er.extensions.foundation.ERXDynamicElementsPatches;

/**
 * A plain WOPopUpButton that renders a searchable, morph-native dropdown (wonder-select) instead of
 * a bare select. It is deliberately thin: it changes nothing about how WOPopUpButton works except
 *
 *   1. it adds the CSS marker class "ajax-popup-button" to the rendered &lt;select&gt;, and
 *   2. it ensures wonder-select.js / wonder-select.css are on the page.
 *
 * Everything else - list, item, selection, displayString, noSelectionString, etc. - is exactly
 * WOPopUpButton. The widget itself is driven entirely client-side by wonder-select.js, which
 * auto-enhances any &lt;select class="ajax-popup-button"&gt; and keeps it correct across Ajax morphs
 * via a MutationObserver (no per-page init or onRefreshComplete hook needed). So you can equally
 * just put class="ajax-popup-button" on a plain popUpButton; this element is sugar that also
 * guarantees the resources are loaded.
 *
 * The marker class is specific to wonder-select on purpose, so that if another select-enhancing
 * library is present on the page, the two don't both try to enhance the same select. The marker class
 * is merged into any author-supplied (possibly dynamic) "class" binding by
 * {@link ERXMarkerClassAssociation}.
 */
public class AjaxPopUpButton extends ERXDynamicElementsPatches.PopUpButton {

	public static final String MARKER_CLASS = "ajax-popup-button";

	public AjaxPopUpButton(String name, NSDictionary associations, WOElement element) {
		super(name, ERXMarkerClassAssociation.mergeMarkerClass(associations, MARKER_CLASS), element);
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		AjaxUtils.addScriptResourceInHead(context, response, "wonder-select.js");
		AjaxUtils.addStylesheetResourceInHead(context, response, "wonder-select.css");
		super.appendToResponse(response, context);
	}
}
