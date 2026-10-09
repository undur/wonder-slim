package er.extensions.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The class a declaration is watched by is the one calling into the router, however many of the router's own methods
 * the call goes through: an option-less declare delegates to the one with options
 */
public class RouteIdentityCallerTest {

	/**
	 * Stands for the router: an overload delegating to another, which asks who called
	 */
	static class Api {

		static Class<?> declare() {
			return declare( 0 );
		}

		static Class<?> declare( final int options ) {
			return RouteIdentity.callerOf( Api.class );
		}
	}

	@Test
	public void throughADelegatingOverload() {
		assertEquals( RouteIdentityCallerTest.class, Api.declare() );
	}

	@Test
	public void direct() {
		assertEquals( RouteIdentityCallerTest.class, Api.declare( 1 ) );
	}
}
