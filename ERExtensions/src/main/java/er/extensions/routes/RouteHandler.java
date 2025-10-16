package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;

/**
 * TODO: Hmmm… This is reeeaaally a duplicate of WORequestHandler (apart from handling a RouteRequest and returning WOActionResults) // Hugi 2025-10-11 
 * TODO: Should probably be an interface // Hugi 2025-10-11
 */

public abstract class RouteHandler {

	public abstract WOActionResults handle( final RouteInvocation invocation );
}