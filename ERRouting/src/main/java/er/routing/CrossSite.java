package er.routing;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

/**
 * EXPERIMENTAL (route-links branch). Whether a route takes requests that change things (POST, PUT, PATCH, DELETE) from
 * a page on another site. By default it doesn't: such a request is answered with {@code 403}, so a page elsewhere can't
 * post a form to the application with the user's cookies. A route meant for that (a webhook a browser posts to, a form
 * another site embeds) says so with {@link #ALLOWED}, as a group can for its routes.
 *
 * The browser says where a request comes from: {@code Sec-Fetch-Site}, or failing that {@code Origin}, compared with the
 * request's host (and the {@link PublicAddress public address}). A request with neither is allowed, since it doesn't
 * come from a browser page (curl, a server's webhook). Another subdomain counts as another site: a club's page on
 * {@code kronan.example.com} doesn't post to {@code acme.example.com}. Schemes aren't compared, since behind a front end
 * terminating TLS the application sees http.
 *
 * Covers routes only: component actions and the route table's other routes aren't checked.
 */

public enum CrossSite implements RouteBehavior {

	/**
	 * The route takes requests from pages on other sites
	 */
	ALLOWED;

	private static final Set<String> SAFE_METHODS = Set.of( "GET", "HEAD", "OPTIONS", "TRACE" );

	/**
	 * @return true if the request changes things (its method isn't GET, HEAD, OPTIONS or TRACE) and comes from a page on
	 *         another site
	 */
	static boolean refused( final WORequest request, final String requestHost, final PublicAddress.Origin publicAddress ) {

		if( SAFE_METHODS.contains( request.method().toUpperCase( Locale.ROOT ) ) ) {
			return false;
		}

		final String fetchSite = request.headerForKey( "sec-fetch-site" );

		if( fetchSite != null ) {
			// none: the user's own doing (typed, bookmarked)
			return !fetchSite.equals( "same-origin" ) && !fetchSite.equals( "none" );
		}

		final String origin = request.headerForKey( "origin" );

		if( origin == null ) {
			return false;
		}

		final String authority = authority( origin );

		if( authority == null ) {
			return true;
		}

		return !authority.equals( lower( requestHost ) ) && (publicAddress == null || !authority.equals( publicAddress.host() + publicAddress.portSuffix() ));
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
