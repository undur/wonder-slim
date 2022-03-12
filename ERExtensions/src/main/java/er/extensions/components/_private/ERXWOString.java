package er.extensions.components._private;

import java.text.Format;

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
	 * Format string to use for formatting NSTimestamp dates.
	 * 
	 * FIXME: This binding is essentially obsolete since it's only meant for use with NSTimestamp, which sucks and is dead // Hugi 2022-03-12
	 */
	private final WOAssociation _dateFormat;
	
	/**
	 * Format string to use with numbers (as specified by NSNumberFormatter)
	 */
	private final WOAssociation _numberFormat;
	
	/**
	 * An instance of java.util.format to use to format the passed {value]
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
	 * Introduced as a performance enhancement to check.
	 * 
	 * FIXME: Shouldn't this just be a local variable inside appendToResponse()? Or is there performance to be gained from using this here? I.e. is the DynamicElement instance reused? // Hugi 2022-03-12 
	 */
	private final boolean  _shouldFormat;

	public ERXWOString(String s, NSDictionary nsdictionary, WOElement woelement) {
		super(null, null, null);
		_value = (WOAssociation) nsdictionary.objectForKey("value");

		// [value] is the only required binding
		if (_value == null) {
			throw new WODynamicElementCreationException("<" + getClass().getName() + "> ( no 'value' attribute specified.");
		}

		_valueWhenEmpty = (WOAssociation) nsdictionary.objectForKey("valueWhenEmpty");
		_escapeHTML = (WOAssociation) nsdictionary.objectForKey("escapeHTML");
		_dateFormat = (WOAssociation) nsdictionary.objectForKey("dateformat");
		_numberFormat = (WOAssociation) nsdictionary.objectForKey("numberformat");
		_formatter = (WOAssociation) nsdictionary.objectForKey("formatter");

		_shouldFormat = _dateFormat != null || _numberFormat != null || _formatter != null;

		if ((_dateFormat != null && _numberFormat != null) || (_formatter != null && _dateFormat != null) || (_formatter != null && _numberFormat != null)) {
			throw new WODynamicElementCreationException("<" + getClass().getName() + "> ( cannot have 'dateFormat' and 'numberFormat' or 'formatter' attributes at the same time.");
		}
	}

	@Override
	public void appendToResponse(WOResponse woresponse, WOContext wocontext) {
		final WOComponent component = wocontext.component();
		Object valueInComponent = null;

		if (_value != null) {
			valueInComponent = _value.valueInComponent(component);

			if (_shouldFormat) {
				Format format = null;

				if (_formatter != null) {
					format = (Format) _formatter.valueInComponent(component);
				}

				if (format == null) {
					if (_dateFormat != null) {
						String formatString = (String) _dateFormat.valueInComponent(component);
						if (formatString == null) {
							format = ERXTimestampFormatter.defaultDateFormatterForObject(formatString);
						}
						else {
							format = ERXTimestampFormatter.dateFormatterForPattern(formatString);
						}
					}
					else if (_numberFormat != null) {
						String formatString = (String) _numberFormat.valueInComponent(component);
						if (formatString == null) {
							format = ERXNumberFormatter.defaultNumberFormatterForObject(valueInComponent);
						}
						else {
							format = ERXNumberFormatter.numberFormatterForPattern(formatString);
						}
					}
				}

				if (valueInComponent == NSKeyValueCoding.NullValue) {
					valueInComponent = null;
				}

				if (format != null) {
					if (valueInComponent != null) {
						try {
							valueInComponent = format.format(valueInComponent);
						}
						catch (IllegalArgumentException ex) {
							log.info("Exception while formatting", ex);
							valueInComponent = null;
						}
					}
				}
				else {
					if (valueInComponent != null) {
						log.debug("no formatter found! {}", valueInComponent);
					}
				}
			}
		}
		else {
			// FIXME: Why are we logging this? The binding is checked at element construction time // Hugi 2022-03-12
			log.warn("value binding is null !");
		}

		String stringValue = null;

		if (valueInComponent != null) {
			stringValue = valueInComponent.toString();
		}

		if ((stringValue == null || stringValue.length() == 0) && _valueWhenEmpty != null) {
			stringValue = (String) _valueWhenEmpty.valueInComponent(component);
			woresponse.appendContentString(stringValue);
		}
		else if (stringValue != null) {
			boolean escapeHTML = true;

			if (_escapeHTML != null) {
				escapeHTML = _escapeHTML.booleanValueInComponent(component);
			}

			if (escapeHTML) {
				woresponse.appendContentHTMLString(stringValue);
			}
			else {
				woresponse.appendContentString(stringValue);
			}
		}
	}
}