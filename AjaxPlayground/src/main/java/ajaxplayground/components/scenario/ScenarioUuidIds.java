package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: UUID / dashed element ids. Placeholder - to be built out: update containers whose ids
 * contain '-' (UUID-style), verifying AUC.register / triggers no longer throw and later scripts in
 * the same fragment still run (the eval-global blast-radius class).
 */
public class ScenarioUuidIds extends PlaygroundPage {

	public ScenarioUuidIds( WOContext context ) {
		super( context );
	}
}
