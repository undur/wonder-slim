package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: inline script execution under morph, and script-batch isolation.
 *
 * Two properties to verify:
 *  1. Inline &lt;script&gt; blocks inside a morphed container still execute after the morph
 *     (Idiomorph leaves script nodes inert, so AjaxMorph re-evals them explicitly).
 *  2. One throwing script does not abort the rest of the batch - AjaxMorph.evalScriptsIsolated
 *     evals each script in its own try/catch, so a bad fragment degrades to a logged error while
 *     later scripts (e.g. an AjaxUpdateTrigger) still run.
 *
 * The page renders, inside the morphed container, three inline scripts: one that sets a global
 * marker, one that deliberately throws, and one that sets a second marker AFTER the thrower. If
 * isolation works, both markers are set despite the middle script throwing. A bumpable counter
 * makes each refresh a real morph.
 */
public class ScenarioScripts extends PlaygroundPage {

	private int _updateCount;

	public ScenarioScripts( WOContext context ) {
		super( context );
	}

	public int updateCount() {
		return _updateCount;
	}

	public WOActionResults bumpCounter() {
		_updateCount++;
		return null;
	}

	/**
	 * Deliberately throws during an ajax update, so the server returns its exception page as the
	 * response. Used to exercise the client's error reporting: the failure must NOT be morphed into the
	 * container; instead the built-in presenter shows a banner with a "Show details" overlay rendering
	 * this exception page.
	 */
	public WOActionResults triggerServerError() {
		throw new IllegalStateException("Deliberate server error from the AjaxSlim playground - this exception page is what the error overlay renders.");
	}
}
