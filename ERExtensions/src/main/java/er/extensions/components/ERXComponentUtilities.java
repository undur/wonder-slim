package er.extensions.components;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.IntStream;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver._private.WOComponentDefinition;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
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
	 * Checks if the association for a binding with the given name can assign
	 * values at runtime.
	 * 
	 * @param name binding name
	 * @param associations array of associations
	 * @return <code>true</code> if binding is settable
	 */
	public static boolean bindingIsSettable(String name, NSDictionary<String, WOAssociation> associations) {
		boolean isSettable = false;
		WOAssociation association = associations.objectForKey(name);
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
		WOAssociation association = associations.objectForKey(name);
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
		WOAssociation association = associations.objectForKey(name);
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
		WOAssociation association = associations.objectForKey(name);
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
		WOAssociation association = associations.objectForKey(name);
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
		WOAssociation association = associations.objectForKey(name);
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
		WOAssociation association = associations.objectForKey(name);
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

	/**
	 * Constructs a component instance for embedding with ERXWOComponentInstance.
	 *
	 * This is what WOComponentReference does when it embeds a component by name - look up the component definition,
	 * construct an instance in the given context - and nothing more: the instance is NOT awakened and NOT flagged
	 * as a page. That is the state ERXWOComponentInstance expects to adopt it in (it awakens the instance itself).
	 * Don't use pageWithName() for this: it awakens the instance and marks it as a page, and adopting it then
	 * awakens it a second time in the same request.
	 *
	 * @param componentClass The component's class. Resolved by simple class name, as component names are
	 * @param context The context to construct the instance in - normally the constructing component's context()
	 * @return A new, not yet awakened instance of the component
	 */
	public static <T extends WOComponent> T instantiate( Class<T> componentClass, WOContext context ) {
		return componentClass.cast( instantiate( componentClass.getSimpleName(), context ) );
	}

	/**
	 * See {@link #instantiate(Class, WOContext)} - for a component known by name only.
	 */
	public static WOComponent instantiate( String componentName, WOContext context ) {
		final WOComponentDefinition definition = WOApplication.application()._componentDefinition( componentName, context._languages() );

		if( definition == null ) {
			throw new IllegalArgumentException( "No component named '%s' found".formatted( componentName ) );
		}

		return definition.componentInstanceInContext( context );
	}

	/**
	 * The {@code list} and {@code selections} handling of the list elements (the popup button, browser and checkbox
	 * list patches), in place of WO's.
	 */
	public static class InputLists {

		private InputLists() {}

		/**
		 * Pushes the selections to the {@code selections} binding as a mutable array, if it's bound and settable.
		 * Unlike WO's, errors aren't swallowed.
		 */
		public static void setSelectionListInContext( final WOContext context, final List selections, final WOAssociation selectionsAssociation ) {

			if( selectionsAssociation != null && selectionsAssociation.isValueSettable() ) {
				final List wrappedSelections = new NSMutableArray( selections );
				selectionsAssociation.setValue( wrappedSelections, context.component() );
			}
		}

		/**
		 * @return The value of the {@code list} binding as a List: any {@code java.util.List}, or a Java array of any
		 *         type. Null is an empty list.
		 * @throws IllegalArgumentException if the binding evaluates to anything else
		 */
		public static List listInContext( final WOContext context, final WOAssociation listAssociation ) {

			final Object bindingValue = listAssociation.valueInComponent( context.component() );

			if( bindingValue == null ) {
				return Collections.emptyList();
			}

			if( bindingValue instanceof List list ) {
				return list;
			}

			if( bindingValue.getClass().isArray() ) {
				// A little lengthy, but we need to go this way to ensure we're
				// handling arrays of any primitive type (not just Object[])
				final int length = Array.getLength( bindingValue );
				return IntStream.range( 0, length ).mapToObj( i -> Array.get( bindingValue, i ) ).toList();
			}

			throw new IllegalArgumentException( "[list] binding returned an object of class '%s'. We only support java.util.List and java arrays".formatted( bindingValue.getClass() ) );
		}
	}
}
