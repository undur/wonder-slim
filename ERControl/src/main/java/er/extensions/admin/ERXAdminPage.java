package er.extensions.admin;

import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;
import er.extensions.control.ERXControlPages.Page;

/**
 * Renders a registered control panel page: the admin layout, with the page's component as its content.
 */
public class ERXAdminPage extends ERXComponent {

	/**
	 * The page rendered
	 */
	public Page controlPage;

	public ERXAdminPage( WOContext context ) {
		super( context );
	}

	public String componentName() {
		return controlPage.component().getName();
	}
}
