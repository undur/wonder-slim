package er.ajax;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

/**
 * Makes the rows of a list drag-reorderable. Drop this element inside (or next to) the container whose
 * children should be sortable; it renders the one-time client registration and, on drop, fires your
 * {@code action} and morphs your {@code updateContainerID} - so reordering is a normal server action
 * that can mutate the model and refresh whatever it needs.
 * <p>
 * The browser side lives in {@code ajaxslim-sortable.js} ({@link er.ajax} resource): a dependency-free,
 * morph-safe sortable that drags by a handle and, on drop, reports the move <em>positionally</em> - the
 * dragged row's original index and its final index. There is no per-row id binding; the contract is
 * purely positional, the existing move-up/move-down generalized to an arbitrary distance. The live drag
 * is optimistic (rows reorder under the pointer); the authoritative server response morphs the list
 * back in and makes it real.
 * <p>
 * Server side, read the move in your action with the {@link #dragResult(WORequest)} helper:
 * <pre>
 *   public WOActionResults reorderLines() {
 *     AjaxSortable.DragResult move = AjaxSortable.dragResult(context().request());
 *     if (move != null &amp;&amp; move.isValidFor(_lines.size())) {
 *       _lines.add(move.toIndex(), _lines.remove(move.fromIndex()));
 *     }
 *     return null;
 *   }
 * </pre>
 * The reorderable rows and their drag grips are MARKED in your own row markup with data attributes -
 * {@code data-sortable-item} on each draggable row and {@code data-sortable-grip} on the grab handle
 * inside it - so the container's internal DOM structure stays out of the template bindings (no CSS
 * selectors), matching the {@code data-morph-*} marker idiom. A drag only starts from a grip, so the
 * row's own controls (inputs, links) don't initiate one.
 * <p>
 * Template usage:
 * <pre>
 *   &lt;wo:AjaxUpdateContainer id="linesContainer" elementName="div"&gt;
 *     &lt;wo:AjaxSortable listID="linesContainer" action="$reorderLines"
 *        updateContainerID="linesContainer;totalsPanel;renderedInvoice" /&gt;
 *     &lt;table&gt;
 *       &lt;tr ... data-sortable-item&gt;
 *         &lt;td data-sortable-grip&gt;&#x283F;&lt;/td&gt; ...
 *       &lt;/tr&gt;
 *     &lt;/table&gt;
 *   &lt;/wo:AjaxUpdateContainer&gt;
 * </pre>
 *
 * @binding listID the id of the container holding the rows marked with {@code data-sortable-item}
 * @binding action the server action fired on drop. Read the move with {@link #dragResult(WORequest)}.
 * @binding updateContainerID the container id(s) to morph from the action's response (";"-joined for a
 *          multi-container update, e.g. the list plus a totals panel).
 */
public class AjaxSortable extends AjaxDynamicElement {

	/** The request form values the client sends on drop. */
	public static final String OLD_INDEX_KEY = "_oldIndex";
	public static final String NEW_INDEX_KEY = "_newIndex";

	/**
	 * The result of a drag: the row moved from {@link #fromIndex()} to {@link #toIndex()}, both
	 * positional indices into the same (pre-move) list the user was looking at. Apply it with
	 * {@code list.add(toIndex(), list.remove(fromIndex()))}.
	 */
	public record DragResult(int fromIndex, int toIndex) {

		/** True when both indices are in range for a list of the given size and actually differ. */
		public boolean isValidFor(int listSize) {
			return fromIndex >= 0 && fromIndex < listSize
					&& toIndex >= 0 && toIndex < listSize
					&& fromIndex != toIndex;
		}
	}

	public AjaxSortable(String name, NSDictionary<String, WOAssociation> associations, WOElement children) {
		super(name, associations, children);
	}

	/**
	 * Parse the drag move out of a reorder request, or null if it carries no (or unparseable) indices.
	 * Call this from your {@code action} method to learn which row moved where.
	 *
	 * @param request the current request
	 * @return the move, or null if the request is not a reorder (no valid _oldIndex/_newIndex)
	 */
	public static DragResult dragResult(WORequest request) {
		Integer from = intFormValue(request, OLD_INDEX_KEY);
		Integer to = intFormValue(request, NEW_INDEX_KEY);
		if (from == null || to == null) {
			return null;
		}
		return new DragResult(from, to);
	}

	private static Integer intFormValue(WORequest request, String key) {
		String value = request == null ? null : request.stringFormValueForKey(key);
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(value.trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	protected void addRequiredWebResources(WOResponse response, WOContext context) {
		addScriptResourceInHead(context, response, "idiomorph.js");
		addScriptResourceInHead(context, response, "ajaxslim.js");
		addScriptResourceInHead(context, response, "ajaxslim-sortable.js");
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		super.appendToResponse(response, context);

		WOComponent component = context.component();
		String listID = stringValueForBinding("listID", component);
		if (listID == null) {
			throw new IllegalStateException("AjaxSortable requires a 'listID' binding (the id of the container whose rows are sortable).");
		}
		String updateContainerID = AjaxUpdateContainer.updateContainerID(this, component);
		// A component-action URL that routes back to THIS element's handleRequest (where we fire the
		// action and morph). The sortable appends &_oldIndex=..&_newIndex=.. to it on drop.
		String actionUrl = AjaxUtils.ajaxComponentActionUrl(context);

		// The rows and grips are marked in the row markup (data-sortable-item / data-sortable-grip), so
		// the element only needs to tell the client WHERE to send the move and WHAT to morph.
		AjaxUtils.appendScriptHeader(response);
		response.appendContentString("AjaxSlim.Sortable.register(" + AjaxUtils.quote(listID) + ", {");
		StringBuilder opts = new StringBuilder();
		appendOption(opts, "url", actionUrl);
		appendOption(opts, "update", updateContainerID != null ? updateContainerID : listID);
		response.appendContentString(opts.toString());
		response.appendContentString("});");
		AjaxUtils.appendScriptFooter(response);
	}

	private void appendOption(StringBuilder opts, String key, String value) {
		if (value == null) {
			return;
		}
		if (opts.length() > 0) {
			opts.append(", ");
		}
		opts.append(key).append(": ").append(AjaxUtils.quote(value));
	}

	@Override
	public WOActionResults handleRequest(WORequest request, WOContext context) {
		WOComponent component = context.component();
		// Tell the response pass which container(s) to render, then fire the app's reorder action. The
		// action reads the move via dragResult(request) and mutates its model; the morph reflects it.
		// updateContainerID may be a single id or a ";"-joined multi-target set ("a;b;c").
		String updateContainerID = AjaxUpdateContainer.updateContainerID(this, component);
		AjaxUpdateContainer.setUpdateContainerID(request, updateContainerID);
		WOActionResults result = (WOActionResults) valueForBinding("action", component);
		// Mirror AjaxUpdateLink: when the action returns null (the usual case - it just mutated the
		// model), set the page-replacement cache key and return null, letting AjaxDynamicElement's
		// invokeAction create the response and drive the multi-container update traversal. Returning a
		// response here would halt that traversal at the first target.
		if (result == null && updateContainerID != null) {
			AjaxUtils.setPageReplacementCacheKey(context, updateContainerID);
		}
		return result;
	}
}
