package er.extensions.components.patches;

import java.util.List;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WOPopUpButton;
import com.webobjects.foundation.NSDictionary;

/**
 * Patch of WOPopUpButton, installed in its place. {@code list} accepts any {@code java.util.List} or array, and
 * the {@code <select>} gets no {@code value} attribute.
 */

public class ERXWOPopUpButton extends WOPopUpButton {

	public ERXWOPopUpButton(String name, NSDictionary associations, WOElement element) {
		super(name, associations, element);
	}

	/**
	 * select element shouldn't worry about value attribute
	 * 
	 * FIXME: We should really look into what this does and document it a
	 * little better // Hugi 2025-06-16
	 */
	@Override
	protected void _appendValueAttributeToResponse(WOResponse response, WOContext context) {
	}

	@Override
	protected List listInContext(WOContext context) {
		return ERXWOInputListSupport.listInContext(context, _list);
	}
}
