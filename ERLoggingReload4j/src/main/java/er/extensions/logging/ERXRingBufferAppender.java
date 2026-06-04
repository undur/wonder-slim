package er.extensions.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/**
 * A bounded, in-memory log4j appender that retains the most recent formatted log
 * lines in a ring buffer so they can be read back later (e.g. served over HTTP by a
 * development-only request handler).
 *
 * <p><b>Why an appender, not a System.out tee.</b> In this stack the WO {@code NSLog}
 * facility is bridged INTO log4j ({@link ERXNSLogLog4jBridge}), while log4j's own
 * {@code ConsoleAppender} writes back OUT to {@code System.out}. Wrapping
 * {@code System.out}/{@code System.err} to capture output therefore sits inside that
 * feedback path and can re-capture the appender's own output, producing runaway
 * duplication. Capturing here — at the appender layer, the single point where every
 * log event converges — avoids the loop completely and never disturbs the streams.
 *
 * <p><b>Formatting.</b> Each event is rendered with this appender's {@link Layout}
 * (set to the same pattern the console uses, so captured lines read like the console)
 * plus any throwable's stack trace. Each rendered event is split into individual
 * lines before being stored, so callers reading the buffer get clean per-line output.
 *
 * <p><b>Lifecycle.</b> {@link #install()} attaches a single shared instance to the
 * root logger; it is idempotent (a second call is a no-op) so repeated app setup can't
 * stack multiple appenders and duplicate every line. Intended for development use.
 */
public class ERXRingBufferAppender extends AppenderSkeleton {

	/** Most recent lines retained. Bounded so memory stays flat over long sessions. */
	private static final int MAX_LINES = 2000;

	/**
	 * The conversion pattern used to format captured events. Kept simple and
	 * console-like (timestamp, level, logger, message) and deliberately free of the
	 * WO-specific converters ('$', '#', 'W', …) so capture never reaches into
	 * WOApplication state while formatting.
	 */
	private static final String CAPTURE_PATTERN = "%d{MMM dd HH:mm:ss} %-5p %c - %m";

	/** The single shared instance attached to the root logger, or null if not installed. */
	private static volatile ERXRingBufferAppender _installed;

	/** The ring buffer of recent lines. Guarded by itself. */
	private final Deque<String> _lines = new ArrayDeque<>(MAX_LINES);

	/** The appender's name on the root logger; also used to detect detachment. */
	private static final String APPENDER_NAME = "ERXRingBuffer";

	/** Set once we've subscribed to re-attach on logging reconfiguration. */
	private static boolean _reattachObserverRegistered;

	public ERXRingBufferAppender() {
		setLayout(new PatternLayout(CAPTURE_PATTERN));
		setName(APPENDER_NAME);
	}

	/**
	 * Attaches the shared ring-buffer appender to the root logger and keeps it
	 * attached across logging reconfigurations.
	 *
	 * <p>Idempotent, and deliberately robust against detachment: logging gets
	 * reconfigured several times during startup (ERXExtensions / ERXConfigurationManager
	 * each call {@code configureLogging}, which begins with
	 * {@code LogManager.resetConfiguration()} — that strips <em>all</em> appenders from
	 * <em>all</em> loggers, including this one). A single attach in the app constructor
	 * therefore survives only until the next reset, which is why capture used to go
	 * silent after startup. To stay attached, we (a) re-add ourselves here if we've been
	 * detached, and (b) subscribe once to {@code ConfigurationDidChangeNotification},
	 * which {@code configureLogging} posts at the very end (after the reset and after it
	 * re-adds its own console appenders) — so every future reconfiguration re-attaches us.
	 *
	 * @return true if capture is active after this call
	 */
	public static synchronized boolean install() {
		if (_installed == null) {
			_installed = new ERXRingBufferAppender();
		}
		reattachIfNeeded();
		registerReattachObserver();
		return true;
	}

	/** Re-adds the appender to the root logger if a reset removed it. */
	private static synchronized void reattachIfNeeded() {
		if (_installed == null) {
			return;
		}
		final Logger root = Logger.getRootLogger();
		if (root.getAppender(APPENDER_NAME) == null) {
			root.addAppender(_installed);
		}
	}

