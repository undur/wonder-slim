package er.extensions.appserver;

import java.util.List;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;

import ng.dev.NGDevJson;
import ng.dev.NGRuntimeProblems;

/**
 * A development-only request handler that returns the runtime problems the application rendered
 * into its pages — the inline binding-error boxes Parsley draws when a binding fails — as JSON,
 * so an external tool notices them without scraping rendered HTML. The WebObjects-side
 * counterpart of ng-objects' /ng/dev/problems, over the same shared store
 * ({@link NGRuntimeProblems} in ng-core) and the same response shape.
 *
 * <p>Registered under the handler key {@value #KEY} (only in development mode):
 *
 * <pre>
 *   http://localhost:PORT/cgi-bin/WebObjects/App.woa/problems
 *   …/problems?contains=WORepetition     only problems mentioning "WORepetition"
 *   …/problems?tail=20                   only the last 20
 *   …/problems?clear=true                empty the buffer after snapshotting (mark a baseline)
 * </pre>
 */
public class ERXRuntimeProblemsRequestHandler extends WORequestHandler {

	/** The request-handler key — the path segment after the .woa in the URL. */
	public static final String KEY = "problems";

	@Override
	public WOResponse handleRequest(final WORequest request) {
		final WOResponse response = new WOResponse();
		response.setHeader("application/json; charset=utf-8", "Content-Type");

		if (!ERXApplication.isDevelopmentModeSafe()) {
			response.setStatus(404);
			response.setContent("{\"problems\":[],\"count\":0}");
			return response;
		}

		final String contains = request.stringFormValueForKey("contains");
		final int tail = parseInt(request.stringFormValueForKey("tail"), 0);

		final List<NGRuntimeProblems.Problem> problems = NGRuntimeProblems.snapshot(contains, tail);

		if ("true".equals(request.stringFormValueForKey("clear"))) {
			NGRuntimeProblems.clear();
		}

		final StringBuilder b = new StringBuilder(problems.size() * 96 + 32);
		b.append("{\"problems\":[");
		for (int i = 0; i < problems.size(); i++) {
			final NGRuntimeProblems.Problem problem = problems.get(i);
			if (i > 0) {
				b.append(',');
			}
			b.append("{\"time\":").append(problem.epochMillis())
					.append(",\"kind\":").append(NGDevJson.str(problem.kind()))
					.append(",\"element\":").append(NGDevJson.str(problem.element()))
					.append(",\"message\":").append(NGDevJson.str(problem.message()))
					.append('}');
		}
		b.append("],\"count\":").append(problems.size()).append('}');

		response.setContent(b.toString());
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
