package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: one trigger refreshing SEVERAL update containers in a single round-trip via the
 * updateContainerID="a;b;c" multi-target syntax. The link bumps a shared counter; all three
 * containers should show the new value after one request (one fetch, framed fragments, morph each).
 */
public class ScenarioMultiUpdate extends PlaygroundPage {

	private int _count;

	public ScenarioMultiUpdate(WOContext context) {
		super(context);
	}

	public int count() {
		return _count;
	}

	public WOActionResults increment() {
		_count++;
		return null;
	}
}
