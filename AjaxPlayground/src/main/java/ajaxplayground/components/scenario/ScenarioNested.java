package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: nested update containers. Placeholder - to be built out: an outer morphing container
 * holding inner containers (incl. a periodic one) to verify inner registration scripts re-running
 * on an outer morph do not duplicate timers / observers.
 */
public class ScenarioNested extends PlaygroundPage {

	public ScenarioNested( WOContext context ) {
		super( context );
	}
}
