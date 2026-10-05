package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: the repeated-request guard (refresh/double-submit protection).
 *
 * Plain, non-ajax component actions that increment counters. Re-sending the byte-identical request
 * (a browser refresh of the POST, a double-fired link, backtrack-and-click-the-same-link) must NOT
 * increment again - the unified page cache detects the repeat and re-renders the stored result
 * without re-invoking the action. A genuinely new click (from a fresh render, so a fresh contextID)
 * must increment. Driven over raw HTTP by tools/playwright-bridge/examples/replay-guard.mjs.
 */
public class ScenarioReplay extends PlaygroundPage {

	private int _submitCount;
	private int _linkCount;

	public String note = "";

	public ScenarioReplay( WOContext context ) {
		super( context );
	}

	public int submitCount() {
		return _submitCount;
	}

	public int linkCount() {
		return _linkCount;
	}

	public WOActionResults submitAction() {
		_submitCount++;
		return null;
	}

	public WOActionResults linkAction() {
		_linkCount++;
		return null;
	}
}
