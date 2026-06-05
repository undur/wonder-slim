package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: several AjaxObserveFields on ONE field, each updating a different container.
 *
 * Regression test for a signature-collision bug in the observer-idempotency guard: when an observe
 * field does a partial submit (the default), the guard's signature dropped the target container, so
 * multiple observers on the same field collapsed to one and only the FIRST attached - silently
 * dropping the others (this broke "edit a row -> add the next row" on a real page, where one key
 * field fans out to six containers).
 *
 * This page wires one source field to three separate counters via three AjaxObserveFields. Changing
 * the field must update ALL THREE - if any stays at zero, the guard is over-deduping again.
 */
public class ScenarioMultiObserve extends PlaygroundPage {

	public String source;
	private int _countA;
	private int _countB;
	private int _countC;

	public ScenarioMultiObserve( WOContext context ) {
		super( context );
	}

	public int countA() {
		return _countA;
	}

	public int countB() {
		return _countB;
	}

	public int countC() {
		return _countC;
	}

	public WOActionResults bumpA() {
		_countA++;
		return null;
	}

	public WOActionResults bumpB() {
		_countB++;
		return null;
	}

	public WOActionResults bumpC() {
		_countC++;
		return null;
	}
}
