package er.ajax;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXWOContext;

/**
 * AjaxUpdateContainer - a region of a page that can be refreshed independently via an ajax request,
 * morphing the freshly-rendered HTML into the live DOM (preserving focus, scroll, selection and
 * unchanged subtrees) instead of blowing away its innerHTML.
 * <p>
 * This is the AjaxSlim rewrite. It keeps the legacy server structure (the update-container-id
 * threading, the {@link #handleRequest} action pass) but the client contract is the clean
 * fetch + Idiomorph runtime in <code>ajaxslim.js</code> - no Prototype, no Scriptaculous, no
 * effects. The element emits a small, declarative HTML contract:
 * <ul>
 * <li><code>data-updateUrl</code> - the ajax component-action URL to (re)fetch this container from.</li>
 * <li><code>data-morph</code> - <code>"true"</code> to reconcile via Idiomorph, <code>"false"</code>
 *     to do a classic innerHTML replacement. Always emitted explicitly so the JS side has a single,
 *     unambiguous source of truth.</li>
 * </ul>
 * plus a single <code>AjaxSlim.AUC.register(id, options)</code> call.
 *
 * <p>
 * This is a <b>passive</b> update container: a named region that <i>other</i> elements
 * (AjaxUpdateLink / AjaxObserveField / AjaxSubmitButton with <code>updateContainerID</code>) refresh.
 * It does not refresh itself and has no action of its own. For a region that refreshes itself - on a
 * timer or when a field changes - use {@link AjaxSelfUpdatingContainer}.
 * </p>
 *
 * @binding id the DOM id of the container (defaults to a safe element id)
 * @binding elementName the HTML tag to render (defaults to "div")
 * @binding onRefreshComplete javascript to run after the container has been refreshed/morphed
 * @binding optional set to true to skip rendering the container tags when already inside an update container
 * @binding morph if true, content updates reconcile the existing DOM via Idiomorph instead of replacing
 *                innerHTML, preserving focus/scroll/selection and unchanged subtrees. Bind morph="$false"
 *                to force classic innerHTML replacement. When unbound, {@link #MORPH_BY_DEFAULT} applies.
 * <p>
 * Any other attribute (class, style, data-*, role, aria-*, ...) is passed through verbatim onto the
 * rendered tag - it is not a binding this element interprets, so it is not listed above or in the .api.
 */
public class AjaxUpdateContainer extends AjaxDynamicElement {

	/**
	 * The framework-wide default for whether AjaxUpdateContainers morph their content on update
	 * (as opposed to replacing innerHTML). This is the SINGLE SOURCE OF TRUTH for the default -
	 * flipping morphing on globally is a one-line change here. A per-container "morph" binding
	 * always overrides this default in either direction, so morph="$false" remains a permanent,
	 * reliable opt-out even after the default flips to true.
	 */
	public static final boolean MORPH_BY_DEFAULT = true;

	/**
	 * Resolves whether the given container should morph: the per-container "morph" binding if
	 * present, otherwise {@link #MORPH_BY_DEFAULT}.
	 */
	public boolean shouldMorph(WOComponent component) {
		return booleanValueForBinding("morph", MORPH_BY_DEFAULT, component);
	}

	public AjaxUpdateContainer(String name, NSDictionary<String, WOAssociation> associations, WOElement children) {
		super(name, associations, children);
	}

	/**
	 * Adds all required resources: the slim runtime and the morph engine. No Prototype, no effects.
	 */
	@Override
	protected void addRequiredWebResources(WOResponse response, WOContext context) {
		addScriptResourceInHead(context, response, "idiomorph.js");
		addScriptResourceInHead(context, response, "ajaxslim.js");
	}

	protected boolean shouldRenderContainer(WOComponent component) {
		boolean renderContainer = !booleanValueForBinding("optional", false, component) || AjaxUpdates.currentUpdateContainerID() == null;
		return renderContainer;
	}

	@Override
	public void takeValuesFromRequest(WORequest request, WOContext context) {
		if (shouldRenderContainer(context.component())) {
			String previousUpdateContainerID = AjaxUpdates.currentUpdateContainerID();
			try {
				AjaxUpdates.setCurrentUpdateContainerID(_containerID(context));
				super.takeValuesFromRequest(request, context);
			}
			finally {
				AjaxUpdates.setCurrentUpdateContainerID(previousUpdateContainerID);
			}
		}
		else {
			super.takeValuesFromRequest(request, context);
		}
	}

