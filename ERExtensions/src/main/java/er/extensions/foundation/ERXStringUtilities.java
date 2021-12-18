package er.extensions.foundation;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;

@Deprecated
public class ERXStringUtilities {

	private static final Logger log = LoggerFactory.getLogger(ERXStringUtilities.class);

	/**
	 * Calculates a default display name for a given key path. For instance for
	 * the key path: "foo.bar" the display name would be "Bar".
	 * 
	 * @param key to calculate the display name
	 * @return display name for the given key
	 */
	public static String displayNameForKey(String key) {
		StringBuilder finalString = null;
		if (!isNullOrEmpty(key) && !key.trim().equals("")) {
			finalString = new StringBuilder();
			String lastHop = key.indexOf(".") == -1 ? key : key.endsWith(".") ? "" : key.substring(key.lastIndexOf(".") + 1);
			StringBuilder tempString = new StringBuilder();
			char[] originalArray = lastHop.toCharArray();
			originalArray[0] = Character.toUpperCase(originalArray[0]);
			Character tempChar = null;
			Character nextChar = Character.valueOf(originalArray[0]);
			for (int i = 0; i < (originalArray.length - 1); i++) {
				tempChar = Character.valueOf(originalArray[i]);
				nextChar = Character.valueOf(originalArray[i + 1]);
				if (Character.isUpperCase(originalArray[i]) &&
						Character.isLowerCase(originalArray[i + 1])) {
					finalString.append(tempString.toString());
					if (i > 0)
						finalString.append(' ');
					tempString = new StringBuilder();
				}
				tempString.append(tempChar.toString());
			}
			finalString.append(tempString.toString());
			finalString.append(nextChar);
		}
		return finalString == null ? "" : finalString.toString();
	}

	/**
	 * Cleans up the given version string by removing extra dots(.), for
	 * example, 5.1.3 becomes 5.13, so that the string can be converted to a
	 * double or BigDecimal type easily.
	 * 
	 * @param version string
	 * @return cleaned-up string that only contains the first dot(.) as the floating point indicator.
	 */
	public static String removeExtraDotsFromVersionString(String version) {
		int floatingPointIndex = version.indexOf(".");
		if (floatingPointIndex >= 0 && floatingPointIndex + 1 < version.length()) {
			String minorVersion = version.substring(floatingPointIndex + 1).replace( ".", "");
			version = version.substring(0, floatingPointIndex + 1) + minorVersion;
		}
		return version;
	}

	/**
	 * Capitalizes a given string. That is, the first character of the returned
	 * string will be upper case, and other characters will be unchanged. For
	 * example, for the input string "{@code you have a dog}", this method would
	 * return "{@code You have a dog}".
	 * 
	 * @param value to be capitalized
	 * @return capitalized string
	 */
	public static String capitalize(String value) {
		String capital = null;
		if (value != null && value.length() > 0) {
			StringBuilder buffer = new StringBuilder(value);

			buffer.setCharAt(0, Character.toUpperCase(value.charAt(0)));
			capital = buffer.toString();
		}
		return capital != null ? capital : value;
	}

	/**
	 * Simple test if the string is either null or equal to "".
	 * 
	 * @param string string to test
	 * @return result of the above test
	 */
	public static boolean isNullOrEmpty(String string) {
		return string == null || string.isEmpty();
	}

	/**
	 * Retrieves a given string for a given name, extension and bundle.
	 * 
	 * @param name of the resource
	 * @param extension of the resource, example: txt or rtf
	 * @param bundle to look for the resource in
	 * @return string of the given file specified in the bundle
	 */
	public static String stringFromResource(String name, String extension, NSBundle bundle) {
		String path = null;

		if (bundle == null) {
			bundle = NSBundle.mainBundle();
		}

		path = bundle.resourcePathForLocalizedResourceNamed(name + (extension == null || extension.length() == 0 ? "" : "." + extension), null);

		if (path != null) {
			try( InputStream stream = bundle.inputStreamForResourcePath(path)) {
				byte bytes[] = stream.readAllBytes();
				return new String(bytes);
			}
			catch (IOException e) {
				log.warn("IOException when stringFromResource({}.{} in bundle {}", name, extension, bundle.name());
			}
		}

		return null;
	}

	/**
	 * Creates an NSDictionary from a resource associated with a given bundle that is in property list format.
	 * 
	 * @param name name of the file or resource.
	 * @param bundle NSBundle to which the resource belongs.
	 * @return NSDictionary de-serialized from the property list.
	 */
	@SuppressWarnings("unchecked")
	public static NSDictionary dictionaryFromPropertyList(String name, NSBundle bundle) {
		String string = stringFromResource(name, "plist", bundle);
		return (NSDictionary<?, ?>) NSPropertyListSerialization.propertyListFromString(string);
	}
}