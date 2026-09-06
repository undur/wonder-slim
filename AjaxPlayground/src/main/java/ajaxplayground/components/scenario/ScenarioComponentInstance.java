package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;
import er.extensions.components.ERXComponentUtilities;

/**
 * Scenario: embedding a constructed component instance with ERXWOComponentInstance.
 *
 * The page constructs an EmbeddedCounter itself, configures it through a setter before it is ever
 * rendered, keeps the reference, and binds it into the template. From there the test proves:
 * the instance's own state survives across requests (its action advances a counter); the page can
 * call methods on the instance directly; binding passing still works alongside; binding a new
 * instance replaces the embedded component; and a stateless instance is refused, loudly.
 */
public class ScenarioComponentInstance extends PlaygroundPage {

	/** The embedded instance. Replaced wholesale by replaceAction(). */
	private EmbeddedCounter _embedded;

	/** Counts replacements, to label each generation. */
	private int _generation = 1;

	/** Flipped by breakWithStatelessAction(): the template is then handed a stateless instance and must throw. */
	private boolean _bindStateless;

	/** Passed to the embedded instance as a binding. */
	public String note = "hello from the page";

	public ScenarioComponentInstance( WOContext context ) {
		super( context );
		_embedded = newCounter( "first" );
	}

	/** Construct + configure: the step a name-based switch can't offer. */
	private EmbeddedCounter newCounter( String label ) {
		final EmbeddedCounter counter = ERXComponentUtilities.instantiate( EmbeddedCounter.class, context() );
		counter.setLabel( label );
		return counter;
	}

	/** What the template binds. Normally the counter; a pooled stateless instance once the test asks for it. */
	public WOComponent boundInstance() {
		if( _bindStateless ) {
			return ERXComponentUtilities.instantiate( StatelessProbe.class, context() );
		}

		return _embedded;
	}

	public String generation() {
		return String.valueOf( _generation );
	}

	/** The page reaching into the instance and invoking a method on it - no binding, no threadlocal. */
	public WOActionResults bumpDirectlyAction() {
		_embedded.bump();
		return null;
	}

	/** Swap the embedded instance for a freshly constructed, differently configured one. */
	public WOActionResults replaceAction() {
		_generation++;
		_embedded = newCounter( "generation " + _generation );
		return null;
	}

	public WOActionResults breakWithStatelessAction() {
		_bindStateless = true;
		return null;
	}
}
