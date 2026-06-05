package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: observer / request accumulation.
 *
 * The danger: when a container morphs, any registration script inside it re-runs against the
 * PRESERVED DOM, so without idempotency guards each update attaches another observer - and the Nth
 * interaction fires N duplicate requests. The framework now guards against this (AjaxObserveField,
 * autocomplete, etc.), and this page is the regression test.
 *
 * How to test it: drive an update repeatedly and count the Ajax requests that ONE interaction
 * fires. With guards working, it stays at one per interaction no matter how many prior updates
 * happened. Without them, it grows. The Playwright bridge asserts this with marker/requestCount.
 *
 * Two variants are offered so we cover both the realistic case and a deterministic one:
 *  - observe-field driven: a field whose change triggers the morphing container (the real-world
 *    case where the bug originally appeared).
 *  - explicit-trigger driven: an AjaxUpdateLink that morphs the same container on demand, which is
 *    deterministic for a counting test (one click = expect exactly one request).
 */
public class ScenarioAccumulation extends PlaygroundPage {

	private int _updateCount;
	public String observedValue;

	public ScenarioAccumulation( WOContext context ) {
		super( context );
	}

	/** A server-side counter so each refresh visibly changes the rendered content (real morph). */
	public int updateCount() {
		return _updateCount;
	}

	/** Action for the explicit-trigger variant: bump the counter and let the container re-render. */
	public WOActionResults bumpCounter() {
		_updateCount++;
		return null;
	}

	/** Action backing the observe field (counts observed changes too). */
	public WOActionResults observedChanged() {
		_updateCount++;
		return null;
	}
}
