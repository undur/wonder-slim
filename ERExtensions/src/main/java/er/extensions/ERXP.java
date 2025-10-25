package er.extensions;

import er.extensions.foundation.ERXProperties;

/**
 * A central notification for information about declared and used properties
 * 
 *  FIXME: Properties should be typed (i.e. a property should know which type it will return and offer suitably typed retrieval methods) // Hugi 2025-10-25
 *  FIXME: Libraries/Frameworks/Apps should be able to declare their own properties // Hugi 2025-10-25
 *  FIXME: Collect all properties used in Wonder and add here // Hugi 2025-10-25
 *  FIXME: Eliminate direct usage of ERXPRoperties in code // Hugi 2025-10-25
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