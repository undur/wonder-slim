package er.extensions.control;

import er.extensions.ERXFrameworkPrincipal;
import er.extensions.admin.ERXAdmin;

/**
 * Framework principal for ERControl, the framework's control panel. Registers the control panel's routes from
 * finishInitialization(), which ERXFrameworkPrincipal invokes on ApplicationDidCreateNotification - posted at the end
 * of the application's constructor, after it has mapped its own routes, so a route an application maps itself wins.
 * A stand-in until the framework has proper plugin initialization.
 */
public class ERXControl extends ERXFrameworkPrincipal {

	public static Class[] REQUIRES = new Class[0];

	static {
		setUpFrameworkPrincipalClass( ERXControl.class );
	}

	@Override
	public void finishInitialization() {
		ERXAdmin.registerRoutes();
	}
}
