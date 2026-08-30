package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: an update container that VANISHES during its own update pass.
 *
 * The page is deliberately wrong-by-design: the AjaxUpdateContainer sits inside a conditional,
 * and the action fired from within the container turns that conditional OFF - the shape of
 * "delete the last item of the list the container is conditioned on". During the update pass the
 * container then never renders, no content is generated, and the framework answers 500.
 *
 * What this scenario guards is the DIAGNOSTICS of that failure: the 500 must carry an explanatory
 * body (naming the targeted container, the page, and the two causes - conditional-turned-off and
 * misspelled id) instead of the historical empty body + misleading "you probably misspelled it"
 * warning, which left a very confusing blank error. The correct application-side structure (the
 * conditional INSIDE the container) is what the body's advice prescribes.
 */
public class ScenarioVanishingContainer extends PlaygroundPage {

	/** The condition the container hangs on - starts true, the action turns it off. */
	public boolean showList = true;

	public ScenarioVanishingContainer(WOContext context) {
		super(context);
	}

	/** The self-defeating action: empties "the list", so the container's conditional goes false. */
	public WOActionResults emptyTheList() {
		showList = false;
		return null;
	}
}
