package er.routing.core;

/**
 * A route answers requests over one scheme: {@link #HTTPS} for a route that must not be reached over plain http. A
 * request over the other isn't for the route, which isn't there for it.
 */

public enum Scheme implements RouteCondition {

	HTTP,
	HTTPS;

	@Override
	public Result test( final RouteRequest request ) {
		return request.secure() == (this == HTTPS) ? Satisfied.NO_PARAMETERS : NotHere.INSTANCE;
	}
}
