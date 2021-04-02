package er.extensions.foundation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Enumeration;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCodingAdditions;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;

import er.extensions.appserver.ERXMessageEncoding;

/**
 * Collection of {@link java.lang.String String} utilities. Contains the base
 * localization support.
 */
public class ERXStringUtilities {

	private static final Logger log = LoggerFactory.getLogger(ERXStringUtilities.class);

	private static final char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	/**
	 * Retrieves a given string for a given name, extension and bundle.
	 * 
	 * @param name
	 *            of the resource
	 * @param extension
	 *            of the resource, example: txt or rtf
	 * @param bundle
	 *            to look for the resource in
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
				byte bytes[] = ERXFileUtilities.bytesFromInputStream(stream);
				return new String(bytes);
			}
			catch (IOException e) {
				log.warn("IOException when stringFromResource({}.{} in bundle {}", name, extension, bundle.name());
			}
		}
		return null;
	}

	public static final String firstPropertyKeyInKeyPath(String keyPath) {
		String part = null;
		if (keyPath != null) {
			int index = keyPath.indexOf(NSKeyValueCodingAdditions.KeyPathSeparator);
			if (index != -1)
				part = keyPath.substring(0, index);
			else
				part = keyPath;
		}
		return part;
	}

	public static final String lastPropertyKeyInKeyPath(String keyPath) {
		String part = null;
		if (keyPath != null) {
			int index = keyPath.lastIndexOf(NSKeyValueCodingAdditions.KeyPathSeparator);
			if (index != -1)
				part = keyPath.substring(index + 1);
			else
				part = keyPath;
		}
		return part;
	}

	/**
	 * Calculates a default display name for a given key path. For instance for
	 * the key path: "foo.bar" the display name would be "Bar".
	 * 
	 * @param key
	 *            to calculate the display name
	 * @return display name for the given key
	 */
	public static String displayNameForKey(String key) {
		StringBuilder finalString = null;
		if (!stringIsNullOrEmpty(key) && !key.trim().equals("")) {
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
	 * Locate the the first numeric character in the given string.
	 * 
	 * @param str
	 *            string to scan
	 * @return position in string or -1 if no numeric found
	 */
	public static int indexOfNumericInString(String str) {
		return indexOfNumericInString(str, 0);
	}

	/**
	 * Locate the the first numeric character after <code>fromIndex</code> in
	 * the given string.
	 * 
	 * @param str
	 *            string to scan
	 * @param fromIndex
	 *            index position from where to start
	 * @return position in string or -1 if no numeric found
	 */
	private static int indexOfNumericInString(String str, int fromIndex) {
		if (str == null)
			throw new IllegalArgumentException("String cannot be null.");

		int pos = -1;
		for (int i = fromIndex; i < str.length(); i++) {
			char c = str.charAt(i);
			if ('0' <= c && c <= '9') {
				pos = i;
				break;
			}
		}
		return pos;
	}

	/**
	 * This method runs about 20 times faster than java.lang.String.toLowerCase
	 * (and doesn't waste any storage when the result is equal to the input).
	 * Warning: Don't use this method when your default locale is Turkey.
	 * java.lang.String.toLowerCase is slow because (a) it uses a StringBuffer
	 * (which has synchronized methods), (b) it initializes the StringBuffer to
	 * the default size, and (c) it gets the default locale every time to test
	 * for name equal to "tr".
	 * 
	 * @see <a href="http://www.norvig.com/java-iaq.html#tolower">tolower</a>
	 * @author Peter Norvig
	 **/
	private static String toLowerCase(String str) {
		if (str == null)
			return null;

		int len = str.length();
		int different = -1;
		// See if there is a char that is different in lowercase
		for (int i = len - 1; i >= 0; i--) {
			char ch = str.charAt(i);
			if (Character.toLowerCase(ch) != ch) {
				different = i;
				break;
			}
		}

		// If the string has no different char, then return the string as is,
		// otherwise create a lowercase version in a char array.
		if (different == -1) {
			return str;
		}
		char[] chars = new char[len];
		str.getChars(0, len, chars, 0);
		// (Note we start at different, not at len.)
		for (int j = different; j >= 0; j--) {
			chars[j] = Character.toLowerCase(chars[j]);
		}

		return new String(chars);
	}

	/**
	 * Simple test if the string is either null or equal to "".
	 * 
	 * @param s
	 *            string to test
	 * @return result of the above test
	 */
	public static boolean stringIsNullOrEmpty(String s) {
		return ((s == null) || (s.length() == 0));
	}

	/**
	 * XML entities to unescape.
	 */
	private static final NSDictionary<String, String> XML_UNESCAPES;

	/**
	 * ISO entities to unescape.
	 */
	private static final NSDictionary<String, String> ISO_UNESCAPES;

	/**
	 * Symbol entities to unescape.
	 */
	private static final NSDictionary<String, String> SYMBOL_UNESCAPES;

	/**
	 * Safe HTML entities to unescape (SYMBOL+ISO). This still prevents
	 * injection attacks.
	 */
	private static final NSDictionary<String, String> HTML_SAFE_UNESCAPES;

	/**
	 * HTML entities to unescape (XML+SYMBOL+ISO).
	 */
	private static final NSDictionary<String, String> HTML_UNESCAPES;

	static {
		// NOTE AK I used:
		// http://www.w3schools.com/tags/ref_symbols.asp
		// http://www.w3schools.com/tags/ref_entities.asp
		// as apache commons lang didn't really work for me?!?

		Object[] xml = new Object[] { '<', "lt", '>', "gt", '&', "amp", '\"', "quot" };
		NSMutableDictionary<String, String> dict = new NSMutableDictionary<>();
		for (int i = 0; i < xml.length; i += 2) {
			Character charValue = ((Character) xml[i]);
			String key = (String) xml[i + 1];
			dict.setObjectForKey(charValue + "", key);
			dict.setObjectForKey(charValue + "", "#" + charValue);
		}
		XML_UNESCAPES = dict.immutableClone();

		Object[] iso = { 160, "nbsp", 161, "iexcl", 162, "cent", 163, "pound", 164, "curren", 165, "yen", 166, "brvbar", 167, "sect", 168, "uml", 169, "copy", 170, "ordf", 171, "laquo", 172, "not", 173, "shy", 174, "reg", 175, "macr", 176, "deg", 177, "plusmn", 178, "sup2", 179, "sup3", 180, "acute", 181, "micro", 182, "para", 183, "middot", 184, "cedil", 185, "sup1", 186, "ordm", 187, "raquo", 188, "frac14", 189, "frac12", 190, "frac34", 191, "iquest", 215, "times", 247, "divide", 192, "Agrave", 193, "Aacute", 194, "Acirc", 195, "Atilde", 196, "Auml", 197, "Aring", 198, "AElig", 199, "Ccedil", 200, "Egrave", 201, "Eacute", 202, "Ecirc", 203, "Euml", 204, "Igrave", 205, "Iacute", 206, "Icirc", 207, "Iuml", 208, "ETH", 209, "Ntilde", 210, "Ograve", 211, "Oacute", 212, "Ocirc", 213, "Otilde", 214, "Ouml", 216, "Oslash", 217, "Ugrave", 218, "Uacute", 219, "Ucirc", 220, "Uuml", 221, "Yacute", 222, "THORN", 223, "szlig", 224, "agrave", 225, "aacute", 226, "acirc", 227, "atilde", 228,
				"auml", 229, "aring", 230, "aelig", 231, "ccedil", 232, "egrave", 233, "eacute", 234, "ecirc", 235, "euml", 236, "igrave", 237, "iacute", 238, "icirc", 239, "iuml", 240, "eth", 241, "ntilde", 242, "ograve", 243, "oacute", 244, "ocirc", 245, "otilde", 246, "ouml", 248, "oslash", 249, "ugrave", 250, "uacute", 251, "ucirc", 252, "uuml", 253, "yacute", 254, "thorn", 255, "yuml" };

		dict = new NSMutableDictionary<>();
		for (int i = 0; i < iso.length; i += 2) {
			Integer charValue = ((Integer) iso[i]);
			String key = (String) iso[i + 1];
			dict.setObjectForKey(Character.toChars(charValue)[0] + "", key);
			dict.setObjectForKey(Character.toChars(charValue)[0] + "", "#" + charValue);
		}
		ISO_UNESCAPES = dict.immutableClone();

		Object[] symbols = new Object[] { 8704, "forall", 8706, "part", 8707, "exists", 8709, "empty", 8711, "nabla", 8712, "isin", 8713, "notin", 8715, "ni", 8719, "prod", 8721, "sum", 8722, "minus", 8727, "lowast", 8730, "radic", 8733, "prop", 8734, "infin", 8736, "ang", 8743, "and", 8744, "or", 8745, "cap", 8746, "cup", 8747, "int", 8756, "there4", 8764, "sim", 8773, "cong", 8776, "asymp", 8800, "ne", 8801, "equiv", 8804, "le", 8805, "ge", 8834, "sub", 8835, "sup", 8836, "nsub", 8838, "sube", 8839, "supe", 8853, "oplus", 8855, "otimes", 8869, "perp", 8901, "sdot", 913, "Alpha", 914, "Beta", 915, "Gamma", 916, "Delta", 917, "Epsilon", 918, "Zeta", 919, "Eta", 920, "Theta", 921, "Iota", 922, "Kappa", 923, "Lambda", 924, "Mu", 925, "Nu", 926, "Xi", 927, "Omicron", 928, "Pi", 929, "Rho", 931, "Sigma", 932, "Tau", 933, "Upsilon", 934, "Phi", 935, "Chi", 936, "Psi", 937, "Omega", 945, "alpha", 946, "beta", 947, "gamma", 948, "delta", 949, "epsilon", 950, "zeta", 951, "eta", 952, "theta",
				953, "iota", 954, "kappa", 955, "lambda", 956, "mu", 957, "nu", 958, "xi", 959, "omicron", 960, "pi", 961, "rho", 962, "sigmaf", 963, "sigma", 964, "tau", 965, "upsilon", 966, "phi", 967, "chi", 968, "psi", 969, "omega", 977, "thetasym", 978, "upsih", 982, "piv", 338, "OElig", 339, "oelig", 352, "Scaron", 353, "scaron", 376, "Yuml", 402, "fnof", 710, "circ", 732, "tilde", 8194, "ensp", 8195, "emsp", 8201, "thinsp", 8204, "zwnj", 8205, "zwj", 8206, "lrm", 8207, "rlm", 8211, "ndash", 8212, "mdash", 8216, "lsquo", 8217, "rsquo", 8218, "sbquo", 8220, "ldquo", 8221, "rdquo", 8222, "bdquo", 8224, "dagger", 8225, "Dagger", 8226, "bull", 8230, "hellip", 8240, "permil", 8242, "prime", 8243, "Prime", 8249, "lsaquo", 8250, "rsaquo", 8254, "oline", 8364, "euro", 8482, "trade", 8592, "larr", 8593, "uarr", 8594, "rarr", 8595, "darr", 8596, "harr", 8629, "crarr", 8968, "lceil", 8969, "rceil", 8970, "lfloor", 8971, "rfloor", 9674, "loz", 9824, "spades", 9827, "clubs", 9829, "hearts",
				9830, "diams" };
		dict = new NSMutableDictionary<>();
		for (int i = 0; i < symbols.length; i += 2) {
			Integer charValue = ((Integer) symbols[i]);
			String key = (String) symbols[i + 1];
			dict.setObjectForKey(Character.toChars(charValue)[0] + "", key);
			dict.setObjectForKey(Character.toChars(charValue)[0] + "", "#" + charValue);
		}
		SYMBOL_UNESCAPES = dict.immutableClone();

		dict = new NSMutableDictionary<>();
		dict.addEntriesFromDictionary(ISO_UNESCAPES);
		dict.addEntriesFromDictionary(SYMBOL_UNESCAPES);
		HTML_SAFE_UNESCAPES = dict.immutableClone();

		dict.addEntriesFromDictionary(XML_UNESCAPES);
		HTML_UNESCAPES = dict.immutableClone();
	}

	/**
	 * Escapes the apostrophes in a Javascript string with a backslash.
	 * 
	 * @param sourceString
	 *            the source string to escape
	 * @return the escaped javascript string
	 */
	public static String escapeJavascriptApostrophes(String sourceString) {
		return ERXStringUtilities.escape(new char[] { '\'' }, '\\', sourceString);
	}

	/**
	 * Escapes the given characters with the given escape character in
	 * _sourceString. This implementation is specifically designed for large
	 * strings. In the event that no characters are escaped, the original string
	 * will be returned with no new object creation. A null _sourceString will
	 * return null.
	 * 
	 * Example: sourceString = Mike's, escape chars = ', escape with = \,
	 * returns Mike\'s
	 * 
	 * @param _escapeChars
	 *            the list of characters to escape
	 * @param _escapeWith
	 *            the escape character to use
	 * @param _sourceString
	 *            the string to escape the characters in.
	 * @return the escaped string
	 */
	private static String escape(char[] _escapeChars, char _escapeWith, String _sourceString) {
		String targetString;
		if (_sourceString == null) {
			targetString = null;
		}
		else {
			StringBuilder targetBuffer = null;
			int lastMatch = 0;
			int length = _sourceString.length();
			for (int sourceIndex = 0; sourceIndex < length; sourceIndex++) {
				char ch = _sourceString.charAt(sourceIndex);
				boolean escape = false;
				for (int escapeNum = 0; !escape && escapeNum < _escapeChars.length; escapeNum++) {
					if (ch == _escapeChars[escapeNum]) {
						escape = true;
					}
				}
				if (escape) {
					if (targetBuffer == null) {
						targetBuffer = new StringBuilder(length + 100);
					}
					if (sourceIndex - lastMatch > 0) {
						targetBuffer.append(_sourceString.substring(lastMatch, sourceIndex));
					}
					targetBuffer.append(_escapeWith);
					lastMatch = sourceIndex;
				}
			}
			if (targetBuffer == null) {
				targetString = _sourceString;
			}
			else {
				targetBuffer.append(_sourceString.substring(lastMatch, length));
				targetString = targetBuffer.toString();
			}
		}
		return targetString;
	}

	/**
	 * Converts a byte array to hex string.
	 * 
	 * @param block
	 *            byte array
	 * @return hex string
	 */
	private static String byteArrayToHexString(byte[] block) {
		int len = block.length;
		StringBuilder buf = new StringBuilder(2 * len);
		for (int i = 0; i < len; ++i) {
			int high = ((block[i] & 0xf0) >> 4);
			int low = (block[i] & 0x0f);
			buf.append(HEX_CHARS[high]);
			buf.append(HEX_CHARS[low]);
		}
		return buf.toString();
	}

	/**
	 * Cleans up the given version string by removing extra dots(.), for
	 * example, 5.1.3 becomes 5.13, so that the string can be converted to a
	 * double or BigDecimal type easily.
	 * 
	 * @param version
	 *            string
	 * @return cleaned-up string that only contains the first dot(.) as the
	 *         floating point indicator.
	 */
	public static String removeExtraDotsFromVersionString(String version) {
		int floatingPointIndex = version.indexOf(".");
		if (floatingPointIndex >= 0 && floatingPointIndex + 1 < version.length()) {
			String minorVersion = StringUtils.replace(version.substring(floatingPointIndex + 1), ".", "");
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
	 * @param value
	 *            to be capitalized
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
	 * Uncapitalizes a given string.
	 * 
	 * @param value
	 *            to be uncapitalized
	 * @return capitalized string
	 */
	public static String uncapitalize(String value) {
		String capital = null;
		if (value != null) {
			int length = value.length();
			if (length > 0) {
				StringBuilder buffer = new StringBuilder(value);
				for (int i = 0; i < length; i++) {
					char ch = value.charAt(i);
					if (i == 0 || i == length - 1 || (i < length - 1 && Character.isUpperCase(value.charAt(i + 1)))) {
						buffer.setCharAt(i, Character.toLowerCase(ch));
					}
					else {
						break;
					}
				}
				capital = buffer.toString();
			}
		}
		return capital != null ? capital : value;
	}

	public static boolean stringEqualsString(String s1, String s2) {
		if (s1 == s2)
			return true;
		if (s1 != null && s2 != null && s1.equals(s2))
			return true;
		if (s1 == null && s2 == null)
			return true;
		return false;
	}

	/**
	 * Returns a string from the contents of the given URL.
	 * 
	 * @param url
	 *            the URL to read from
	 * @return the string that was read
	 * @throws IOException
	 *             if the connection fails
	 */
	public static String stringFromURL(URL url) throws IOException {
		try( InputStream is = url.openStream()) {
			return ERXStringUtilities.stringFromInputStream(is);
		}
	}

	/**
	 * Returns a string from the input stream using the default encoding.
	 * 
	 * @param in
	 *            stream to read
	 * @return string representation of the stream.
	 * @throws IOException
	 *             if things go wrong
	 */
	private static String stringFromInputStream(InputStream in) throws IOException {
		return new String(ERXFileUtilities.bytesFromInputStream(in));
	}

	/**
	 * Returns a String by invoking toString() on each object from the array.
	 * After each toString() call the separator is appended to the buffer.
	 * 
	 * @param array
	 *            an object array from which to get a nice String representation
	 * @param separator
	 *            a separator which is displayed between the objects toString()
	 *            value
	 *
	 * @return a string representation from the array
	 */
	public static String toString(Object[] array, String separator) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < array.length; i++) {
			Object o = array[i];
			buf.append(o.toString());
			buf.append(separator);
		}
		return buf.toString();
	}

	/**
	 * Generate an MD5 hash from a String.
	 *
	 * @param str
	 *            the string to hash
	 * @param encoding
	 *            MD5 operates on byte arrays, so we need to know the encoding
	 *            to getBytes as
	 * @return the MD5 sum of the bytes
	 */
	private static byte[] md5(String str, String encoding) {
		byte[] bytes;
		if (str == null) {
			bytes = new byte[0];
		}
		else {
			try {
				if (encoding == null) {
					encoding = "UTF-8";
				}
				bytes = md5(new ByteArrayInputStream(str.getBytes(encoding)));
			}
			catch (UnsupportedEncodingException e) {
				throw NSForwardException._runtimeExceptionForThrowable(e);
			}
			catch (IOException e) {
				throw NSForwardException._runtimeExceptionForThrowable(e);
			}
		}
		return bytes;
	}

	/**
	 * Generate an MD5 hash from an input stream.
	 *
	 * @param in
	 *            the input stream to sum
	 * @return the MD5 sum of the bytes in file
	 * @exception IOException
	 *                if the input stream could not be read
	 */
	private static byte[] md5(InputStream in) throws IOException {
		try {
			java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
			byte[] buf = new byte[50 * 1024];
			int numRead;

			while ((numRead = in.read(buf)) != -1) {
				md5.update(buf, 0, numRead);
			}
			return md5.digest();
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new NSForwardException(e);
		}
	}

	/**
	 * Generate an MD5 hash as hex from a String.
	 *
	 * @param str
	 *            the string to hash
	 * @param encoding
	 *            MD5 operates on byte arrays, so we need to know the encoding
	 *            to getBytes as
	 * @return the MD5 sum of the bytes in a hex string
	 */
	public static String md5Hex(String str, String encoding) {
		String hexStr;
		if (str == null) {
			hexStr = null;
		}
		else {
			hexStr = ERXStringUtilities.byteArrayToHexString(ERXStringUtilities.md5(str, encoding));
		}
		return hexStr;
	}

	/**
	 * Returns a string case-matched against the original string. For instance,
	 * if originalString is "Mike" and newString is "john", this returns "John".
	 * If originalString is "HTTP" and newString is "something", this returns
	 * "SOMETHING".
	 * 
	 * @param originalString
	 *            the original string to analyze the case of
	 * @param newString
	 *            the new string
	 * @return the case-matched variant of newString
	 */
	public static String matchCase(String originalString, String newString) {
		String matchedCase = newString;
		if (matchedCase != null) {
			int length = originalString.length();
			if (length > 0) {
				boolean uppercase = true;
				boolean lowercase = true;
				boolean capitalize = true;

				for (int i = 0; i < length; i++) {
					char ch = originalString.charAt(i);
					if (Character.isUpperCase(ch)) {
						lowercase = false;
						if (i > 0) {
							capitalize = false;
						}
					}
					else {
						uppercase = false;
						if (i == 0) {
							capitalize = false;
						}
					}
				}

				if (capitalize) {
					matchedCase = ERXStringUtilities.capitalize(newString);
				}
				else if (uppercase) {
					matchedCase = newString.toUpperCase();
				}
				else if (lowercase) {
					matchedCase = newString.toLowerCase();
				}
			}
		}
		return matchedCase;
	}

	public static void indent(PrintWriter writer, int level) {
		for (int i = 0; i < level; i++) {
			writer.append("  ");
		}
	}

	private static void indent(StringBuffer sb, int level) {
		for (int i = 0; i < level; i++) {
			sb.append("  ");
		}
	}

	private static void dumpArray(StringBuffer sb, NSArray array, int level) {
		sb.append("(\n");
		for (Enumeration e = array.objectEnumerator(); e.hasMoreElements();) {
			Object value = e.nextElement();
			dumpObject(sb, value, level + 1);
			sb.append(",\n");
		}
		indent(sb, level);
		sb.append(')');
	}

	private static void dumpDictionary(StringBuffer sb, NSDictionary dict, int level) {
		sb.append("{\n");
		for (Enumeration e = dict.keyEnumerator(); e.hasMoreElements();) {
			Object key = e.nextElement();
			Object value = dict.objectForKey(key);
			indent(sb, level + 1);
			sb.append(key).append(" = ");
			dumpObject(sb, value, level + 1);
			sb.append(";\n");
		}
		indent(sb, level);
		sb.append('}');
	}

	private static void dumpObject(StringBuffer sb, Object value, int level) {
		if (value instanceof NSDictionary) {
			dumpDictionary(sb, (NSDictionary) value, level);
		}
		else if (value instanceof NSArray) {
			dumpArray(sb, (NSArray) value, level);
		}
		else if (value instanceof NSData) {
			NSData data = (NSData) value;
			sb.append(byteArrayToHexString(data.bytes()));
		}
		else {
			indent(sb, level);
			sb.append(value);
		}
	}

	/**
	 * Same as NSPropertySerialization except it sorts on keys first.
	 * 
	 * @param dict
	 */
	public static String stringFromDictionary(NSDictionary dict) {
		NSArray orderedKeys = dict.allKeys();
		orderedKeys = ERXArrayUtilities.sortedArraySortedWithKey(orderedKeys, "toString.toLowerCase");
		StringBuilder result = new StringBuilder();
		for (Enumeration keys = orderedKeys.objectEnumerator(); keys.hasMoreElements();) {
			Object key = keys.nextElement();
			Object value = dict.objectForKey(key);
			String stringValue = NSPropertyListSerialization.stringFromPropertyList(value);
			String stringKey = NSPropertyListSerialization.stringFromPropertyList(key);
			if (!(value instanceof String)) {
				stringValue = stringValue.replaceAll("\n", "\n\t");
			}
			result.append('\t');
			result.append(stringKey);
			result.append(" = ");
			result.append(stringValue);
			result.append(";\n");
		}
		return "{\n" + result + "}\n";
	}

	/**
	 * It's ridiculous that StringBuffer doesn't have a .regionMatches like
	 * String. This is stolen from String and re-implemented on top of
	 * StringBuffer. It's slightly slower than String's because we have to call
	 * charAt instead of just accessing the underlying array, but so be it.
	 * 
	 * @param str
	 *            the StringBuffer to compare a region of
	 * @param toffset
	 *            the starting offset of the sub-region in this string.
	 * @param other
	 *            the string argument.
	 * @param ooffset
	 *            the starting offset of the sub-region in the string argument.
	 * @param len
	 *            the number of characters to compare.
	 * @return <code>true</code> if the specified sub-region of this string
	 *         exactly matches the specified sub-region of the string argument;
	 *         <code>false</code> otherwise.
	 */
	public static boolean regionMatches(StringBuffer str, int toffset, String other, int ooffset, int len) {
		int to = toffset;
		int po = ooffset;
		// Note: toffset, ooffset, or len might be near -1>>>1.
		int count = str.length();
		int otherCount = other.length();
		if ((ooffset < 0) || (toffset < 0) || (toffset > (long) count - len) || (ooffset > (long) otherCount - len)) {
			return false;
		}
		while (len-- > 0) {
			if (str.charAt(to++) != other.charAt(po++)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Converts source to be suitable for use as an identifier in JavaScript.
	 * prefix is prefixed to source if the first character of source is not
	 * suitable to start an identifier (e.g. a number). Any characters in source
	 * that are not allowed in an identifier are replaced with replacement.
	 * 
	 * @see Character#isJavaIdentifierStart(char)
	 * @see Character#isJavaIdentifierPart(char)
	 * 
	 * @param source
	 *            String to make into a identifier name
	 * @param prefix
	 *            String to prefix source with to make it a valid identifier
	 *            name
	 * @param replacement
	 *            character to use to replace characters in source that are no
	 *            allowed in an identifier name
	 * @return source converted to a name suitable for use as an identifier in
	 *         JavaScript
	 */
	private static String safeIdentifierName(String source, String prefix, char replacement) {
		StringBuilder b = new StringBuilder();
		// Add prefix if source does not start with valid character
		if (source == null || source.length() == 0 || !Character.isJavaIdentifierStart(source.charAt(0))) {
			b.append(prefix);
		}
		b.append(source);

		for (int i = 0; i < b.length(); i++) {
			char c = b.charAt(i);
			if (!Character.isJavaIdentifierPart(c)) {
				b.setCharAt(i, replacement);
			}
		}

		return b.toString();
	}

	/**
	 * Convenience method to call safeIdentifierName(source, "_", '_')
	 *
	 * @see #safeIdentifierName(String, String, char)
	 * 
	 * @param source
	 *            String to make into a identifier name
	 * @return source converted to a name suitable for use as an identifier in
	 *         JavaScript
	 */
	public static String safeIdentifierName(String source) {
		return safeIdentifierName(source, "_", '_');
	}

	/**
	 * Utility to encode an URL without the try/catch. Throws an
	 * NSForwardException in the unlikely case that
	 * ERXMessageEncoding.defaultEncoding() can't be found.
	 * 
	 * @param string
	 */
	public static String urlEncode(String string) {
		try {
			return URLEncoder.encode(string, ERXMessageEncoding.defaultEncoding());
		}
		catch (UnsupportedEncodingException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}

	/**
	 * Inserts the a string into another string at a particular offset.
	 * 
	 * @param destinationString
	 *            the string to insert into
	 * @param contentToInsert
	 *            the string to insert
	 * @param insertOffset
	 *            the offset in destinationString to insert
	 * @return the resulting string
	 */
	public static String insertString(String destinationString, String contentToInsert, int insertOffset) {
		String result;
		if (destinationString == null) {
			if (insertOffset > 0) {
				throw new IndexOutOfBoundsException("You attempted to insert '" + contentToInsert + "' into an empty string at the offset " + insertOffset + ".");
			}
			result = contentToInsert;
		}
		else {
			StringBuilder sb = new StringBuilder(destinationString.length() + contentToInsert.length());
			sb.append(destinationString.substring(0, insertOffset));
			sb.append(contentToInsert);
			sb.append(destinationString.substring(insertOffset));
			result = sb.toString();
		}
		return result;
	}

	/**
	 * Returns whether the given value falls in a range defined by the given
	 * string, which is in the format "1-5,100,500,800-1000".
	 * 
	 * @param value
	 *            the value to check for
	 * @param rangeString
	 *            the range string to parse
	 * @return whether or not the value falls within the given ranges
	 */
	public static boolean isValueInRange(int value, String rangeString) {
		boolean rangeMatches = false;
		if (rangeString != null && rangeString.length() > 0) {
			String[] ranges = rangeString.split(",");
			for (String range : ranges) {
				range = range.trim();
				int dashIndex = range.indexOf('-');
				if (dashIndex == -1) {
					int singleValue = Integer.parseInt(range);
					if (value == singleValue) {
						rangeMatches = true;
						break;
					}
				}
				else {
					int lowValue = Integer.parseInt(range.substring(0, dashIndex).trim());
					int highValue = Integer.parseInt(range.substring(dashIndex + 1).trim());
					if (value >= lowValue && value <= highValue) {
						rangeMatches = true;
						break;
					}
				}
			}
		}
		return rangeMatches;
	}

	public static boolean isBlank(String value) {
		boolean isBlank = false;
		if (value == null || value.trim().length() == 0) {
			isBlank = true;
		}
		return isBlank;
	}

	public static boolean isNotBlank(String value) {
		return !isBlank(value);
	}
}