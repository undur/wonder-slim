package er.extensions.components.patches;

import java.util.List;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver._private.WOBrowser;
import com.webobjects.foundation.NSDictionary;

import er.extensions.components.ERXComponentUtilities.InputLists;

/**
 * Patch of WOBrowser, installed in its place. {@code list} accepts any {@code java.util.List} or array, and
 * {@code selections} receives a mutable array. Errors aren't swallowed.
 */

public class ERXWOBrowser extends WOBrowser {

	public ERXWOBrowser(String name, NSDictionary associations, WOElement element) {
		super(name, associations, element);
	}

	@Override
	protected List listInContext(WOContext context) {
		return InputLists.listInContext(context, _list);
	}

	@Override
	protected void setSelectionListInContext(WOContext context, List selections) {
		InputLists.setSelectionListInContext(context, selections, _selections);
	}
}
