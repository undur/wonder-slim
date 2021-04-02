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
	 * Creates an immutable dictionary containing all of the keys and objects
	 * from two dictionaries.
	 * 
	 * @param dict1
	 *            the first dictionary
	 * @param dict2
	 *            the second dictionary
	 * @return immutbale dictionary containing the union of the two
	 *         dictionaries.
	 */
	public static <K, V> NSDictionary<K, V> dictionaryWithDictionaryAndDictionary(NSDictionary<? extends K, ? extends V> dict1, NSDictionary<? extends K, ? extends V> dict2) {
		if (dict1 == null || dict1.allKeys().count() == 0)
			return (NSDictionary<K, V>) dict2;
		if (dict2 == null || dict2.allKeys().count() == 0)
			return (NSDictionary<K, V>) dict1;

		NSMutableDictionary<K, V> result = new NSMutableDictionary<K, V>(dict2);
		result.addEntriesFromDictionary(dict1);
		return new NSDictionary<K, V>(result);
	}

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