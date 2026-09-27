package er.extensions.components.replacements;

import java.math.BigDecimal;
import java.text.Format;
import java.text.ParseException;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.appserver._private.WOInput;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSValidation;

import er.extensions.formatters.ERXNumberFormatter;
import er.extensions.formatters.ERXTimestampFormatter;

/**
 * Replacement for WOTextField, installed in its place (see parsley-tag-aliases.properties). Differences from WO's:
 * <ul>
 * <li>{@code dateformat} and {@code numberformat} formatters are created for the current locale ({@link er.extensions.appserver.ERXLocale}, when
 * the application or session sets one), where WO takes them from its shared formatter cache, in the JVM's default
 * locale. The locale governs parsing as well as display: "12.50" is 1250 where the comma is the decimal separator.</li>
 * <li>A {@code type} binding sets the input's type ({@code number}, {@code email}, ...). WO has no such binding: it
 * would pass {@code type} through as an attribute next to its own {@code type="text"}.</li>
 * <li>{@code readonly} is rendered as a boolean attribute and, when true, the submitted value isn't taken (see
 * ERXWOText for why WO's pass-through isn't enough).</li>
 * <li>{@code blankIsNull=false} keeps an empty string instead of turning it into null.</li>
 * </ul>
 *
 * @binding blankIsNull if false, "" will not be converted to null; if true, ""
 *          will be converted to null. Default is true.
 *
 * @author ak
 */

public class ERXWOTextField extends WOInput {

	protected WOAssociation _formatter;
	protected WOAssociation _dateFormat;
	protected WOAssociation _numberFormat;
	protected WOAssociation _useDecimalNumber;
	protected WOAssociation _blankIsNull;
	protected WOAssociation _readonly;
	protected WOAssociation _typeAss;

	public ERXWOTextField(String tagname, NSDictionary nsdictionary, WOElement woelement) {
		super("input", nsdictionary, woelement);

		if (_value == null || !_value.isValueSettable()) {
			throw new WODynamicElementCreationException("<" + getClass().getName() + "> 'value' attribute not present or is a constant");
		}

		_formatter = _associations.removeObjectForKey("formatter");
		_dateFormat = _associations.removeObjectForKey("dateformat");
		_numberFormat = _associations.removeObjectForKey("numberformat");
		_useDecimalNumber = _associations.removeObjectForKey("useDecimalNumber");
		_blankIsNull = _associations.removeObjectForKey("blankIsNull");
		_readonly = _associations.removeObjectForKey("readonly");
		_typeAss = _associations.removeObjectForKey("type");

		if (_dateFormat != null && _numberFormat != null) {
			throw new WODynamicElementCreationException("<" + getClass().getName() + "> Cannot have 'dateFormat' and 'numberFormat' attributes at the same time.");
		}
	}

	@Override
	public String type() {
		return "text";
	}

	/**
	 * Overridden to support supplying an overridden "type" attribute for the
	 * field
	 */
	@Override
	protected void _appendTypeAttributeToResponse(WOResponse response, WOContext context) {
		final String type;

		if (_typeAss != null) {
			type = (String) _typeAss.valueInComponent(context.component());
		}
		else {
			type = this.hiddenInContext(context) ? "hidden" : type();
		}

		if (type != null && type.length() > 0) {
			_appendTagAttributeAndValueToResponse(response, "type", type, false);
		}
	}

	protected boolean isReadonlyInContext(WOContext context) {
		return _readonly != null && _readonly.booleanValueInComponent(context.component());
	}

