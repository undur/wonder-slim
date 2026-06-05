package ajaxplayground.components;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;

/**
 * Shared base for every playground page. Provides the "back to index" action so each page can link
 * home without duplicating navigation wiring.
 */
public abstract class PlaygroundPage extends ERXComponent {

	public PlaygroundPage( WOContext context ) {
		super( context );
	}

	public WOActionResults home() {
		return pageWithName( Main.class );
	}
}
