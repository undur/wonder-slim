package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;

/**
 * The component ScenarioComponentInstance embeds via ERXWOComponentInstance. Three things are on show:
 *
 * - a label set by the embedding page at construction time, through a plain setter - the thing a
 *   name-based switch can't do;
 * - a click counter the component owns and advances through its own component action - state that
 *   must survive across requests, proving the instance really is registered as a subcomponent;
 * - a [note] binding pulled from the page, proving binding passing coexists with the instance handle.
 */
public class EmbeddedCounter extends ERXComponent {

	/** Set by whoever constructs us, before we're ever rendered. */
	private String _label;

	/** Owned state, advanced by our own action or directly by the holder of our reference. */
	private int _count;

	/** Pulled from the embedding page's [note] binding. */
	public String note;

	public EmbeddedCounter( WOContext context ) {
		super( context );
	}

	public String label() {
		return _label;
	}

	public void setLabel( String label ) {
		_label = label;
	}

	public int count() {
		return _count;
	}

	/** A method the holder of the instance can call directly - no binding, no threadlocal. */
	public void bump() {
		_count++;
	}

	/** Our own component action; returns null so the page re-renders. */
	public WOActionResults bumpAction() {
		bump();
		return null;
	}
}
