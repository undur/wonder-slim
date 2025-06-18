package er.extensions.foundation;

import java.lang.reflect.Constructor;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WOActiveImage;
import com.webobjects.appserver._private.WOBrowser;
import com.webobjects.appserver._private.WOCheckBoxList;
import com.webobjects.appserver._private.WOForm;
import com.webobjects.appserver._private.WOHiddenField;
import com.webobjects.appserver._private.WOHyperlink;
import com.webobjects.appserver._private.WOPasswordField;
import com.webobjects.appserver._private.WOPopUpButton;
import com.webobjects.appserver._private.WORadioButtonList;
import com.webobjects.appserver._private.WORepetition;
import com.webobjects.appserver._private.WOString;
import com.webobjects.appserver._private.WOSubmitButton;
import com.webobjects.appserver._private.WOSwitchComponent;
import com.webobjects.appserver._private.WOText;
import com.webobjects.appserver._private.WOTextField;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation._NSUtilities;

import er.extensions.appserver.ERXSession;
import er.extensions.components._private.ERXWOForm;
import er.extensions.components._private.ERXWOHyperlink;
import er.extensions.components._private.ERXWORepetition;
import er.extensions.components._private.ERXWOString;
import er.extensions.components._private.ERXWOSwitchComponent;
import er.extensions.components._private.ERXWOTextField;

/**
 * Contains some of Wonder's patches for WO's built in dynamic elements  
 */

public class ERXPatcher {

	public static synchronized void installPatches() {
		replaceClass(WOActiveImage.class, DynamicElementsPatches.ActiveImage.class);
		replaceClass(WOBrowser.class, DynamicElementsPatches.Browser.class);
		replaceClass(WOCheckBoxList.class, DynamicElementsPatches.CheckBoxList.class);
		replaceClass(WOForm.class, ERXWOForm.class);
		replaceClass(WOHiddenField.class, DynamicElementsPatches.HiddenField.class);
		replaceClass(WOHyperlink.class, ERXWOHyperlink.class);
		replaceClass(WOPasswordField.class, DynamicElementsPatches.PasswordField.class);
		replaceClass(WOPopUpButton.class, DynamicElementsPatches.PopUpButton.class);
		replaceClass(WORadioButtonList.class, DynamicElementsPatches.RadioButtonList.class);
		replaceClass(WORepetition.class, ERXWORepetition.class);
		replaceClass(WOString.class, ERXWOString.class);
		replaceClass(WOSubmitButton.class, DynamicElementsPatches.SubmitButton.class);
		replaceClass(WOSwitchComponent.class, ERXWOSwitchComponent.class);
		replaceClass(WOText.class, DynamicElementsPatches.Text.class);
		replaceClass(WOTextField.class, ERXWOTextField.class);
	}

	/**
	 * Register a class by simple name in the _NSUtilities simple class name lookup (used for e.g. elements/components/directactions) 
	 */
	private static void replaceClass(final Class classToReplace, final Class replacementClass) {
		_NSUtilities.setClassForName(replacementClass, classToReplace.getSimpleName());
	}

	public static class DynamicElementsPatches {

		private DynamicElementsPatches() {}

		public static class SubmitButton extends WOSubmitButton {

			public SubmitButton(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
			}

			@Override
			public WOActionResults invokeAction(WORequest request, WOContext context) {
				final WOActionResults result = super.invokeAction(request, context);

				if (result != null && _action != null && ERXSession.anySession() != null) {
					ERXSession.anySession().setObjectForKey(toString(), "ERXActionLogging");
				}

				return result;
			}
		}

		public static class ActiveImage extends WOActiveImage {

			public ActiveImage(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
			}

			@Override
			public WOActionResults invokeAction(WORequest request, WOContext context) {
				final WOActionResults result = super.invokeAction(request, context);

				if (result != null && ERXSession.anySession() != null) {
					ERXSession.anySession().setObjectForKey(toString(), "ERXActionLogging");
				}

				return result;
			}
		}

		public static class Text extends WOText {
			protected WOAssociation _readonly;

			public Text(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
				_readonly = _associations.removeObjectForKey("readonly");
			}

			@Override
			protected void _appendNameAttributeToResponse(WOResponse response, WOContext context) {
				super._appendNameAttributeToResponse(response, context);

				if (_readonly != null && _readonly.booleanValueInComponent(context.component())) {
					response._appendTagAttributeAndValue("readonly", "readonly", false);
				}
			}
			
			/**
			 * If readonly attribute is set to <code>true</code> prevent the takeValuesFromRequest.
			 */
			@Override
			public void takeValuesFromRequest(WORequest request, WOContext context) {
				boolean readOnly = false;

				if (_readonly != null) {
					readOnly = _readonly.booleanValueInComponent(context.component());
				}

				if (!readOnly) {
					super.takeValuesFromRequest(request, context);
				}
			}
		}

		public static class HiddenField extends WOHiddenField {
			protected WOAssociation _readonly;

			public HiddenField(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
				_readonly = _associations.removeObjectForKey("readonly");
			}

