package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;

/**
 * A little like a request handler, but for handling RouteInvocations  
 */

@FunctionalInterface
public interface RouteHandler {

	public abstract WOActionResults handle( final RouteInvocation invocation );
}