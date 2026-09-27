package er.extensions.components.patches;

import java.util.List;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver._private.WOCheckBoxList;
import com.webobjects.foundation.NSDictionary;

/**
 * Patch of WOCheckBoxList, installed in its place. {@code list} accepts any {@code java.util.List} or array, and
 * {@code selections} receives a mutable array. Errors aren't swallowed.
 */

public class ERXWOCheckBoxList extends WOCheckBoxList {

	public ERXWOCheckBoxList(String name, NSDictionary associations, WOElement element) {
		super(name, associations, element);
	}

	@Override
	protected List listInContext(WOContext context) {
		return ERXWOInputListSupport.listInContext(context, _list);
	}

	@Override
	protected void setSelectionListInContext(WOContext context, List selections) {
		ERXWOInputListSupport.setSelectionListInContext(context, selections, _selections);
	}
}
