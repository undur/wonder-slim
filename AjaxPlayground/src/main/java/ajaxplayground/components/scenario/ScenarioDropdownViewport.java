package ajaxplayground.components.scenario;

import java.util.ArrayList;
import java.util.List;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: dropdown stays within the viewport (height + width).
 *
 * Selects with deliberately LONG option labels, placed near the viewport edges, so a small viewport
 * forces the dropdown to flip up and/or right-align. The Playwright guard drives a small viewport,
 * opens each select, and asserts the open dropdown's rect stays entirely within the viewport - a
 * regression lock for the "never beyond viewport" behaviour and the grow-to-content width.
 */
public class ScenarioDropdownViewport extends PlaygroundPage {

	public String currentItem;
	public String topLeftSel;
	public String bottomRightSel;

	public ScenarioDropdownViewport( WOContext context ) {
		super( context );
	}

	/** Many options, each with a long (~100 char) label so the list wants to be tall AND wide. */
	public List<String> items() {
		List<String> items = new ArrayList<>();
		for( int i = 1; i <= 40; i++ ) {
			items.add( "Item " + i + " - a deliberately long accounting-key-style label that should render on one line (" + i + ")" );
		}
		return items;
	}
}
