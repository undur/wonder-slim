package bookclubs;

import er.extensions.appserver.ERXApplication;
import er.routing.ERXRouter;

public class Application extends ERXApplication {

	public static void main( String[] args ) {
		ERXApplication.main( args, Application.class );
	}

	public Application() {
		// A plugin declares its routes as the application does, in a table of its own
		ERXRouter.declare( "guestbook", GuestbookPlugin::declare );
		ERXRouter.declare( BookclubRoutes::declare );
	}
}
