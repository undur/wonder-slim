package er.extensions.components.patches;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSMutableArray;

/**
 * Contains fixes applicable to the subclasses of WOInputList
 */

class ERXWOInputListSupport {

	private ERXWOInputListSupport() {}

	/**
	 * Overridden to:
	 * 
	 * - improve creation of the value that gets pushed to the "selections"
	 * binding - not swallow exceptions
	 */
	static void setSelectionListInContext(final WOContext context, final List selections, final WOAssociation selectionsAssociation) {

		if (selectionsAssociation != null && selectionsAssociation.isValueSettable()) {
			final List wrappedSelections = new NSMutableArray(selections);
			selectionsAssociation.setValue(wrappedSelections, context.component());
		}
	}

	/**
	 * Overridden to:
	 * 
	 * - add support for java arrays - throw an exception if [list] is bound
	 * to an unknown/unhandled type
	 */
	static List listInContext(final WOContext context, final WOAssociation listAssociation) {

		final Object bindingValue = listAssociation.valueInComponent(context.component());

		if (bindingValue == null) {
			return Collections.emptyList();
		}

		if (bindingValue instanceof List list) {
			return list;
		}

		if (bindingValue.getClass().isArray()) {
			// A little lengthy, but we need to go this way to ensure we're
			// handling arrays of any primitive type (not just Object[])
			final int length = Array.getLength(bindingValue);
			return IntStream.range(0, length).mapToObj(i -> Array.get(bindingValue, i)).toList();
		}

		throw new IllegalArgumentException("[list] binding returned an object of class '%s'. We only support java.util.List and java arrays".formatted(bindingValue.getClass()));
	}
}
