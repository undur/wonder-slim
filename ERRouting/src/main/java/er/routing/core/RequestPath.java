package er.routing.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A request's path, split into segments and decoded: what route patterns are matched against.
 *
 * Splitting happens before decoding, so an encoded slash ({@code %2F}) stays inside its segment. {@code +} is a literal
 * plus in a path, not a space.
 *
 * @param raw The path as requested, still encoded ({@code /a/b%20c/})
 * @param segments The decoded segments ({@code [a, b c]}). An empty segment ({@code //}) is kept as an empty string.
 * @param trailingSlash true if the path ends with a slash. The root path ({@code /}) always does.
 */

public record RequestPath( String raw, List<String> segments, boolean trailingSlash ) {

	public RequestPath {
		segments = List.copyOf( segments );
	}

	/**
	 * @return The path parsed, or null if it isn't a path (doesn't start with a slash, or holds invalid percent-encoding)
	 */
	public static RequestPath parse( String path ) {

		if( path == null || path.isEmpty() ) {
			path = "/";
		}

		if( !path.startsWith( "/" ) ) {
			return null;
		}

		if( path.equals( "/" ) ) {
			return new RequestPath( path, List.of(), true );
		}

		final boolean trailingSlash = path.endsWith( "/" );
		final String body = path.substring( 1, trailingSlash ? path.length() - 1 : path.length() );
		final List<String> segments = new ArrayList<>();

		for( final String segment : body.split( "/", -1 ) ) {
			try {
				segments.add( URLDecoder.decode( segment.replace( "+", "%2B" ), StandardCharsets.UTF_8 ) );
			}
			catch( IllegalArgumentException e ) {
				return null;
			}
		}

		return new RequestPath( path, segments, trailingSlash );
	}

	/**
	 * @return The path in the other trailing slash form ({@code /docs} for {@code /docs/}, and the reverse)
	 */
	String withTrailingSlash( final boolean trailing ) {
		if( trailing ) {
			return raw.endsWith( "/" ) ? raw : raw + "/";
		}

		return raw.endsWith( "/" ) && raw.length() > 1 ? raw.substring( 0, raw.length() - 1 ) : raw;
	}
}
