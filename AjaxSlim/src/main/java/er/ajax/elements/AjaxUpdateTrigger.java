package er.ajax.elements;

import er.ajax.*;

import java.util.ArrayList;
import java.util.List;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WODynamicElement;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

/**
 * AjaxUpdateTrigger is a server-side-only element with no UI of its own: when it is rendered into a
 * response, it forces named {@link AjaxUpdateContainer}s elsewhere on the page to refresh. This is
 * useful when a central parent controls several containers - e.g. multiple editable areas where only
 * one may be in edit mode at a time: putting an AjaxUpdateTrigger inside the edit view lets it tell
 * all the others to re-render their (now non-editable) state.
 * <p>
 * This is the AjaxSlim rewrite. The legacy element emitted <code>new Ajax.Updater(...)</code> per
 * target; this one emits <code>AjaxSlim.AUC.update('id')</code> per target (guarded by an existence
 * check so a missing container is silently skipped). Each update goes through the same fetch + morph
 * core as a normal container refresh, so a target with <code>data-morph="true"</code> is morphed.
 * <p>
 * <b>Dropped (vs legacy):</b> the <code>evalScripts</code> binding (the fetch path always runs the
 * fragment's scripts, isolated per-script).
 *
 * @binding updateContainerID the update container(s) to refresh - a single id, a {@code ";"}-joined
 *          set, or a {@code List} of ids; {@code "_parent"} targets the nearest enclosing container.
 *          Resolved by {@link AjaxUpdateContainer#updateContainerID(Object)}, exactly like every other
 *          element's {@code updateContainerID} binding.
 * @binding updateContainerIDs deprecated alias for {@code updateContainerID} (which now also accepts a
 *          {@code List}); read and resolved the same way. Kept only for backward compatibility with
 *          existing templates - prefer {@code updateContainerID}.
 *
 * @author mschrag
 */
public class AjaxUpdateTrigger extends WODynamicElement {
	private WOAssociation _updateContainerID;

	// FIXME: Remove. Deprecated - superseded by updateContainerID, which now accepts a List too, so
	// the separate "updateContainerIDs" binding is redundant. Kept only so existing templates that
	// bind updateContainerIDs keep working; drop this field and its branch below once nothing uses it.
	private WOAssociation _updateContainerIDs_deprecated;

	public AjaxUpdateTrigger(String name, NSDictionary<String, WOAssociation> associations, WOElement template) {
		super(name, associations, template);
		_updateContainerID = associations.objectForKey("updateContainerID");
		_updateContainerIDs_deprecated = associations.objectForKey("updateContainerIDs");
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		super.appendToResponse(response, context);
		final WOComponent component = context.component();
		final List<String> containersToUpdate = new ArrayList<>();

		// updateContainerID is resolved by the same central helper every other element uses, so it
		// accepts a single id, a ";"-joined set, or a List - all normalized to the canonical
		// ";"-joined string, which we split back out to iterate.
		addResolved(containersToUpdate, _updateContainerID, component);

		// FIXME: Remove. Deprecated updateContainerIDs binding (see field above).
		addResolved(containersToUpdate, _updateContainerIDs_deprecated, component);

		if (!containersToUpdate.isEmpty()) {
			AjaxUtils.appendScriptHeader(response);
			for (String nextUpdateContainerID : containersToUpdate) {
				response.appendContentString("if (document.getElementById('" + nextUpdateContainerID + "')) { ");
				response.appendContentString("AjaxSlim.AUC.update('" + nextUpdateContainerID + "');");
				response.appendContentString(" }\n");
			}
			AjaxUtils.appendScriptFooter(response);
		}
	}

	/**
	 * Resolves the given association's value through {@link AjaxUpdateContainer#updateContainerID(Object)}
	 * (which accepts a String, a ";"-joined set, or a List) and adds each container id to {@code target}.
	 * A null association, or a value that resolves to null, is a no-op.
	 */
	private static void addResolved(List<String> target, WOAssociation association, WOComponent component) {
		if (association == null) {
			return;
		}
		final String resolved = AjaxUpdates.updateContainerID(association.valueInComponent(component));
		if (resolved == null) {
			return;
		}
		for (String id : resolved.split(AjaxUpdates.MULTI_UPDATE_SEPARATOR)) {
			// String.split on an empty string yields a single "" element; skip that artifact.
			if (!id.isEmpty()) {
				target.add(id);
			}
		}
	}
}
