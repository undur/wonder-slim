package er.extensions.appserver;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Short URLs: a request handler key as a top-level route. On by default
 * ({@code er.extensions.ERXApplication.shortURLs}); the application accepts
 * {@code /wa/…}, {@code /res/…}, {@code /wo/…} — the first path segment naming
 * one of its registered request handlers — as if the adaptor prefix
 * ({@code /cgi-bin/WebObjects/App.woa}) were present, and generates its URLs
 * without that prefix. The long form keeps working: expansion only happens
 * when the prefix is absent, and only when no explicit route claims the path.
 *
 * Both directions are pure functions of the application's URL prefix (an
 * exact string the app computes — no patterns) and its handler keys; the
 * seams that call them are {@code ERXApplication.createRequest()} for inbound
 * and {@code ERXWOContext._urlWithRequestHandlerKey()} /
 * {@code ERXApplication._newLocationForRequest()} for outbound.
 *
 * Inbound expansion must happen before the {@code WORequest} is constructed:
 * {@code WODynamicURL} finds the adaptor prefix by locating the
 * {@code WebObjects} token, and given {@code /wa/AppAction/search} unchanged
 * it takes {@code wa} for the application name.
 *
 * Note that a short URL carries no {@code .woa/N} instance number: URL-based
 * instance pinning doesn't apply to it (a proxy's cookie affinity does).
 *
 * Design notes. Only the first path segment is consulted, and only against
 * the handler keys actually registered, so an application's own routes
 * ({@code /about}, {@code /i/…}) are never mistaken for handler URLs; where
 * a route and a handler key do collide, the route wins because the app
 * chose it explicitly. Expansion is skipped for anything already inside the
 * adaptor path so long-form URLs — and other applications' URLs behind the
 * same front end — pass through untouched. The prefix is an exact string,
 * never a pattern, which is what makes the shortening safe to apply to
 * every generated URL.
 */
public final class ERXShortURLs {

	private ERXShortURLs() {}

	/**
	 * @param url The request URL as received (path, optionally with a query)
	 * @param applicationPrefix {@code adaptorPath() + name() + applicationExtension()}
	 * @param adaptorPath The adaptor path alone — anything already inside it is left as is
	 * @param handlerKeys The application's registered request handler keys
	 * @param routeClaims Whether an explicit route claims the given path (routes win)
	 * @return The URL with the application prefix prepended when its first
	 *         segment is a handler key, otherwise unchanged
	 */
	public static String expand( final String url, final String applicationPrefix, final String adaptorPath, final Collection<String> handlerKeys, final Predicate<String> routeClaims ) {

		if( url == null || !url.startsWith( "/" ) ) {
			return url;
		}

		final String path = pathOf( url );

		if( path.startsWith( adaptorPath ) || path.startsWith( applicationPrefix ) ) {
			return url;
		}

		final String segment = firstSegment( path );

		if( segment.isEmpty() || !handlerKeys.contains( segment ) || routeClaims.test( path ) ) {
			return url;
		}

		return applicationPrefix + url;
	}

	/**
	 * @param url A generated URL, relative or complete
	 * @param applicationPrefix {@code adaptorPath() + name() + applicationExtension()}
	 * @return The URL with the application prefix — and the instance number
	 *         that may follow it — removed, otherwise unchanged
	 */
	public static String shorten( final String url, final String applicationPrefix ) {

		if( url == null || applicationPrefix == null || applicationPrefix.isEmpty() || !url.contains( applicationPrefix ) ) {
			return url;
		}

		final String shortened = Pattern.compile( Pattern.quote( applicationPrefix ) + "(?:/-?\\d+)?(?=/|\\?|$)" ).matcher( url ).replaceFirst( "" );

		// The prefix alone (or followed only by a query) shortens to the root
		if( shortened.isEmpty() || shortened.startsWith( "?" ) ) {
			return "/" + shortened;
		}

		// A complete URL whose path was exactly the prefix: keep the root slash
		if( shortened.matches( "^[a-zA-Z][a-zA-Z0-9+.-]*://[^/?]*(\\?.*)?$" ) ) {
			final int q = shortened.indexOf( '?' );
			return q == -1 ? shortened + "/" : shortened.substring( 0, q ) + "/" + shortened.substring( q );
		}

		return shortened;
	}

	/**
	 * The application prefix a URL is built from, as WODynamicURL composes it:
	 * {@code <adaptor prefix>/<App><extension>}. Generated URLs inherit the
	 * adaptor prefix of the request they answer, which need not be the
	 * application's own adaptorPath(): a front end that maps {@code /} to
	 * {@code /Apps/WebObjects/App.woa/wa/default} makes the app answer with
	 * {@code /Apps/WebObjects/App.woa/…} URLs while adaptorPath() still says
	 * {@code /cgi-bin/WebObjects}. Shortening therefore removes the prefix the
	 * URL actually carries, taken from the same parsed request URL WO built it
	 * from — never a guess from configuration.
	 *
	 * @param adaptorPrefix The request's adaptor prefix ({@code /cgi-bin/WebObjects}, {@code /Apps/WebObjects}, …), null or empty for none
	 * @param applicationName The application name as parsed, without extension
	 * @param applicationExtension {@code .woa}, or empty for a URL composed without it
	 */
	public static String applicationPrefix( final String adaptorPrefix, final String applicationName, final String applicationExtension ) {
		return ( adaptorPrefix == null ? "" : adaptorPrefix ) + "/" + applicationName + ( applicationExtension == null ? "" : applicationExtension );
	}

	/**
	 * @return The path part of the URL (before any query string)
	 */
	static String pathOf( final String url ) {
		final int q = url.indexOf( '?' );
		return q == -1 ? url : url.substring( 0, q );
	}

	/**
	 * @return The first path segment of an absolute path, empty for the root
	 */
	static String firstSegment( final String path ) {
		final int end = path.indexOf( '/', 1 );
		return end == -1 ? path.substring( 1 ) : path.substring( 1, end );
	}
}
