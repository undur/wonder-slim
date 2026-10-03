package bookclubs;

import er.extensions.appserver.ERXApplication;

public class Application extends ERXApplication {

	public static void main( String[] args ) {
		ERXApplication.main( args, Application.class );
	}

	public Application() {
		BookclubRoutes.declare();
	}
}
