package er.extensions.components.patches;

import java.util.List;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WOPopUpButton;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ajax.ERXAjaxContext;
import er.extensions.components.ERXComponentUtilities.InputLists;

/**
 * Patch of WOPopUpButton, installed in its place. {@code list} accepts any {@code java.util.List} or array.
 */

public class ERXWOPopUpButton extends WOPopUpButton {

	public ERXWOPopUpButton(String name, NSDictionary associations, WOElement element) {
		super(name, associations, element);
	}

	@Override
	protected List listInContext(WOContext context) {
		return InputLists.listInContext(context, _list);
	}

	@Override
	protected void _appendNameAttributeToResponse(WOResponse response, WOContext context) {
		super._appendNameAttributeToResponse(response, context);
		ERXAjaxContext.appendElementIDForPartialSubmit(response, context, _name != null);
	}
}
