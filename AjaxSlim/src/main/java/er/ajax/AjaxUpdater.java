package er.ajax;

import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSMutableArray;

/**
 * The author-facing API for driving Ajax container updates from a server-side action. These are the
 * methods an application calls from its {@code invokeAction} to say "refresh these containers" - kept
 * separate from {@link AjaxUpdateContainer} (which is a dynamic <em>element</em>, not a home for
 * invokable API).
 *
 * <p>
 * The modern path is {@link #add}/{@link #set}/{@link #clear}: the action declares which containers
 * should refresh, and they come back as {@code <ajaxslim-fragment>}s in the <em>current</em> response
 * (one round-trip, response-as-data). This works by mutating the request's update-container set (the
 * "_u" value); the existing fragment-render walk then renders exactly those containers. Adding a
 * container is equivalent to the client having sent it in {@code updateContainerID} - the server simply
 * gets the final say.
 * </p>
 *
 * <p>
 * <b>Phase contract:</b> call these from your ACTION. The set is read once when rendering begins (after
 * your action returns), so it must be final by the end of the action. You can target any container on
 * the page regardless of template position; you cannot grow the set from inside a container's own
 * rendering.
 * </p>
 *
 * <p>
 * The legacy {@link #update}/{@link #safeUpdate} emit an {@code AjaxSlim.AUC.update('x')} JavaScript
 * command per container (the client then fires a separate fetch each) - kept for backward compatibility
 * but deprecated in favour of the fragment path.
 * </p>
 */
public class AjaxUpdater {

	private AjaxUpdater() {
	}

	/**
	 * Adds {@code containerID} to the set of containers this request will refresh (AUGMENT) - on top of
	 * whatever the client already requested. The container renders as a fragment in the current response.
	 *
	 * @param containerID the id of an AjaxUpdateContainer on the current page to refresh
	 * @param context the current context
	 */
	public static void add(String containerID, WOContext context) {
		if (containerID == null) {
			return;
		}
		NSMutableArray<String> ids = new NSMutableArray<>(AjaxUpdateContainer.requestedUpdateContainerIDs(context.request()));
		if (!ids.containsObject(containerID)) {
			ids.addObject(containerID);
		}
		AjaxUpdateContainer.writeUpdateContainerSet(ids, context);
	}

	/**
	 * Replaces the set of containers this request will refresh with exactly {@code containerIDs}
	 * (REPLACE) - discarding whatever the client requested. The server gets the final, authoritative say.
	 * Pass no ids (or call {@link #clear}) to refresh nothing.
	 *
	 * @param context the current context
	 * @param containerIDs the ids of AjaxUpdateContainers on the current page to refresh
	 */
	public static void set(WOContext context, String... containerIDs) {
		NSMutableArray<String> ids = new NSMutableArray<>();
		for (String id : containerIDs) {
			if (id != null && !ids.containsObject(id)) {
				ids.addObject(id);
			}
		}
		AjaxUpdateContainer.writeUpdateContainerSet(ids, context);
	}

	/**
	 * Clears the update set so this request refreshes NOTHING (a deliberate, valid "nothing changed"
	 * outcome - not an error).
	 *
	 * @param context the current context
	 */
	public static void clear(WOContext context) {
		AjaxUpdateContainer.writeUpdateContainerSet(new NSMutableArray<>(), context);
	}

	/**
	 * Creates or updates an Ajax response so that the indicated container will get updated when the
	 * response is processed in the browser. Adds JavaScript like
	 * <code>AjaxSlim.AUC.update('SomeContainerID');</code>
	 *
	 * @param containerID the HTML ID of the container to update
	 * @param context the current context
	 *
	 * @deprecated for refactoring. This emits an {@code AjaxSlim.AUC.update('x')} JavaScript command that
	 *             makes the client fire a SEPARATE fetch per container - N extra round-trips. Prefer
	 *             {@link #add} / {@link #set}, where the action declares the set and the containers come
	 *             back in THIS response.
	 */
	@Deprecated
	public static void update(String containerID, WOContext context) {
		AjaxUtils.javascriptResponse("AjaxSlim.AUC.update('" + containerID + "');", context);
	}

	/**
	 * Like {@link #update} but guards against a missing container: if no element with that id exists on
	 * the page, does nothing.
	 *
	 * @param containerID the HTML ID of the container to update
	 * @param context the current context
	 *
	 * @deprecated for refactoring. See {@link #update} - same JS-command approach, with a guard for a
	 *             missing container. Prefer the fragment path ({@link #add} / {@link #set}).
	 */
	@Deprecated
	public static void safeUpdate(String containerID, WOContext context) {
		AjaxUtils.javascriptResponse("if (document.getElementById('" + containerID + "') != null) AjaxSlim.AUC.update('" + containerID + "');", context);
	}
}
