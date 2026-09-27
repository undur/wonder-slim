//
// ERXWOForm.java
// Project armehaut
//
// Created by ak on Mon Apr 01 2002
//

package er.extensions.components.replacements;

import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WOConstantValueAssociation;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSKeyValueCoding.UnknownKeyException;
import com.webobjects.foundation._NSDictionaryUtilities;

import er.extensions.ERXP;
import er.extensions.appserver.ERXWOContext;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.foundation.ERXProperties;

/**
 * Replacement for WOForm, installed in its place (see parsley-tag-aliases.properties). Like WO's, it omits the tags of
 * a form nested in another, can add an off-screen default submit button, and defaults the method to post. Differences
 * from WO's:
 * <ul>
 * <li>Every form has a name: an unbound {@code name} gets "f" + the element ID, so elements inside can address the
 * form. WO renders a name only when one is bound. The name is published in ERXWOContext's contextDictionary() as
 * "formName" (WO publishes a bound one in the context's userInfo), see {@link #formName(WOContext, String)}, which
 * makes writing JavaScript elements a bit easier.</li>
 * <li>A nested form is omitted with a logged warning, unless it's bound {@code embedded} (a form in an Ajax modal
 * container, say, that's nested in the page's structure but independent to the user).</li>
 * <li>{@code disabled} omits the form tags.</li>
 * <li>{@code fragmentIdentifier} appends "#" + its value to the action. The obvious case comes when you have a form
 * at the bottom of the page and want to jump to the error messages if there are any.</li>
 * <li>The defaults of {@code multipleSubmit} and {@code addDefaultSubmitButton} come from properties, and the form's
 * id can be rendered in place of its name (see below).</li>
 * </ul>
 *
 * @property er.extensions.ERXWOForm.multipleSubmitDefault the default value of
 *           multipleSubmit for all forms, defaults to false
 * @property er.extensions.ERXWOForm.addDefaultSubmitButtonDefault whether or
 *           not a default submit button should be add to the form, defaults to false
 * @property er.extensions.ERXWOForm.useIdInsteadOfNameTag whether or not to use
 *           id instead of name in the form element, defaults to false
 * 
 * @binding action Action method to invoke when this element is activated.
 * @binding actionClass The name of the class in which the method
 * designated in <code>directActionName</code> can be found. Defaults to 
 * <code>DirectAction</code>.
 * @binding addDefaultSubmitButton Injects a submit button at the beginning of the
 * form since some browsers will submit the form using the first nested button
 * when the return key is pressed. Default is false unless it is set to true in 
 * the properties file.
 * @binding directActionName The name of the direct action method 
 * (minus the "Action" suffix) to invoke when this element is activated. 
 * Defaults to <code>default</code>.
 * @binding disabled Disabling a form omits the form element's tags from the
 * generated html. ERXWOForm will automatically disable any nested forms and post
 * a warning to the console if this value is not set.
 * @binding enctype The encoding type of the form, <code>multipart/form-data</code>
 * for a form with a file upload.
 * @binding fragmentIdentifier appends "#" + the value of the binding to the 
 * action.
 * @binding href The HTML <code>href</code> attribute
 * @binding id The HTML <code>id</code> attribute
 * @binding method The HTTP method used by the form. It can be <code>get</code> 
 * or <code>post</code>
 * @binding multipleSubmit If multipleSubmit evaluates to true , the form can 
 * have more than one submit button, each with its own action. By default, the
 * value is false unless it is set to true in the properties file.
 * @binding name The HTML <code>name</code> attribute
 * @binding queryDictionary Takes a dictionary of values that will be submitted
 * with the form.
 * @binding secure Determines if the form is secured with SSL. Default is false.
 * @binding embedded when true, a form inside of a form will still render. this is
 * to support forms inside of ajax modal containers that are structurally nested
 * forms, but appears as independent to the end-user
 * 
 * @author ak
 * @author Mike Schrag (idea to secure binding)
 */  
public class ERXWOForm extends com.webobjects.appserver._private.WOHTMLDynamicElement {
	private static final Logger log = LoggerFactory.getLogger(ERXWOForm.class);

	WOAssociation _formName;
	WOAssociation _enctype;
	WOAssociation _fragmentIdentifier;
	WOAssociation _disabled;

	protected WOAssociation _action;
	protected WOAssociation _href;
	protected WOAssociation _multipleSubmit;
	protected WOAssociation _actionClass;
	protected WOAssociation _queryDictionary;
	protected NSDictionary _otherQueryAssociations;
	protected WOAssociation _directActionName;
	protected WOAssociation _addDefaultSubmitButton;
	protected WOAssociation _embedded;

