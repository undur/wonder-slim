package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: inline script execution under morph. Placeholder - to be built out: a morphing
 * container whose response contains inline scripts (and an AjaxUpdateTrigger), verifying scripts
 * still run after a morph and that one throwing script does not abort the rest of the batch.
 */
public class ScenarioScripts extends PlaygroundPage {

	public ScenarioScripts( WOContext context ) {
		super( context );
	}
}
