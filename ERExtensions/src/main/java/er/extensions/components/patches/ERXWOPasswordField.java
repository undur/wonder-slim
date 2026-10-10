package er.extensions.components.patches;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WOPasswordField;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ajax.ERXAjaxContext;

/**
 * Patch of WOPasswordField, installed in its place. Adds a {@code readonly} binding: when true, the {@code readonly}
 * attribute is rendered and the submitted value isn't taken.
 *
 * WO has no such binding and passes {@code readonly} through as an attribute with its value, so a false value
 * still renders {@code readonly="false"}, which browsers treat as read-only, and the submitted value is taken
 * regardless, so read-only is never enforced on the server.
 */

public class ERXWOPasswordField extends WOPasswordField {

	protected WOAssociation _readonly;

	public ERXWOPasswordField(String name, NSDictionary associations, WOElement element) {
		super(name, associations, element);
		_readonly = _associations.removeObjectForKey("readonly");
	}

	@Override
	protected void _appendNameAttributeToResponse(WOResponse response, WOContext context) {
		super._appendNameAttributeToResponse(response, context);
		ERXAjaxContext.appendElementIDForPartialSubmit(response, context, _name != null);

		if (_readonly != null && _readonly.booleanValueInComponent(context.component())) {
			response._appendTagAttributeAndValue("readonly", "readonly", false);
		}
	}

	/**
	 * If readonly attribute is set to <code>true</code> prevent the
	 * takeValuesFromRequest.
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
