package er.extensions.foundation;

import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;

/**
 * Collection of {@link com.webobjects.foundation.NSDictionary NSDictionary} utilities.
 */

public class ERXDictionaryUtilities {

	/**
	 * Creates an NSDictionary from a resource associated with a given bundle that is in property list format.
	 * 
	 * @param name name of the file or resource.
	 * @param bundle NSBundle to which the resource belongs.
	 * @return NSDictionary de-serialized from the property list.
	 */
	@SuppressWarnings("unchecked")
	public static NSDictionary dictionaryFromPropertyList(String name, NSBundle bundle) {
		String string = ERXStringUtilities.stringFromResource(name, "plist", bundle);
		return (NSDictionary<?, ?>) NSPropertyListSerialization.propertyListFromString(string);
	}
}