package er.extensions.routes;

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
 * seams that call them are {@code ERXRoutingApplication.createRequest()} for inbound
 * ({@link #canonicalize}, which also puts every route under the {@link #ROUTE_KEY}
 * request handler) and {@code ERXRoutingContext._urlWithRequestHandlerKey()} /
 * {@code ERXRoutingApplication._newLocationForRequest()} for outbound.
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
	 * The request handler key every route travels under once a URL has been canonicalized. Hardcoded on purpose; make it configurable when a need shows up.
	 */
	public static final String ROUTE_KEY = "route";

	/**
	 * Turns any inbound URL into the canonical WebObjects URL for it, so that WO parses a well-formed URL every time
	 * and nothing downstream has to know which shape the front end delivered:
	 *
	 * <ul>
	 * <li>{@code /wa/x}, {@code /App.woa/wa/x}, {@code /App.woa/1/wa/x} - the first path segment names a registered request handler: {@code <prefix>[/N]/wa/x}</li>
	 * <li>{@code /a/b}, {@code /App.woa/a/b}, {@code /App.woa/1/a/b}, {@code /App.woa/route/a/b} - anything else is a route: {@code <prefix>[/N]/route/a/b}</li>
	 * </ul>
	 *
	 * The prefix is the one the request carried ({@code <adaptor path>/App[.woa]} plus the instance number the adaptor
	 * added) - whatever adaptor path the front end uses, located by the adaptor name as WO does - or the application's
	 * own when the request carried none. A route named like a handler key wins over the
	 * handler, since the application chose it explicitly. URLs inside the adaptor path that name another application
	 * pass through untouched. The query string is never touched.
	 *
	 * This runs before the {@code WORequest} is constructed: {@code WODynamicURL} finds the adaptor prefix by locating
	 * the {@code WebObjects} token, and given {@code /wa/AppAction/search} unchanged it takes {@code wa} for the
	 * application name.
	 *
	 * @param url The request URL as received (path, optionally with a query)
	 * @param adaptorPath The application's own adaptor path ({@code /cgi-bin/WebObjects}); its last segment is the adaptor name
	 * @param applicationName The application's name
	 * @param applicationExtension The application's extension ({@code .woa}), optional in WO URLs
	 * @param handlerKeys The application's registered request handler keys
	 * @param routeClaims Whether an explicit route claims the given path
	 */
	public static String canonicalize( final String url, final String adaptorPath, final String applicationName, final String applicationExtension, final Collection<String> handlerKeys, final Predicate<String> routeClaims ) {

		if( url == null || !url.startsWith( "/" ) ) {
			return url;
		}

		final String path = pathOf( url );
		final String query = url.substring( path.length() );

		String carriedPrefix = null;
		String rest = path;

		// The adaptor prefix a request carries need not be the application's own adaptorPath(): a front end may map the
		// adaptor under /Apps/WebObjects while adaptorPath() still says /cgi-bin/WebObjects. WO locates the prefix by
		// the adaptor name ("WebObjects"), so we do the same.
		final String adaptorName = adaptorPath.substring( adaptorPath.lastIndexOf( '/' ) + 1 );
		final int token = path.indexOf( "/" + adaptorName + "/" );

		if( token != -1 ) {
			final String carriedAdaptorPath = path.substring( 0, token + adaptorName.length() + 1 );
			final String afterAdaptor = path.substring( carriedAdaptorPath.length() );
			final String appSegment = firstSegment( afterAdaptor );

			if( !appSegment.equals( applicationName ) && !appSegment.equals( applicationName + applicationExtension ) ) {
				return url; // another application's URL behind the same front end
			}

			carriedPrefix = carriedAdaptorPath + "/" + appSegment;
			rest = orRoot( afterAdaptor.substring( appSegment.length() + 1 ) );

			final String maybeInstance = firstSegment( rest );

			if( maybeInstance.matches( "-?\\d+" ) ) {
				carriedPrefix = carriedPrefix + "/" + maybeInstance;
				rest = orRoot( rest.substring( maybeInstance.length() + 1 ) );
			}
		}

		// A front end that already marked the request as a route handed us the canonical form
		if( ROUTE_KEY.equals( firstSegment( rest ) ) ) {
			rest = orRoot( rest.substring( ROUTE_KEY.length() + 1 ) );
		}

		final String segment = firstSegment( rest );
		final boolean handlerURL = !segment.isEmpty() && handlerKeys.contains( segment ) && !routeClaims.test( rest );
		final String prefix = carriedPrefix != null ? carriedPrefix : applicationPrefix( adaptorPath, applicationName, applicationExtension );

		return prefix + ( handlerURL ? rest : "/" + ROUTE_KEY + rest ) + query;
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
	 * @return The path, or the root when nothing is left of it
	 */
	private static String orRoot( final String path ) {
		return path.isEmpty() ? "/" : path;
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
