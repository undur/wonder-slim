package bookclubs;

import er.extensions.appserver.ERXApplication;
import er.routing.ERXRouter;
import er.extensions.routes.RouteTable;

public class Application extends ERXApplication {

	public static void main( String[] args ) {
		ERXApplication.main( args, Application.class );
	}

	public Application() {
		final ERXRouter router = new ERXRouter();
		BookclubRoutes.create( router );
		router.mapInto( RouteTable.defaultRouteTable() );
	}
}
