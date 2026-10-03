package er.routing.core;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * <li>{@code /items/{id}.json} and {@code /book-{id}} have a parameter within an element: the element's text around it
 * is literal. One parameter to an element.</li>
 * <li>{@code /news/*} is a wildcard: {@code /news/} and everything beneath it. What it matched is available as the
 * parameter {@code *}, or by a name of its own: {@code /files/{path*}}. {@code /news} itself is the wildcard's other
 * trailing slash form, which the route's policy decides (redirected, matched with nothing beneath, or not matched).</li>
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

	private sealed interface Segment permits Literal, Parameter, Affixed {}

	private record Literal( String text ) implements Segment {}

	private record Parameter( String name ) implements Segment {}

	/**
	 * A parameter within an element, literal text before or after it ({@code {id}.json})
	 */
	private record Affixed( String prefix, String name, String suffix ) implements Segment {}

	private final String _source;
	private final List<Segment> _segments;
	private final boolean _wildcard;

	/**
	 * The name the wildcard's remainder is available under: {@value #WILDCARD_PARAMETER}, or its own ({@code {path*}})
	 */
	private final String _wildcardName;
	private final boolean _trailingSlash;

	private PathPattern( final String source, final List<Segment> segments, final boolean wildcard, final String wildcardName, final boolean trailingSlash ) {
		_source = source;
		_segments = List.copyOf( segments );
		_wildcard = wildcard;
		_wildcardName = wildcardName;
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
			return new PathPattern( pattern, List.of(), false, WILDCARD_PARAMETER, true );
		}

		// A named wildcard, {path*}, is the wildcard with a name of its own
		final java.util.regex.Matcher namedWildcard = Pattern.compile( "/\\{([A-Za-z_][A-Za-z0-9_]*)\\*\\}$" ).matcher( pattern );
		final String wildcardName = namedWildcard.find() ? namedWildcard.group( 1 ) : WILDCARD_PARAMETER;
		final String unnamed = wildcardName.equals( WILDCARD_PARAMETER ) ? pattern : pattern.substring( 0, namedWildcard.start() ) + "/*";

		final boolean wildcard = unnamed.endsWith( "/*" );
		final String withoutWildcard = wildcard ? unnamed.substring( 0, unnamed.length() - 1 ) : unnamed;
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
				else if( segment.contains( "*" ) ) {
					throw new IllegalArgumentException( "A wildcard ('*' or '{name*}') is the last segment of a path pattern: '%s'".formatted( pattern ) );
				}
				else if( segment.contains( "{" ) || segment.contains( "}" ) ) {
					final java.util.regex.Matcher affixed = Pattern.compile( "([^{}]*)\\{([^{}]*)\\}([^{}]*)" ).matcher( segment );

					if( !affixed.matches() ) {
						throw new IllegalArgumentException( "A path element of a pattern has one parameter at most ('{id}.json'): '%s'".formatted( pattern ) );
					}

					final String name = affixed.group( 2 );

					if( !PARAMETER_NAME.matcher( name ).matches() ) {
						throw new IllegalArgumentException( "'%s' isn't a valid parameter name in the path pattern '%s'".formatted( name, pattern ) );
					}

					if( !names.add( name ) ) {
						throw new IllegalArgumentException( "The parameter {%s} appears twice in the path pattern '%s'".formatted( name, pattern ) );
					}

					segments.add( new Affixed( affixed.group( 1 ), name, affixed.group( 3 ) ) );
				}
				else {
					segments.add( new Literal( segment ) );
				}
			}
		}

		if( !wildcardName.equals( WILDCARD_PARAMETER ) && !names.add( wildcardName ) ) {
			throw new IllegalArgumentException( "The parameter {%s} appears twice in the path pattern '%s'".formatted( wildcardName, pattern ) );
		}

		return new PathPattern( pattern, segments, wildcard, wildcardName, trailingSlash || wildcard );
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
	 * @return The names of the pattern's parameters, in order, a named wildcard's last (an unnamed one's not included)
	 */
	public List<String> parameterNames() {
		final List<String> names = new ArrayList<>();

		for( final Segment segment : _segments ) {
			switch( segment ) {
				case Parameter parameter -> names.add( parameter.name() );
				case Affixed affixed -> names.add( affixed.name() );
				case Literal literal -> {}
			}
		}

		if( _wildcard && !_wildcardName.equals( WILDCARD_PARAMETER ) ) {
			names.add( _wildcardName );
		}

		return List.copyOf( names );
	}

	/**
	 * @return The name the wildcard's remainder is available under, null for a pattern without one
	 */
	public String wildcardName() {
		return _wildcard ? _wildcardName : null;
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

		// The wildcard's prefix itself, without its trailing slash: the wildcard's other form, nothing beneath
		final boolean prefixAlone = _wildcard && requestSegments.size() == count && !path.trailingSlash();

		if( _wildcard ) {
			// Everything beneath the prefix: one or more further segments, or the prefix itself with a trailing slash
			final boolean beneath = requestSegments.size() > count || (requestSegments.size() == count && path.trailingSlash());

			if( !beneath && !prefixAlone ) {
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
				case Affixed affixed -> {
					if( requestSegment.length() <= affixed.prefix().length() + affixed.suffix().length() || !requestSegment.startsWith( affixed.prefix() ) || !requestSegment.endsWith( affixed.suffix() ) ) {
						return null;
					}

					parameters.put( affixed.name(), requestSegment.substring( affixed.prefix().length(), requestSegment.length() - affixed.suffix().length() ) );
				}
			}
		}

		if( _wildcard ) {
			final List<String> remaining = requestSegments.subList( count, requestSegments.size() );

			// The remainder is joined with /, so an element with an encoded / in it (..%2F..%2Fetc) would read as more than
			// one, its own dot segments among them: the remainder is a path a handler may serve files from
			if( remaining.stream().anyMatch( segment -> segment.contains( "/" ) ) ) {
				return null;
			}

			final String remainder = String.join( "/", remaining );
			parameters.put( _wildcardName, requestSegments.size() > count && path.trailingSlash() ? remainder + "/" : remainder );
			return new Match( parameters, !prefixAlone );
		}

		// The root has one form only
		final boolean exactForm = count == 0 || path.trailingSlash() == _trailingSlash;
		return new Match( parameters, exactForm );
	}

	/**
	 * @return The path for the given parameter values, each encoded as a path segment, in the pattern's trailing slash
	 *         form: the reverse of {@link #match(RequestPath)}
	 * @throws IllegalArgumentException if a parameter has no value, or the pattern is a wildcard
	 */
	public String path( final Map<String, String> values ) {

		if( _wildcard && _wildcardName.equals( WILDCARD_PARAMETER ) ) {
			throw new IllegalArgumentException( "A path can't be generated for the wildcard pattern %s, whose remainder has no name: name it ({path*}) to link to it".formatted( _source ) );
		}

		if( _segments.isEmpty() && !_wildcard ) {
			return "/";
		}

		final StringBuilder b = new StringBuilder();

		for( final Segment segment : _segments ) {
			final String text = switch( segment ) {
				case Literal literal -> literal.text();
				case Parameter parameter -> checked( parameter.name(), values.get( parameter.name() ) );
				case Affixed affixed -> affixed.prefix() + checked( affixed.name(), values.get( affixed.name() ) ) + affixed.suffix();
			};

			b.append( '/' ).append( encoded( text ) );
		}

		if( _wildcard ) {

			// The remainder's elements, each a path element (an empty remainder is the prefix, with its slash)
			final String remainder = values.get( _wildcardName );

			if( remainder == null ) {
				throw new IllegalArgumentException( "The path pattern %s needs its parameter '%s'".formatted( _source, _wildcardName ) );
			}

			final boolean trailing = remainder.isEmpty() || remainder.endsWith( "/" );
			final String elements = trailing && !remainder.isEmpty() ? remainder.substring( 0, remainder.length() - 1 ) : remainder;

			if( !elements.isEmpty() ) {
				for( final String element : elements.split( "/", -1 ) ) {
					b.append( '/' ).append( encoded( checked( _wildcardName, element ) ) );
				}
			}

			return trailing ? b.append( '/' ).toString() : b.toString();
		}

		return _trailingSlash ? b.append( '/' ).toString() : b.toString();
	}

	/**
	 * @return The value, checked to be a path element
	 * @throws IllegalArgumentException for none, one a browser resolves away, or one a server refuses
	 */
	private String checked( final String name, final String value ) {

		if( value == null || value.isEmpty() ) {
			throw new IllegalArgumentException( "The path pattern %s needs its parameter '%s'".formatted( _source, name ) );
		}

		// A browser resolves these away, encoded or not, so they can't travel as a path element
		if( value.equals( "." ) || value.equals( ".." ) ) {
			throw new IllegalArgumentException( "The value '%s' of the parameter '%s' (%s) can't be a path element: browsers resolve it away".formatted( value, name, _source ) );
		}

		// Servers refuse these in a path, encoded (an encoded %, \ or control character is "ambiguous" or "suspicious" to
		// them), so the URL wouldn't reach the application. Refused for now, since allowing a character later is easier than
		// refusing it.
		if( value.chars().anyMatch( PathPattern::refusedInPath ) ) {
			throw new IllegalArgumentException( "The value '%s' of the parameter '%s' (%s) can't be a path element: servers refuse a path with an encoded %%, \\ or control character".formatted( value, name, _source ) );
		}

		return value;
	}

	private static String encoded( final String text ) {
		return URLEncoder.encode( text, StandardCharsets.UTF_8 ).replace( "+", "%20" );
	}

	/**
	 * @return true for a character servers refuse in a path, encoded: %, \, the control characters and DEL
	 */
	private static boolean refusedInPath( final int c ) {
		return c == '%' || c == '\\' || c < 0x20 || c == 0x7F;
	}

	/**
	 * @return What two patterns have in common if they match the same paths: literals, parameters (names left out),
	 *         the wildcard. The trailing slash isn't part of it, since a policy can make both forms match.
	 */
	String shape() {
		final StringBuilder b = new StringBuilder();

		for( final Segment segment : _segments ) {
			b.append( '/' ).append( switch( segment ) {
				case Literal literal -> literal.text();
				case Parameter parameter -> "{}";
				case Affixed affixed -> affixed.prefix() + "{}" + affixed.suffix();
			} );
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
			int difference = Integer.compare( a.rank( i ), b.rank( i ) );

			// Two parameters within literal text: the one whose literals contain the other's comes first ({a}.min.json
			// before {a}.json)
			if( difference == 0 && i < a._segments.size() && a._segments.get( i ) instanceof Affixed x && b._segments.get( i ) instanceof Affixed y ) {
				difference = contains( x, y ) && !contains( y, x ) ? -1 : contains( y, x ) && !contains( x, y ) ? 1 : 0;
			}

			if( difference != 0 ) {
				return difference;
			}
		}

		return 0;
	}

	/**
	 * @return true if x's literals contain y's, on their sides: x matches only elements y matches
	 */
	private static boolean contains( final Affixed x, final Affixed y ) {
		return x.prefix().startsWith( y.prefix() ) && x.suffix().endsWith( y.suffix() );
	}

	/**
	 * @return true if the two patterns can match the same path and neither comes first: at a place where both have a
	 *         parameter within literal text, their literals are on opposite sides ({@code pre-{a}} and {@code {a}.json}
	 *         both match {@code pre-7.json}), so which answers would be the mapping order
	 */
	static boolean ambiguous( final PathPattern a, final PathPattern b ) {

		if( a._segments.size() != b._segments.size() || a._wildcard != b._wildcard || comparePrecedence( a, b ) != 0 || a.shape().equals( b.shape() ) ) {
			return false;
		}

		for( int i = 0; i < a._segments.size(); i++ ) {
			if( !overlap( a._segments.get( i ), b._segments.get( i ) ) ) {
				return false;
			}
		}

		return true;
	}

	/**
	 * @return true if some path element could match both segments
	 */
	private static boolean overlap( final Segment a, final Segment b ) {
		return switch( a ) {
			case Literal x -> switch( b ) {
				case Literal y -> x.text().equals( y.text() );
				case Parameter y -> true;
				case Affixed y -> matches( y, x.text() );
			};
			case Parameter x -> true;
			case Affixed x -> switch( b ) {
				case Literal y -> matches( x, y.text() );
				case Parameter y -> true;
				case Affixed y -> (x.prefix().startsWith( y.prefix() ) || y.prefix().startsWith( x.prefix() )) && (x.suffix().endsWith( y.suffix() ) || y.suffix().endsWith( x.suffix() ));
			};
		};
	}

	private static boolean matches( final Affixed affixed, final String text ) {
		return text.length() > affixed.prefix().length() + affixed.suffix().length() && text.startsWith( affixed.prefix() ) && text.endsWith( affixed.suffix() );
	}

	/**
	 * @return The rank of the pattern at a position: 0 a literal, 1 a parameter within literal text, 2 a parameter, 3 the
	 *         wildcard, -1 the end of an exact pattern (lower comes first)
	 */
	private int rank( final int position ) {
		if( position < _segments.size() ) {
			return switch( _segments.get( position ) ) {
				case Literal literal -> 0;
				case Affixed affixed -> 1;
				case Parameter parameter -> 2;
			};
		}

		return _wildcard ? 3 : -1;
	}

	@Override
	public String toString() {
		return _source;
	}
}