	@Override
	public void takeValuesFromRequest(WORequest worequest, WOContext wocontext) {
		final WOComponent component = wocontext.component();

		if (!isDisabledInContext(wocontext) && wocontext.wasFormSubmitted() && !isReadonlyInContext(wocontext)) {
			String name = nameInContext(wocontext, component);

			if (name != null) {
				String stringValue;

				boolean blankIsNull = _blankIsNull == null || _blankIsNull.booleanValueInComponent(component);

				if (blankIsNull) {
					stringValue = worequest.stringFormValueForKey(name);
				}
				else {
					Object objValue = worequest.formValueForKey(name);
					stringValue = (objValue == null) ? null : objValue.toString();
				}

				Object result = stringValue;

				if (stringValue != null) {
					Format format = null;

					if (stringValue.length() != 0) {
						if (_formatter != null) {
							format = (Format) _formatter.valueInComponent(component);
						}

						if (format == null) {
							if (_dateFormat != null) {
								String formatString = (String) _dateFormat.valueInComponent(component);

								if (formatString != null) {
									format = ERXTimestampFormatter.dateFormatterForPattern(formatString);
								}
							}
							else if (_numberFormat != null) {
								String formatString = (String) _numberFormat.valueInComponent(component);

								if (formatString != null) {
									format = ERXNumberFormatter.numberFormatterForPattern(formatString);
								}
							}
						}
					}

					if (format != null) {
						try {
							// Parse, format and parse again, as WO's WOTextField does, so what the formatter can't show
							// (digits beyond the pattern, say) is dropped from the value as it is from the field
							Object parsedObject = format.parseObject(stringValue);
							String reformatedObject = format.format(parsedObject);
							result = format.parseObject(reformatedObject);
						}
						catch (ParseException parseexception) {
							String keyPath = _value.keyPath();
							NSValidation.ValidationException validationexception = new NSValidation.ValidationException(parseexception.getMessage(), stringValue, keyPath);
							component.validationFailedWithException(validationexception, stringValue, keyPath);
							return;
						}

						if (result != null && _useDecimalNumber != null && _useDecimalNumber.booleanValueInComponent(component)) {
							result = new BigDecimal(result.toString());
						}
					}
					else if (blankIsNull && result.toString().length() == 0) {
						result = null;
					}
				}

				_value.setValue(result, component);
			}
		}
	}

	@Override
	protected void _appendValueAttributeToResponse(WOResponse woresponse, WOContext wocontext) {
		final WOComponent component = wocontext.component();

		Object valueInComponent = _value.valueInComponent(component);

		if (valueInComponent != null) {
			String stringValue = null;
			Format format = null;

			if (_formatter != null) {
				format = (Format) _formatter.valueInComponent(component);
			}

			if (format == null) {
				if (_dateFormat != null) {
					String formatString = (String) _dateFormat.valueInComponent(component);

					if (formatString != null) {
						format = ERXTimestampFormatter.dateFormatterForPattern(formatString);
					}
				}
				else if (_numberFormat != null) {
					String formatString = (String) _numberFormat.valueInComponent(component);

					if (formatString != null) {
						format = ERXNumberFormatter.numberFormatterForPattern(formatString);
					}
				}
			}

			if (format != null) {
				try {
					String formatedValue = format.format(valueInComponent);
					Object reparsedObject = format.parseObject(formatedValue);
					stringValue = format.format(reparsedObject);
				}
				catch (IllegalArgumentException illegalargumentexception) {
					NSLog._conditionallyLogPrivateException(illegalargumentexception);
					stringValue = null;
				}
				catch (ParseException parseexception) {
					NSLog._conditionallyLogPrivateException(parseexception);
				}
			}

			if (stringValue == null) {
				stringValue = valueInComponent.toString();
			}

			woresponse._appendTagAttributeAndValue("value", stringValue, true);
		}

		if (isReadonlyInContext(wocontext)) {
			woresponse._appendTagAttributeAndValue("readonly", "readonly", false);
		}
	}

	@Override
	protected void _appendCloseTagToResponse(WOResponse woresponse, WOContext wocontext) {
	}

	@Override
	public String toString() {
		StringBuilder stringbuffer = new StringBuilder();
		stringbuffer.append('<');
		stringbuffer.append(getClass().getName());
		stringbuffer.append(" formatter=");
		stringbuffer.append(_formatter);
		stringbuffer.append(" dateFormat=");
		stringbuffer.append(_dateFormat);
		stringbuffer.append(" numberFormat=");
		stringbuffer.append(_numberFormat);
		stringbuffer.append(" useDecimalNumber=");
		stringbuffer.append(_useDecimalNumber);
		stringbuffer.append('>');
		return stringbuffer.toString();
	}
}