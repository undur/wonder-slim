package ajaxplayground.components.scenario;

import java.util.ArrayList;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;
import er.ajax.AjaxUpdater;
import er.ajax.AjaxUpdateProtocol;

/**
 * Scenario: SERVER-SIDE update targeting via {@link AjaxUpdater#update(String, com.webobjects.appserver.WOContext)}.
 *
 * Unlike {@link ScenarioMultiUpdate} (where the CLIENT declares the target set via
 * <code>updateContainerID="a;b;c"</code>), here the action declares the targets on the SERVER: it
 * calls {@code AjaxUpdater.triggerUpdate(id, context())} for each container it decided should refresh,
 * and returns null. The framework appends an {@code AjaxSlim.AUC.update('id')} script per call, and
 * the client turns around and re-fetches each named container.
 *
 * This is the exact idiom real apps use (NBDocumentEditCustomerComponent fires two; ScrabblePage
 * fires a set minus the current container), and it had NO playground coverage - so this scenario is
 * the regression harness for it.
 *
 * Each box has its OWN counter so a test can prove precisely which containers refreshed and which
 * were left untouched - the whole point is that the server, not the client, chose the set.
 */
public class ScenarioServerUpdate extends PlaygroundPage {

	private int _countBasic;
	private int _countBottom;
	private int _countUntouched;

	private int _countAll1;
	private int _countAll2;
	private int _countAll3;

	public ScenarioServerUpdate( WOContext context ) {
		super( context );
	}

	public int countBasic() { return _countBasic; }
	public int countBottom() { return _countBottom; }
	public int countUntouched() { return _countUntouched; }

	public int countAll1() { return _countAll1; }
	public int countAll2() { return _countAll2; }
	public int countAll3() { return _countAll3; }

	// --- NB pattern: a link whose action refreshes two OTHER named containers server-side ----------

	/**
	 * Mirrors NBDocumentEditCustomerComponent.selectCustomer(): the action bumps state and tells the
	 * server to refresh two sibling containers BY NAME, then returns null. "untouched" is deliberately
	 * NOT named, so it must stay put - proving the server chose exactly {basicInfo, bottomContainer}.
	 */
	public WOActionResults selectCustomer() {
		_countBasic++;
		_countBottom++;
		AjaxUpdater.triggerUpdate( "basicInfo", context() );
		AjaxUpdater.triggerUpdate( "bottomContainer", context() );
		return null;
	}

	// --- Scrabble pattern: refresh a SET, excluding the current container --------------------------

	/**
	 * Mirrors ScrabblePage.updateContainers(true): refresh a set of containers but DROP the one that
	 * sent the request (currentUpdateContainerID), since it is already being refreshed by the click
	 * itself. Bumps all three counters regardless (so we can see which actually re-rendered), but only
	 * the non-current ones get a server-side update call.
	 */
	public WOActionResults bumpSetExceptCurrent() {
		_countAll1++;
		_countAll2++;
		_countAll3++;

		final List<String> containers = new ArrayList<>();
		containers.add( "allBox1" );
		containers.add( "allBox2" );
		containers.add( "allBox3" );
		containers.remove( AjaxUpdateProtocol.currentUpdateContainerID() );

		for( final String id : containers ) {
			AjaxUpdater.triggerUpdate( id, context() );
		}

		return null;
	}
}
