package er.extensions.foundation;

@Deprecated
public class ERXStringUtilities {

	/**
	 * Cleans up the given version string by removing extra dots(.), for
	 * example, 5.1.3 becomes 5.13, so that the string can be converted to a
	 * double or BigDecimal type easily.
	 * 
	 * @param version string
	 * @return cleaned-up string that only contains the first dot(.) as the floating point indicator.
	 */
	public static String removeExtraDotsFromVersionString( String version ) {
		int floatingPointIndex = version.indexOf( "." );

		if( floatingPointIndex >= 0 && floatingPointIndex + 1 < version.length() ) {
			String minorVersion = version.substring( floatingPointIndex + 1 ).replace( ".", "" );
			version = version.substring( 0, floatingPointIndex + 1 ) + minorVersion;
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
	public static String capitalize( String value ) {
		String capital = null;

		if( value != null && value.length() > 0 ) {
			StringBuilder buffer = new StringBuilder( value );

			buffer.setCharAt( 0, Character.toUpperCase( value.charAt( 0 ) ) );
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
	public static boolean isNullOrEmpty( String string ) {
		return string == null || string.isEmpty();
	}
}