			@Override
			protected void _appendNameAttributeToResponse(WOResponse response, WOContext context) {
				super._appendNameAttributeToResponse(response, context);

				if (_readonly != null && _readonly.booleanValueInComponent(context.component())) {
					response._appendTagAttributeAndValue("readonly", "readonly", false);
				}
			}
			
			/**
			 * If readonly attribute is set to <code>true</code> prevent the takeValuesFromRequest.
			 */
			@Override
			public void takeValuesFromRequest(WORequest request, WOContext context) {
				boolean readOnly = false;

				if (_readonly != null) {
					readOnly = _readonly.booleanValueInComponent(context.component());
				}

				if (!readOnly) {
					super.takeValuesFromRequest(request, context);
				}
			}
		}

		public static class PasswordField extends WOPasswordField {
			protected WOAssociation _readonly;

			public PasswordField(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
				_readonly = _associations.removeObjectForKey("readonly");
			}

			@Override
			protected void _appendNameAttributeToResponse(WOResponse response, WOContext context) {
				super._appendNameAttributeToResponse(response, context);

				if (_readonly != null && _readonly.booleanValueInComponent(context.component())) {
					response._appendTagAttributeAndValue("readonly", "readonly", false);
				}
			}
			
			/**
			 * If readonly attribute is set to <code>true</code> prevent the takeValuesFromRequest.
			 */
			@Override
			public void takeValuesFromRequest(WORequest request, WOContext context) {
				boolean readOnly = false;

				if (_readonly != null) {
					readOnly = _readonly.booleanValueInComponent(context.component());
				}

				if (!readOnly) {
					super.takeValuesFromRequest(request, context);
				}
			}
		}

		public static class PopUpButton extends WOPopUpButton {

			public PopUpButton(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
			}

			/**
			 * select element shouldn't worry about value attribute
			 * 
			 * FIXME: We should really look into this and explain better // Hugi 2025-06-16
			 */
			@Override
			protected void _appendValueAttributeToResponse(WOResponse response, WOContext context) {}
			
			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				ERXWOInputListPatch.setSelectionListInContext(context, selections, _selections, _list );
			}
		}

		public static class Browser extends WOBrowser {

			public Browser(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
			}
			
			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				ERXWOInputListPatch.setSelectionListInContext(context, selections, _selections, _list );
			}
		}

		public static class CheckBoxList extends WOCheckBoxList {

			public CheckBoxList(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
			}
			
			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				ERXWOInputListPatch.setSelectionListInContext(context, selections, _selections, _list );
			}
		}

		public static class RadioButtonList extends WORadioButtonList {

			public RadioButtonList(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
			}
			
			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				ERXWOInputListPatch.setSelectionListInContext(context, selections, _selections, _list );
			}
		}
	}
	
	/**
	 * Patch methods for WOInputList's subclasses, adding support for java collections  
	 */
	private static class ERXWOInputListPatch {

		/**
		 * Overridden to stop swallowing all exceptions and properly handle listClassInContext(WOContext) returning an NSArray.
		 * 
		 * FIXME: This entire functionality needs some refactoring, and probably just re-thinking in general, // Hugi 2025-06-18
		 */
		private static void setSelectionListInContext(final WOContext context, final List selections, final WOAssociation selectionsAssociation, final WOAssociation listAssociation ) {

			if(selectionsAssociation != null && selectionsAssociation.isValueSettable()) {
				try {
					final Class resultClass = listClassInContext(context, listAssociation);
					Object result = resultClass.newInstance();

					if(result instanceof NSMutableArray) {
						((NSMutableArray)result).addObjects(selections.toArray());
					}
					else if (result instanceof NSArray) {
						// If "result" is an instanceof NSArray, we need to assign a new NSArray instance containing the contents
						// of the "selections" parameter instead of calling addAll(Collection) on the existing instance.
						// We use reflection to do the assignment in case resultClass is actually a subclass of NSArray.
						final Class nsArrayArgTypes[] = new Class[] {List.class, Boolean.TYPE};
						final Constructor nsArrayConstructor = resultClass.getConstructor(nsArrayArgTypes);
						final Object nsArrayConstructorArgs[] = new Object[] {selections, Boolean.TRUE};
						result = nsArrayConstructor.newInstance(nsArrayConstructorArgs);
					}
					else { 
						if(result instanceof List) {
							((List)result).addAll(selections);
						}
					}
					selectionsAssociation.setValue(result, context.component());
				}
				catch(Exception exception) {
					throw NSForwardException._runtimeExceptionForThrowable(exception); // WOInputList ignores exceptions. We throw. Like real men.
				}
			}
		}
		
		/**
		 * Overridden to return an NSMutableArray by default 8rather than an NSArray)
		 * 
		 * @return A <b>mutable</b> Class that implements {@link List}
		 */
		private static Class<List> listClassInContext(final WOContext context, final WOAssociation _list ) {
			Class listClass = NSMutableArray.class;

			if (_list != null) {
				final Object value = _list.valueInComponent(context.component());

				if (value instanceof NSArray) {
					listClass = NSMutableArray.class;
				}
				else if (value instanceof List) {
					listClass = value.getClass();
				}
			}

			return listClass;
		}
	}
}