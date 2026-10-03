package er.routing;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

/**
 * EXPERIMENTAL (route-links branch). Which sites a route takes requests that change things (POST, PUT, PATCH, DELETE)
 * from. By default only its own origin ({@link #SAME_ORIGIN}): such a request from a page elsewhere is answered with
 * {@code 403}, so another site can't post a form to the application with the user's cookies. {@link #OWN_HOSTS} also
 * takes the application's other hosts (a form on the landing page posting to a club's host), and {@link #ALLOWED} any
 * site (a form another site embeds). A group's level reaches its routes, and a route can set its own.
 *
 * The browser says where a request comes from: {@code Sec-Fetch-Site}, and {@code Origin}, compared with the request's
 * host (and the {@link PublicAddress public address}). A request with neither is taken, since it doesn't come from a
 * browser page (curl, a server's webhook). Schemes aren't compared, since behind a front end terminating TLS the
 * application sees http. Whether a browser's script on another site may read the answer is CORS's business, not this.
 *
 * Covers routes only: component actions and the route table's other routes aren't checked.
 */

public enum CrossSite implements RouteBehavior {

	/**
	 * Requests from the route's own origin only (the default)
	 */
	SAME_ORIGIN,

	/**
	 * Requests from any of the application's hosts too: a host one of its routes answers, or the public address's
	 */
	OWN_HOSTS,

	/**
	 * Requests from any site
	 */
	ALLOWED;

	private static final Set<String> SAFE_METHODS = Set.of( "GET", "HEAD", "OPTIONS", "TRACE" );

	/**
	 * @param ownHost Whether a host (without its port) is one of the application's, for {@link #OWN_HOSTS}
	 * @return true if the request changes things (its method isn't GET, HEAD, OPTIONS or TRACE) and comes from a site
	 *         this level doesn't take
	 */
	boolean refuses( final WORequest request, final String requestHost, final PublicAddress.Origin publicAddress, final Predicate<String> ownHost ) {

		if( this == ALLOWED || SAFE_METHODS.contains( request.method().toUpperCase( Locale.ROOT ) ) ) {
			return false;
		}

		final String fetchSite = request.headerForKey( "sec-fetch-site" ) == null ? null : request.headerForKey( "sec-fetch-site" ).toLowerCase( Locale.ROOT );

		// none: the user's own doing (typed, bookmarked)
		if( "same-origin".equals( fetchSite ) || "none".equals( fetchSite ) ) {
			return false;
		}

		// The browser's verdict accounts for the scheme, which the application can't see (an http page posting to the same
		// host over https is another origin), so it decides for the same origin. Other hosts are told by the Origin.
		if( fetchSite != null && this == SAME_ORIGIN ) {
			return true;
		}

		final String origin = request.headerForKey( "origin" );

		if( origin == null ) {
			return fetchSite != null;
		}

		final String authority = authority( origin );

		if( authority == null ) {
			return true;
		}

		if( authority.equals( lower( requestHost ) ) || (publicAddress != null && authority.equals( publicAddress.host() + publicAddress.portSuffix() )) ) {
			return false;
		}

		return !(this == OWN_HOSTS && ownHost.test( withoutPort( authority ) ));
	}

	private static String withoutPort( final String authority ) {
		final int colon = authority.lastIndexOf( ':' );
		return colon == -1 || authority.endsWith( "]" ) ? authority : authority.substring( 0, colon );
	}

	/**
	 * @return The host and port of an Origin header ({@code acme.example.com:1300}), lower case, null for one that isn't an
	 *         origin ({@code null}, from a sandboxed page or a file)
	 */
	private static String authority( final String origin ) {
		try {
			final URI uri = new URI( origin );
			return uri.getHost() == null ? null : uri.getHost().toLowerCase( Locale.ROOT ) + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
		}
		catch( URISyntaxException e ) {
			return null;
		}
	}

	private static String lower( final String host ) {
		return host == null ? null : host.toLowerCase( Locale.ROOT );
	}

	/**
	 * @return The answer to a refused request
	 */
	static WOResponse forbidden() {
		final WOResponse response = new WOResponse();
		response.setStatus( 403 );
		response.setHeader( "text/plain; charset=utf-8", "content-type" );
		response.setContent( "A request from another site, refused" );
		return response;
	}
}
