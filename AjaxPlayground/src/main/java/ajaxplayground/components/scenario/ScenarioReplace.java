package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: AjaxUpdateLink's replaceID - the ajax REPLACEMENT (_r) path.
 *
 * Unlike an update (_u), a replacement response is UNFRAMED by design: the server renders the
 * action's returned component and the body is the new markup for the target element itself
 * (outerHTML semantics). Regression guard for the bug where the client discarded the unframed body
 * (while still running any scripts it carried), so the replacement never landed in the DOM.
 */
public class ScenarioReplace extends PlaygroundPage {

	private int _replaceCount;

	public ScenarioReplace(WOContext context) {
		super(context);
	}

	/** The link's action: returns the component whose rendering REPLACES the target element. */
	public WOActionResults replaceRegion() {
		_replaceCount++;
		ReplacementContent replacement = pageWithName(ReplacementContent.class);
		replacement.generation = _replaceCount;
		return replacement;
	}
}