	/**
	 * Subscribes (once) to the configuration-changed notification so we re-attach after
	 * any logging reconfiguration. The notification name matches the one
	 * {@code configureLogging} posts at the end of every reconfiguration.
	 */
	private static synchronized void registerReattachObserver() {
		if (_reattachObserverRegistered) {
			return;
		}
		// Mark registered up front and guard the call: console capture is a dev
		// convenience and must never be able to abort app startup. If wiring the
		// re-attach observer fails for any reason, we degrade to "captures until the
		// next logging reset" rather than taking the app down.
		_reattachObserverRegistered = true;
		try {
			com.webobjects.foundation.NSNotificationCenter.defaultCenter().addObserver(
					new ReattachObserver(),
					new com.webobjects.foundation.NSSelector("configurationDidChange", new Class[] { com.webobjects.foundation.NSNotification.class }),
					er.extensions.foundation.ERXConfigurationManager.ConfigurationDidChangeNotification,
					null);
		}
		catch (Throwable t) {
			System.err.println("ERXRingBufferAppender: could not register re-attach observer (console capture may stop after a logging reconfiguration): " + t);
		}
	}

	/**
	 * Notification target for re-attaching after logging reconfiguration. Must be a
	 * named, accessible class with a public method — NSSelector reflection cannot invoke
	 * a method on an anonymous inner class (the enclosing class member isn't accessible),
	 * which throws at notification time and aborts startup.
	 */
	public static final class ReattachObserver {
		public void configurationDidChange(com.webobjects.foundation.NSNotification n) {
			// Never let a re-attach problem propagate: this runs inside the logging
			// reconfiguration's notification dispatch, and an exception here would abort
			// that (and startup, as it once did).
			try {
				reattachIfNeeded();
			}
			catch (Throwable t) {
				System.err.println("ERXRingBufferAppender: re-attach after logging reconfiguration failed: " + t);
			}
		}
	}

	public static boolean isInstalled() {
		return _installed != null;
	}

	/**
	 * @return a snapshot of the captured lines (oldest first), optionally filtered to
	 *         lines containing {@code contains} (case-sensitive; ignored when null/empty)
	 *         and limited to the last {@code tail} matching lines (ignored when {@code <= 0}).
	 *         Returns an empty list if capture isn't installed.
	 */
	public static List<String> snapshot(final String contains, final int tail) {
		final ERXRingBufferAppender appender = _installed;
		if (appender == null) {
			return List.of();
		}

		final List<String> all;
		synchronized (appender._lines) {
			all = new ArrayList<>(appender._lines);
		}

		List<String> result = all;
		if (contains != null && !contains.isEmpty()) {
			result = new ArrayList<>();
			for (final String line : all) {
				if (line.contains(contains)) {
					result.add(line);
				}
			}
		}

		if (tail > 0 && result.size() > tail) {
			result = new ArrayList<>(result.subList(result.size() - tail, result.size()));
		}
		return result;
	}

	@Override
	protected void append(final LoggingEvent event) {
		final Layout layout = getLayout();
		if (layout == null) {
			return;
		}

		final StringBuilder rendered = new StringBuilder(layout.format(event));

		// Include a throwable's stack trace when the layout doesn't handle throwables
		// itself (PatternLayout does not), so captured errors carry their traces.
		if (layout.ignoresThrowable()) {
			final ThrowableInformation ti = event.getThrowableInformation();
			if (ti != null) {
				for (final String traceLine : ti.getThrowableStrRep()) {
					rendered.append('\n').append(traceLine);
				}
			}
		}

		// Store one entry per physical line so readers get clean line-oriented output.
		final String[] lines = rendered.toString().split("\n", -1);
		synchronized (_lines) {
			for (final String line : lines) {
				if (_lines.size() >= MAX_LINES) {
					_lines.removeFirst();
				}
				_lines.addLast(line);
			}
		}
	}

	@Override
	public void close() {
		// Nothing to release — the buffer is plain heap memory.
	}

	@Override
	public boolean requiresLayout() {
		return true;
	}
}
