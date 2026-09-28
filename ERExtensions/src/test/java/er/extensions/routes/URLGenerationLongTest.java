package er.extensions.routes;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * With short URLs off, generated URLs are long and mirror the request: its adaptor path and instance number are kept,
 * and a short or route request gets the application's own prefix
 */
public class URLGenerationLongTest extends URLGenerationMatrix {

	private static App app;

	@BeforeAll
	static void createApplication() {
		app = application( false );
	}

	@Override
	App app() {
		return app;
	}

	private static final String PREFIX = "/cgi-bin/WebObjects/App.woa";

	@Test
	public void shortRequest() {
		assertURLs( "/wa/x", PREFIX + "/wa/act", PREFIX + "/wo/SID/0.", PREFIX + "/res/app/x.css", "/cgi-bin/WebObjects/App" );
	}

	@Test
	public void longRequest() {
		assertURLs( "/cgi-bin/WebObjects/App.woa/wa/x", PREFIX + "/wa/act", PREFIX + "/wo/SID/0.", PREFIX + "/res/app/x.css", "/cgi-bin/WebObjects/App" );
	}

	/**
	 * Generated URLs always carry the extension
	 */
	@Test
	public void longRequestWithoutTheExtension() {
		assertURLs( "/cgi-bin/WebObjects/App/wa/x", PREFIX + "/wa/act", PREFIX + "/wo/SID/0.", PREFIX + "/res/app/x.css", "/cgi-bin/WebObjects/App" );
	}

	/**
	 * As in WebObjects, only a URL generated in a session carries the instance number (component actions here): it pins
	 * the session's instance
	 */
	@Test
	public void longRequestWithAnInstanceNumber() {
		assertURLs( "/cgi-bin/WebObjects/App.woa/3/wa/x", PREFIX + "/wa/act", PREFIX + "/3/wo/SID/0.", PREFIX + "/res/app/x.css", "/cgi-bin/WebObjects/App" );
	}

	@Test
	public void aFrontEndsOwnAdaptorPath() {
		final String prefix = "/Apps/WebObjects/App.woa";
		assertURLs( "/Apps/WebObjects/App.woa/wa/x", prefix + "/wa/act", prefix + "/wo/SID/0.", prefix + "/res/app/x.css", "/Apps/WebObjects/App" );
		assertURLs( "/Apps/WebObjects/App.woa/3/wa/x", prefix + "/wa/act", prefix + "/3/wo/SID/0.", prefix + "/res/app/x.css", "/Apps/WebObjects/App" );
	}

	@Test
	public void routes() {
		assertURLs( "/", PREFIX + "/wa/act", PREFIX + "/wo/SID/0.", PREFIX + "/res/app/x.css", "/cgi-bin/WebObjects/App" );
		assertURLs( "/about", PREFIX + "/wa/act", PREFIX + "/wo/SID/0.", PREFIX + "/res/app/x.css", "/cgi-bin/WebObjects/App" );
		assertURLs( "/cgi-bin/WebObjects/App.woa", PREFIX + "/wa/act", PREFIX + "/wo/SID/0.", PREFIX + "/res/app/x.css", "/cgi-bin/WebObjects/App" );
		assertURLs( "/cgi-bin/WebObjects/App.woa/3/about", PREFIX + "/wa/act", PREFIX + "/3/wo/SID/0.", PREFIX + "/res/app/x.css", "/cgi-bin/WebObjects/App" );
	}

	/**
	 * The context fills in the application name the request didn't carry
	 */
	@Test
	public void noApplicationName() {
		assertURLs( "/cgi-bin/WebObjects/", PREFIX + "/wa/act", PREFIX + "/wo/SID/0.", PREFIX + "/res/app/x.css", "/cgi-bin/WebObjects/App" );
	}

	@Test
	public void completeURLs() {
		assertCompleteURLs( "/wa/x", "https://host" + PREFIX + "/wa/act", "http://host" + PREFIX + "/wa/act" );
		assertCompleteURLs( "/cgi-bin/WebObjects/App.woa/3/wa/x", "https://host" + PREFIX + "/wa/act", "http://host" + PREFIX + "/wa/act" );
		assertCompleteURLs( "/Apps/WebObjects/App.woa/wa/x", "https://host/Apps/WebObjects/App.woa/wa/act", "http://host/Apps/WebObjects/App.woa/wa/act" );
	}
}
