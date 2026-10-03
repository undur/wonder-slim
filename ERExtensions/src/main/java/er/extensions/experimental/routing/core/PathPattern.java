package er.extensions.experimental.routing.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A route's path pattern.
 *
 * <ul>
 * <li>{@code /items} is exact.</li>
 * <li>{@code /items/{id}} has a parameter: one non-empty path element, available by name.</li>
 * <li>{@code /news/*} is a wildcard: {@code /news/} and everything beneath it, but not {@code /news}. What it matched
 * is available as the parameter {@code *}.</li>
 * </ul>
 *
 * A pattern declares its trailing slash form ({@code /docs} or {@code /docs/}). Whether a request in the other form
 * matches is the route's trailing slash policy, see {@link TrailingSlash}.
 */

public final class PathPattern {

	/**
	 * The name the remainder matched by a wildcard is available under
	 */
	public static final String WILDCARD_PARAMETER = "*";

	private static final Pattern PARAMETER_NAME = Pattern.compile( "[A-Za-z_][A-Za-z0-9_]*" );

	private sealed interface Segment permits Literal, Parameter {}

	private record Literal( String text ) implements Segment {}

	private record Parameter( String name ) implements Segment {}

	private final String _source;
	private final List<Segment> _segments;
	private final boolean _wildcard;
	private final boolean _trailingSlash;

	private PathPattern( final String source, final List<Segment> segments, final boolean wildcard, final boolean trailingSlash ) {
		_source = source;
		_segments = List.copyOf( segments );
		_wildcard = wildcard;
		_trailingSlash = trailingSlash;
	}

	/**
	 * @throws IllegalArgumentException if the pattern isn't valid, saying why
	 */
	public static PathPattern parse( final String pattern ) {

		if( pattern == null || !pattern.startsWith( "/" ) ) {
			throw new IllegalArgumentException( "A path pattern starts with '/': '%s'".formatted( pattern ) );
		}

		if( pattern.equals( "/" ) ) {
			return new PathPattern( pattern, List.of(), false, true );
		}

		final boolean wildcard = pattern.endsWith( "/*" );
		final String withoutWildcard = wildcard ? pattern.substring( 0, pattern.length() - 1 ) : pattern;
		final boolean trailingSlash = withoutWildcard.endsWith( "/" );
		final String body = withoutWildcard.equals( "/" ) ? "" : withoutWildcard.substring( 1, trailingSlash ? withoutWildcard.length() - 1 : withoutWildcard.length() );
		final List<Segment> segments = new ArrayList<>();
		final Set<String> names = new HashSet<>();

		if( !body.isEmpty() ) {
			for( final String segment : body.split( "/", -1 ) ) {
				if( segment.isEmpty() ) {
					throw new IllegalArgumentException( "A path pattern has no empty segments ('//'): '%s'".formatted( pattern ) );
				}

				if( segment.startsWith( "{" ) && segment.endsWith( "}" ) ) {
					final String name = segment.substring( 1, segment.length() - 1 );

					if( !PARAMETER_NAME.matcher( name ).matches() ) {
						throw new IllegalArgumentException( "'%s' isn't a valid parameter name in the path pattern '%s'".formatted( name, pattern ) );
					}

					if( !names.add( name ) ) {
						throw new IllegalArgumentException( "The parameter {%s} appears twice in the path pattern '%s'".formatted( name, pattern ) );
					}

					segments.add( new Parameter( name ) );
				}
				else if( segment.contains( "{" ) || segment.contains( "}" ) || segment.contains( "*" ) ) {
					throw new IllegalArgumentException( "A parameter ('{name}') or a wildcard ('*', at the end) is a whole segment of a path pattern: '%s'".formatted( pattern ) );
				}
				else {
					segments.add( new Literal( segment ) );
				}
			}
		}

		return new PathPattern( pattern, segments, wildcard, trailingSlash || wildcard );
	}

	public String source() {
		return _source;
	}

	public boolean isWildcard() {
		return _wildcard;
	}

	public boolean hasTrailingSlash() {
		return _trailingSlash;
	}

	/**
	 * @return The names of the pattern's parameters, in order (the wildcard's not included)
	 */
	public List<String> parameterNames() {
		return _segments.stream().filter( Parameter.class::isInstance ).map( s -> ((Parameter)s).name() ).toList();
	}

	/**
	 * The outcome of matching a request path
	 *
	 * @param parameters The parameters' values by name, the wildcard's remainder under {@link PathPattern#WILDCARD_PARAMETER}
	 * @param exactForm false if the path matched only in the other trailing slash form
	 */
	public record Match( Map<String, String> parameters, boolean exactForm ) {}

	/**
	 * @return The match, or null if the path doesn't match (in either trailing slash form)
	 */
	public Match match( final RequestPath path ) {
		final List<String> requestSegments = path.segments();
		final int count = _segments.size();

		if( _wildcard ) {
			// Everything beneath the prefix: one or more further segments, or the prefix itself with a trailing slash
			final boolean beneath = requestSegments.size() > count || (requestSegments.size() == count && path.trailingSlash());

			if( !beneath ) {
				return null;
			}
		}
		else if( requestSegments.size() != count ) {
			return null;
		}

		final Map<String, String> parameters = new LinkedHashMap<>();

		for( int i = 0; i < count; i++ ) {
			final String requestSegment = requestSegments.get( i );

			switch( _segments.get( i ) ) {
				case Literal literal -> {
					if( !literal.text().equals( requestSegment ) ) {
						return null;
					}
				}
				case Parameter parameter -> {
					if( requestSegment.isEmpty() ) {
						return null;
					}

					parameters.put( parameter.name(), requestSegment );
				}
			}
		}

		if( _wildcard ) {
			final String remainder = String.join( "/", requestSegments.subList( count, requestSegments.size() ) );
			parameters.put( WILDCARD_PARAMETER, requestSegments.size() > count && path.trailingSlash() ? remainder + "/" : remainder );
			return new Match( parameters, true );
		}

		// The root has one form only
		final boolean exactForm = count == 0 || path.trailingSlash() == _trailingSlash;
		return new Match( parameters, exactForm );
	}

	/**
	 * @return What two patterns have in common if they match the same paths: literals, parameters (names left out),
	 *         the wildcard. The trailing slash isn't part of it, since a policy can make both forms match.
	 */
	String shape() {
		final StringBuilder b = new StringBuilder();

		for( final Segment segment : _segments ) {
			b.append( '/' ).append( segment instanceof Literal literal ? literal.text() : "{}" );
		}

		return _wildcard ? b + "/*" : b.isEmpty() ? "/" : b.toString();
	}

	/**
	 * Precedence between two patterns that match the same path: at the first position where they differ, a literal
	 * comes before a parameter, a parameter before a wildcard, and a pattern that has ended (exact) before a wildcard.
	 */
	static int comparePrecedence( final PathPattern a, final PathPattern b ) {
		final int length = Math.max( a._segments.size(), b._segments.size() ) + 1;

		for( int i = 0; i < length; i++ ) {
			final int difference = Integer.compare( a.rank( i ), b.rank( i ) );

			if( difference != 0 ) {
				return difference;
			}
		}

		return 0;
	}

	/**
	 * @return The rank of the pattern at a position: 0 a literal, 1 a parameter, 2 the wildcard, -1 the end of an exact
	 *         pattern (lower comes first)
	 */
	private int rank( final int position ) {
		if( position < _segments.size() ) {
			return _segments.get( position ) instanceof Literal ? 0 : 1;
		}

		return _wildcard ? 2 : -1;
	}

	@Override
	public String toString() {
		return _source;
	}
}
