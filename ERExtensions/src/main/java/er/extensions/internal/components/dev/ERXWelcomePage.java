package er.extensions.internal.components.dev;

import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSBundle;

import er.extensions.components.ERXComponent;
import er.extensions.projectlayout.ERXProjectLayoutBundle;
import er.extensions.routes.RouteTable;

/**
 * What {@code /} shows in development while the application hasn't mapped it: what's running, and how to map a page of
 * its own. Deployed, an unmapped {@code /} answers 404, like any other URL nothing claims.
 */
public class ERXWelcomePage extends ERXComponent {

	public ERXWelcomePage( final WOContext context ) {
		super( context );
	}

	/**
	 * @return The application's project bundle, or null when it isn't run from a project with a layout
	 */
	private static ERXProjectLayoutBundle projectBundle() {
		return NSBundle.mainBundle() instanceof ERXProjectLayoutBundle bundle ? bundle : null;
	}

	public boolean hasProject() {
		return projectBundle() != null;
	}

	public String projectPath() {
		return projectBundle().bundlePath();
	}

	public String componentsFolder() {
		return projectBundle().layout().components();
	}

	/**
	 * @return true if the control panel (ERXControl) is mapped
	 */
	public boolean hasControlPanel() {
		return RouteTable.defaultRouteTable().hasRouteFor( "/wonder/admin" );
	}
}
