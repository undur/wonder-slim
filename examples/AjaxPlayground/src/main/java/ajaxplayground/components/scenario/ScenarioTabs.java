package ajaxplayground.components.scenario;

import java.util.List;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: a tabbed panel built from primitives - no AjaxTabbedPanel.
 *
 * The whole point: a tabbed panel is not a primitive, it's a composition. It's N links that each swap
 * a content region and highlight the active tab - which is exactly AjaxUpdateContainer (the region)
 * plus AjaxUpdateLink (the clicks). The selected tab is just a String on the component; clicking a tab
 * sets it and updates one container. The container re-renders the tab strip (highlighting the active
 * one) and the matching panel. No JavaScript, no per-tab child components, no AjaxTabbedPanel.
 *
 * The "panel" bodies here are trivial on purpose - the demonstration is the mechanism, not the content.
 */
public class ScenarioTabs extends PlaygroundPage {

	/** The tabs. Just their names; each name is both the label and the selection key. */
	public final List<String> tabs = List.of( "Overview", "Details", "Settings" );

	/** The tab currently shown. Starts on the first. */
	public String selectedTab = tabs.getFirst();

	/** Bound by the repetition as it renders the tab strip. */
	public String currentTab;

	public ScenarioTabs( WOContext context ) {
		super( context );
	}

	/** A tab link's action: select the clicked tab. The AjaxUpdateLink updates the container after. */
	public void selectTab() {
		selectedTab = currentTab;
	}

	/** True for the tab being rendered when it is the selected one (drives the active-tab styling). */
	public boolean isCurrentTabSelected() {
		return currentTab.equals( selectedTab );
	}
}
