package er.extensions.components.patches;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver._private.WOSubmitButton;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXSession;

/**
 * Patch of WOSubmitButton, installed in its place. Records the invoked action in the session (under
 * {@code ERXActionLogging}) for logging.
 */

public class ERXWOSubmitButton extends WOSubmitButton {

	public ERXWOSubmitButton(String name, NSDictionary associations, WOElement element) {
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
