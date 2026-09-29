package er.extensions.control;

import java.util.List;

import er.extensions.ERXExtensions;
import er.extensions.ERXPlugin;
import er.extensions.admin.ERXAdmin;
import er.extensions.appserver.ERXApplication;

/**
 * ERControl's plugin, for the framework's control panel. Registers the control panel's routes once the application is
 * constructed, after the routes it maps in its constructor, so a route the application maps itself at the control
 * panel's path wins.
 */
public class ERXControl implements ERXPlugin {

	@Override
	public List<Class<? extends ERXPlugin>> requires() {
		return List.of( ERXExtensions.class );
	}

	@Override
	public void finishInitialization( final ERXApplication application ) {
		ERXAdmin.registerRoutes();
	}
}
