package er.ajax;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WOPopUpButton;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

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
 * A distinct class (not Chosen's "chosen-select") is used on purpose, so wonder-select and any
 * remaining legacy Chosen popups don't both try to enhance the same select during migration.
 */
public class AjaxPopUpButton extends WOPopUpButton {

	public static final String MARKER_CLASS = "ajax-popup-button";

	public AjaxPopUpButton(String name, NSDictionary associations, WOElement element) {
		super(name, withMarkerClass(associations), element);
	}

	/**
	 * Returns a copy of the associations with the marker class merged into the "class" binding,
	 * preserving any class the developer set (which may be dynamic) and appending the marker at
	 * render time.
	 */
	@SuppressWarnings("unchecked")
	private static NSDictionary withMarkerClass(NSDictionary associations) {
		NSMutableDictionary<String, WOAssociation> merged = associations == null
				? new NSMutableDictionary<>()
				: associations.mutableClone();
		WOAssociation existing = (WOAssociation) merged.objectForKey("class");
		if (existing == null) {
			merged.setObjectForKey(WOAssociation.associationWithValue(MARKER_CLASS), "class");
		}
		else {
			merged.setObjectForKey(new MarkerClassAssociation(existing), "class");
		}
		return merged;
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		AjaxUtils.addScriptResourceInHead(context, response, "wonder-select.js");
		AjaxUtils.addStylesheetResourceInHead(context, response, "wonder-select.css");
		super.appendToResponse(response, context);
	}

	/**
	 * Wraps the developer's "class" association and appends the marker class to whatever it resolves
	 * to at render time, so a dynamic class binding still keeps working.
	 */
	private static class MarkerClassAssociation extends WOAssociation {

		private final WOAssociation _delegate;

		MarkerClassAssociation(WOAssociation delegate) {
			_delegate = delegate;
		}

		@Override
		public Object valueInComponent(WOComponent component) {
			Object value = _delegate.valueInComponent(component);
			String base = value == null ? "" : value.toString().trim();
			return base.isEmpty() ? MARKER_CLASS : base + " " + MARKER_CLASS;
		}

		@Override
		public boolean isValueConstant() {
			return _delegate.isValueConstant();
		}

		@Override
		public boolean isValueSettable() {
			return false;
		}

		@Override
		public String keyPath() {
			return _delegate.keyPath();
		}

		@Override
		public String bindingInComponent(WOComponent component) {
			return _delegate.bindingInComponent(component);
		}
	}
}
