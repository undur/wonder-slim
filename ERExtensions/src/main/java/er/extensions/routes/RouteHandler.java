package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

/**
 * FIXME: Really a duplicate of WORequestHandler (apart from returning WOActionResults instead of WOResponse, making it a bit more convenient) // Hugi 2025-10-11 
 * FIXME: Should be an interface // Hugi 2025-10-11
 */

public abstract class RouteHandler {

	public abstract WOActionResults handle( WrappedURL url, WOContext context );
}