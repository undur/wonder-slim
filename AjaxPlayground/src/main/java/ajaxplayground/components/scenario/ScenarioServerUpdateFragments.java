package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;
import er.ajax.AjaxUpdateContainer;

/**
 * Scenario: the MODERN server-side update path - the action declares which containers refresh via
 * {@link AjaxUpdateContainer#addUpdateContainer} / {@link AjaxUpdateContainer#setUpdateContainers},
 * and they come back as <ajaxslim-fragment>s in THIS response (one round-trip, response-as-data),
 * instead of the legacy updateContainerWithID emitting AUC.update('x') JavaScript + N follow-up fetches.
 *
 * Sits BESIDE {@link ScenarioServerUpdate} (the legacy JS-command path), which stays as the control.
 */
public class ScenarioServerUpdateFragments extends PlaygroundPage {

	private int _countBasic;
	private int _countBottom;
	private int _countUntouched;

	public ScenarioServerUpdateFragments( WOContext context ) {
		super( context );
	}

	public int countBasic() { return _countBasic; }
	public int countBottom() { return _countBottom; }
	public int countUntouched() { return _countUntouched; }

	/**
	 * AUGMENT path (bare-action link, no client-declared updateContainerID): the action bumps state and
	 * adds two containers to this request's update set. They render as fragments in this response;
	 * "untouched" is never added, so it must stay put.
	 */
	public WOActionResults refreshBasicAndBottom() {
		_countBasic++;
		_countBottom++;
		AjaxUpdateContainer.addUpdateContainer( "basicInfo", context() );
		AjaxUpdateContainer.addUpdateContainer( "bottomContainer", context() );
		return null;
	}

	/** SINGLE-container case: add exactly one. Tests that a one-element set (no ";") still frames a
	 *  fragment and morphs - the edge that isMultiUpdate's ";"-check could miss. */
	public WOActionResults refreshBasicOnly() {
		_countBasic++;
		AjaxUpdateContainer.addUpdateContainer( "basicInfo", context() );
		return null;
	}

	/** REPLACE case: ignore any client set, render exactly bottomContainer. */
	public WOActionResults replaceWithBottom() {
		_countBottom++;
		AjaxUpdateContainer.setUpdateContainers( context(), "bottomContainer" );
		return null;
	}

	/** EMPTY case: clear the set - a deliberate "nothing changed" with no visible outcome and NO 500. */
	public WOActionResults updateNothing() {
		AjaxUpdateContainer.clearUpdateContainers( context() );
		return null;
	}
}
