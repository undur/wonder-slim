package er.routing.options;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Which other sites' scripts may call a route from a browser (CORS): their requests
 * are answered with {@code Access-Control-Allow-Origin}, and the browser's preflight ({@code OPTIONS} with
 * {@code Access-Control-Request-Method}) with what the route takes, so a page elsewhere can read the answer. A route or a
 * group says so:
 *
 * <pre>
 * final RouteGroup api = club.group( "/api", CrossOrigin.allow( "https://partner.example.com" ) );
 * </pre>
 *
 * A named origin allowed here may also post to the route, whatever its {@link CrossSite} level: allowing a partner's
 * script to call a route is allowing it to change things through it. {@link #ANY} allows every origin's scripts to read,
 * without credentials, and doesn't waive {@link CrossSite}: a form posted from any site carries the user's cookies by
 * the browser's rules. {@link #withCredentials()} lets a named origin's requests carry the user's cookies.
 */

public final class CrossOrigin implements RouteBehavior {

	/**
	 * Any origin, without credentials: a public API
	 */
	public static final CrossOrigin ANY = new CrossOrigin( Set.of(), true, false );

	/**
	 * How long a browser keeps a preflight's answer, in seconds
	 */
	private static final int PREFLIGHT_MAX_AGE = 600;

	/**
	 * The origins allowed, lower case ({@code https://partner.example.com})
	 */
	private final Set<String> _origins;
	private final boolean _any;
	private final boolean _credentials;

	private CrossOrigin( final Set<String> origins, final boolean any, final boolean credentials ) {
		_origins = origins;
		_any = any;
		_credentials = credentials;
	}

	/**
	 * @param origins The origins allowed: a scheme, a host and a port if it isn't the scheme's ({@code https://partner.example.com})
	 */
	public static CrossOrigin allow( final String... origins ) {
		if( origins.length == 0 ) {
			throw new IllegalArgumentException( "CrossOrigin.allow names at least one origin, or is CrossOrigin.ANY" );
		}

		return new CrossOrigin( Arrays.stream( origins ).map( CrossOrigin::origin ).collect( Collectors.toUnmodifiableSet() ), false, false );
	}

	/**
	 * @return The same origins allowed, their requests carrying the user's cookies ({@code Access-Control-Allow-Credentials})
	 * @throws IllegalStateException for {@link #ANY}: a browser refuses credentials for any origin, and answering every
	 *         origin as itself would let any site act as the user
	 */
	public CrossOrigin withCredentials() {
		if( _any ) {
			throw new IllegalStateException( "CrossOrigin.ANY can't have credentials: allow the origins that may send them, CrossOrigin.allow( … ).withCredentials()" );
		}

		return new CrossOrigin( _origins, false, true );
	}

	/**
	 * @return true if the request's {@code Origin} is allowed
	 */
	public boolean allows( final RouteRequest request ) {
		final String origin = request.header( "origin" );
		return origin != null && !origin.equals( "null" ) && (_any || _origins.contains( origin.toLowerCase( Locale.ROOT ) ));
	}

	/**
	 * @return true if every answer of the route varies by Origin: for named origins, whose answer names the origin, so a
	 *         cache keeps one origin's answer from another
	 */
	public boolean variesByOrigin() {
		return !_any;
	}

	/**
	 * @return true if an origin this allows may also post to the route, whatever its {@link CrossSite} level: a named
	 *         origin, trusted by name. Not {@link #ANY}: a form posted from any site carries the user's cookies by the
	 *         browser's rules, whatever CORS says, so allowing any site's scripts to read isn't allowing every site to
	 *         post.
	 */
	public boolean waivesCrossSite() {
		return !_any;
	}

	/**
	 * @return true if the request is a browser's preflight for a cross-origin call
	 */
	public static boolean isPreflight( final RouteRequest request ) {
		return request.method().equals( "OPTIONS" ) && request.header( "origin" ) != null && request.header( "access-control-request-method" ) != null;
	}

	/**
	 * @return The headers allowing the request's origin to read the answer, by name (the framework adds them, and
	 *         {@code Vary: Origin} when {@link #variesByOrigin()})
	 */
	public Map<String, String> allowHeaders( final RouteRequest request ) {
		final Map<String, String> headers = new LinkedHashMap<>();
		headers.put( "access-control-allow-origin", _any ? "*" : request.header( "origin" ) );

		if( _credentials ) {
			headers.put( "access-control-allow-credentials", "true" );
		}

		return headers;
	}

	/**
	 * @return The headers answering a preflight ({@code 204}): the origin's, the methods the route takes, and the headers
	 *         the request asks to send
	 */
	public Map<String, String> preflightHeaders( final RouteRequest request, final Set<String> methods ) {
		final Map<String, String> headers = allowHeaders( request );
		headers.put( "access-control-allow-methods", String.join( ", ", methods ) );

		final String requestedHeaders = request.header( "access-control-request-headers" );

		if( requestedHeaders != null ) {
			headers.put( "access-control-allow-headers", requestedHeaders );
		}

		headers.put( "access-control-max-age", String.valueOf( PREFLIGHT_MAX_AGE ) );
		return headers;
	}

	private static String origin( final String origin ) {
		try {
			final URI uri = new URI( Objects.requireNonNull( origin ).trim() );

			if( uri.getScheme() == null || uri.getHost() == null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) ) {
				throw new IllegalArgumentException( "An origin is a scheme, a host and a port if it isn't the scheme's ('https://partner.example.com'), and '%s' isn't one".formatted( origin ) );
			}

			return (uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort())).toLowerCase( Locale.ROOT );
		}
		catch( URISyntaxException e ) {
			throw new IllegalArgumentException( "'%s' isn't an origin".formatted( origin ), e );
		}
	}

	@Override
	public String toString() {
		return "CrossOrigin " + (_any ? "*" : _origins) + (_credentials ? " with credentials" : "");
	}
}
