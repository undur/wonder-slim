package er.extensions.routes;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;

public class RouteRequestHandler extends WORequestHandler {

	@Override
	public WOResponse handleRequest(WORequest request) {
		final WOApplication app = WOApplication.application();
		
		// For some reason ERXRequest disables WO's automatic context creation, so we have to create that context ourselves.
		final WOContext context = app.createContextForRequest(request);
		
		// The request's URL  doesn't have an adaptor prefix, so we set it on our context to ensure proper dynamic URL generation
		context._url().setPrefix(app.adaptorPath());

		return RouteTable.defaultRouteTable().handle( request, false ).generateResponse();
	}
}