package er.extensions.components;

import java.util.Enumeration;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.foundation.ERXValueUtilities;

/**
 * ERXComponentUtilities contains WOComponent/WOElement-related utility methods.
 * 
 * @author mschrag
 */
public class ERXComponentUtilities {
	
	/**
	 * Returns a query parameter dictionary from a set of ?key=association
	 * WOAssociation dictionary.
	 * 
	 * @param associations
	 *            the set of associations
	 * @param component
	 *            the component to evaluate their values within
	 * @return a dictionary of key-value query parameters
	 */
	public static NSMutableDictionary queryParametersInComponent(NSDictionary associations, WOComponent component) {
		NSMutableDictionary queryParameterAssociations = ERXComponentUtilities.queryParameterAssociations(associations);
		return _queryParametersInComponent(queryParameterAssociations, component);
	}

	/**
	 * Returns a query parameter dictionary from a set of ?key=association
	 * WOAssociation dictionary.
	 * 
	 * @param associations
	 *            the set of associations
	 * @param component
	 *            the component to evaluate their values within
	 * @param removeQueryParametersAssociations
	 *            should the entries be removed from the passed-in dictionary?
	 * @return a dictionary of key-value query parameters
	 */
	public static NSMutableDictionary queryParametersInComponent(NSMutableDictionary associations, WOComponent component, boolean removeQueryParametersAssociations) {
		NSMutableDictionary queryParameterAssociations = ERXComponentUtilities.queryParameterAssociations(associations, removeQueryParametersAssociations);
		return _queryParametersInComponent(queryParameterAssociations, component);
	}

	public static NSMutableDictionary _queryParametersInComponent(NSMutableDictionary associations, WOComponent component) {
		NSMutableDictionary queryParameters = new NSMutableDictionary();
		Enumeration keyEnum = associations.keyEnumerator();
		while (keyEnum.hasMoreElements()) {
			String key = (String) keyEnum.nextElement();
			WOAssociation association = (WOAssociation) associations.valueForKey(key);
			Object associationValue = association.valueInComponent(component);
			if (associationValue != null) {
				queryParameters.setObjectForKey(associationValue, key.substring(1));
			}
		}
		return queryParameters;
	}

	/**
	 * Returns the set of ?key=value associations from an associations
	 * dictionary.
	 * 
	 * @param associations
	 *            the associations to enumerate
	 * @return dictionary with query parameter associations
	 */
	public static NSMutableDictionary<String, WOAssociation> queryParameterAssociations(NSDictionary<String, WOAssociation> associations) {
		return ERXComponentUtilities._queryParameterAssociations(associations, false);
	}

	/**
	 * Returns the set of ?key=value associations from an associations
	 * dictionary. If removeQueryParameterAssociations is <code>true</code>, the
	 * corresponding entries will be removed from the associations dictionary
	 * that was passed in.
	 * 
	 * @param associations
	 *            the associations to enumerate
	 * @param removeQueryParameterAssociations
	 *            should the entries be removed from the passed-in dictionary?
	 * @return dictionary with query parameter associations
	 */
	public static NSMutableDictionary<String, WOAssociation> queryParameterAssociations(NSMutableDictionary<String, WOAssociation> associations, boolean removeQueryParameterAssociations) {
		return ERXComponentUtilities._queryParameterAssociations(associations, removeQueryParameterAssociations);
	}

	public static NSMutableDictionary<String, WOAssociation> _queryParameterAssociations(NSDictionary<String, WOAssociation> associations, boolean removeQueryParameterAssociations) {
		NSMutableDictionary<String, WOAssociation> mutableAssociations = null;
		if (removeQueryParameterAssociations) {
			mutableAssociations = (NSMutableDictionary) associations;
		}
		NSMutableDictionary<String, WOAssociation> queryParameterAssociations = new NSMutableDictionary<>();
		Enumeration keyEnum = associations.keyEnumerator();
		while (keyEnum.hasMoreElements()) {
			String key = (String) keyEnum.nextElement();
			if (key.startsWith("?")) {
				WOAssociation association = (WOAssociation) associations.valueForKey(key);
				if (mutableAssociations != null) {
					mutableAssociations.removeObjectForKey(key);
				}
				queryParameterAssociations.setObjectForKey(association, key);
			}
		}
		return queryParameterAssociations;
	}

	/**
	 * Returns the boolean value of a binding.
	 * 
	 * @param component
	 *            the component
	 * @param bindingName
	 *            the name of the boolean binding
	 * @return a boolean
	 */
	public static boolean booleanValueForBinding(WOComponent component, String bindingName) {
		return ERXComponentUtilities.booleanValueForBinding(component, bindingName, false);
	}

	/**
	 * Returns the boolean value of a binding.
	 * 
	 * @param component
	 *            the component
	 * @param bindingName
	 *            the name of the boolean binding
	 * @param defaultValue
	 *            the default value if the binding is null
	 * @return a boolean
	 */
	public static boolean booleanValueForBinding(WOComponent component, String bindingName, boolean defaultValue) {
		if(component == null) {
			return defaultValue;
		}
		return ERXValueUtilities.booleanValueWithDefault(component.valueForBinding(bindingName), defaultValue);
	}

	/**
	 * Checks if there is an association for a binding with the given name.
	 * 
	 * @param name binding name
	 * @param associations array of associations
	 * @return <code>true</code> if the association exists
	 */
	public static boolean hasBinding(String name, NSDictionary<String, WOAssociation> associations) {
		return associations.objectForKey(name) != null;
	}
	
	/**
	 * Returns the association for a binding with the given name. If there is
	 * no such association <code>null</code> will be returned.
	 * 
	 * @param name binding name
	 * @param associations array of associations
	 * @return association for given binding or <code>null</code>
	 */
	public static WOAssociation bindingNamed(String name, NSDictionary<String, WOAssociation> associations) {
		return associations.objectForKey(name);
	}
	
