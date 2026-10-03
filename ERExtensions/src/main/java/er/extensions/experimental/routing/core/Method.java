package er.extensions.experimental.routing.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A route accepts only the given HTTP methods. A request with another method gets {@code 405} if no route at its path
 * accepts it. {@code HEAD} is accepted wherever {@code GET} is.
 */

public final class Method implements RouteCondition {

	public static final Method GET = of( "GET" );
	public static final Method POST = of( "POST" );
	public static final Method PUT = of( "PUT" );
	public static final Method PATCH = of( "PATCH" );
	public static final Method DELETE = of( "DELETE" );

	private final Set<String> _methods;

	private Method( final Set<String> methods ) {
		_methods = Collections.unmodifiableSet( new TreeSet<>( methods ) );
	}

	/**
	 * @return The condition that the request uses one of the given methods
	 */
	public static Method of( final String... methods ) {
		if( methods.length == 0 ) {
			throw new IllegalArgumentException( "A method condition names at least one method" );
		}

		return new Method( Arrays.stream( methods ).map( m -> m.toUpperCase( Locale.ROOT ) ).collect( Collectors.toSet() ) );
	}

	/**
	 * @return The methods accepted, {@code HEAD} included where {@code GET} is
	 */
	public Set<String> accepted() {
		if( _methods.contains( "GET" ) && !_methods.contains( "HEAD" ) ) {
			final Set<String> withHead = new TreeSet<>( _methods );
			withHead.add( "HEAD" );
			return Collections.unmodifiableSet( withHead );
		}

		return _methods;
	}

	@Override
	public Result test( final RouteRequest request ) {
		return accepted().contains( request.method() ) ? Satisfied.NO_PARAMETERS : new NotAllowed( accepted() );
	}

	/**
	 * Two method conditions overlap if they accept a method in common
	 */
	@Override
	public boolean overlaps( final RouteCondition other ) {
		return other instanceof Method method && !Collections.disjoint( accepted(), method.accepted() );
	}

	@Override
	public boolean equals( final Object other ) {
		return other instanceof Method method && _methods.equals( method._methods );
	}

	@Override
	public int hashCode() {
		return _methods.hashCode();
	}

	@Override
	public String toString() {
		return "Method " + String.join( ",", _methods );
	}
}
