package er.routing.core;

import java.util.Locale;
import java.util.Objects;

/**
 * What the router knows of a request: plain strings, no framework types.
 *
 * @param method The HTTP method, upper case ({@code GET})
 * @param host The host the request was made to, without the port, lower case. Null if unknown.
 * @param path The path, without the query string, still percent-encoded
 */

public record RouteRequest( String method, String host, String path ) {

	public RouteRequest {
		method = Objects.requireNonNull( method ).toUpperCase( Locale.ROOT );
		host = host == null ? null : normalizedHost( host );
		path = Objects.requireNonNull( path );
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
