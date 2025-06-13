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
import com.webobjects.appserver._private.WOConstantValueAssociation;
import com.webobjects.appserver._private.WOHiddenField;
import com.webobjects.appserver._private.WOJavaScript;
import com.webobjects.appserver._private.WOPasswordField;
import com.webobjects.appserver._private.WOPopUpButton;
import com.webobjects.appserver._private.WORadioButtonList;
import com.webobjects.appserver._private.WOResetButton;
import com.webobjects.appserver._private.WOSubmitButton;
import com.webobjects.appserver._private.WOText;
import com.webobjects.appserver._private.WOTextField;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation._NSUtilities;

import er.extensions.appserver.ERXSession;
import er.extensions.components._private.ERXWOHyperlink;
import er.extensions.components._private.ERXWOSwitchComponent;

/**
 * Wrapper around the WO-private NSUtilities which allows for some Objective-C-Style poseAs. Using these methods may or may not break in the future.
 */

public class ERXPatcher {

	/**
	 * Sets the class registered for the name <code>className</code> to the given class.
	 * Changes the private WebObjects class cache.
	 * 
	 * @param clazz class object
	 * @param className name for the class - normally clazz.getName()
	 */
	public static void setClassForName(Class clazz, String className) {
		_NSUtilities.setClassForName(clazz, className);
	}

	public static synchronized void installPatches() {
		ERXPatcher.setClassForName(DynamicElementsPatches.ActiveImage.class, "WOActiveImage");
		ERXPatcher.setClassForName(DynamicElementsPatches.Browser.class, "WOBrowser");
		ERXPatcher.setClassForName(DynamicElementsPatches.CheckBoxList.class, "WOCheckBoxList");
		ERXPatcher.setClassForName(DynamicElementsPatches.HiddenField.class, "WOHiddenField");
		ERXPatcher.setClassForName(ERXWOHyperlink.class, "WOHyperlink");
		ERXPatcher.setClassForName(DynamicElementsPatches.JavaScript.class, "WOJavaScript");
		ERXPatcher.setClassForName(DynamicElementsPatches.PasswordField.class, "WOPasswordField");
		ERXPatcher.setClassForName(DynamicElementsPatches.PopUpButton.class, "WOPopUpButton");
		ERXPatcher.setClassForName(DynamicElementsPatches.RadioButtonList.class, "WORadioButtonList");
		ERXPatcher.setClassForName(DynamicElementsPatches.ResetButton.class, "WOResetButton");
		ERXPatcher.setClassForName(DynamicElementsPatches.SubmitButton.class, "WOSubmitButton");
		ERXPatcher.setClassForName(ERXWOSwitchComponent.class, "WOSwitchComponent");
		ERXPatcher.setClassForName(DynamicElementsPatches.Text.class, "WOText");
		ERXPatcher.setClassForName(DynamicElementsPatches.TextField.class, "WOTextField");
	}

	/**
	 * This class holds patches for WebObjects dynamic elements, which have always a closing tag and all attribute
	 * values are enclosed in quotes. The patches are automatically registered if this framework gets loaded.
	 * <p>
	 * <b>Note</b>: <code>WOForm</code> is not replaced, because it is ok if you don't use <code>?</code>-bindings.
	 * If you need additional parameters, just insert <code>WOHiddenField</code>s.
	 * <p>
	 * Also <code>WOJavaScript</code> is not replaced, even if it is not XHTML-conform.
	 */
	public static class DynamicElementsPatches {
		private static final boolean suppressValueBindingSlow = false;
		
		private DynamicElementsPatches() {}

		public static class SubmitButton extends WOSubmitButton {

			public SubmitButton(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
			}
			
			protected String _valueStringInContext(WOContext context) {
				String valueString = null;
				Object value = _value.valueInComponent(context.component());
				if (value != null) {
					valueString = value.toString();
				}
				return valueString;
			}

			/**
			 * Appends the attribute "value" to the response. First tries to get a localized version and if that fails,
			 * uses the supplied value as the default
			 */
			@Override
			protected void _appendValueAttributeToResponse(WOResponse response, WOContext context) {
				if (_value != null) {
					String valueString = _valueStringInContext(context);
					if (valueString != null) {
						// stringValue = ERXLocalizer.currentLocalizer().localizedStringForKeyWithDefault(stringValue);
						response._appendTagAttributeAndValue("value", valueString, escapeHTMLInContext(context));
					}
				}
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

		public static class ResetButton extends WOResetButton {

			public ResetButton(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
			}

			/**
			 * Appends the attribute "value" to the response. First tries to get a localized version and if that fails,
			 * uses the supplied value as the default
			 */
			@Override
			protected void _appendValueAttributeToResponse(WOResponse response, WOContext context) {
				if (_value != null) {
					Object object = _value.valueInComponent(context.component());
					if (object != null) {
						String string = object.toString();
						// string = ERXLocalizer.currentLocalizer().localizedStringForKeyWithDefault(string);
						response._appendTagAttributeAndValue("value", string, escapeHTMLInContext(context));
					}
				}
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

		public static class TextField extends WOTextField {
			protected WOAssociation _readonly;

			public TextField(String aName, NSDictionary associations, WOElement element) {
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

		public static class PopUpButton extends WOPopUpButton {

			public PopUpButton(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
				_loggedSlow = suppressValueBindingSlow;
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
				_loggedSlow = suppressValueBindingSlow;
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
		
		public static class JavaScript extends WOJavaScript {
			private WOAssociation _language;
			
			public JavaScript(String aName, NSDictionary associations, WOElement element) {
				super(aName, associations, element);
				if (_language == null) {
					_language = (WOAssociation) associations.objectForKey("language");
				}
			}
			
			@Override
			protected void setLanguage(String s) {
				super.setLanguage(s);
				if (s != null) {
					_language = new WOConstantValueAssociation(s);
				}
			}
		}
	}
}