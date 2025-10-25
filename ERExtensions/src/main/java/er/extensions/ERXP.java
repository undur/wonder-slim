package er.extensions;

import er.extensions.foundation.ERXProperties;

/**
 * A central notification for informatino about declared and used properties 
 */

public enum ERXP {

	/**
	 * FIXME: ??? 	// Hugi 2025-10-25
	 */
	RESOURCE_URL_PREFIX( "er.extensions.ERXResourceManager.resourceUrlPrefix" ),

	/**
	 * FIXME: ??? 	// Hugi 2025-10-25
	 */
	SECURE_RESOURCE_URL_PREFIX( "er.extensions.ERXResourceManager.secureResourceUrlPrefix" );

	private String _id;

	private ERXP( String id ) {
		_id = id;
	}
	
	/**
	 * @return The Actual name of the property (used in Property files)
	 */
	public String id() {
		return _id;
	}
	
	public String stringValue() {
		return ERXProperties.stringForKey(_id);
	}
}