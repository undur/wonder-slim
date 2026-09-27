package er.extensions.components;

import java.text.Format;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WODynamicElement;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSKeyValueCoding;

import er.extensions.formatters.ERXDateTimeFormatters;
import er.extensions.formatters.ERXNumberFormatter;
import er.extensions.formatters.ERXTimestampFormatter;

/**
 * Reimplementation of WOString. Automatically patched in by ERXPatcher.
 */

public class ERXWOString extends WODynamicElement {

	private static final Logger log = LoggerFactory.getLogger(ERXWOString.class);

	/**
	 * The object to append to the response as a string.
	 */
	private final WOAssociation _value;

	/**
	 * Date format pattern. For an NSTimestamp, an NSTimestampFormatter (strftime-style) pattern, as always. For a java.time
	 * value, either that syntax ({@code %d.%m.%Y}, told apart by its {@code %}) or a DateTimeFormatter pattern
	 * ({@code dd.MM.yyyy}). See {@link ERXDateTimeFormatters}.
	 */
	private final WOAssociation _dateFormat;
	
	/**
	 * Format string to use with numbers (as specified by NSNumberFormatter)
	 */
	private final WOAssociation _numberFormat;
	
	/**
	 * A java.text.Format to format the value with, or a java.time.format.DateTimeFormatter for java.time values
	 */
	private final WOAssociation _formatter;
	
	/**
	 * Indicates if we'd like to escape HTML before appending to the response.
	 */
	private final WOAssociation _escapeHTML;
	
	/**
	 * The value to render in case the passed in object is null or an empty string.
	 */
	private final WOAssociation _valueWhenEmpty;

	/**
	 * Set in the constructor, just indicates if any of the formatter bindings are set.
	 */
	private final boolean  _shouldFormat;

	public ERXWOString(String name, NSDictionary associations, WOElement template) {
		super(null, null, null);
		_value = (WOAssociation) associations.objectForKey("value");

		// [value] is the only required binding
		if (_value == null) {
			throw new WODynamicElementCreationException("<" + getClass().getName() + "> ( no 'value' attribute specified.");
		}

		_valueWhenEmpty = (WOAssociation) associations.objectForKey("valueWhenEmpty");
		_escapeHTML = (WOAssociation) associations.objectForKey("escapeHTML");
		_dateFormat = (WOAssociation) associations.objectForKey("dateformat");
		_numberFormat = (WOAssociation) associations.objectForKey("numberformat");
		_formatter = (WOAssociation) associations.objectForKey("formatter");

		_shouldFormat = _dateFormat != null || _numberFormat != null || _formatter != null;

		if ((_dateFormat != null && _numberFormat != null) || (_formatter != null && _dateFormat != null) || (_formatter != null && _numberFormat != null)) {
			throw new WODynamicElementCreationException("<" + getClass().getName() + "> ( cannot have 'dateFormat' and 'numberFormat' or 'formatter' attributes at the same time.");
		}
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		final WOComponent component = context.component();
		Object valueInComponent = _value.valueInComponent(component);

		if (_shouldFormat) {
			if (valueInComponent == NSKeyValueCoding.NullValue) {
				valueInComponent = null;
			}

			// Nothing to format for a null value: it renders nothing, or valueWhenEmpty
			if (valueInComponent != null) {
				final Object formatter = _formatter != null ? _formatter.valueInComponent(component) : null;

				if (formatter instanceof DateTimeFormatter dateTimeFormatter) {
					// A java.time formatter, for java.time values. Anything else throws.
					valueInComponent = ERXDateTimeFormatters.format(valueInComponent, dateTimeFormatter);
				}
				else if (formatter == null && _dateFormat != null && valueInComponent instanceof TemporalAccessor) {
					// A java.time value with a dateformat pattern, in either syntax (see ERXDateTimeFormatters). A null pattern
					// leaves the value unformatted (its ISO toString()), since no single pattern suits every java.time type.
					final String pattern = (String) _dateFormat.valueInComponent(component);

					if (pattern != null) {
						valueInComponent = ERXDateTimeFormatters.format(valueInComponent, pattern);
					}
				}
				else {
					Format format = (Format) formatter;

					if (format == null) {
						if (_dateFormat != null) {
							final String formatString = (String) _dateFormat.valueInComponent(component);

							if (formatString == null) {
								format = ERXTimestampFormatter.defaultDateFormatterForObject(valueInComponent);
							}
							else {
								format = ERXTimestampFormatter.dateFormatterForPattern(formatString);
							}
						}
						else if (_numberFormat != null) {
							final String formatString = (String) _numberFormat.valueInComponent(component);

							if (formatString == null) {
								format = ERXNumberFormatter.defaultNumberFormatterForObject(valueInComponent);
							}
							else {
								format = ERXNumberFormatter.numberFormatterForPattern(formatString);
							}
						}
					}

					if (format != null) {
						try {
							valueInComponent = format.format(valueInComponent);
						}
						catch (IllegalArgumentException ex) {
							log.info("Exception while formatting", ex);
							valueInComponent = null;
						}
					}
					else {
						log.debug("no formatter found! {}", valueInComponent);
					}
				}
			}
		}

		String stringValue = null;

		if (valueInComponent != null) {
			stringValue = valueInComponent.toString();
		}

		if ((stringValue == null || stringValue.isEmpty()) && _valueWhenEmpty != null) {
			stringValue = (String) _valueWhenEmpty.valueInComponent(component);
			response.appendContentString(stringValue);
		}
		else if (stringValue != null) {
			boolean escapeHTML = true;

			if (_escapeHTML != null) {
				escapeHTML = _escapeHTML.booleanValueInComponent(component);
			}

			if (escapeHTML) {
				response.appendContentHTMLString(stringValue);
			}
			else {
				response.appendContentString(stringValue);
			}
		}
	}
}