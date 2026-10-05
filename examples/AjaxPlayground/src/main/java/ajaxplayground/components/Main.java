package ajaxplayground.components;

import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;

/**
 * Navigation index for the playground: a static list of links to every gallery and scenario page.
 * Navigation is by clean route URL (see {@code ajaxplayground.Routes}), so this component holds no
 * action methods - the template is just anchor tags to those routes.
 */
public class Main extends ERXComponent {

	public Main( WOContext context ) {
		super( context );
	}
}
