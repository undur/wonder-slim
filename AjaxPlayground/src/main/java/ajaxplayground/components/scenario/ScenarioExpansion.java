package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: AjaxExpansion disclosure sections surviving a morph.
 *
 * The whole point of AjaxExpansion's server ping is that the open state survives an update of an
 * enclosing container: without it, morphing the zone would reconcile an open section against the
 * server's collapsed markup and snap it shut. This page holds two sections inside one update
 * container - one keeping its state internally, one bound to a page ivar (echoed so a test can
 * prove the state round-tripped) - plus a link that re-renders the container.
 */
public class ScenarioExpansion extends PlaygroundPage {

	/** State of the bound section - owned by the page, pushed by the expansion's toggle ping. */
	public boolean boundExpanded;

	public ScenarioExpansion( WOContext context ) {
		super( context );
	}

	/** The server-side view of the bound section's state, as text a test can read. */
	public String boundEcho() {
		return String.valueOf( boundExpanded );
	}
}
