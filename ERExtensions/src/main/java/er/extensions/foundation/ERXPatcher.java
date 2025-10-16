package er.extensions.foundation;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

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
import com.webobjects.appserver._private.WORepetition;
import com.webobjects.appserver._private.WOString;
import com.webobjects.appserver._private.WOSubmitButton;
import com.webobjects.appserver._private.WOSwitchComponent;
import com.webobjects.appserver._private.WOText;
import com.webobjects.appserver._private.WOTextField;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation._NSUtilities;

import er.extensions.appserver.ERXSession;
import er.extensions.components.ERXWOForm;
import er.extensions.components.ERXWOHyperlink;
import er.extensions.components.ERXWORepetition;
import er.extensions.components.ERXWOString;
import er.extensions.components.ERXWOSwitchComponent;
import er.extensions.components.ERXWOTextField;

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
			 * FIXME: We should really look into what this does and document it a little better // Hugi 2025-06-16
			 */
			@Override
			protected void _appendValueAttributeToResponse(WOResponse response, WOContext context) {}

			@Override
			protected List listInContext(WOContext context) {
				return ERXWOInputListPatch.listInContext(context, _list);
			}
		}

		public static class Browser extends WOBrowser {

			public Browser(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
			}

			@Override
			protected List listInContext(WOContext context) {
				return ERXWOInputListPatch.listInContext(context, _list);
			}

			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				ERXWOInputListPatch.setSelectionListInContext(context, selections, _selections);
			}
		}

		public static class CheckBoxList extends WOCheckBoxList {

			public CheckBoxList(String name, NSDictionary associations, WOElement element) {
				super(name, associations, element);
			}
			
			@Override
			protected List listInContext(WOContext context) {
				return ERXWOInputListPatch.listInContext(context, _list);
			}

			@Override
			protected void setSelectionListInContext(WOContext context, List selections) {
				ERXWOInputListPatch.setSelectionListInContext(context, selections, _selections);
			}
		}
	}

	/**
	 * Contains fixes applicable to the subclasses of WOInputList
	 */
	private static class ERXWOInputListPatch {

		/**
		 * Overridden to:
		 * 
		 * - improve creation of the value that gets pushed to the "selections" binding
		 * - not swallow exceptions
		 */
		private static void setSelectionListInContext(final WOContext context, final List selections, final WOAssociation selectionsAssociation ) {

			if(selectionsAssociation != null && selectionsAssociation.isValueSettable()) {
				final List wrappedSelections = new NSMutableArray(selections);
				selectionsAssociation.setValue(wrappedSelections, context.component());
			}
		}
		
		/**
		 * Overridden to:
		 * 
		 * - add support for java arrays
		 * - throw an exception if [list] is bound to an unknown/unhandled type
		 */
		private static List listInContext( final WOContext context, final WOAssociation listAssociation ) {

			final Object bindingValue = listAssociation.valueInComponent(context.component());

			if( bindingValue == null ) {
				return Collections.emptyList();
			}

			if (bindingValue instanceof List list ) {
				return list;
			}
			
			if( bindingValue.getClass().isArray() ) {
				// A little lengthy, but we need to go this way to ensure we're handling arrays of any primitive type (not just Object[])
				final int length = Array.getLength(bindingValue);
		        return IntStream
		        		.range(0, length)
		                .mapToObj(i -> Array.get(bindingValue, i))
		                .toList();
			}
			
			throw new IllegalArgumentException( "[list] binding returned an object of class '%s'. We only support java.util.List and java arrays".formatted(bindingValue.getClass()) );
		}
	}
}