	public static boolean multipleSubmitDefault = ERXProperties.booleanForKeyWithDefault(ERXP.FORM_MULTIPLE_SUBMIT_DEFAULT.id(), false);
	public static boolean addDefaultSubmitButtonDefault = ERXProperties.booleanForKeyWithDefault(ERXP.FORM_ADD_DEFAULT_SUBMIT_BUTTON_DEFAULT.id(), false);
	public static boolean useIdInsteadOfNameTag = ERXProperties.booleanForKeyWithDefault(ERXP.FORM_USE_ID_INSTEAD_OF_NAME_TAG.id(), false);

	@SuppressWarnings("unchecked")
	public ERXWOForm(String name, NSDictionary<String, WOAssociation> associations, WOElement element) {
		super("form", associations, element);
		_otherQueryAssociations = _NSDictionaryUtilities.extractObjectsForKeysWithPrefix(_associations, "?", true);
		if (_otherQueryAssociations.count() == 0) {
			_otherQueryAssociations = null;
		}
		_action = _associations.removeObjectForKey("action");
		_href = _associations.removeObjectForKey("href");
		_multipleSubmit = _associations.removeObjectForKey("multipleSubmit");
		if (_multipleSubmit == null && ERXWOForm.multipleSubmitDefault) {
			_multipleSubmit = new WOConstantValueAssociation(Boolean.valueOf(multipleSubmitDefault));
		}
		_actionClass = _associations.removeObjectForKey("actionClass");
		_queryDictionary = _associations.removeObjectForKey("queryDictionary");
		_directActionName = _associations.removeObjectForKey("directActionName");
		_formName = _associations.removeObjectForKey("name");
		if (ERXWOForm.useIdInsteadOfNameTag && _id != null) {
			_formName = _id;	// id takes precedence over name - then subsequently written as id
			_id = null;
		}
		_enctype = _associations.removeObjectForKey("enctype");
		_fragmentIdentifier = _associations.removeObjectForKey("fragmentIdentifier");
		_disabled = _associations.removeObjectForKey("disabled");
		_addDefaultSubmitButton = _associations.removeObjectForKey("addDefaultSubmitButton");
		_embedded = _associations.removeObjectForKey("embedded");
		if (_associations.objectForKey("method") == null && _associations.objectForKey("Method") == null && _associations.objectForKey("METHOD") == null) {
			_associations.setObjectForKey(new WOConstantValueAssociation("post"), "method");
		}
		if (_action != null && _href != null || _action != null && _directActionName != null || _href != null && _directActionName != null || _action != null && _actionClass != null || _href != null && _actionClass != null) {
			throw new WODynamicElementCreationException("<" + getClass().getName() + ">: At least two of these conflicting attributes are present: 'action', 'href', 'directActionName', 'actionClass'");
		}
		if (_action != null && _action.isValueConstant()) {
			throw new WODynamicElementCreationException("<" + getClass().getName() + ">: 'action' is a constant.");
		}
	}

	@Override
	public String toString() {
		return new StringBuilder().append('<').append(getClass().getName()).append(" name: ").append(_formName)
				.append(" id: ").append(_id).append(" action: ").append(_action)
				.append(" actionClass: ").append(_actionClass).append(" directActionName: ")
				.append(_directActionName).append(" href: ").append(_href)
				.append(" multipleSubmit: ").append(_multipleSubmit).append(" queryDictionary: ")
				.append(_queryDictionary).append(" otherQueryAssociations: ")
				.append(_otherQueryAssociations).append('>').toString();
	}

	protected boolean _enterFormInContext(WOContext context) {
		boolean wasInForm = context.isInForm();
		context.setInForm(true);
		if (context.elementID().equals(context.senderID())) {
			context.setFormSubmitted(true);
		}
		return wasInForm;
	}

	protected void _exitFormInContext(WOContext context, boolean wasInForm, boolean wasFormSubmitted) {
		context.setInForm(wasInForm);
		context.setFormSubmitted(wasFormSubmitted);
	}

	protected String _enctype(WOContext context) {
		return _enctype != null ? (String) _enctype.valueInComponent(context.component()) : null;
	}

	@SuppressWarnings("unchecked")
	protected void _setEnctype(String enctype) {
		ERXWOContext.contextDictionary().setObjectForKey(enctype.toLowerCase(), "enctype");
	}

	protected void _clearEnctype() {
		ERXWOContext.contextDictionary().removeObjectForKey("enctype");
	}

