package er.extensions.dev;

import java.util.List;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXApplication;

/**
 * A development-only request handler that returns the application's recently
 * captured console output (see {@link ERXConsoleCapture}) as plain text, so an
 * external tool can read the app's logs over HTTP instead of a human copying the
 * IDE console by hand.
 *
 * <p>Registered under the handler key {@value #KEY} (only in development mode), so
 * the URL looks like:
 *
 * <pre>
 *   http://localhost:PORT/cgi-bin/WebObjects/App.woa/log
 *   …/log?contains=PARSLEY        only lines containing "PARSLEY"
 *   …/log?tail=200                only the last 200 (matching) lines
 *   …/log?contains=ERROR&amp;tail=50
 * </pre>
 *
 * <p>Pairs with the WOLips/Parsley dev server (which lets the app drive the IDE):
 * this is the other direction — letting a tool read the app's console back.
 *
 * <h2>Not for production</h2>
 * Exposing console output over HTTP is a dev convenience. The handler is only
 * registered when the app is in development mode, and it additionally refuses to
 * serve if capture wasn't installed.
 */
public class ERXConsoleLogRequestHandler extends WORequestHandler {

	/** The request-handler key — the path segment after the .woa in the URL. */
	public static final String KEY = "log";

	@Override
	public WOResponse handleRequest(final WORequest request) {
		final WOResponse response = new WOResponse();
		response.setHeader("text/plain; charset=utf-8", "Content-Type");

		// Defensive: even though we only register in dev, never serve logs if capture
		// isn't active (e.g. a stray registration in a non-dev build).
		if (!ERXApplication.isDevelopmentModeSafe() || !ERXConsoleCapture.isInstalled()) {
			response.setStatus(404);
			response.setContent("Console log capture is not available.");
			return response;
		}

		final String contains = request.stringFormValueForKey("contains");
		final int tail = parseInt(request.stringFormValueForKey("tail"), 0);

		final List<String> lines = ERXConsoleCapture.snapshot(contains, tail);

		final StringBuilder body = new StringBuilder(lines.size() * 80 + 64);
		for (final String line : lines) {
			body.append(line).append('\n');
		}
		// A small trailer so a reader knows it reached the end and how much it got.
		body.append("--- ").append(lines.size()).append(" line(s)");
		if (contains != null && !contains.isEmpty()) {
			body.append(" matching \"").append(contains).append('"');
		}
		body.append(" ---\n");

		response.setContent(body.toString());
		response.setStatus(200);
		return response;
	}

	private static int parseInt(final String value, final int fallback) {
		if (value == null || value.isEmpty()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (final NumberFormatException e) {
			return fallback;
		}
	}
}
