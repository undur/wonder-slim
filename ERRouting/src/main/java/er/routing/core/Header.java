package er.routing.core;

import java.util.Locale;
import java.util.Objects;

/**
 * A route answers requests carrying a header: with a given value ({@code Header.of( "X-Api-Version", "2" )}), or with
 * any ({@code Header.present( "X-Requested-With" )}). Names compare without case, values exactly. A request without it
 * isn't for the route, which isn't there for it: another route at the path answers.
 */

public final class Header implements RouteCondition {

	private final String _name;

	/**
	 * The value required, null for any
	 */
	private final String _value;

	private Header( final String name, final String value ) {
		_name = Objects.requireNonNull( name ).toLowerCase( Locale.ROOT );
		_value = value;
	}

	/**
	 * @return The condition that the request has the header with the value
	 */
	public static Header of( final String name, final String value ) {
		return new Header( name, Objects.requireNonNull( value ) );
	}

	/**
	 * @return The condition that the request has the header, with any value
	 */
	public static Header present( final String name ) {
		return new Header( name, null );
	}

	@Override
	public Result test( final RouteRequest request ) {
		final String value = request.header( _name );
		return value != null && (_value == null || _value.equals( value )) ? Satisfied.NO_PARAMETERS : NotHere.INSTANCE;
	}

	/**
	 * A header with a value is more specific than the header with any value
	 */
	@Override
	public int specificity() {
		return _value == null ? 1 : 2;
	}

	/**
	 * Two header conditions overlap if they're on the same header, and a value could satisfy both
	 */
	@Override
	public boolean overlaps( final RouteCondition other ) {
		return other instanceof Header header && _name.equals( header._name ) && (_value == null || header._value == null || _value.equals( header._value ));
	}

	@Override
	public boolean equals( final Object other ) {
		return other instanceof Header header && _name.equals( header._name ) && Objects.equals( _value, header._value );
	}

	@Override
	public int hashCode() {
		return Objects.hash( _name, _value );
	}

	@Override
	public String toString() {
		return "Header " + _name + (_value == null ? "" : "=" + _value);
	}
}