	@Override
	public WOActionResults invokeAction(WORequest worequest, WOContext context) {
		boolean wasInForm = context.isInForm();
		WOActionResults result;
		if (_shouldAppendFormTags(context, wasInForm)) {
			boolean wasFormSubmitted = context.wasFormSubmitted();
			_enterFormInContext(context);
			boolean wasMultipleSubmitForm = context.isMultipleSubmitForm();
			String enctype = _enctype(context);
			if (enctype != null) {
				_setEnctype(enctype);
			}
	
			context.setActionInvoked(false);
			context.setIsMultipleSubmitForm(_multipleSubmit == null ? false : _multipleSubmit.booleanValueInComponent(context.component()));
			String previousFormName = _setFormName(context, wasInForm);
			try {
				result = super.invokeAction(worequest, context);
				if (!wasInForm && !context.wasActionInvoked() && context.wasFormSubmitted()) {
					if (_action != null) {
						result = (WOActionResults) _action.valueInComponent(context.component());
					}
					if (result == null && !ERXAjaxApplication.isAjaxSubmit(worequest)) {
						result = context.page();
					}
				}
			}
			finally {
				context.setIsMultipleSubmitForm(wasMultipleSubmitForm);
				_exitFormInContext(context, wasInForm, wasFormSubmitted);
				_clearFormName(context, previousFormName, wasInForm);
				_clearEnctype();
			}
		}
		else {
			result = super.invokeAction(worequest, context);
		}
		return result;
	}

	protected void _appendHiddenFieldsToResponse(WOResponse response, WOContext context) {
		boolean flag = _actionClass != null;
		NSDictionary hiddenFields = hiddenFieldsInContext(context, flag);
		if (hiddenFields.count() > 0) {
			for (Enumeration enumeration = hiddenFields.keyEnumerator(); enumeration.hasMoreElements();) {
				String s = (String) enumeration.nextElement();
				Object obj = hiddenFields.objectForKey(s);
				response._appendContentAsciiString("<input type=\"hidden\"");
				response._appendTagAttributeAndValue("name", s, true);
				response._appendTagAttributeAndValue("value", obj.toString(), true);
				response._appendContentAsciiString(" />\n");
			}

		}
	}

	private NSDictionary hiddenFieldsInContext(WOContext context, boolean hasActionClass) {
		return computeQueryDictionaryInContext("", _queryDictionary, _otherQueryAssociations, true, context);
	}

	@Override
	public void appendChildrenToResponse(WOResponse response, WOContext context) {
		super.appendChildrenToResponse(response, context);
		_appendHiddenFieldsToResponse(response, context);
	}

	protected String cgiAction(WOResponse response, WOContext context, boolean secure) {
		String s = computeActionStringInContext(_actionClass, _directActionName, context);
		return context._directActionURL(s, null, secure, 0, false);
	}

	@Override
	public void takeValuesFromRequest(WORequest request, WOContext context) {
		boolean wasInForm = context.isInForm();
		if (_shouldAppendFormTags(context, wasInForm)) {
			boolean wasFormSubmitted = context.wasFormSubmitted();
			_enterFormInContext(context);
			// log.info("{}->{}", _formName, toString().replaceAll(".*(keyPath=\\w+).*", "$1"));
			String previousFormName = _setFormName(context, wasInForm);
			try {
				super.takeValuesFromRequest(request, context);
			}
			finally {
				// log.info("{}->{}->{}", context.elementID(), context.senderID(), context._wasFormSubmitted());
				_exitFormInContext(context, wasInForm, wasFormSubmitted);
				_clearFormName(context, previousFormName, wasInForm);
			}
		}
		else {
			super.takeValuesFromRequest(request, context);
		}
	}

	protected String _formName(WOContext context) {
		String formName = null;
		if (_formName != null) {
			formName = (String) _formName.valueInComponent(context.component());
		}
		if (formName == null) {
			formName = "f" + ERXWOContext.safeIdentifierName(context.elementID());
		}
		return formName;
	}

	protected boolean _disabled(WOContext context) {
		boolean disabled = _disabled != null && _disabled.booleanValueInComponent(context.component());
		return disabled;
	}

	protected boolean _shouldAppendFormTags(WOContext context, boolean wasInForm) {
		boolean shouldAppendFormTags = !_disabled(context);
		if (shouldAppendFormTags) {
			// MS: If embedded = true, allow a nested form, which can be useful if you're doing funky ajax
			// dialogs and components that have forms in them.
			if (_embedded != null && _embedded.booleanValueInComponent(context.component())) {
				shouldAppendFormTags = true;
			}
			else {
				shouldAppendFormTags = !wasInForm;
			}
		}
		return shouldAppendFormTags;
	}

	@SuppressWarnings("unchecked")
	protected String _setFormName(WOContext context, boolean wasInForm) {
		String previousFormName = ERXWOForm.formName(context, null);
		if (_shouldAppendFormTags(context, wasInForm)) {
			String formName = _formName(context);
			if (formName != null) {
				ERXWOContext.contextDictionary().setObjectForKey(formName, "formName");
			}
		}
		return previousFormName;
	}

