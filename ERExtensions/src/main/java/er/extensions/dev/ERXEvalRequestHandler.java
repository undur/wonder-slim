package er.extensions.dev;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXApplication;
import er.extensions.foundation.ERXHTTPUtilities;
import er.extensions.dev.ng.NGDevJson;
import er.extensions.dev.ng.NGDevLoopback;
import er.extensions.dev.ng.NGEvalSession;

/**
 * A development-only request handler that evaluates a Java snippet inside this running
 * application's JVM and returns the result as JSON — the WebObjects-side counterpart of
 * ng-objects' /ng/dev/eval, sharing one engine ({@link NGEvalSession} in ng-core) and the same
 * request/response shape.
 *
 * <p>Registered under the handler key {@value #KEY} (only in development mode), so the URL is:
 *
 * <pre>
 *   http://localhost:PORT/cgi-bin/WebObjects/App.woa/eval?snippet=1%2B1
 *   …/eval?reset=true                discard the persistent session first
 *   POST …/eval  with the snippet as the request body
 * </pre>
 *
 * <p>The snippet runs against the application's own live classes and statics, so a tool can
 * inspect real objects — a live Cayenne {@code ObjectContext}, the running app singleton —
 * rather than reconstructing them in a separate jshell process. The session is persistent:
 * variables and definitions survive across calls; {@code reset=true} starts fresh.
 *
 * <h2>Not for production, and loopback-only</h2>
 * This is arbitrary code execution in the app's JVM. It's registered only in development mode
 * (and re-checks that per request), and additionally refuses any client that isn't on the
 * loopback interface.
 */
public class ERXEvalRequestHandler extends WORequestHandler {

	/** The request-handler key — the path segment after the .woa in the URL. */
	public static final String KEY = "eval";

	@Override
	public WOResponse handleRequest(final WORequest request) {
		final WOResponse response = new WOResponse();
		response.setHeader("application/json; charset=utf-8", "Content-Type");

		// Defensive: even though we only register in dev, never evaluate in a non-dev build.
		if (!ERXApplication.isDevelopmentModeSafe()) {
			response.setStatus(404);
			response.setContent("{\"status\":\"error\",\"diagnostics\":[\"eval is not available\"]}");
			return response;
		}

		if (!NGDevLoopback.isLoopback(ERXHTTPUtilities.ipAddressFromRequest(request))) {
			response.setStatus(403);
			response.setContent("{\"status\":\"error\",\"diagnostics\":[\"/eval is restricted to loopback clients\"]}");
			return response;
		}

		if ("true".equals(request.stringFormValueForKey("reset"))) {
			NGEvalSession.shared().reset();
		}

		final NGEvalSession.EvalResult result = NGEvalSession.shared().eval(snippetFrom(request));

		final StringBuilder b = new StringBuilder(256);
		b.append("{\"status\":\"").append(result.ok() ? "ok" : "error").append('"');
		b.append(",\"value\":").append(NGDevJson.str(result.value()));
		if (result.exception() != null) {
			b.append(",\"exception\":").append(NGDevJson.str(result.exception()));
		}
		b.append(",\"diagnostics\":[");
		for (int i = 0; i < result.diagnostics().size(); i++) {
			if (i > 0) {
				b.append(',');
			}
			b.append(NGDevJson.str(result.diagnostics().get(i)));
		}
		b.append("]}");

		response.setContent(b.toString());
		response.setStatus(200);
		return response;
	}

	/**
	 * @return the snippet to evaluate: the {@code snippet} form value, else the raw request body
	 *         (a {@code text/plain} or form-encoded {@code --data} POST — WO keeps the body around
	 *         after form parsing, so {@code contentString()} works regardless of content type).
	 */
	private static String snippetFrom(final WORequest request) {
		final String param = request.stringFormValueForKey("snippet");
		if (param != null && !param.isBlank()) {
			return param;
		}
		return request.contentString();
	}
}
