package er.extensions.appserver;

import java.lang.reflect.Method;
import java.util.List;

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
 * <p>This class is a thin, backend-agnostic facade: it reaches the actual ring-buffer
 * appender through the same reflective logging bridge that {@link er.extensions.ERXLoggingSupport}
 * uses, so ERExtensions keeps no compile dependency on a specific logging backend. If
 * no bridge is present (or it predates this feature), install is a no-op and snapshots
 * are empty.
 *
 * <h2>Development only</h2>
 * Install is gated by the caller on development mode. Exposing log output over HTTP is
 * a dev convenience and is not meant to run in production.
 */
public final class ERXConsoleCapture {

	private static final String LOGGING_BRIDGE_CLASS = "er.extensions.logging.ERXTemporaryLoggingBridge";

	private static volatile boolean _installed;

	private ERXConsoleCapture() {
	}

	/**
	 * Attaches the in-memory log capture appender via the logging bridge. Idempotent;
	 * silently does nothing if no compatible logging bridge is available.
	 */
	public static synchronized void install() {
		if (_installed) {
			return;
		}
		final Boolean active = (Boolean) invokeBridge("installLogCapture", new Class<?>[0]);
		_installed = Boolean.TRUE.equals(active);
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
	@SuppressWarnings("unchecked")
	public static List<String> snapshot(final String contains, final int tail) {
		if (!_installed) {
			return List.of();
		}
		final Object result = invokeBridge("logSnapshot", new Class<?>[] { String.class, int.class }, contains, tail);
		return result instanceof List ? (List<String>) result : List.of();
	}

	/**
	 * Invokes a static method on the logging bridge reflectively, so ERExtensions need
	 * not compile-depend on the logging backend. Returns null (and leaves capture
	 * disabled) if the bridge or method isn't present.
	 */
	private static Object invokeBridge(final String methodName, final Class<?>[] paramTypes, final Object... args) {
		try {
			final Class<?> bridge = Class.forName(LOGGING_BRIDGE_CLASS);
			final Method method = bridge.getMethod(methodName, paramTypes);
			return method.invoke(null, args);
		}
		catch (final ClassNotFoundException | NoSuchMethodException e) {
			// No logging bridge, or an older bridge without capture support: capture is
			// simply unavailable. Not fatal — the app runs fine without the log endpoint.
			return null;
		}
		catch (final ReflectiveOperationException e) {
			throw new RuntimeException("Failed to invoke logging bridge method " + methodName, e);
		}
	}
}
