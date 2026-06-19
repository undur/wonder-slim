package er.extensions.appserver;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

/**
 * A {@link WOAssociation} that appends a fixed CSS marker class to whatever the developer's own
 * {@code class} binding resolves to <i>at render time</i>. This is how a dynamic-element subclass can
 * force a marker class onto its rendered tag without clobbering an author-supplied class - including a
 * dynamic one (e.g. {@code class="$someDynamicClass"}): the wrapped association is evaluated per
 * render and the marker appended to the result.
 * <p>
 * Use {@link #mergeMarkerClass(NSDictionary, String)} to fold the marker into an element's
 * associations in its constructor; it handles both the "no class binding" and "existing class binding"
 * cases. This lives in ERExtensions so the several thin marker-class elements that need it (e.g. the
 * wonder-select {@code AjaxPopUpButton} / {@code AjaxBrowser} in both the Ajax and AjaxSlim frameworks)
 * share one implementation rather than each carrying a copy.
 */
public class ERXMarkerClassAssociation extends WOAssociation {

	private final WOAssociation _delegate;
	private final String _markerClass;

	public ERXMarkerClassAssociation(WOAssociation delegate, String markerClass) {
		_delegate = delegate;
		_markerClass = markerClass;
	}

	/**
	 * Returns a copy of {@code associations} with {@code markerClass} merged into the {@code class}
	 * binding: if the developer set no class, the marker becomes a constant class; if they set one (even
	 * a dynamic one), it is wrapped so the marker is appended to whatever it resolves to at render time.
	 * The original dictionary is not modified.
	 *
	 * Returns a fresh, mutable dictionary, so a caller that needs to add more associations of its own
	 * (e.g. WOBrowser surfacing noSelectionString as data-placeholder) can keep mutating the result
	 * without copying it again.
	 *
	 * @param associations the element's associations (may be null)
	 * @param markerClass the CSS class to guarantee on the rendered tag
	 * @return a new mutable associations dictionary carrying the marker class
	 */
	@SuppressWarnings("unchecked")
	public static NSMutableDictionary<String, WOAssociation> mergeMarkerClass(NSDictionary associations, String markerClass) {
		NSMutableDictionary<String, WOAssociation> merged = associations == null
				? new NSMutableDictionary<>()
				: associations.mutableClone();
		WOAssociation existing = (WOAssociation) merged.objectForKey("class");
		if (existing == null) {
			merged.setObjectForKey(WOAssociation.associationWithValue(markerClass), "class");
		}
		else {
			merged.setObjectForKey(new ERXMarkerClassAssociation(existing, markerClass), "class");
		}
		return merged;
	}

	@Override
	public Object valueInComponent(WOComponent component) {
		Object value = _delegate.valueInComponent(component);
		String base = value == null ? "" : value.toString().trim();
		return base.isEmpty() ? _markerClass : base + " " + _markerClass;
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
