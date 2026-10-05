package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: a checkbox observed by an AjaxObserveField (partial submit).
 *
 * Regression guard for the partial-submit checkbox bug: a partial submit serialises ONLY the changed
 * field, and an unchecked checkbox must contribute NOTHING to the body (exactly as a real form does) so
 * the server reads it as false. The serializer used to append the checkbox's value regardless of
 * checked state, so toggling a checkbox off never registered server-side. This page echoes the
 * server-side boolean so a test can prove the toggle round-trips both ways.
 */
public class ScenarioCheckbox extends PlaygroundPage {

	/** Bound to the checkbox; flipped by the observe field's partial submit. Starts checked. */
	public boolean flag = true;

	private int _changeCount;

	public ScenarioCheckbox(WOContext context) {
		super(context);
	}

	/** The observe field's action - just lets the binding round-trip and counts the changes. */
	public WOActionResults flagChanged() {
		_changeCount++;
		return null;
	}

	/** The server-side view of the checkbox, as a string the test can read. */
	public String flagState() {
		return flag ? "ON" : "OFF";
	}

	public int changeCount() {
		return _changeCount;
	}
}
