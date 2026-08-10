package er.extensions.dev;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.webobjects.appserver.WOAdaptor;
import com.webobjects.appserver.WOApplication;

import er.extensions.foundation.ERXProperties;

/**
 * Announces this application's port to the Parsley/WOLips Eclipse dev server at
 * startup (development only), so external tools and AI agents can discover where the
 * app is running by name — by querying the dev server's {@code /apps} endpoint —
 * instead of being told the port or guessing a per-developer convention.
 *
 * <p>This is the app→dev-server "here I am" direction, complementing the
 * dev-server→app hooks the app already uses (e.g. {@code /openComponent} links on the
 * exception page). It reuses the same dev-server location the rest of the app uses:
 * {@code localhost} on the {@code wolips.port} property (default 9485).
 *
 * <h2>Best-effort by design</h2>
 * The dev server may not be running (no Eclipse, headless CI, production). Registration
 * must never affect the app: it runs on a short-timeout background HTTP call and
 * swallows every failure. It's also development-gated by the caller — there's no
 * reason to announce a production app to a developer's IDE.
 *
 * <h2>When</h2>
 * Called once from {@code didFinishLaunching}, not the constructor — the listening
 * port isn't reliably bound until the app has finished launching.
 */
public final class ERXDevServerRegistration {

	private ERXDevServerRegistration() {
	}

	/**
	 * Fires a one-shot, best-effort registration of this app's name and port to the
	 * dev server. Returns immediately; the HTTP call runs on a background thread so a
	 * slow or absent dev server never delays startup.
	 */
	public static void registerAtStartup() {
		final WOApplication app = WOApplication.application();
		if (app == null) {
			return;
		}

		final int port = boundPort(app);
		if (port <= 0) {
			// No bound port to announce (e.g. an unusual launch); nothing useful to register.
			return;
		}

		final String appName = app.name();
		final int devServerPort = ERXProperties.intForKeyWithDefault("wolips.port", 9485);
		final String pid = String.valueOf(ProcessHandle.current().pid());

		final String url = "http://localhost:" + devServerPort
				+ "/registerApp?name=" + urlEncode(appName)
				+ "&port=" + port
				+ "&pid=" + urlEncode(pid);

		// Background + short timeouts: the dev server is local, so it's either there and
		// instant, or absent and we don't want to wait on it.
		final Thread t = new Thread(() -> ping(url, appName, port), "ERXDevServerRegistration");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * The port the app is actually listening on.
	 *
	 * <p>{@link WOApplication#port()} only reflects an explicitly passed {@code -WOPort}
	 * and is {@code -1} when the port was auto-assigned (no {@code -WOPort}, or
	 * {@code -WOPort 0} → the OS picks a free port). The real bound port lives on the
	 * adaptor — which is how WO's own direct-connect URL sources it. We read the adaptor
	 * first so auto-port apps register correctly, falling back to {@code port()} only if
	 * there's no adaptor yet.
	 */
	private static int boundPort(WOApplication app) {
		final WOAdaptor adaptor = app.defaultAdaptor();
		if (adaptor != null && adaptor.port() > 0) {
			return adaptor.port();
		}
		final Number configured = app.port();
		return configured == null ? -1 : configured.intValue();
	}

	private static void ping(String url, String appName, int port) {
		try {
			final HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofMillis(500))
					.build();
			final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(2))
					.GET()
					.build();
			final HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() == 200) {
				System.out.println("Registered '" + appName + "' (port " + port + ") with the Eclipse dev server.");
			}
		}
		catch (Exception e) {
			// Expected and harmless when no dev server is listening (no Eclipse, CI, prod).
			// Deliberately quiet — this is a convenience, not a requirement.
		}
	}

	private static String urlEncode(String s) {
		return java.net.URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
	}
}