	@Override
	public WOActionResults invokeAction(WORequest request, WOContext context) {
		WOActionResults results;
		if (shouldRenderContainer(context.component())) {
			String previousUpdateContainerID = AjaxUpdates.currentUpdateContainerID();
			try {
				AjaxUpdates.setCurrentUpdateContainerID(_containerID(context));
				results = super.invokeAction(request, context);
			}
			finally {
				AjaxUpdates.setCurrentUpdateContainerID(previousUpdateContainerID);
			}
		}
		else {
			results = super.invokeAction(request, context);
		}
		return results;
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		WOComponent component = context.component();
		if (!shouldRenderContainer(component)) {
			if (hasChildrenElements()) {
				appendChildrenToResponse(response, context);
			}
			super.appendToResponse(response, context);
		}
		else {
			String previousUpdateContainerID = AjaxUpdates.currentUpdateContainerID();
			try {
				String elementName = (String) valueForBinding("elementName", "div", component);
				String id = _containerID(context);
				AjaxUpdates.setCurrentUpdateContainerID(id);
				response.appendContentString("<" + elementName + " ");
				appendTagAttributeToResponse(response, "id", id);
				appendTagAttributeToResponse(response, "data-updateUrl", AjaxUtils.ajaxComponentActionUrl(context));
				// Emit the resolved morph decision explicitly in both states so the JS side has a
				// single, unambiguous source of truth. An explicit data-morph="false" forces classic
				// innerHTML replacement and keeps doing so even after MORPH_BY_DEFAULT flips to true.
				appendTagAttributeToResponse(response, "data-morph", shouldMorph(component) ? "true" : "false");
				// Pass through every author-supplied binding this element does not handle itself - class,
				// style, data-*, role, aria-*, title, ... An AjaxUpdateContainer is a <wo:container> with
				// ajax behavior; attributes it has no special meaning for belong on the rendered tag, not
				// silently dropped. (class/style are not special-cased here; they flow through like any
				// other passthrough attribute.)
				appendPassthroughAttributes(response, component);
				response.appendContentString(">");
				if (hasChildrenElements()) {
					appendChildrenToResponse(response, context);
				}
				response.appendContentString("</" + elementName + ">");

				super.appendToResponse(response, context);

				String onRefreshComplete = (String) valueForBinding("onRefreshComplete", component);

				AjaxUtils.appendScriptHeader(response);

				// register(id, options) - store this container's options (currently just the
				// onRefreshComplete hook) keyed by id so a later update(id) can find them. The
				// options object is a plain JS object literal; we keep it tiny. This is a passive-target
				// concern (the hook to run after THIS container is morphed), so it stays on the base.
				response.appendContentString("AjaxSlim.AUC.register('" + id + "', {");
				if (onRefreshComplete != null) {
					response.appendContentString("onRefreshComplete: function() { " + onRefreshComplete + " }");
				}
				response.appendContentString("});");

				// Self-updating behaviour (periodic refresh / observe-a-field) is NOT part of a passive
				// update container - it lives in AjaxSelfUpdatingContainer, which overrides this hook.
				// The base emits nothing here.
				appendSelfUpdateScript(response, context, id);

				AjaxUtils.appendScriptFooter(response);
			}
			finally {
				AjaxUpdates.setCurrentUpdateContainerID(previousUpdateContainerID);
			}
		}
	}

	/**
	 * The binding names THIS element interprets itself - the ones it reads to drive its own behavior or
	 * emits as a specific tag attribute. Every OTHER author-supplied binding is passed through verbatim
	 * to the rendered tag by {@link #appendPassthroughAttributes}: it is not our job to enumerate or
	 * suppress attributes that belong to some other framework or to plain HTML (class, style, data-*,
	 * role, aria-*, ...) - those just flow through. Subclasses that consume their own bindings add them
	 * via {@link #handledBindingNames()}.
	 */
	private static final NSArray<String> HANDLED_BINDINGS = new NSArray<>(new String[] {
		"id", "elementName", "morph", "onRefreshComplete", "optional"
	});

	/**
	 * The set of binding names this element handles itself and must NOT pass through onto the rendered
	 * tag. Subclasses override to add their own.
	 *
	 * @return the handled binding names
	 */
	protected NSArray<String> handledBindingNames() {
		return HANDLED_BINDINGS;
	}

