package ajaxplayground;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WORequest;

import er.extensions.appserver.ERXDirectAction;
import ajaxplayground.components.Main;

/**
 * Lands the bare app URL (.../wa/ or .../wa/default) on the {@link Main} index page, so the
 * playground is reachable without knowing a component name.
 */
public class DirectAction extends ERXDirectAction {

	public DirectAction( WORequest request ) {
		super( request );
	}

	@Override
	public WOActionResults defaultAction() {
		return pageWithName( Main.class );
	}
}
