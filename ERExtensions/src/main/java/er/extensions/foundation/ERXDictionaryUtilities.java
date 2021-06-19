package er.extensions.foundation;

import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;

/**
 * Collection of {@link com.webobjects.foundation.NSDictionary NSDictionary}
 * utilities.
 */
public class ERXDictionaryUtilities {

	/**
	 * Creates an NSDictionary from a resource associated with a given bundle
	 * that is in property list format.
	 * 
	 * @param name
	 *            name of the file or resource.
	 * @param bundle
	 *            NSBundle to which the resource belongs.
	 * @return NSDictionary de-serialized from the property list.
	 */
	@SuppressWarnings("unchecked")
	public static NSDictionary dictionaryFromPropertyList(String name, NSBundle bundle) {
		String string = ERXStringUtilities.stringFromResource(name, "plist", bundle);
		return (NSDictionary<?, ?>) NSPropertyListSerialization.propertyListFromString(string);
	}

	/**
	 * Creates a dictionary from a list of alternating objects and keys starting
	 * with an object.
	 * 
	 * @param objectsAndKeys
	 *            alternating list of objects and keys
	 * @return NSDictionary containing all of the object-key pairs.
	 */
	@SuppressWarnings("unchecked")
	public static NSDictionary dictionaryWithObjectsAndKeys(Object[] objectsAndKeys) {
		NSMutableDictionary<Object, Object> result = new NSMutableDictionary<Object, Object>();
		Object object;
		String key;
		int length = objectsAndKeys.length;
		for (int i = 0; i < length; i += 2) {
			object = objectsAndKeys[i];
			if (object == null) {
				break;
			}
			key = (String) objectsAndKeys[i + 1];
			result.setObjectForKey(object, key);
		}
		return new NSDictionary<Object, Object>(result);
	}
}