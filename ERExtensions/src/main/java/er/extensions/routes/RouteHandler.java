package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

/**
 * FIXME: Really a duplicate of WORequestHandler (apart from returning WOActionResults instead of WOResponse, making it a bit more convenient) // Hugi 2025-10-11 
 * FIXME: Should be an interface // Hugi 2025-10-11
 * FIXME: handle() should really accept something like a "RouteRequest" or "RouteContext" rather than the current parameters // Hugi 2025-10-16
 */

public abstract class RouteHandler {

	public abstract WOActionResults handle( RouteURL url, WOContext context );
}