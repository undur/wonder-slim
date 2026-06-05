package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: observer / request accumulation. Placeholder - to be built out: a container with an
 * AjaxObserveField (and an autocomplete / periodic child) that is refreshed repeatedly; the test
 * is that the number of requests fired per change stays flat instead of growing with each morph.
 */
public class ScenarioAccumulation extends PlaygroundPage {

	public ScenarioAccumulation( WOContext context ) {
		super( context );
	}
}
