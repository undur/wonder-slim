package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: nested update containers.
 *
 * An outer morphing container holds an inner container whose registration script (here an
 * AjaxObserveField) re-runs whenever the outer morphs. The danger is duplication: the inner
 * observer/timer stacking each time the outer refreshes. With the Stage 3 guards (and
 * registerPeriodic stopping a prior updater) the inner registration converges instead of stacking.
 *
 * Test: morph the outer container several times, then interact with the inner field once and
 * confirm it fires a single request - not one-per-outer-morph.
 */
public class ScenarioNested extends PlaygroundPage {

	private int _outerCount;
	private int _innerCount;
	public String innerValue;

	public ScenarioNested( WOContext context ) {
		super( context );
	}

	public int outerCount() {
		return _outerCount;
	}

	public int innerCount() {
		return _innerCount;
	}

	/** Morphs the outer container (which re-renders the inner one and re-runs its registration). */
	public WOActionResults bumpOuter() {
		_outerCount++;
		return null;
	}

	/** Fired when the inner observe field changes - counts inner interactions. */
	public WOActionResults innerChanged() {
		_innerCount++;
		return null;
	}
}
