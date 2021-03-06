package er.extensions.appserver.ajax;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;

/**
 * ERXAjaxContext provides the overrides necessary methods for partial form
 * submits to work. If you want to use the Ajax framework without using other
 * parts of Project Wonder (i.e. ERXSession or ERXApplication), you should steal
 * all of the code in ERXAjaxSession, ERXAjaxApplication, and ERXAjaxContext.
 * 
 * @author mschrag
 */
public class ERXAjaxContext extends WOContext {
	
	public ERXAjaxContext(WORequest request) {
		super(request);
	}

	@Override
	public boolean wasFormSubmitted() {
		return _wasFormSubmitted();
	}
	
	@Override
	@Deprecated
	public boolean _wasFormSubmitted() {
		boolean wasFormSubmitted = super._wasFormSubmitted();
		if (wasFormSubmitted) {
			WORequest request = request();
			String partialSubmitSenderID = ERXAjaxApplication.partialFormSenderID(request);
			if (partialSubmitSenderID != null) {
				// TODO When explicitly setting the "name" binding on an input, 
				// the following will fail in the takeValuesFromRequest phase.
				String elementID = elementID();
				if (!partialSubmitSenderID.equals(elementID) 
						&& !partialSubmitSenderID.startsWith(elementID + ",") 
						&& !partialSubmitSenderID.endsWith("," + elementID) 
						&& !partialSubmitSenderID.contains("," + elementID + ",")) {
					String ajaxSubmitButtonID = ERXAjaxApplication.ajaxSubmitButtonName(request);
					if (ajaxSubmitButtonID == null || !ajaxSubmitButtonID.equals(elementID)) {
						wasFormSubmitted = false;
					}
				}
			}
		}
		return wasFormSubmitted;
	}
}