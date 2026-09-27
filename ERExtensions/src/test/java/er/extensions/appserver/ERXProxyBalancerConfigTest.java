package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WOCookie;

public class ERXProxyBalancerConfigTest {

	/**
	 * The route and cookie name must match what the balancer configuration names (BalancerMember route=app_2001,
	 * stickysession=routeid_app), and the value must carry the route after a dot, which is where the balancer reads it.
	 */
	@Test
	public void cookieMatchesTheBalancerConfiguration() {
		final ERXProxyBalancerConfig config = new ERXProxyBalancerConfig( "My.App", 2001 );
		assertEquals( "my_app_2001", config.route() );
		assertEquals( "routeid_my_app", config.cookieName() );

		final WOCookie cookie = config.createCookie( false );
		assertEquals( "routeid_my_app", cookie.name() );
		assertEquals( ".my_app_2001", cookie.value() );
		assertEquals( "/", cookie.path() );
		assertTrue( cookie.isHttpOnly() );
	}

	@Test
	public void routeIsTheValueAfterTheFirstDot() {
		final String value = new ERXProxyBalancerConfig( "App", 2002 ).createCookie( true ).value();
		assertEquals( "app_2002", value.substring( value.indexOf( '.' ) + 1 ) );
	}
}
