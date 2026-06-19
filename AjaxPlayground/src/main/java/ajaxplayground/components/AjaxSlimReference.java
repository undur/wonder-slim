package ajaxplayground.components;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.scenario.ScenarioInvoice;

/**
 * The canonical reference page for the AjaxSlim element library: every element, what it does, and its
 * full binding table. Static documentation (no live widgets) - the content is sourced verbatim from
 * each element's javadoc @binding docs, so this page IS the human-readable form of that contract.
 */
public class AjaxSlimReference extends PlaygroundPage {

	public AjaxSlimReference(WOContext context) {
		super(context);
	}

	/** Open the invoice editor integration page (linked from the bottom of the reference). */
	public WOActionResults scenarioInvoice() {
		return pageWithName( ScenarioInvoice.class );
	}
}
