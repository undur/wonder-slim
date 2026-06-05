package ajaxplayground.components.scenario;

import java.util.Arrays;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: UUID / dashed element ids.
 *
 * DOM ids may legally contain '-' and start with a digit (e.g. UUIDs). The framework used to build
 * JS globals from ids via eval(id + "..."), which threw on such ids - and because a fragment's
 * scripts are evaluated as a batch, one throw aborted later scripts (this is what once silently
 * stopped an AjaxUpdateTrigger from firing). Stage 1 removed those evals.
 *
 * This page gives an AjaxUpdateContainer a hard-coded UUID-style id (with dashes). Updating it must
 * not throw, and an AjaxUpdateTrigger inside it must still fire a second container's update - so we
 * prove later scripts in the same response survive even with a dash-laden id in play.
 */
public class ScenarioUuidIds extends PlaygroundPage {

	private int _mainCount;
	private int _triggeredCount;

	public ScenarioUuidIds( WOContext context ) {
		super( context );
	}

	/** A fixed UUID-style id (dashes + leading digit segments) for the main container. */
	public String uuidContainerID() {
		return "uc-3f2504e0-4f89-41d3-9a0c-0305e82c3301";
	}

	public int mainCount() {
		return _mainCount;
	}

	/**
	 * How many times the triggered container has been RENDERED. Each time the trigger fires, that
	 * container re-renders and this increments - so a rising number proves the trigger script ran
	 * (and therefore was not aborted by a throw from the UUID container's registration script).
	 */
	public int triggeredCount() {
		return _triggeredCount;
	}

	/** Bumps the main counter; the trigger inside the container then refreshes the second one. */
	public WOActionResults bumpMain() {
		_mainCount++;
		return null;
	}

	/** Called from the triggered container's template each time it renders. */
	public String triggeredRenderTick() {
		_triggeredCount++;
		return "";
	}

	/** The containers the AjaxUpdateTrigger should refresh (just the second container). */
	public List<String> triggeredContainersInArray() {
		return Arrays.asList( "triggeredContainer" );
	}
}
