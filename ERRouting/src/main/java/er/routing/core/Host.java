package er.routing.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A route answers requests to a host: exact ({@code admin.example.com}) or a pattern ({@code {tenant}.example.com}),
 * whose parameters reach the route like path parameters. Hosts are compared without case and port.
 *
 * A pattern may end in {@value #DOMAIN}, the application's domain ({@code {tenant}.@}), so the same routes answer
 * {@code acme.localhost} in development and {@code acme.example.com} deployed. Such a host is resolved with
 * {@link #withDomain(String)} before it's used.
 */

public final class Host implements RouteCondition {

	private static final Pattern PARAMETER_NAME = Pattern.compile( "[A-Za-z_][A-Za-z0-9_]*" );

	/**
	 * One label of a host name (RFC 1123)
	 */
	private static final Pattern HOST_LABEL = Pattern.compile( "[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?" );

	/**
	 * The last label of a pattern relative to the application's domain
	 */
	public static final String DOMAIN = "@";

	private final String _pattern;
	private final List<String> _labels;
	private final List<String> _parameterNames;

	private Host( final String pattern ) {
		final List<String> labels = new ArrayList<>();

		// Literal labels compare without case, so they're lower case. Parameter names keep their case.
		if( pattern.contains( ":" ) || pattern.contains( "[" ) ) {
			throw new IllegalArgumentException( "A host pattern has no port, and is a name: '%s'. Hosts are compared without their port, which differs between development and deployment".formatted( pattern ) );
		}

		for( final String label : pattern.trim().split( "\\.", -1 ) ) {
			labels.add( label.startsWith( "{" ) ? label : label.toLowerCase( Locale.ROOT ) );
		}

		_labels = List.copyOf( labels );
		_pattern = String.join( ".", _labels );

		final List<String> names = new ArrayList<>();
		final Set<String> unique = new HashSet<>();

		for( final String label : _labels ) {
			if( label.isEmpty() ) {
				throw new IllegalArgumentException( "A host pattern has no empty labels: '%s'".formatted( pattern ) );
			}

			if( label.startsWith( "{" ) && label.endsWith( "}" ) ) {
				final String name = label.substring( 1, label.length() - 1 );

				if( !PARAMETER_NAME.matcher( name ).matches() ) {
					throw new IllegalArgumentException( "'%s' isn't a valid parameter name in the host pattern '%s'".formatted( name, pattern ) );
				}

				if( !unique.add( name ) ) {
					throw new IllegalArgumentException( "The parameter {%s} appears twice in the host pattern '%s'".formatted( name, pattern ) );
				}

				names.add( name );
			}
			else if( label.equals( DOMAIN ) ) {
				if( _labels.indexOf( DOMAIN ) != _labels.size() - 1 ) {
					throw new IllegalArgumentException( "The domain (%s) is the last label of a host pattern: '%s'".formatted( DOMAIN, pattern ) );
				}
			}
			else if( label.contains( DOMAIN ) ) {
				throw new IllegalArgumentException( "The domain (%s) is a whole label of a host pattern: '%s'".formatted( DOMAIN, pattern ) );
			}
			else if( label.contains( "{" ) || label.contains( "}" ) ) {
				throw new IllegalArgumentException( "A parameter is a whole label of a host pattern: '%s'".formatted( pattern ) );
			}
		}

		_parameterNames = List.copyOf( names );
	}

	/**
	 * @return The condition that the request is to the given host or matches the given host pattern
	 */
	public static Host of( final String pattern ) {
		return new Host( pattern );
	}

	public boolean isRelative() {
		return _labels.getLast().equals( DOMAIN );
	}

	/**
	 * @return The host with the application's domain for {@value #DOMAIN} ({@code {tenant}.@} with {@code example.com}
	 *         is {@code {tenant}.example.com}), itself if it isn't relative
	 */
	public Host withDomain( final String domain ) {
		if( !isRelative() ) {
			return this;
		}

		final List<String> labels = new ArrayList<>( _labels.subList( 0, _labels.size() - 1 ) );
		labels.add( domain );
		return new Host( String.join( ".", labels ) );
	}

	private void requireResolved() {
		if( isRelative() ) {
			throw new IllegalStateException( "The host pattern %s is relative to the application's domain, and was used before it was resolved (withDomain())".formatted( _pattern ) );
		}
	}

	public String pattern() {
		return _pattern;
	}

	public List<String> parameterNames() {
		return _parameterNames;
	}

	@Override
	public Result test( final RouteRequest request ) {
		requireResolved();
		final String host = request.host();

		if( host == null ) {
			return NotHere.INSTANCE;
		}

		final String[] labels = host.split( "\\.", -1 );

		if( labels.length != _labels.size() ) {
			return NotHere.INSTANCE;
		}

		final Map<String, String> parameters = new LinkedHashMap<>();

		for( int i = 0; i < labels.length; i++ ) {
			final String label = _labels.get( i );

			if( label.startsWith( "{" ) ) {

				// Only what a URL to the route could have (one host label), as generating one requires
				if( !HOST_LABEL.matcher( labels[i] ).matches() ) {
					return NotHere.INSTANCE;
				}

				parameters.put( label.substring( 1, label.length() - 1 ), labels[i] );
			}
			else if( !label.equals( labels[i] ) ) {
				return NotHere.INSTANCE;
			}
		}

		return parameters.isEmpty() ? Satisfied.NO_PARAMETERS : new Satisfied( parameters );
	}

	/**
	 * An exact host is more specific than a pattern
	 */
	@Override
	public int specificity() {
		return _parameterNames.isEmpty() ? 2 : 1;
	}

	@Override
	public boolean overlaps( final RouteCondition other ) {
		return other instanceof Host host && shape().equals( host.shape() );
	}

	/**
	 * @return The pattern with parameter names left out
	 */
	private String shape() {
		return String.join( ".", _labels.stream().map( l -> l.startsWith( "{" ) ? "{}" : l ).toList() );
	}

	@Override
	public boolean equals( final Object other ) {
		return other instanceof Host host && _pattern.equals( host._pattern );
	}

	@Override
	public int hashCode() {
		return _pattern.hashCode();
	}

	@Override
	public String toString() {
		return "Host " + _pattern;
	}

	/**
	 * @return The host for the given parameter values, for generating a URL to the route
	 */
	public String host( final Map<String, String> parameters ) {
		requireResolved();
		final List<String> labels = new ArrayList<>();

		for( final String label : _labels ) {
			if( label.startsWith( "{" ) ) {
				final String name = label.substring( 1, label.length() - 1 );
				final String value = parameters.get( name );

				if( value == null || value.isEmpty() ) {
					throw new IllegalArgumentException( "The host pattern %s needs its parameter '%s'".formatted( _pattern, name ) );
				}

				// A host label can't be encoded, so a value has to be one already: no dots, slashes or other characters
				if( !HOST_LABEL.matcher( value ).matches() ) {
					throw new IllegalArgumentException( "The value '%s' of the host parameter '%s' (%s) isn't a host label: letters, digits and hyphens, not starting or ending with a hyphen, at most 63 characters".formatted( value, name, _pattern ) );
				}

				labels.add( value.toLowerCase( Locale.ROOT ) );
			}
			else {
				labels.add( label );
			}
		}

		return String.join( ".", labels );
	}
}