	/**
	 * Checks if the association for a binding with the given name can assign
	 * values at runtime.
	 * 
	 * @param name binding name
	 * @param associations array of associations
	 * @return <code>true</code> if binding is settable
	 */
	public static boolean bindingIsSettable(String name, NSDictionary<String, WOAssociation> associations) {
		boolean isSettable = false;
		WOAssociation association = bindingNamed(name, associations);
		if (association != null) {
			isSettable = association.isValueSettable();
		}
		return isSettable;
	}
	
	/**
	 * Will try to set the given binding in the component to the passed value.
	 * 
	 * @param value new value for the binding
	 * @param name binding name
	 * @param associations array of associations
	 * @param component component to set the value in
	 */
	public static void setValueForBinding(Object value, String name, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		WOAssociation association = bindingNamed(name, associations);
		if (association != null) {
			association.setValue(value, component);
		}
	}
	
	/**
	 * Retrieves the current value of the given binding from the component. If there
	 * is no such binding or its value evaluates to <code>null</code> the default
	 * value will be returned.
	 * 
	 * @param name binding name
	 * @param defaultValue default value
	 * @param associations array of associations
	 * @param component component to get value from
	 * @return retrieved value or default value
	 */
	public static Object valueForBinding(String name, Object defaultValue, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		Object value = valueForBinding(name, associations, component);
		if (value != null) {
			return value;
		}
		return defaultValue;
	}
	
	/**
	 * Retrieves the current value of the given binding from the component. If there
	 * is no such binding <code>null</code> will be returned.
	 * 
	 * @param name binding name
	 * @param associations array of associations
	 * @param component component to get value from
	 * @return retrieved value or <code>null</code>
	 */
	public static Object valueForBinding(String name, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		WOAssociation association = bindingNamed(name, associations);
		if (association != null) {
			return association.valueInComponent(component);
		}
		return null;
	}
	
	/**
	 * Retrieves the current string value of the given binding from the component. If there
	 * is no such binding or its value evaluates to <code>null</code> the default
	 * value will be returned.
	 * 
	 * @param name binding name
	 * @param defaultValue default value
	 * @param associations array of associations
	 * @param component component to get value from
	 * @return retrieved string value or default value
	 */
	public static String stringValueForBinding(String name, String defaultValue, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		String value = stringValueForBinding(name, associations, component);
		if (value != null) {
			return value;
		}
		return defaultValue;
	}

	/**
	 * Retrieves the current string value of the given binding from the component. If there
	 * is no such binding <code>null</code> will be returned.
	 * 
	 * @param name binding name
	 * @param associations array of associations
	 * @param component component to get value from
	 * @return retrieved string value or <code>null</code>
	 */
	public static String stringValueForBinding(String name, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		WOAssociation association = bindingNamed(name, associations);
		if (association != null) {
			return (String) association.valueInComponent(component);
		}
		return null;
	}
	
	/**
	 * Retrieves the current boolean value of the given binding from the component. If there
	 * is no such binding the default value will be returned.
	 * 
	 * @param name binding name
	 * @param defaultValue default value
	 * @param associations array of associations
	 * @param component component to get value from
	 * @return retrieved boolean value or default value
	 */
	public static boolean booleanValueForBinding(String name, boolean defaultValue, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		WOAssociation association = bindingNamed(name, associations);
		if (association != null) {
			return association.booleanValueInComponent(component);
		}
		return defaultValue;
	}
	
	/**
	 * Retrieves the current boolean value of the given binding from the component. If there
	 * is no such binding <code>false</code> will be returned.
	 * 
	 * @param name binding name
	 * @param associations array of associations
	 * @param component component to get value from
	 * @return retrieved boolean value or <code>false</code>
	 */
	public static boolean booleanValueForBinding(String name, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		return booleanValueForBinding(name, false, associations, component);
	}
	
	/**
	 * Retrieves the current int value of the given binding from the component. If there
	 * is no such binding the default value will be returned.
	 * 
	 * @param name binding name
	 * @param defaultValue default value
	 * @param associations array of associations
	 * @param component component to get value from
	 * @return retrieved int value or default value
	 */
	public static int integerValueForBinding(String name, int defaultValue, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		WOAssociation association = bindingNamed(name, associations);
		if (association != null) {
			Object value = association.valueInComponent(component);
			return ERXValueUtilities.intValueWithDefault(value, defaultValue);
		}
		return defaultValue;
	}
	
	
	/**
	 * Retrieves the current array value of the given binding from the component. If there
	 * is no such binding or its value evaluates to <code>null</code> the default
	 * value will be returned.
	 * 
	 * @param name binding name
	 * @param defaultValue default value
	 * @param associations array of associations
	 * @param component component to get value from
	 * @return retrieved array value or default value
	 */
	public static <T> NSArray<T> arrayValueForBinding(String name, NSArray<T> defaultValue, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		WOAssociation association = bindingNamed(name, associations);
		if (association != null) {
			Object value = association.valueInComponent(component);
			return ERXValueUtilities.arrayValueWithDefault(value, defaultValue);
		}
		return defaultValue;
	}

	/**
	 * Retrieves the current array value of the given binding from the component. If there
	 * is no such binding <code>null</code> will be returned.
	 * 
	 * @param name binding name
	 * @param associations array of associations
	 * @param component component to get value from
	 * @return retrieved array value or <code>null</code>
	 */
	public static NSArray arrayValueForBinding(String name, NSDictionary<String, WOAssociation> associations, WOComponent component) {
		return arrayValueForBinding(name, null, associations, component);
	}
}
