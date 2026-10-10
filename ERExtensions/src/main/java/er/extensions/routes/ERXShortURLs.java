package er.extensions.routes;

import java.util.Collection;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Short URLs: a request handler key as a top-level route. On by default
 * ({@code er.extensions.ERXApplication.shortURLs}); the application accepts
 * {@code /wa/…}, {@code /res/…}, {@code /wo/…} — the first path segment naming
 * one of its registered request handlers — as if the adaptor prefix
 * ({@code /cgi-bin/WebObjects/App.woa}) were present, and generates its URLs
 * without that prefix. The long form keeps working: expansion only happens
 * when the prefix is absent.
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
 * ({@code /about}, {@code /i/…}) are never mistaken for handler URLs. A
 * handler URL always goes to its handler: mapping a route whose first segment
 * is a handler key is refused (see {@code ERXRouter}), and a wildcard
 * route such as a catch-all {@code /*} doesn't take handler URLs. Expansion is skipped for anything already inside the
 * adaptor path so long-form URLs — and other applications' URLs behind the
 * same front end — pass through untouched. The prefix is an exact string,
 * never a pattern, which is what makes the shortening safe to apply to
 * every generated URL.
 */
public final class ERXShortURLs {

	private ERXShortURLs() {}

	/**
	 * The request handler key every route travels under once a URL has been canonicalized. Internal (it never appears in
	 * a generated URL) and named so no application wants it as the first element of a route of its own (#197).
	 */
	public static final String ROUTE_KEY = "_erxroute_";

	/**
	 * The key before {@link #ROUTE_KEY}, which a front end configured for an earlier version still forwards under
	 */
	private static final String FORMER_ROUTE_KEY = "route";

	private static final Logger logger = LoggerFactory.getLogger( ERXShortURLs.class );

	private static volatile boolean _warnedOfFormerKey;

	/**
	 * Turns any inbound URL into the canonical WebObjects URL for it, so that WO parses a well-formed URL every time
	 * and nothing downstream has to know which shape the front end delivered:
	 *
	 * <ul>
	 * <li>{@code /wa/x}, {@code /App.woa/wa/x}, {@code /App.woa/1/wa/x} - the first path segment names a registered request handler: {@code <prefix>[/N]/wa/x}</li>
	 * <li>{@code /a/b}, {@code /App.woa/a/b}, {@code /App.woa/1/a/b}, {@code /App.woa/_erxroute_/a/b} - anything else is a route: {@code <prefix>[/N]/_erxroute_/a/b}</li>
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
	 */
	public static String canonicalize( final String url, final String adaptorPath, final String applicationName, final String applicationExtension, final Collection<String> handlerKeys ) {
		return canonicalize( url, null, adaptorPath, applicationName, applicationExtension, handlerKeys );
	}

	/**
	 * As {@link #canonicalize(String, String, String, String, Collection)}, the application's base path ({@code /App},
	 * #51) removed first when the URL starts with it, so {@code /App/wo/123}, {@code /App/about} and {@code /App/} are
	 * the application's URLs behind a front end that passes paths on as they arrive
	 *
	 * @param basePath The public path the application is served beneath, null or empty for the root
	 */
	public static String canonicalize( final String url, final String basePath, final String adaptorPath, final String applicationName, final String applicationExtension, final Collection<String> handlerKeys ) {

		if( url == null || !url.startsWith( "/" ) ) {
			return url;
		}

		final String path = withoutBasePath( pathOf( url ), basePath );
		final String query = url.substring( pathOf( url ).length() );
		return canonicalizePath( path, query, adaptorPath, applicationName, applicationExtension, handlerKeys );
	}

	private static String canonicalizePath( final String path, final String query, final String adaptorPath, final String applicationName, final String applicationExtension, final Collection<String> handlerKeys ) {
		final String url = path + query;

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

			if( INSTANCE_NUMBER.matcher( maybeInstance ).matches() ) {
				carriedPrefix = carriedPrefix + "/" + maybeInstance;
				rest = orRoot( rest.substring( maybeInstance.length() + 1 ) );
			}
		}

		// A front end forwarding under the former key, inside the adaptor path, is configured for an earlier version: the
		// request is still routed, as /route/…, which is now an ordinary path. Said once, so the configuration is fixed.
		if( carriedPrefix != null && !_warnedOfFormerKey && FORMER_ROUTE_KEY.equals( firstSegment( rest ) ) ) {
			_warnedOfFormerKey = true;
			logger.warn( "A request arrived as {}: the front end forwards routes under '{}', the route key before 8.1.2. It's '{}' now, so the request is routed as {}. Change the front end's configuration (the Apache rewrite target, say) to forward under {}/{}/.", path, FORMER_ROUTE_KEY, ROUTE_KEY, rest, carriedPrefix, ROUTE_KEY );
		}

		// A front end that already marked the request as a route handed us the canonical form
		if( ROUTE_KEY.equals( firstSegment( rest ) ) ) {
			rest = orRoot( rest.substring( ROUTE_KEY.length() + 1 ) );
		}

		final String segment = firstSegment( rest );
		final boolean handlerURL = !segment.isEmpty() && handlerKeys.contains( segment );
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
		return shorten( url, applicationPrefix, null );
	}

	/**
	 * @param basePath The public path the application is served beneath ({@code /App}, #51), prepended to a URL that was
	 *        shortened (the application's own), null or empty for the root
	 * @return The URL with the application prefix removed, and the base path in its place
	 */
	public static String shorten( final String url, final String applicationPrefix, final String basePath ) {
		final String shortened = shortenWithoutBasePath( url, applicationPrefix );
		return shortened == url || basePath == null || basePath.isEmpty() ? shortened : withBasePath( shortened, basePath );
	}

	/**
	 * An instance number, as a path element ({@code 2}, {@code -1})
	 */
	private static final Pattern INSTANCE_NUMBER = Pattern.compile( "-?\\d+" );

	/**
	 * A complete URL with no path (an origin, perhaps a query)
	 */
	private static final Pattern ORIGIN_ONLY = Pattern.compile( "^[a-zA-Z][a-zA-Z0-9+.-]*://[^/?]*(\\?.*)?$" );

	/**
	 * @return The URL without the first occurrence of the prefix that ends a path element (followed by {@code /}, {@code ?}
	 *         or the end), together with an instance number following it ({@code /2}); the URL itself (the same object)
	 *         if the prefix doesn't occur so. Runs for every URL the application generates, so it's a plain scan, not a
	 *         regular expression.
	 */
	static String withoutPrefix( final String url, final String prefix ) {
		final int length = url.length();
		int at = url.indexOf( prefix );

		while( at != -1 ) {
			final int afterPrefix = at + prefix.length();
			int end = -1;

			// An instance number: /, an optional -, digits, then the end of the element
			if( afterPrefix < length && url.charAt( afterPrefix ) == '/' ) {
				int digits = afterPrefix + 1;

				if( digits < length && url.charAt( digits ) == '-' ) {
					digits++;
				}

				int i = digits;

				while( i < length && url.charAt( i ) >= '0' && url.charAt( i ) <= '9' ) {
					i++;
				}

				if( i > digits && endsElement( url, i ) ) {
					end = i;
				}
			}

			if( end == -1 && endsElement( url, afterPrefix ) ) {
				end = afterPrefix;
			}

			if( end != -1 ) {
				return url.substring( 0, at ) + url.substring( end );
			}

			at = url.indexOf( prefix, at + 1 );
		}

		return url;
	}

	/**
	 * @return true if a path element ends at the position: the end of the URL, a slash or a query
	 */
	private static boolean endsElement( final String url, final int position ) {
		return position == url.length() || url.charAt( position ) == '/' || url.charAt( position ) == '?';
	}

	private static String shortenWithoutBasePath( final String url, final String applicationPrefix ) {

		if( url == null || applicationPrefix == null || applicationPrefix.isEmpty() || !url.contains( applicationPrefix ) ) {
			return url;
		}

		final String shortened = withoutPrefix( url, applicationPrefix );

		// The prefix alone (or followed only by a query) shortens to the root
		if( shortened.isEmpty() || shortened.startsWith( "?" ) ) {
			return "/" + shortened;
		}

		// A complete URL whose path was exactly the prefix: keep the root slash
		if( shortened.contains( "://" ) && ORIGIN_ONLY.matcher( shortened ).matches() ) {
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
	 * @return The URL, relative or complete, with the base path before its path ({@code /about} gives {@code /App/about},
	 *         and {@code /} gives {@code /App/})
	 */
	static String withBasePath( final String url, final String basePath ) {
		final int schemeEnd = url.indexOf( "://" );
		final int pathStart = schemeEnd == -1 ? 0 : url.indexOf( '/', schemeEnd + 3 );

		if( pathStart == -1 ) {
			return url + basePath + "/";
		}

		return url.substring( 0, pathStart ) + basePath + url.substring( pathStart );
	}

	/**
	 * @return The path without the base path it starts with, the path as it is if it doesn't
	 */
	static String withoutBasePath( final String path, final String basePath ) {

		if( basePath == null || basePath.isEmpty() ) {
			return path;
		}

		if( path.equals( basePath ) ) {
			return "/";
		}

		return path.startsWith( basePath + "/" ) ? path.substring( basePath.length() ) : path;
	}

	/**
	 * @return A short URL to a route without the route key it was composed under ({@code /_erxroute_/search/bork} is
	 *         {@code /search/bork}, {@code /App/_erxroute_/search} is {@code /App/search}): the reverse of canonicalize()
	 */
	public static String withoutRouteKey( final String url, final String basePath ) {
		final int schemeEnd = url.indexOf( "://" );
		final int authorityEnd = schemeEnd == -1 ? 0 : url.indexOf( '/', schemeEnd + 3 );

		if( authorityEnd == -1 ) {
			return url;
		}

		final int pathStart = basePath != null && !basePath.isEmpty() && url.startsWith( basePath + "/", authorityEnd ) ? authorityEnd + basePath.length() : authorityEnd;
		final String routePrefix = "/" + ROUTE_KEY;

		if( !url.startsWith( routePrefix, pathStart ) ) {
			return url;
		}

		final int afterKey = pathStart + routePrefix.length();

		if( afterKey == url.length() || url.charAt( afterKey ) == '?' ) {
			return url.substring( 0, pathStart ) + "/" + url.substring( afterKey );
		}

		return url.charAt( afterKey ) == '/' ? url.substring( 0, pathStart ) + url.substring( afterKey ) : url;
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
