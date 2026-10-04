package er.routing.options;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * What the router knows of a request: plain strings and a header lookup, no framework types, so a framework adapts its
 * own request to it.
 *
 * @param method The HTTP method, upper case ({@code GET})
 * @param host The host the request was made to, without the port, lower case. Null if unknown.
 * @param path The path, without the query string, still percent-encoded
 * @param secure True if the request came over https (as the framework determines it, a front end's word included)
 * @param headers A header's value by its name in lower case, null for one the request doesn't have
 */

public record RouteRequest( String method, String host, String path, boolean secure, Function<String, String> headers ) {

	private static final Function<String, String> NO_HEADERS = name -> null;

	public RouteRequest {
		method = Objects.requireNonNull( method ).toUpperCase( Locale.ROOT );
		host = host == null ? null : normalizedHost( host );
		path = Objects.requireNonNull( path );
		headers = headers == null ? NO_HEADERS : headers;
	}

	/**
	 * A request over http without headers: for matching a host or a path alone
	 */
	public RouteRequest( final String method, final String host, final String path ) {
		this( method, host, path, false, null );
	}

	/**
	 * @return The value of the header (its name in any case), null if the request doesn't have it
	 */
	public String header( final String name ) {
		return headers.apply( name.toLowerCase( Locale.ROOT ) );
	}

	/**
	 * @return The host without its port and a trailing dot, lower case ({@code Example.com.:8080} gives {@code example.com})
	 */
	static String normalizedHost( String host ) {
		host = host.trim();

		if( host.startsWith( "[" ) ) {
			final int end = host.indexOf( ']' );
			host = end == -1 ? host : host.substring( 0, end + 1 );
		}
		else {
			final int colon = host.indexOf( ':' );
			host = colon == -1 ? host : host.substring( 0, colon );
		}

		if( host.endsWith( "." ) ) {
			host = host.substring( 0, host.length() - 1 );
		}

		return host.toLowerCase( Locale.ROOT );
	}
}
