package er.extensions.foundation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCodingAdditions;

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

	/*
	 * FIXME: Isn't this just basically Objects.equals() ?
	 */
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
	 * Generate an MD5 hash from a String.
	 *
	 * @param str
	 *            the string to hash
	 * @param encoding
	 *            MD5 operates on byte arrays, so we need to know the encoding
	 *            to getBytes as
	 * @return the MD5 sum of the bytes
	 * 
	 * FIXME: Replace with standard Java methods
	 */
	@Deprecated
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
	 *                
	 * FIXME: Replace with Java methods
	 */
	@Deprecated
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
	 * 
	 * FIXME: Replace with Java methods
	 */
	@Deprecated
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
     * Convenience method to call safeIdentifierName(source, prefix, '_')
     * 
     * @see #safeIdentifierName(String, String, char)
     * 
     * @param source String to make into a identifier name
     * @param prefix String to prefix source with to make it a valid identifier name
     * @return source converted to a name suitable for use as an identifier in JavaScript
     */
    public static String safeIdentifierName(String source, String prefix) {
    	return safeIdentifierName(source, prefix, '_');

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