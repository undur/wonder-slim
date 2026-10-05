package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;

/**
 * A stateless component, existing only so ScenarioComponentInstance can prove that ERXWOComponentInstance refuses
 * to embed a stateless (pooled, shared) instance.
 */
public class StatelessProbe extends ERXComponent {

	public StatelessProbe( WOContext context ) {
		super( context );
	}

	@Override
	public boolean isStateless() {
		return true;
	}
}
