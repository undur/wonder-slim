package er.extensions.experimental.routing.core;

import java.util.Map;
import java.util.Set;

/**
 * A condition a route places on a request, beyond its path: a host, HTTP methods. Declared with the route.
 */

public interface RouteCondition {

	/**
	 * @return What the condition says about the request
	 */
	public Result test( RouteRequest request );

	/**
	 * @return How specific the condition is. Among routes with the same path shape, the one whose conditions add up to
	 *         more comes first.
	 */
	public default int specificity() {
		return 1;
	}

	/**
	 * @return true if a request could satisfy both this condition and the other, of the same type: two routes with the
	 *         same path shape whose conditions all overlap like this are a conflict
	 */
	public default boolean overlaps( final RouteCondition other ) {
		return equals( other );
	}

	/**
	 * What a condition says about a request
	 */
	public sealed interface Result {}

	/**
	 * The request satisfies the condition, which may contribute parameters (a host pattern's)
	 */
	public record Satisfied( Map<String, String> parameters ) implements Result {

		public static final Satisfied NO_PARAMETERS = new Satisfied( Map.of() );

		public Satisfied {
			parameters = Map.copyOf( parameters );
		}
	}

	/**
	 * For this request, the route isn't there (a host it doesn't answer)
	 */
	public record NotHere() implements Result {

		public static final NotHere INSTANCE = new NotHere();
	}

	/**
	 * The route is there, but not for this request (a method it doesn't accept). If every route at a path says this, the
	 * answer is {@code 405}, allowing what they allow.
	 */
	public record NotAllowed( Set<String> allowedMethods ) implements Result {

		public NotAllowed {
			allowedMethods = Set.copyOf( allowedMethods );
		}
	}
}
