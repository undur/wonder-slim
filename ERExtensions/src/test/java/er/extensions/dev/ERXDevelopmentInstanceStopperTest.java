package er.extensions.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ERXDevelopmentInstanceStopperTest {

	private static String stopURL( String adaptorURL, boolean onLocalhost, boolean directConnect ) throws Exception {
		return ERXDevelopmentInstanceStopper.stopURL( adaptorURL, onLocalhost, directConnect, 2001, "App.woa", "wa" ).toString();
	}

	@Test
	public void directConnect() throws Exception {
		assertEquals( "http://localhost:2001/cgi-bin/WebObjects/App.woa/wa/stop", stopURL( "http://localhost:1200/cgi-bin/WebObjects", true, true ) );
		assertEquals( "http://myhost:2001/cgi-bin/WebObjects/App.woa/wa/stop", stopURL( "http://myhost:1200/cgi-bin/WebObjects", false, true ) );
	}

	@Test
	public void behindAnAdaptor() throws Exception {
		assertEquals( "http://myhost/cgi-bin/WebObjects/App.woa/-2001/wa/stop", stopURL( "http://myhost/cgi-bin/WebObjects", false, false ) );
		assertEquals( "http://localhost/cgi-bin/WebObjects/App.woa/-2001/wa/stop", stopURL( "http://myhost/cgi-bin/WebObjects/", true, false ) );
	}
}
