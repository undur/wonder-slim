package er.ajax;

import java.util.List;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSMutableArray;

import er.extensions.appserver.ERXWOContext;
import er.extensions.appserver.ajax.ERXAjaxApplication;

/**
 * The ajax-update targeting protocol: which {@link AjaxUpdateContainer}s a request asks to refresh, and
 * how that intent is read, written, and resolved. This is framework-wide protocol logic - every ajax
 * element consults it - so it lives in its own namespace rather than on the element class. (Not a
 * request <em>type</em>; a set of static operations over the current request/context.)
 *
 * <p>
 * Three related things live here:
 * </p>
 * <ul>
 * <li>The <b>target set</b> carried by the request as the {@code _u} value ({@code "a;b;c"}): reading
 *     it ({@link #updateContainerID(WORequest)} / {@link #requestedUpdateContainerIDs(WORequest)}),
 *     writing it ({@link #setUpdateContainerID}), and testing membership
 *     ({@link #isRequestedUpdateContainer} / {@link #isMultiUpdate}).</li>
 * <li>The <b>current-container stack</b> threaded during rendering
 *     ({@link #currentUpdateContainerID()} / {@link #setCurrentUpdateContainerID}) - what enclosing
 *     container is being rendered right now, used to resolve {@code "_parent"}.</li>
 * <li><b>Binding resolution</b>: turning an element's {@code updateContainerID} binding value (a
 *     String, a {@code List}, or the magic {@code "_parent"}) into the canonical {@code ";"}-joined
 *     id string the rest of the framework uses ({@link #updateContainerID(Object)} and overloads).</li>
 * </ul>
 *
 * <p>
 * The action-side counterpart - declaring which containers should refresh - is {@link AjaxUpdater}.
 * </p>
 */
public class AjaxUpdates {

	private AjaxUpdates() {
	}

	/** The character separating ids in a multi-target update request ({@code _u=a;b;c}). */
	public static final String MULTI_UPDATE_SEPARATOR = ";";

	/** Context-dictionary key for the current-container render stack. */
	private static final String CURRENT_UPDATE_CONTAINER_ID_KEY = "er.ajax.AjaxUpdateContainer.currentID";

	public static String updateContainerID(WORequest request) {
		return (String) ERXWOContext.contextDictionary().objectForKey(ERXAjaxApplication.KEY_UPDATE_CONTAINER_ID);
	}

	public static void setUpdateContainerID(WORequest request, String updateContainerID) {
		if (updateContainerID != null) {
			ERXWOContext.contextDictionary().setObjectForKey(updateContainerID, ERXAjaxApplication.KEY_UPDATE_CONTAINER_ID);
		}
	}

	public static boolean hasUpdateContainerID(WORequest request) {
		return AjaxUpdates.updateContainerID(request) != null;
	}

	/**
	 * True when the current update pass targets MORE THAN ONE container (the {@code _u} value is a
	 * separated set). Drives the framed-fragment response format; a single-target update is unaffected.
	 */
	public static boolean isMultiUpdate(WORequest request) {
		String id = AjaxUpdates.updateContainerID(request);
		return id != null && id.indexOf(MULTI_UPDATE_SEPARATOR) != -1;
	}

	/**
	 * The set of requested update container ids for this pass. One id for a normal update; several for
	 * a multi-target update ({@code updateContainerID="a;b;c"}). Empty/blank ids are dropped.
	 */
	public static NSArray<String> requestedUpdateContainerIDs(WORequest request) {
		String id = AjaxUpdates.updateContainerID(request);
		if (id == null) {
			return NSArray.emptyArray();
		}
		if (id.indexOf(MULTI_UPDATE_SEPARATOR) == -1) {
			return new NSArray<>(id);
		}
		NSMutableArray<String> ids = new NSMutableArray<>();
		for (String part : id.split(MULTI_UPDATE_SEPARATOR)) {
			String trimmed = part.trim();
			if (trimmed.length() > 0) {
				ids.addObject(trimmed);
			}
		}
		return ids;
	}

