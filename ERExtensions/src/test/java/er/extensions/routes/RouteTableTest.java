package er.extensions.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class RouteTableTest {

	@Test
	public void routePathDropsTheQueryString() {
		assertEquals( "/about", RouteTable.routePath( "/about?x=1", null ) );
		assertEquals( "/", RouteTable.routePath( "/?x=1", null ) );
	}

	@Test
	public void routePathStripsTheCarriedApplicationPrefixWhateverTheAdaptorPath() {
		assertEquals( "/", RouteTable.routePath( "/cgi-bin/WebObjects/App.woa", "/cgi-bin/WebObjects/App.woa" ) );
		assertEquals( "/", RouteTable.routePath( "/cgi-bin/WebObjects/App.woa/", "/cgi-bin/WebObjects/App.woa" ) );
		assertEquals( "/", RouteTable.routePath( "/Apps/WebObjects/App.woa/?x=1", "/Apps/WebObjects/App.woa" ) );
		assertEquals( "/about", RouteTable.routePath( "/Apps/WebObjects/App.woa/about", "/Apps/WebObjects/App.woa" ) );
		assertEquals( "/about", RouteTable.routePath( "/Apps/WebObjects/App.woa/2/about", "/Apps/WebObjects/App.woa" ) );
	}

	@Test
	public void freestyleURLsAndForeignPrefixesPassThrough() {
		assertEquals( "/about", RouteTable.routePath( "/about", null ) );
		assertEquals( "/about", RouteTable.routePath( "/about", "" ) );
		assertEquals( "/cgi-bin/WebObjects/Other.woa/x", RouteTable.routePath( "/cgi-bin/WebObjects/Other.woa/x", "/cgi-bin/WebObjects/App.woa" ) );
	}
}
