package er.extensions.localization;

import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSKeyValueCodingAdditions;

import er.extensions.components.ERXStatelessComponent;

/**
 * Examples:
 * <ol>
 * <li>value = "Localize me" -&gt; the localized value of "Localize me"</li>
 * <li>keyPath = "componentName" (note that the path must be a String) -&gt; localized name of the parent component</li>
 * <li>object = bug, an EO -&gt; localized version of bug.userPresentableDescription (may or may not be useful)</li>
 * <li>object = bug, keyPath = "state" -&gt;  localized version of the bugs state</li>
 * <li>templateString = "You have @assignedBugs.count@ Bug(s) assigned to you", object = session.user
 * -&gt; localized template is evaluated</li>
 * </ol>
 * 
 * Bindings:
 * @binding escapeHTML when <code>true</code> will escape the value
 * @binding keyPath the keyPath to get of the object which is to be localized
 * @binding object the object to derive the value of, if not given and keyPath is set, parent() is assumed
 * @binding omitWhenEmpty outputs an empty string if <code>true</code> when it would be <code>null</code>
 * @binding otherObject second object to use with templateString
 * @binding templateString the key to the template to evaluate with object and otherObject
 * @binding value string to localize
 * @binding valueWhenEmpty display this value if value evaluates to <code>null</code>. The binding <i>omitWhenEmpty</i> will prevent this.
 */

public class ERXLocalizedString extends ERXStatelessComponent {

    public ERXLocalizedString(WOContext context) {
        super(context);
    }

    private String objectToString(Object value) {
        String string = null;
        if(value != null) {
            if(value instanceof String)
                string = (String)value;
            else
                string = value.toString();
        }
        return string;
    }

    public Object object() {
        Object value;
        if(hasBinding("object"))
            value = valueForBinding("object");
        else
            value = parent();
        return value;
    }
    
    public String value() {
        ERXLocalizer localizer = ERXLocalizer.currentLocalizer();
        String stringToLocalize = null, localizedString = null;
        if(!hasBinding("templateString")) {
            if(hasBinding("object") || hasBinding("keyPath")) {
                Object value = object();
                if(hasBinding("keyPath"))
                    value = NSKeyValueCodingAdditions.Utility.valueForKeyPath(value, stringValueForBinding("keyPath"));
                stringToLocalize = objectToString(value);
            } else if(hasBinding("value")) {
            	stringToLocalize = stringValueForBinding("value");
            	if(booleanValueForBinding("omitWhenEmpty") && localizer.localizedStringForKey(stringToLocalize) == null) {
            		stringToLocalize = "";
            	}
            }
            if(stringToLocalize == null && hasBinding("valueWhenEmpty")) {
                stringToLocalize = stringValueForBinding("valueWhenEmpty");
            }
            if(stringToLocalize != null) {
                localizedString = localizer.localizedStringForKeyWithDefault(stringToLocalize);
            }
        } else {
        	String templateString = stringValueForBinding("templateString");
            Object otherObject = valueForBinding("otherObject");
        	localizedString = localizer.localizedTemplateStringForKeyWithObjectOtherObject(templateString, object(), otherObject);
        }
        return localizedString;
    }
}