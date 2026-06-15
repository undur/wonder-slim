package er.ajax;

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
 * @binding updateContainerID a single update container id to refresh
 * @binding updateContainerIDs an array of update container ids to refresh
 * @binding resetAfterUpdate if true, the array of ids is cleared after appendToResponse
 *
 * @author mschrag
 */
public class AjaxUpdateTrigger extends WODynamicElement {
	private WOAssociation _updateContainerID;
	private WOAssociation _updateContainerIDs;
	private WOAssociation _resetAfterUpdate;

	public AjaxUpdateTrigger(String name, NSDictionary<String, WOAssociation> associations, WOElement template) {
		super(name, associations, template);
		_updateContainerID = associations.objectForKey("updateContainerID");
		_updateContainerIDs = associations.objectForKey("updateContainerIDs");
		_resetAfterUpdate = associations.objectForKey("resetAfterUpdate");
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		super.appendToResponse(response, context);
		final WOComponent component = context.component();
		final List<String> containersToUpdate = new ArrayList<>();

		if (_updateContainerID != null) {
			final String updateContainerID = (String) _updateContainerID.valueInComponent(component);
			if (updateContainerID != null) {
				containersToUpdate.add(updateContainerID);
			}
		}

		if (_updateContainerIDs != null) {
			@SuppressWarnings("unchecked")
			final List<String> updateContainerIDs = (List<String>) _updateContainerIDs.valueInComponent(component);
			if (updateContainerIDs != null) {
				containersToUpdate.addAll(updateContainerIDs);
			}
		}

		if (!containersToUpdate.isEmpty()) {
			AjaxUtils.appendScriptHeader(response);
			for (String nextUpdateContainerID : containersToUpdate) {
				response.appendContentString("if (document.getElementById('" + nextUpdateContainerID + "')) { ");
				response.appendContentString("AjaxSlim.AUC.update('" + nextUpdateContainerID + "');");
				response.appendContentString(" }\n");
			}
			AjaxUtils.appendScriptFooter(response);

			if (_resetAfterUpdate != null && _resetAfterUpdate.booleanValueInComponent(component)) {
				containersToUpdate.clear();
			}
		}
	}
}
