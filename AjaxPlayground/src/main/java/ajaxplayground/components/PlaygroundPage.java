package ajaxplayground.components;

import com.webobjects.appserver.WOContext;

import ajaxplayground.Endpoints;

import er.extensions.components.ERXComponent;

/**
 * Shared base for every playground page. Navigation between pages is by clean route URL (see
 * {@code ajaxplayground.Routes}), so pages link home with a plain anchor to "/" rather than a
 * component action.
 */
public abstract class PlaygroundPage extends ERXComponent {

	public PlaygroundPage( WOContext context ) {
		super( context );
	}

	/**
	 * EXPERIMENTAL (route-links branch). The endpoints, for links: {@code <wo:link route="$routes.search" …>}
	 */
	public Endpoints routes() {
		return Endpoints.instance();
	}
}
