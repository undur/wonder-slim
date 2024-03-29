package er.extensions.formatters;

import java.text.DateFormatSymbols;
import java.text.Format;
import java.util.Hashtable;
import java.util.Map;

import com.webobjects.foundation.NSTimestamp;
import com.webobjects.foundation.NSTimestampFormatter;

import er.extensions.localization.ERXLocalizer;

/**
 * Provides localization to timestamp formatters.
 */

public class ERXTimestampFormatter extends NSTimestampFormatter {

	/**
	 * Holds a reference to the repository
	 */
	private static final Map<String, NSTimestampFormatter> _repository = new Hashtable<>();

	protected static final String DefaultKey = "ERXTimestampFormatter.DefaultKey";

	/**
	 * The default pattern used in the UI
	 */
	
	public static final String DEFAULT_PATTERN = "%m/%d/%Y";

	static {
		_repository.put(DefaultKey, new ERXTimestampFormatter());
	}

	/**
	 * The default pattern used by WOString and friends when no pattern is set.
	 * Looks like this only for compatibility's sake.
	 */
	public static Format defaultDateFormatterForObject(Object object) {
		Format result = null;
		if (object != null && object instanceof NSTimestamp) {
			result = dateFormatterForPattern("%Y/%m/%d");
		}
		return result;
	}

	/**
	 * Returns a shared instance for the specified pattern.
	 * 
	 * @return shared instance of formatter
	 */
	public static NSTimestampFormatter dateFormatterForPattern(String pattern) {
		NSTimestampFormatter formatter;
		if (ERXLocalizer.useLocalizedFormatters()) {
			ERXLocalizer localizer = ERXLocalizer.currentLocalizer();
			formatter = (NSTimestampFormatter) localizer.localizedDateFormatForKey(pattern);
		}
		else {
			synchronized (_repository) {
				formatter = _repository.get(pattern);
				if (formatter == null) {
					formatter = new NSTimestampFormatter(pattern);
					_repository.put(pattern, formatter);
				}
			}
		}
		return formatter;
	}

	/**
	 * Sets a shared instance for the specified pattern.
	 */
	public static void setDateFormatterForPattern(NSTimestampFormatter formatter, String pattern) {
		if (ERXLocalizer.useLocalizedFormatters()) {
			ERXLocalizer localizer = ERXLocalizer.currentLocalizer();
			localizer.setLocalizedDateFormatForKey(formatter, pattern);
		}
		else {
			synchronized (_repository) {
				if (formatter == null) {
					_repository.remove(pattern);
				}
				else {
					_repository.put(pattern, formatter);
				}
			}
		}
	}

	
	public ERXTimestampFormatter() {
		super();
	}

	public ERXTimestampFormatter(String arg0) {
		super(arg0);
	}

	public ERXTimestampFormatter(String arg0, DateFormatSymbols arg1) {
		super(arg0, arg1);
	}
}