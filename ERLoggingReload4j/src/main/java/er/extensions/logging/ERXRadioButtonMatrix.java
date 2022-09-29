/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.logging;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;

import er.extensions.components.ERXStatelessComponent;
import er.extensions.foundation.ERXUtilities;

public class ERXRadioButtonMatrix extends ERXStatelessComponent {

	public Object currentItem;
	public Object _selection;
	public Number index;
	public Object uniqueID;

	public ERXRadioButtonMatrix(WOContext aContext) {
		super(aContext);
	}

	@Override
	public void reset() {
		invalidateCaches();
	}

	@Override
	public void awake() {
		super.awake();
		uniqueID = valueForBinding("uniqueID");
		if (uniqueID == null) {
			uniqueID = context().elementID();
		}
	}

	public void invalidateCaches() {
		_selection = null;
		currentItem = null;
		index = null;
		uniqueID = null;
	}

	@Override
	public void takeValuesFromRequest(WORequest aRequest, WOContext aContext) {
		setSelection(aRequest.stringFormValueForKey(uniqueID()));
		super.takeValuesFromRequest(aRequest, aContext);
	}

	public Object currentItem() {
		return currentItem;
	}

	public void setCurrentItem(Object aValue) {
		currentItem = aValue;
		setValueForBinding(aValue, "item");
	}

	public Number index() {
		return index;
	}

	public boolean disabled() {
		return booleanValueForBinding("disabled", false);
	}

	public void setIndex(Number newIndex) {
		index = newIndex;
	}

	public Object selection() {
		if (_selection == null) {
			// ** only pull this one time
			_selection = valueForBinding("selection");
		}

		return _selection;
	}

	public void setSelection(String anIndex) {
		if (anIndex != null) {
			// ** push the selection to the parent
			NSArray anItemList = (NSArray) valueForBinding("list");
			Object aSelectedObject = anItemList.objectAtIndex(Integer.parseInt(anIndex));
			setValueForBinding(aSelectedObject, "selection");
			// ** and force it to be pulled if there's a next time.
		}

		_selection = null;
	}

	public String isCurrentItemSelected() {
		if (selection() != null && selection().equals(currentItem)) {
			return "checked";
		}

		return "";
	}

	public String otherTagStringForRadioButton() {
		boolean isDisabled = disabled();
		boolean isChecked = !ERXUtilities.stringIsNullOrEmpty(isCurrentItemSelected());
		return (isDisabled ? "disabled" : "") + (isDisabled && isChecked ? " " : "") + (isChecked ? "checked" : "");
	}

	public String uniqueID() {
		return uniqueID.toString();
	}
}