package er.extensions.foundation;

import java.lang.reflect.Constructor;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
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

	/**
	 * Register a class by simple name in the _NSUtilities simple classname lookup (used for e.g. elements/components/directaction) 
	 */
	private static void replaceClass(Class replacementClass, Class classToReplace) {
		_NSUtilities.setClassForName(replacementClass, classToReplace.getSimpleName());
	}

	public static synchronized void installPatches() {
		replaceClass(DynamicElementsPatches.ActiveImage.class, WOActiveImage.class);
		replaceClass(DynamicElementsPatches.Browser.class, WOBrowser.class);
		replaceClass(DynamicElementsPatches.CheckBoxList.class, WOCheckBoxList.class);
		replaceClass(ERXWOForm.class, WOForm.class);
		replaceClass(DynamicElementsPatches.HiddenField.class, WOHiddenField.class);
		replaceClass(ERXWOHyperlink.class, WOHyperlink.class);
		replaceClass(DynamicElementsPatches.PasswordField.class, WOPasswordField.class);
		replaceClass(DynamicElementsPatches.PopUpButton.class, WOPopUpButton.class);
		replaceClass(DynamicElementsPatches.RadioButtonList.class, WORadioButtonList.class);
		replaceClass(ERXWORepetition.class, WORepetition.class);
		replaceClass(ERXWOString.class, WOString.class);
		replaceClass(DynamicElementsPatches.SubmitButton.class, WOSubmitButton.class);
		replaceClass(ERXWOSwitchComponent.class, WOSwitchComponent.class);
		replaceClass(DynamicElementsPatches.Text.class, WOText.class);
		replaceClass(ERXWOTextField.class, WOTextField.class);
	}

	public static class DynamicElementsPatches {

		private DynamicElementsPatches() {}

		public static class SubmitButton extends WOSubmitButton {

			public SubmitButton(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
			}

			/*
			 * logs the action name into session's dictionary with a key = ERXActionLogging
			 */
			@Override
			public WOActionResults invokeAction(WORequest arg0, WOContext arg1) {
				WOActionResults result = super.invokeAction(arg0, arg1);
				if (result != null && _action != null && ERXSession.anySession() != null) {
					ERXSession.anySession().setObjectForKey(toString(), "ERXActionLogging");
				}
				return result;
			}
		}

		public static class ActiveImage extends WOActiveImage {

			public ActiveImage(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
			}

			/*
			 * logs the action name into session's dictionary with a key = ERXActionLogging if log is set to debug.
			 */
			@Override
			public WOActionResults invokeAction(WORequest arg0, WOContext arg1) {
				WOActionResults result = super.invokeAction(arg0, arg1);
				if (result != null && ERXSession.anySession() != null) {
					ERXSession.anySession().setObjectForKey(toString(), "ERXActionLogging");
				}
				return result;
			}

		}

		public static class Text extends WOText {
			protected WOAssociation _readonly;

			public Text(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
				_readonly = _associations.removeObjectForKey("readonly");
			}

			@Override
			protected void _appendNameAttributeToResponse(WOResponse woresponse, WOContext wocontext) {
				super._appendNameAttributeToResponse(woresponse, wocontext);

				if (_readonly != null && _readonly.booleanValueInComponent(wocontext.component())) {
					woresponse._appendTagAttributeAndValue("readonly", "readonly", false);
				}
			}
			
			/**
			 * If readonly attribute is set to <code>true</code> prevent the takeValuesFromRequest.
			 */
			@Override
			public void takeValuesFromRequest(WORequest aRequest, WOContext wocontext) {
				WOComponent aComponent = wocontext.component();
				Boolean readOnly = false;
				if (_readonly != null) {
					readOnly = _readonly.booleanValueInComponent(aComponent);
				}
				if (!readOnly) {
					super.takeValuesFromRequest(aRequest, wocontext);
				}
			}
		}

		public static class HiddenField extends WOHiddenField {
			protected WOAssociation _readonly;

			public HiddenField(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
				_readonly = _associations.removeObjectForKey("readonly");
			}

			@Override
			protected void _appendNameAttributeToResponse(WOResponse woresponse, WOContext wocontext) {
				super._appendNameAttributeToResponse(woresponse, wocontext);

				if (_readonly != null && _readonly.booleanValueInComponent(wocontext.component())) {
					woresponse._appendTagAttributeAndValue("readonly", "readonly", false);
				}
			}
			
			/**
			 * If readonly attribute is set to <code>true</code> prevent the takeValuesFromRequest.
			 */
			@Override
			public void takeValuesFromRequest(WORequest aRequest, WOContext wocontext) {
				WOComponent aComponent = wocontext.component();
				Boolean readOnly = false;
				if (_readonly != null) {
					readOnly = _readonly.booleanValueInComponent(aComponent);
				}
				if (!readOnly) {
					super.takeValuesFromRequest(aRequest, wocontext);
				}
			}
		}

		public static class PasswordField extends WOPasswordField {
			protected WOAssociation _readonly;

			public PasswordField(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
				_readonly = _associations.removeObjectForKey("readonly");
			}

			@Override
			protected void _appendNameAttributeToResponse(WOResponse woresponse, WOContext wocontext) {
				super._appendNameAttributeToResponse(woresponse, wocontext);

				if (_readonly != null && _readonly.booleanValueInComponent(wocontext.component())) {
					woresponse._appendTagAttributeAndValue("readonly", "readonly", false);
				}
			}
			
			/**
			 * If readonly attribute is set to <code>true</code> prevent the takeValuesFromRequest.
			 */
			@Override
			public void takeValuesFromRequest(WORequest aRequest, WOContext wocontext) {
				WOComponent aComponent = wocontext.component();
				Boolean readOnly = false;
				if (_readonly != null) {
					readOnly = _readonly.booleanValueInComponent(aComponent);
				}
				if (!readOnly) {
					super.takeValuesFromRequest(aRequest, wocontext);
				}
			}
		}

		public static class PopUpButton extends WOPopUpButton {

			public PopUpButton(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
			}

			/* select element shouldn't worry about value attribute */
			@Override
			protected void _appendValueAttributeToResponse(WOResponse response, WOContext context) {}
			
			/**
			 * Overridden to stop swallowing all exceptions and properly handle
			 * listClassInContext(WOContext) returning an NSArray.
			 * 
			 * This method isn't actually used by WOPopUpButton, but just in case...
			 */
			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				if(_selections != null && _selections.isValueSettable()) {
					try {
						Class resultClass = listClassInContext(context);
						Object result = resultClass.newInstance();
						if(result instanceof NSMutableArray) {
							((NSMutableArray)result).addObjects(selections.toArray());
						} else if (result instanceof NSArray) {
							/*
							 * If "result" is an instanceof NSArray, we need to
							 * assign a new NSArray instance containing the
							 * contents of the "selections" parameter instead of
							 * calling addAll(Collection) on the existing
							 * instance because NSArray does not support it.
							 * 
							 * We are using reflection to do the assignment in
							 * case resultClass is actually a subclass of
							 * NSArray.
							 */
							Class nsArrayArgTypes[] = new Class[] {List.class, Boolean.TYPE};
							Constructor nsArrayConstructor = resultClass.getConstructor(nsArrayArgTypes);
							Object nsArrayConstructorArgs[] = new Object[] {selections, Boolean.TRUE};
							result = nsArrayConstructor.newInstance(nsArrayConstructorArgs);
						} else { 
							if(result instanceof List) {
								((List)result).addAll(selections);
							}
						}
						_selections.setValue(result, context.component());
                    } catch(Exception exception) {
                    	/*
                    	 * Don't ignore Exceptions like WOInputList does. Throw.
                    	 */
                    	throw NSForwardException._runtimeExceptionForThrowable(exception);
                    }
				}
			}
			
			/**
			 * Overridden to make the default return {@link Class} a
			 * NSMutableArray instead of NSArray.
			 * 
			 * @return a <b>mutable</b> Class that implements {@link List}
			 */
			@Override
			protected Class<List> listClassInContext(WOContext context) {
				Class aListClass = NSMutableArray.class;
				if (_list != null) {
					Object value = _list.valueInComponent(context.component());
					if (value instanceof NSArray)
						aListClass = NSMutableArray.class;
					else if (value instanceof List)
						aListClass = value.getClass();
				}
				return aListClass;
			}
		}

		public static class Browser extends WOBrowser {

			public Browser(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
			}
			
			/**
			 * Overridden to stop swallowing all exceptions and properly handle
			 * listClassInContext(WOContext) returning an NSArray.
			 */
			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				if(_selections != null && _selections.isValueSettable()) {
					try {
						Class resultClass = listClassInContext(context);
						Object result = resultClass.newInstance();
						if(result instanceof NSMutableArray) {
							((NSMutableArray)result).addObjects(selections.toArray());
						} else if (result instanceof NSArray) {
							/*
							 * If "result" is an instanceof NSArray, we need to
							 * assign a new NSArray instance containing the
							 * contents of the "selections" parameter instead of
							 * calling addAll(Collection) on the existing
							 * instance because NSArray does not support it.
							 * 
							 * We are using reflection to do the assignment in
							 * case resultClass is actually a subclass of
							 * NSArray.
							 */
							Class nsArrayArgTypes[] = new Class[] {List.class, Boolean.TYPE};
							Constructor nsArrayConstructor = resultClass.getConstructor(nsArrayArgTypes);
							Object nsArrayConstructorArgs[] = new Object[] {selections, Boolean.TRUE};
							result = nsArrayConstructor.newInstance(nsArrayConstructorArgs);
						} else { 
							if(result instanceof List) {
								((List)result).addAll(selections);
							}
						}
						_selections.setValue(result, context.component());
                    } catch(Exception exception) {
                    	/*
                    	 * Don't ignore Exceptions like WOInputList does. Throw.
                    	 */
                    	throw NSForwardException._runtimeExceptionForThrowable(exception);
                    }
				}
			}
			
			/**
			 * Overridden to make the default return {@link Class} a
			 * NSMutableArray instead of NSArray.
			 * 
			 * @return a <b>mutable</b> Class that implements {@link List}
			 */
			@Override
			protected Class<List> listClassInContext(WOContext context) {
				Class aListClass = NSMutableArray.class;
				if (_list != null) {
					Object value = _list.valueInComponent(context.component());
					if (value instanceof NSArray)
						aListClass = NSMutableArray.class;
					else if (value instanceof List)
						aListClass = value.getClass();
				}
				return aListClass;
			}
		}

		public static class CheckBoxList extends WOCheckBoxList {

			public CheckBoxList(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
			}
			
			/**
			 * Overridden to stop swallowing all exceptions and properly handle
			 * listClassInContext(WOContext) returning an NSArray.
			 */
			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				if(_selections != null && _selections.isValueSettable()) {
					try {
						Class resultClass = listClassInContext(context);
						Object result = resultClass.newInstance();
						if(result instanceof NSMutableArray) {
							((NSMutableArray)result).addObjects(selections.toArray());
						} else if (result instanceof NSArray) {
							/*
							 * If "result" is an instanceof NSArray, we need to
							 * assign a new NSArray instance containing the
							 * contents of the "selections" parameter instead of
							 * calling addAll(Collection) on the existing
							 * instance because NSArray does not support it.
							 * 
							 * We are using reflection to do the assignment in
							 * case resultClass is actually a subclass of
							 * NSArray.
							 */
							Class nsArrayArgTypes[] = new Class[] {List.class, Boolean.TYPE};
							Constructor nsArrayConstructor = resultClass.getConstructor(nsArrayArgTypes);
							Object nsArrayConstructorArgs[] = new Object[] {selections, Boolean.TRUE};
							result = nsArrayConstructor.newInstance(nsArrayConstructorArgs);
						} else { 
							if(result instanceof List) {
								((List)result).addAll(selections);
							}
						}
						_selections.setValue(result, context.component());
                    } catch(Exception exception) {
                    	/*
                    	 * Don't ignore Exceptions like WOInputList does. Throw.
                    	 */
                    	throw NSForwardException._runtimeExceptionForThrowable(exception);
                    }
				}
			}
			
			/**
			 * Overridden to make the default return {@link Class} a
			 * NSMutableArray instead of NSArray.
			 * 
			 * @return a <b>mutable</b> Class that implements {@link List}
			 */
			@Override
			protected Class<List> listClassInContext(WOContext context) {
				Class aListClass = NSMutableArray.class;
				if (_list != null) {
					Object value = _list.valueInComponent(context.component());
					if (value instanceof NSArray)
						aListClass = NSMutableArray.class;
					else if (value instanceof List)
						aListClass = value.getClass();
				}
				return aListClass;
			}
		}

		public static class RadioButtonList extends WORadioButtonList {

			public RadioButtonList(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
			}
			
			/**
			 * Overridden to stop swallowing all exceptions and properly handle
			 * listClassInContext(WOContext) returning an NSArray.
			 */
			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				if(_selections != null && _selections.isValueSettable()) {
					try {
						Class resultClass = listClassInContext(context);
						Object result = resultClass.newInstance();
						if(result instanceof NSMutableArray) {
							((NSMutableArray)result).addObjects(selections.toArray());
						} else if (result instanceof NSArray) {
							/*
							 * If "result" is an instanceof NSArray, we need to
							 * assign a new NSArray instance containing the
							 * contents of the "selections" parameter instead of
							 * calling addAll(Collection) on the existing
							 * instance because NSArray does not support it.
							 * 
							 * We are using reflection to do the assignment in
							 * case resultClass is actually a subclass of
							 * NSArray.
							 */
							Class nsArrayArgTypes[] = new Class[] {List.class, Boolean.TYPE};
							Constructor nsArrayConstructor = resultClass.getConstructor(nsArrayArgTypes);
							Object nsArrayConstructorArgs[] = new Object[] {selections, Boolean.TRUE};
							result = nsArrayConstructor.newInstance(nsArrayConstructorArgs);
						} else { 
							if(result instanceof List) {
								((List)result).addAll(selections);
							}
						}
						_selections.setValue(result, context.component());
                    } catch(Exception exception) {
                    	/*
                    	 * Don't ignore Exceptions like WOInputList does. Throw.
                    	 */
                    	throw NSForwardException._runtimeExceptionForThrowable(exception);
                    }
				}
			}
			
			/**
			 * Overridden to make the default return {@link Class} a
			 * NSMutableArray instead of NSArray.
			 * 
			 * @return a <b>mutable</b> Class that implements {@link List}
			 */
			@Override
			protected Class<List> listClassInContext(WOContext context) {
				Class aListClass = NSMutableArray.class;
				if (_list != null) {
					Object value = _list.valueInComponent(context.component());
					if (value instanceof NSArray)
						aListClass = NSMutableArray.class;
					else if (value instanceof List)
						aListClass = value.getClass();
				}
				return aListClass;
			}
		}
	}
}