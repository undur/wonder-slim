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
 */

public final class Host implements RouteCondition {

	private static final Pattern PARAMETER_NAME = Pattern.compile( "[A-Za-z_][A-Za-z0-9_]*" );

	private final String _pattern;
	private final List<String> _labels;
	private final List<String> _parameterNames;

	private Host( final String pattern ) {
		_pattern = RouteRequest.normalizedHost( pattern );
		_labels = List.of( _pattern.split( "\\.", -1 ) );

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

	public String pattern() {
		return _pattern;
	}

	public List<String> parameterNames() {
		return _parameterNames;
	}

	@Override
	public Result test( final RouteRequest request ) {
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
				if( labels[i].isEmpty() ) {
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
		final List<String> labels = new ArrayList<>();

		for( final String label : _labels ) {
			if( label.startsWith( "{" ) ) {
				final String name = label.substring( 1, label.length() - 1 );
				final String value = parameters.get( name );

				if( value == null || value.isEmpty() ) {
					throw new IllegalArgumentException( "The host pattern %s needs its parameter '%s'".formatted( _pattern, name ) );
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
