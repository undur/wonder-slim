package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: a video served by the resource request handler, which a browser plays and seeks with range requests
 * (Range: bytes=… answered with 206 Partial Content). The video is a generated test pattern with a running clock, so a
 * seek shows at a glance whether it landed where it should.
 */
public class ScenarioVideo extends PlaygroundPage {

	public ScenarioVideo( WOContext context ) {
		super( context );
	}

	/**
	 * @return The video's resource URL (stamped in production, like any other)
	 */
	public String videoURL() {
		return application().resourceManager().urlForResourceNamed( "video/range-test.webm", null, null, context().request() );
	}
}
