package er.extensions.formatters;

import java.text.DateFormatSymbols;
import java.text.Format;
import java.util.Locale;

import com.webobjects.foundation.NSTimestamp;
import com.webobjects.foundation.NSTimestampFormatter;

import er.extensions.appserver.ERXLocale;

/**
 * Timestamp formatters for a pattern, in the current formatting locale ({@link ERXLocale}).
 */

public class ERXTimestampFormatter extends NSTimestampFormatter {

	/**
	 * The default pattern used in the UI
	 */
	
	public static final String DEFAULT_PATTERN = "%m/%d/%Y";

	public ERXTimestampFormatter() {
		super();
	}

	public ERXTimestampFormatter(String pattern) {
		super(pattern);
	}

	public ERXTimestampFormatter(String pattern, DateFormatSymbols symbols) {
		super(pattern, symbols);
	}

	/**
	 * The default pattern used by WOString and friends when no pattern is set.
	 * Looks like this only for compatibility's sake.
	 */
	public static Format defaultDateFormatterForObject(Object object) {

		if (object instanceof NSTimestamp) {
			return dateFormatterForPattern("%Y/%m/%d");
		}

		return null;
	}

	/**
	 * A new formatter for the given pattern, in the current locale ({@link ERXLocale#current()}); with no locale
	 * configured, created exactly as {@code new NSTimestampFormatter(pattern)}, as it always was. A new instance every
	 * time: formatters are not thread-safe, so they are never shared, and creating one is cheap.
	 *
	 * @return A formatter for the pattern, owned by the caller
	 */
	public static NSTimestampFormatter dateFormatterForPattern(String pattern) {
		final Locale locale = ERXLocale.current();
		return locale == null ? new NSTimestampFormatter(pattern) : new NSTimestampFormatter(pattern, new DateFormatSymbols(locale));
	}
}