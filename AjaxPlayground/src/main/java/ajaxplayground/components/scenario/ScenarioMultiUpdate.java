package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: one trigger refreshing SEVERAL update containers in a single round-trip via the
 * updateContainerID="a;b;c" multi-target syntax.
 *
 * The page is built to exercise the edge cases that a single "all boxes show the same number" test
 * would miss:
 *
 * - Each box has its OWN counter ({@link #countA()}/{@link #countB()}/{@link #countC()}), so a test
 *   can prove each fragment carried that box's content and was morphed into the RIGHT container
 *   (not the same fragment duplicated everywhere).
 * - {@link #bumpAll()} bumps all three; {@link #bumpAC()} bumps only A and C (the trigger targets
 *   "boxA;ghost;boxC", so it also covers a MISSING target id - boxB must be untouched and the
 *   non-existent "ghost" must be skipped without breaking the others).
 * - A form path ({@link #submitBump()}) drives the AjaxSubmitButton multi-update branch.
 */
public class ScenarioMultiUpdate extends PlaygroundPage {

	private int _countA;
	private int _countB;
	private int _countC;

	/** Bound to a text field, round-tripped through the form-submit multi path. Accessor pair (not a
	 *  bare public field) so WO key-value coding pushes the submitted value back on takeValuesFromRequest. */
	private String _note;

	public ScenarioMultiUpdate(WOContext context) {
		super(context);
	}

	public int countA() { return _countA; }
	public int countB() { return _countB; }
	public int countC() { return _countC; }

	public String note() { return _note; }
	public void setNote(String value) { _note = value; }

	/** Trigger targeting boxA;boxB;boxC - all three refresh from one request. */
	public WOActionResults bumpAll() {
		_countA++;
		_countB++;
		_countC++;
		return null;
	}

	/** Trigger targeting boxA;ghost;boxC - only A and C refresh; boxB is left alone; ghost is skipped. */
	public WOActionResults bumpAC() {
		_countA++;
		_countC++;
		return null;
	}

	/** Form-submit trigger (AjaxSubmitButton) targeting boxA;boxB;boxC, also echoing the note field. */
	public WOActionResults submitBump() {
		_countA++;
		_countB++;
		_countC++;
		return null;
	}
}
