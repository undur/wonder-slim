package sun.security.action;

import java.security.PrivilegedAction;

/**
 * Replaces an old class from the Security Manager (and thus removed from modern JDKs). Used by NSTimeZone.
 */

public class GetPropertyAction implements PrivilegedAction<String> {

	private final String _propertyName;

	/**
	 * Constructor taking the name of a system property to get the value of
	 *
	 */
	public GetPropertyAction( String propertyName ) {
		this._propertyName = propertyName;
	}

	/**
	 * @return The value of the property this object represents/wraps
	 */
	@Override
	public String run() {
		return System.getProperty( _propertyName );
	}
}