package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: WebSockets end to end.
 *
 * The page's JavaScript opens a WebSocket to this app's {@code /ws/echo}
 * endpoint (the wo-adaptor-jetty example EchoWebSocketHandler, registered in
 * Application) and echoes typed messages through it. Direct-connect this
 * exercises the adaptor's WebSocket support; through modulo it exercises the
 * WebSocket tunnel — same page, same URL, the browser picks ws:// or wss://
 * to match how the page was loaded.
 *
 * All the action is client-side; the component itself renders once and has
 * no server-side state.
 */
public class ScenarioWebSocket extends PlaygroundPage {

	public ScenarioWebSocket( WOContext context ) {
		super( context );
	}
}
