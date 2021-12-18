package er.extensions.foundation;

public class ERXStringUtilities {

	/**
	 * @return The given string with the first letter set to uppercase
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
	 * @return true if [string] is null or empty
	 */
	public static boolean isNullOrEmpty( String string ) {
		return string == null || string.isEmpty();
	}
}