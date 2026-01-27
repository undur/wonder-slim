package er.extensions.routes;

import com.webobjects.appserver._private.WODirectActionRequestHandler;

public class RouteRequestHandler extends WODirectActionRequestHandler {

	public RouteRequestHandler() {
		super("er.extensions.routes.RouteAction", "default", true);
	}
}