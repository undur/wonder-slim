package er.extensions.routes;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * With short URLs on (the default), every generated URL is short, whatever shape the request arrived in
 */
public class URLGenerationShortTest extends URLGenerationMatrix {

	private static App app;

	@BeforeAll
	static void createApplication() {
		app = application( true );
	}

	@Override
	App app() {
		return app;
	}

	@Test
	public void shortRequest() {
		assertURLs( "/wa/x", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
	}

	@Test
	public void longRequest() {
		assertURLs( "/cgi-bin/WebObjects/App.woa/wa/x", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
	}

	@Test
	public void longRequestWithoutTheExtension() {
		assertURLs( "/cgi-bin/WebObjects/App/wa/x", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
	}

	/**
	 * The instance number goes with the prefix: a short URL can't carry one (a proxy's cookie affinity pins the instance)
	 */
	@Test
	public void longRequestWithAnInstanceNumber() {
		assertURLs( "/cgi-bin/WebObjects/App.woa/3/wa/x", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
	}

	@Test
	public void aFrontEndsOwnAdaptorPath() {
		assertURLs( "/Apps/WebObjects/App.woa/wa/x", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
		assertURLs( "/Apps/WebObjects/App.woa/3/wa/x", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
	}

	@Test
	public void routes() {
		assertURLs( "/", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
		assertURLs( "/about", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
		assertURLs( "/cgi-bin/WebObjects/App.woa", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
		assertURLs( "/cgi-bin/WebObjects/App.woa/3/about", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
	}

	@Test
	public void noApplicationName() {
		assertURLs( "/cgi-bin/WebObjects/", "/wa/act", "/wo/SID/0.", "/res/app/x.css", "/" );
	}

	@Test
	public void completeURLs() {
		assertCompleteURLs( "/wa/x", "https://host/wa/act", "http://host/wa/act" );
		assertCompleteURLs( "/cgi-bin/WebObjects/App.woa/3/wa/x", "https://host/wa/act", "http://host/wa/act" );
		assertCompleteURLs( "/Apps/WebObjects/App.woa/wa/x", "https://host/wa/act", "http://host/wa/act" );
	}
}
