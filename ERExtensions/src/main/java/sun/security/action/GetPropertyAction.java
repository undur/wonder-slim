package sun.security.action;

import java.security.PrivilegedAction;

/**
 * Replaces a Security Manager JDK class removed in JDK 24.
 * The class is used by at least NSTimeZone, which causes a WO app to fail on startup if this class is not present.
 */

public class GetPropertyAction implements PrivilegedAction<String> {

	private final String _propertyName;

	/**
	 * Constructor taking the name of a system property to get the value of
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