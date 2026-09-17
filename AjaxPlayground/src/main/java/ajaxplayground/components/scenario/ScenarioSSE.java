package ajaxplayground.components.scenario;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.sse.SSEHub;
import com.webobjects.appserver.sse.SSEStream;

import ajaxplayground.components.PlaygroundPage;
import er.extensions.routes.RouteInvocation;

/**
 * Scenario: server-sent events end to end.
 *
 * The page's JavaScript opens an {@code EventSource} on this app's {@code /sse/clock}
 * route (served by {@link #clockStream}), which streams a {@code tick} event once a
 * second to every subscriber, plus a {@code subscribers} event whenever someone joins
 * or leaves. Direct-connect this exercises wo-adaptor-jetty's streaming responses;
 * through modulo it exercises the proxy passing an open-ended response through
 * unbuffered. Same page, same URL, either way.
 *
 * The subscriber count is the interesting bit: it drops when you close the
 * EventSource, which shows the server noticing a client going away.
 */
public class ScenarioSSE extends PlaygroundPage {

	private static final SSEHub CLOCK = new SSEHub();
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern( "HH:mm:ss" );
	private static ScheduledExecutorService _ticker;

	public ScenarioSSE( WOContext context ) {
		super( context );
	}

	/**
	 * The {@code /sse/clock} route: subscribes the caller to the clock feed.
	 */
	public static WOActionResults clockStream( final RouteInvocation invocation ) {
		startTicker();

		final SSEStream stream = CLOCK.open();

		// Last-Event-ID is what a reconnecting EventSource sends; echo it so the page can show resumption at work
		final String lastEventID = invocation.request().headerForKey( "last-event-id" );
		stream.send( "hello", lastEventID == null ? "connected" : "reconnected, your last event id was " + lastEventID );

		CLOCK.broadcast( "subscribers", String.valueOf( CLOCK.size() ) );
		stream.onClose( () -> CLOCK.broadcast( "subscribers", String.valueOf( CLOCK.size() ) ) );

		return stream.response();
	}

	private static synchronized void startTicker() {
		if( _ticker == null ) {
			_ticker = Executors.newSingleThreadScheduledExecutor( r -> {
				final Thread t = new Thread( r, "sse-clock" );
				t.setDaemon( true );
				return t;
			} );

			_ticker.scheduleAtFixedRate( () -> {
				final String now = LocalTime.now().format( TIME );
				CLOCK.broadcast( "tick", now, now );
			}, 1, 1, TimeUnit.SECONDS );
		}
	}
}
