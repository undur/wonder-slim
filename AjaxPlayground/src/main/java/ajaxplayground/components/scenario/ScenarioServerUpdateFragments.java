package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;
import er.ajax.AjaxUpdater;

/**
 * Scenario: the MODERN server-side update path - the action declares which containers refresh via
 * {@link AjaxUpdater#add} / {@link AjaxUpdater#set}, and they come back as <ajaxslim-fragment>s in THIS
 * response (one round-trip, response-as-data), instead of the legacy AUC.update('x') JavaScript +
 * N follow-up fetches.
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
		AjaxUpdater.add( "basicInfo", context() );
		AjaxUpdater.add( "bottomContainer", context() );
		return null;
	}

	/** SINGLE-container case: add exactly one. Tests that a one-element set (no ";") still frames a
	 *  fragment and morphs - the edge that isMultiUpdate's ";"-check could miss. */
	public WOActionResults refreshBasicOnly() {
		_countBasic++;
		AjaxUpdater.add( "basicInfo", context() );
		return null;
	}

	/**
	 * AMEND path: the CLIENT requested basicInfo (the link declares updateContainerID="basicInfo"), and
	 * the server adds bottomContainer ON TOP via {@link AjaxUpdater#add} - the union refreshes. This is
	 * the case that proves add() AUGMENTS the client's set rather than replacing it; the other buttons
	 * are server-only (no client updateContainerID). "untouched" stays put.
	 */
	public WOActionResults amendClientSet() {
		_countBasic++;
		_countBottom++;
		AjaxUpdater.add( "bottomContainer", context() );
		return null;
	}

	/** REPLACE case: ignore any client set, render exactly bottomContainer. */
	public WOActionResults replaceWithBottom() {
		_countBottom++;
		AjaxUpdater.set( context(), "bottomContainer" );
		return null;
	}

	/** EMPTY case: clear the set - a deliberate "nothing changed" with no visible outcome and NO 500. */
	public WOActionResults updateNothing() {
		AjaxUpdater.clear( context() );
		return null;
	}
}
