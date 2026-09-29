package er.extensions.dev;

import java.util.List;

import er.extensions.ERXLoggingSupport;

/**
 * Development aid that makes the application's recent log output readable back over
 * HTTP (see {@link ERXConsoleLogRequestHandler}).
 *
 * <p>Why this exists: when driving the app from an external tool (a script, or an AI
 * assistant working in the repo) it's common to add temporary debug logging and then
 * need to read the result. Without this, that means a human copying the IDE console
 * by hand. With it, the output is one HTTP fetch away.
 *
 * <h2>How it captures — and why not a stream tee</h2>
 * Capture happens at the <em>logging backend's appender</em>, not by wrapping
 * {@code System.out}/{@code System.err}. In this stack WO's {@code NSLog} is bridged
 * INTO log4j while log4j's {@code ConsoleAppender} writes back OUT to
 * {@code System.out}; a stream tee would therefore sit inside that feedback path and
 * re-capture the appender's own output, producing runaway duplication. Attaching a
 * bounded in-memory appender at the single point where all log events converge avoids
 * the loop and never disturbs the streams.
 *
 * <p>This class is a thin, backend-agnostic facade over the logging backend a plugin
 * provides ({@link er.extensions.ERXLoggingBackend}), so ERExtensions keeps no compile
 * dependency on a specific logging backend. Without one, install is a no-op and
 * snapshots are empty.
 *
 * <h2>Development only</h2>
 * Install is gated by the caller on development mode. Exposing log output over HTTP is
 * a dev convenience and is not meant to run in production.
 */
public final class ERXConsoleCapture {

	private static volatile boolean _installed;

	private ERXConsoleCapture() {
	}

	/**
	 * Attaches the in-memory log capture through the logging backend. Idempotent;
	 * silently does nothing without a logging backend.
	 */
	public static synchronized void install() {
		if (_installed) {
			return;
		}
		_installed = ERXLoggingSupport.installCapture();
	}

	public static boolean isInstalled() {
		return _installed;
	}

	/**
	 * @return a snapshot of recently captured log lines (oldest first), optionally
	 *         filtered to lines containing {@code contains} (case-sensitive; ignored
	 *         when null or empty) and limited to the last {@code tail} matching lines
	 *         (ignored when {@code <= 0}). Empty when capture isn't installed.
	 */
	public static List<String> snapshot(final String contains, final int tail) {
		if (!_installed) {
			return List.of();
		}
		return ERXLoggingSupport.capturedLines(contains, tail);
	}
}
