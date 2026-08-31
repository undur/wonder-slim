package er.ajax.elements;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.ajax.AjaxUtils;
import er.extensions.appserver.ERXResponseRewriter;
import er.extensions.components.ERXComponent;

/**
 * A titled disclosure section: a summary line that expands and collapses its content. This is the
 * AjaxSlim rebuild of the legacy AjaxExpansion, on the platform's native
 * <code>&lt;details&gt;/&lt;summary&gt;</code> - which supplies the disclosure behaviour, keyboard
 * handling and accessibility for free, and toggles instantly client-side (no round-trip before the
 * user sees the content, unlike the legacy fetch-on-expand).
 *
 * <p>
 * The one genuinely interesting part is morph correctness: the <code>open</code> attribute of a
 * <code>&lt;details&gt;</code> is pure client state, so a morph of an enclosing update container
 * would reconcile an open section against the server's collapsed markup and snap it shut mid-use.
 * Rather than opting the section out of morphing (the modal's approach - wrong here, because the
 * section's CONTENT should keep updating), the element keeps the SERVER truthful: every toggle
 * sends a tiny background ping carrying the new state, so every subsequent render - morphs
 * included - carries the right <code>open</code> attribute. State lives in the component instance
 * (or in the <code>expanded</code> binding when bound and settable, letting the page own it).
 * </p>
 *
 * <p>
 * The legacy element's effect machinery (<code>insertion</code>/<code>insertionDuration</code>,
 * Scriptaculous) is deliberately gone - apps that want motion can animate
 * <code>&lt;details&gt;</code> in plain CSS. The legacy lazy content rendering is gone too: content
 * renders inline (collapsed content is merely hidden), which is what makes the instant client-side
 * toggle possible.
 * </p>
 *
 * <p>
 * <b>Caveat</b> (shared with every stateful component): a WORepetition renders ONE component
 * instance across all its iterations, so an unbound AjaxExpansion inside a repetition shares a
 * single expansion state across rows. Bind <code>expanded</code> to per-row state in that case.
 * </p>
 *
 * @binding label the summary line's text
 * @binding expanded optionally binds the expansion state; when settable, toggles push the new state
 *          into it (letting the page keep it, e.g. per-row in a repetition). When absent, the
 *          component keeps the state itself, starting collapsed.
 * @binding id the id of the details element
 * @binding class CSS class(es) for the details element
 */
public class AjaxExpansion extends ERXComponent {

	/** The expansion state, when the 'expanded' binding doesn't own it. */
	private boolean _expanded;

	/** The elementID our toggle-ping URL points at, recorded at render time (see toggleUrl()). */
	private String _toggleSenderID;

	public AjaxExpansion( WOContext context ) {
		super( context );
	}

	@Override
	public boolean synchronizesVariablesWithBindings() {
		return false;
	}

	@Override
	public void appendToResponse( WOResponse response, WOContext context ) {
		ERXResponseRewriter.addScriptResourceInHead( response, context, "AjaxSlim", "ajaxslim-expansion.js" );
		super.appendToResponse( response, context );
	}

	public String label() {
		return stringValueForBinding( "label" );
	}

	public String id() {
		return stringValueForBinding( "id" );
	}

	public String cssClass() {
		return stringValueForBinding( "class" );
	}

	public boolean isExpanded() {
		if( hasBinding( "expanded" ) ) {
			return booleanValueForBinding( "expanded" );
		}

		return _expanded;
	}

	private void setExpanded( boolean expanded ) {
		if( hasBinding( "expanded" ) && canSetValueForBinding( "expanded" ) ) {
			setValueForBinding( expanded, "expanded" );
		}
		else {
			_expanded = expanded;
		}
	}

	/**
	 * @return "open" when expanded, null otherwise - bound to the details element's open attribute,
	 *         which WO omits entirely for a null value (the only correct way to render a boolean
	 *         HTML attribute: open="false" would still mean open).
	 */
	public String openAttributeOrNull() {
		return isExpanded() ? "open" : null;
	}

	/**
	 * The toggle ping's target URL. Evaluated while the details element renders, so the elementID
	 * recorded here is the one the ping's senderID carries back - invokeAction below matches on it.
	 */
	public String toggleUrl() {
		_toggleSenderID = context().elementID();
		return AjaxUtils.ajaxComponentActionUrl( context() );
	}

	/**
	 * Handles the toggle ping: a background request carrying the client's new expansion state as an
	 * 'expanded' parameter. Idempotent by design (the ping states the new value rather than
	 * requesting a flip), so a duplicate or late ping can't desync. Everything else passes through
	 * to the template as usual.
	 */
	@Override
	public WOActionResults invokeAction( WORequest request, WOContext context ) {
		if( _toggleSenderID != null && _toggleSenderID.equals( context.senderID() ) ) {
			setExpanded( "true".equals( request.stringFormValueForKey( "expanded" ) ) );

			// A content-less ajax response: the ping needs no reply, and a keyless ajax render is
			// deliberately not stored in the page cache (no contextID aliases for pings).
			final WOResponse response = AjaxUtils.createResponse( request, context );
			return response;
		}

		return super.invokeAction( request, context );
	}
}
