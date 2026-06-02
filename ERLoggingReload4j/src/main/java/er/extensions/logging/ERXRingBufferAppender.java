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

	public ERXRingBufferAppender() {
		setLayout(new PatternLayout(CAPTURE_PATTERN));
		setName("ERXRingBuffer");
	}

	/**
	 * Attaches a shared ring-buffer appender to the root logger, if not already
	 * attached. Idempotent.
	 *
	 * @return true if capture is active after this call
	 */
	public static synchronized boolean install() {
		if (_installed != null) {
			return true;
		}
		final ERXRingBufferAppender appender = new ERXRingBufferAppender();
		Logger.getRootLogger().addAppender(appender);
		_installed = appender;
		return true;
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
