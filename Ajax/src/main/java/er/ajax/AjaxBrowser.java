package er.ajax;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.appserver.ERXMarkerClassAssociation;
import er.extensions.foundation.ERXPatcher;

/**
 * The MULTI-SELECT companion to {@link AjaxPopUpButton}: a plain WOBrowser that renders the
 * searchable, morph-native wonder-select widget instead of a bare multi-select. It is deliberately
 * thin - it changes nothing about how WOBrowser works except
 *
 *   1. it adds the CSS marker class "ajax-popup-button" to the rendered &lt;select multiple&gt;, and
 *   2. it ensures wonder-select.js / wonder-select.css are on the page.
 *
 * Everything else - list, item, selections, displayString, multiple, etc. - is exactly WOBrowser.
 * The SAME wonder-select.js drives both this and AjaxPopUpButton: it reads the &lt;select&gt;'s
 * multiple attribute and renders removable tags for multi-select, a single value for single-select.
 * So WO's own single (WOPopUpButton) vs multi (WOBrowser) split is mirrored by AjaxPopUpButton vs
 * AjaxBrowser, over one shared widget.
 *
 * As with AjaxPopUpButton, you can equally just put class="ajax-popup-button" on a plain browser;
 * this element is sugar that also guarantees the resources are loaded. The marker class is specific
 * to wonder-select on purpose, so it can coexist with another select-enhancing library on the same
 * page without both enhancing the same select.
 */
public class AjaxBrowser extends ERXPatcher.DynamicElementsPatches.Browser {

	public static final String MARKER_CLASS = "ajax-popup-button";

	public AjaxBrowser(String name, NSDictionary associations, WOElement element) {
		super(name, withWonderSelectAssociations(associations), element);
	}

	/**
	 * Merges the marker class into the "class" binding (via {@link ERXMarkerClassAssociation}) and
	 * surfaces noSelectionString as a "data-placeholder" attribute. Unlike WOPopUpButton, WOBrowser
	 * renders no "no selection" option for a multi-select, so the widget can't read the placeholder text
	 * from an option - data-placeholder is how wonder-select's placeholderFor() picks it up.
	 */
	private static NSDictionary withWonderSelectAssociations(NSDictionary associations) {
		NSMutableDictionary<String, WOAssociation> merged = ERXMarkerClassAssociation.mergeMarkerClass(associations, MARKER_CLASS);
		// Surface noSelectionString as data-placeholder so the multi-select widget can show it when
		// nothing is selected. (noSelectionString is not a real WOBrowser binding, so we don't pass
		// it through as-is - we map it to a plain HTML attribute the widget reads.)
		WOAssociation noSelection = (WOAssociation) merged.removeObjectForKey("noSelectionString");
		if (noSelection != null && merged.objectForKey("data-placeholder") == null) {
			merged.setObjectForKey(noSelection, "data-placeholder");
		}
		return merged;
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		AjaxUtils.addScriptResourceInHead(context, response, "wonder-select.js");
		AjaxUtils.addStylesheetResourceInHead(context, response, "wonder-select.css");
		super.appendToResponse(response, context);
	}
}
