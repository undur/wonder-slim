package ajaxplayground.components.scenario;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.sse.SSEHub;

import ajaxplayground.components.PlaygroundPage;
import er.extensions.routes.RouteInvocation;

/**
 * Scenario: a Datastar-driven page, entirely fed by server-sent events.
 *
 * The page opens one long-lived stream ({@code /ds/feed}) on load, through which the server pushes a clock, the
 * shared counter, the subscriber count and the message list to every open tab. Every button and form on the page is
 * a short SSE stream of its own ({@code /ds/inc}, {@code /ds/say}, {@code /ds/job}, {@code /ds/reset}): the server
 * answers with patches, then closes. Between them this exercises element patches in outer, inner, append and remove
 * modes, signal patches, request signals in both GET (query) and POST (JSON body) form, a per-request stream that
 * keeps going for a while (the job), and the hub broadcasting to everyone.
 *
 * All state is in-process and shared by every visitor, on purpose: open two tabs and use one.
 */
public class ScenarioDatastar extends PlaygroundPage {

	private static final SSEHub FEED = new SSEHub();
	private static final AtomicInteger COUNT = new AtomicInteger();
	private static final Deque<String> MESSAGES = new ArrayDeque<>();
	private static final int MAX_MESSAGES = 12;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern( "HH:mm:ss" );
	private static ScheduledExecutorService _ticker;

	public ScenarioDatastar( WOContext context ) {
		super( context );
	}

	/**
	 * {@code /ds/feed}: the page's long-lived stream. Sends the current state, then stays open for broadcasts.
	 */
	public static WOActionResults feed( final RouteInvocation invocation ) {
		startTicker();

		final DatastarSSE ds = new DatastarSSE();
		FEED.add( ds.stream() );

		ds.patchSignals( "{\"count\": " + COUNT.get() + "}" );
		ds.patchElements( messagesHtml() );

		broadcastSubscribers();
		ds.stream().onClose( ScenarioDatastar::broadcastSubscribers );

		return ds.response();
	}

	/**
	 * {@code /ds/inc}: bump the shared counter. Everyone gets the new value through the feed; the caller also gets it on
	 * this request's own stream, then the stream ends.
	 */
	public static WOActionResults increment( final RouteInvocation invocation ) {
		final int count = COUNT.incrementAndGet();
		DatastarSSE.patchSignals( FEED, "{\"count\": " + count + "}" );

		final DatastarSSE ds = new DatastarSSE();
		ds.patchSignals( "{\"count\": " + count + "}" );
		ds.close();
		return ds.response();
	}

	/**
	 * {@code /ds/say}: the message form. The text arrives as the {@code message} signal in the POST body. The new message
	 * is appended to every tab's list; the caller's input is cleared by patching the signal back to empty.
	 */
	public static WOActionResults say( final RouteInvocation invocation ) {
		final Map<String, String> signals = DatastarSSE.signals( invocation.request() );
		final String message = signals.getOrDefault( "message", "" ).strip();

		if( !message.isEmpty() ) {
			final String line;

			synchronized( MESSAGES ) {
				MESSAGES.addLast( LocalTime.now().format( TIME ) + "  " + message );

				while( MESSAGES.size() > MAX_MESSAGES ) {
					MESSAGES.removeFirst();
				}

				line = MESSAGES.getLast();
			}

			DatastarSSE.patchElements( FEED, "#messages", "append", messageHtml( line ) );
		}

		final DatastarSSE ds = new DatastarSSE();
		ds.patchSignals( "{\"message\": \"\"}" );
		ds.close();
		return ds.response();
	}

	/**
	 * {@code /ds/job}: a request whose stream stays open for a few seconds, patching a progress bar as it goes. Runs on
	 * its own virtual thread; the route returns the open response right away.
	 */
	public static WOActionResults job( final RouteInvocation invocation ) {
		final DatastarSSE ds = new DatastarSSE();

		Thread.startVirtualThread( () -> {
			try {
				for( int step = 1; step <= 10 && ds.isOpen(); step++ ) {
					final int percent = step * 10;
					ds.patchElements( "#job", "inner", "<div class=\"bar\"><div class=\"fill\" style=\"width:" + percent + "%\"></div></div><p>Step " + step + " of 10</p>" );
					Thread.sleep( 300 );
				}

				if( ds.isOpen() ) {
					ds.patchElements( "#job", "inner", "<p class=\"done\">Done at " + LocalTime.now().format( TIME ) + " - <a href=\"#\" data-on:click__prevent=\"@get('/ds/job')\">run again</a></p>" );
				}
			}
			catch( final InterruptedException e ) {
				Thread.currentThread().interrupt();
			}
			finally {
				ds.close();
			}
		} );

		return ds.response();
	}

	/**
	 * {@code /ds/reset}: counter to zero, messages gone, for every tab.
	 */
	public static WOActionResults reset( final RouteInvocation invocation ) {
		COUNT.set( 0 );

		synchronized( MESSAGES ) {
			MESSAGES.clear();
		}

		DatastarSSE.patchSignals( FEED, "{\"count\": 0}" );
		DatastarSSE.patchElements( FEED, null, null, messagesHtml() );

		final DatastarSSE ds = new DatastarSSE();
		ds.close();
		return ds.response();
	}

	private static void broadcastSubscribers() {
		DatastarSSE.patchSignals( FEED, "{\"subscribers\": " + FEED.size() + "}" );
	}

	private static String messagesHtml() {
		final List<String> lines;

		synchronized( MESSAGES ) {
			lines = new ArrayList<>( MESSAGES );
		}

		final StringBuilder sb = new StringBuilder( "<ul id=\"messages\">\n" );

		for( final String line : lines ) {
			sb.append( messageHtml( line ) ).append( '\n' );
		}

		return sb.append( "</ul>" ).toString();
	}

	private static String messageHtml( final String line ) {
		return "<li>" + DatastarSSE.html( line ) + "</li>";
	}

	private static synchronized void startTicker() {
		if( _ticker == null ) {
			_ticker = Executors.newSingleThreadScheduledExecutor( r -> {
				final Thread t = new Thread( r, "datastar-clock" );
				t.setDaemon( true );
				return t;
			} );

			_ticker.scheduleAtFixedRate( () -> DatastarSSE.patchSignals( FEED, "{\"clock\": " + DatastarSSE.json( LocalTime.now().format( TIME ) ) + "}" ), 1, 1, TimeUnit.SECONDS );
		}
	}
}