	/**
	 * Emit every author-supplied binding that this element does not handle itself as a tag attribute -
	 * so arbitrary HTML attributes (data-*, role, aria-*, title, ...) placed on an
	 * {@code &lt;wo:AjaxUpdateContainer&gt;} land on the rendered element instead of being dropped.
	 *
	 * @param response the response being built
	 * @param component the current component (for evaluating each association)
	 */
	protected void appendPassthroughAttributes(WOResponse response, WOComponent component) {
		NSArray<String> handled = handledBindingNames();
		for (String name : associations().allKeys()) {
			if (handled.containsObject(name)) {
				continue;
			}
			WOAssociation association = associations().objectForKey(name);
			Object value = association.valueInComponent(component);
			appendTagAttributeToResponse(response, name, value);
		}
	}

	/**
	 * Hook for emitting the client registration of self-updating behaviour (periodic refresh,
	 * observe-a-field). A passive {@link AjaxUpdateContainer} has none, so this is empty;
	 * {@link AjaxSelfUpdatingContainer} overrides it. Called inside the container's script block during
	 * {@link #appendToResponse}, after the base {@code AUC.register(...)} call.
	 */
	protected void appendSelfUpdateScript(WOResponse response, WOContext context, String id) {
		// no-op for a passive update container
	}

	/**
	 * Hook for invoking a self-update container's own refresh action when the container is the request
	 * sender (i.e. it refreshed itself). A passive {@link AjaxUpdateContainer} has no action, so this is
	 * a no-op; {@link AjaxSelfUpdatingContainer} overrides it to invoke its {@code action} binding.
	 */
	protected void performSelfUpdateAction(WORequest request, WOContext context) {
		// no-op for a passive update container
	}

	@Override
	public WOActionResults handleRequest(WORequest request, WOContext context) {
		WOComponent component = context.component();
		String id = _containerID(context);

		performSelfUpdateAction(request, context);

		WOResponse response = AjaxUtils.createResponse(request, context);
		AjaxUtils.setPageReplacementCacheKey(context, id);

		// UNIFORM framing: every container that renders into an ajax update response frames its content in
		// an inert <ajaxslim-fragment data-id> wrapper, which the client always demuxes - whether the
		// update targets one container or many, and whether the target set came from the client
		// (updateContainerID="a;b;c") or the server (addUpdateContainer/setUpdateContainers). "Content
		// destined for an update container IS a fragment" - one rule, no single-vs-multi or
		// client-vs-server special case. The client morphs each fragment into its container; the response's
		// content-type still distinguishes a fragment response (morph) from a text/javascript one (run).
		response.appendContentString("<ajaxslim-fragment data-id=\"" + id + "\">");

		if (hasChildrenElements()) {
			appendChildrenToResponse(response, context);
		}
		String onRefreshComplete = (String) valueForBinding("onRefreshComplete", component);
		if (onRefreshComplete != null) {
			AjaxUtils.appendScriptHeader(response);
			response.appendContentString(onRefreshComplete);
			AjaxUtils.appendScriptFooter(response);
		}

		response.appendContentString("</ajaxslim-fragment>");
		return null;
	}

	@Override
	protected String _containerID(WOContext context) {
		String id = (String) valueForBinding("id", context.component());
		if (id == null) {
			id = ERXWOContext.safeIdentifierName(context, false);
		}
		return id;
	}

	/**
	 * @param updateContainerID the HTML ID of the container to update
	 * @param context WOContext for response
	 *
	 * @deprecated moved to {@link AjaxUpdater#update(String, WOContext)} - server-side update API does not
	 *             belong on the element class. This delegate is kept for backward compatibility and is
	 *             itself deprecated (the JS-command approach; prefer the fragment path {@link AjaxUpdater#add}).
	 */
	@Deprecated
	public static void updateContainerWithID(String updateContainerID, WOContext context) {
		AjaxUpdater.update(updateContainerID, context);
	}

	/**
	 * @param updateContainerID the HTML ID of the container to update
	 * @param context WOContext for response
	 *
	 * @deprecated moved to {@link AjaxUpdater#safeUpdate(String, WOContext)}. Kept as a delegate for
	 *             backward compatibility; itself deprecated - prefer the fragment path ({@link AjaxUpdater#add}).
	 */
	@Deprecated
	public static void safeUpdateContainerWithID(String updateContainerID, WOContext context) {
		AjaxUpdater.safeUpdate(updateContainerID, context);
	}
}
