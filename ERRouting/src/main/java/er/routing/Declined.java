package er.routing;

/**
 * EXPERIMENTAL (route-links branch). Thrown to decline a request from deep inside a route, as returning
 * {@link RouteHandler#DECLINED} does from its handler: the next matching route gets the request. Thrown by
 * {@link RouteInvocation#parameter(String, Class)} for a value that doesn't convert.
 */

public class Declined extends RuntimeException {

	public Declined( final String reason ) {
		super( reason, null, false, false );
	}
}