	/**
	 * True if {@code containerID} is (one of) the requested update target(s). For a single-target
	 * request this is exactly an id equality check; for multi-target it is set membership.
	 */
	public static boolean isRequestedUpdateContainer(WORequest request, String containerID) {
		if (containerID == null) {
			return false;
		}
		String id = AjaxUpdates.updateContainerID(request);
		if (id == null) {
			return false;
		}
		if (id.indexOf(MULTI_UPDATE_SEPARATOR) == -1) {
			return containerID.equals(id);
		}
		return AjaxUpdates.requestedUpdateContainerIDs(request).containsObject(containerID);
	}

	public static String currentUpdateContainerID() {
		return (String) ERXWOContext.contextDictionary().objectForKey(AjaxUpdates.CURRENT_UPDATE_CONTAINER_ID_KEY);
	}

	public static void setCurrentUpdateContainerID(String updateContainerID) {
		if (updateContainerID == null) {
			ERXWOContext.contextDictionary().removeObjectForKey(AjaxUpdates.CURRENT_UPDATE_CONTAINER_ID_KEY);
		}
		else {
			ERXWOContext.contextDictionary().setObjectForKey(updateContainerID, AjaxUpdates.CURRENT_UPDATE_CONTAINER_ID_KEY);
		}
	}

	public static String updateContainerID(AjaxDynamicElement element, WOComponent component) {
		return AjaxUpdates.updateContainerID(element, "updateContainerID", component);
	}

	public static String updateContainerID(AjaxDynamicElement element, String bindingName, WOComponent component) {
		return AjaxUpdates.updateContainerID(element.valueForBinding(bindingName, component));
	}

	/**
	 * Resolves an {@code updateContainerID} binding value to the canonical, {@code ";"}-joined string
	 * form the rest of the framework uses (the wire format for {@code _u=a;b;c} and the JS
	 * {@code AUC.update('a;b;c')}). This is the single place every element's {@code updateContainerID}
	 * binding is resolved, so the accepted forms are uniform across the framework. The binding may be:
	 * <ul>
	 * <li>a single container id, as a String;</li>
	 * <li>a {@code ";"}-separated set of ids, as a String ({@code "a;b;c"});</li>
	 * <li>a {@code List} (e.g. {@code List<String>}) of ids - joined with {@code ";"} here, so callers
	 *     never have to care which they were given;</li>
	 * <li>the magic value {@code "_parent"}, which resolves to the nearest enclosing
	 *     {@link AjaxUpdateContainer} (so an element can target the container it lives in without
	 *     naming it). This applies to a String value only - it is not interpreted inside a list.</li>
	 * </ul>
	 *
	 * @param value the raw binding value (String, List, or null)
	 * @return the {@code ";"}-joined container ids, or null
	 */
	@SuppressWarnings("unchecked")
	public static String updateContainerID(Object value) {
		if (value instanceof List<?> list) {
			return String.join(MULTI_UPDATE_SEPARATOR, (List<String>) list);
		}
		return AjaxUpdates.updateContainerID((String) value);
	}

	public static String updateContainerID(String updateContainerID) {
		if ("_parent".equals(updateContainerID)) {
			updateContainerID = AjaxUpdates.currentUpdateContainerID();
		}
		return updateContainerID;
	}

	/**
	 * Writes the resolved id set back to the request's {@code _u} value as the canonical {@code ";"}-joined
	 * string, so the fragment-render machinery ({@link #requestedUpdateContainerIDs} /
	 * {@link #isRequestedUpdateContainer}) reads it. An empty set is written as the empty string - present
	 * but targeting nothing - which the render pass treats as "update nothing", distinct from a null (no
	 * update pass at all).
	 * <p>
	 * Also ensures the context's response is an {@link AjaxResponse}: declaring an update set means "this
	 * is an ajax update response", and it is {@code AjaxResponse.generateResponse} that runs the
	 * fragment-render pass. A bare-action link (no client {@code updateContainerID}) would otherwise leave
	 * a plain WOResponse in the context, so the pass would never fire and the response would be empty.
	 * <p>
	 * Package-private: the author-facing entry points live on {@link AjaxUpdater}.
	 */
	static void writeUpdateContainerSet(NSMutableArray<String> ids, WOContext context) {
		String joined = String.join(MULTI_UPDATE_SEPARATOR, ids);
		ERXWOContext.contextDictionary().setObjectForKey(joined, ERXAjaxApplication.KEY_UPDATE_CONTAINER_ID);
		AjaxUtils.createResponse(context.request(), context);
	}
}