	protected void _clearFormName(WOContext context, String previousFormName, boolean wasInForm) {
		try {
			if (_shouldAppendFormTags(context, wasInForm)) {
				String formName = _formName(context);
				if (formName != null) {
					ERXWOContext.contextDictionary().removeObjectForKey("formName");
				}
			}
			if (previousFormName != null) {
				ERXWOContext.contextDictionary().setObjectForKey(previousFormName, "formName");
			}
		} catch(UnknownKeyException e) {
			/*
			 * _clearFormName is called in the finally block of takeValues, invoke, and append.
			 * If anything in those methods throws an exception, the form is often no longer
			 * the context.component.  When that happens, this exception is thrown when _formName
			 * or _disabled is called, and has the effect of swallowing the *real* exception.
			 * Since I know of no case where this is actually the legitimate exception, I'm
			 * swallowing this one instead. -RG
			 */
			log.error("UnknownKeyException thrown in ERXWOForm as a result of other exception.");
		}
	}

	@Override
	public void appendAttributesToResponse(WOResponse response, WOContext context) {
		String formName = _formName(context);
		if (formName != null) {
			response._appendTagAttributeAndValue(ERXWOForm.useIdInsteadOfNameTag ? "id" : "name", formName, false);
		}
		String enctype = _enctype(context);
		if (enctype != null) {
			_setEnctype(enctype);
			response._appendTagAttributeAndValue("enctype", enctype, false);
		}
		boolean secure = secureInContext(context);
		Object hrefObject = null;
		WOComponent wocomponent = context.component();
		super.appendAttributesToResponse(response, context);
		boolean generatingCompleteURLs = context.doesGenerateCompleteURLs();
		boolean requestIsSecure = context.secureMode();
		boolean switchToCompleteURLs = secure ^ requestIsSecure;
		if (switchToCompleteURLs && !generatingCompleteURLs) {
			context.generateCompleteURLs();
		}
		try {
			if (_href != null) {
				hrefObject = _href.valueInComponent(wocomponent);
				// MS: This is certainly not ideal, but I suspect nobody is
				// even calling it this way, anyway.
				if (secure && hrefObject != null) {
					hrefObject = hrefObject.toString().replaceFirst("http://", "https://");
				}
			}
			else if (_directActionName != null || _actionClass != null) {
				hrefObject = cgiAction(response, context, secure);
			}
			else {
				hrefObject = context.componentActionURL(WOApplication.application().componentRequestHandlerKey(), secure);
			}
			if (hrefObject != null) {
				String href = hrefObject.toString();
				Object fragmentIdentifier = (_fragmentIdentifier != null ? _fragmentIdentifier.valueInComponent(context.component()) : null);
				if (fragmentIdentifier != null) {
					href = href + "#" + fragmentIdentifier;
				}
				response._appendTagAttributeAndValue("action", href, false);
			}
			else {
				log.error("<WOForm> : action attribute evaluates to null");
			}
		}
		finally {
			if (switchToCompleteURLs && !generatingCompleteURLs) {
				context.generateRelativeURLs();
			}
		}
	}

	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		boolean wasInForm = context.isInForm();
		if (_shouldAppendFormTags(context, wasInForm)) {
			context.setInForm(true);
			String previousFormName = _setFormName(context, wasInForm);
			try {
				_appendOpenTagToResponse(response, context);
				if (_multipleSubmit != null && _multipleSubmit.booleanValueInComponent(context.component())) {
					if (_addDefaultSubmitButton != null && _addDefaultSubmitButton.booleanValueInComponent(context.component()) || (_addDefaultSubmitButton == null && addDefaultSubmitButtonDefault)) {
						response._appendContentAsciiString("<div style=\"position:absolute;left:-100000px\"><input type=\"submit\" name=\"WOFormDummySubmit\" value=\"WOFormDummySubmit\" /></div>");
					}
				}
				appendChildrenToResponse(response, context);
				_appendCloseTagToResponse(response, context);
			}
			finally {
				_clearFormName(context, previousFormName, wasInForm);
				_clearEnctype();
				context.setInForm(wasInForm);
			}
		}
		else {
			if (!_disabled(context)) {
				log.warn("This form is embedded inside another form, so the inner form with these bindings is being omitted: {}", this);
				log.warn("    page: {}", context.page());
				log.warn("    component: {}", context.component());
			}
			appendChildrenToResponse(response, context);
		}
	}

	/**
	 * Retrieves the current FORM's name in the supplied context. If none is set
	 * (there's no form around the current element, or it isn't an ERXWOForm)
	 * the supplied default value is used.
	 * 
	 * @param context
	 *            current context
	 * @param defaultName
	 *            default name to use
	 * @return form name in context or default value
	 */
	public static String formName(WOContext context, String defaultName) {
		String formName = (String) ERXWOContext.contextDictionary().objectForKey("formName");
		if (formName == null) {
			formName = defaultName;
		}
		return formName;
	}
}
