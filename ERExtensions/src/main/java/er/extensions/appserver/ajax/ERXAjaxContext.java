package er.extensions.appserver.ajax;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.extensions.routes.ERXRoutingContext;

/**
 * Makes partial form submits work (see {@link #_wasFormSubmitted()}).
 *
 * @author mschrag
 */
public class ERXAjaxContext extends ERXRoutingContext {
	
	public ERXAjaxContext(WORequest request) {
		super(request);
	}

	/**
	 * WO's input elements ask this public method, which in WO reads the form-submitted flag directly rather than going
	 * through {@link #_wasFormSubmitted()}, so both answer the same.
	 */
	@Override
	public boolean wasFormSubmitted() {
		return _wasFormSubmitted();
	}

	/**
	 * WO decides per form, not per element, whether submitted values are taken: once the form was submitted, every
	 * input in it pushes its form value into its binding, and an input the client didn't send pushes null. A partial
	 * submit sends only some of the form's fields (their element IDs are listed in the request, see
	 * {@link ERXAjaxApplication#partialFormSenderID(WORequest)}), so for every other element in the form we answer
	 * false, and its binding keeps its value. The Ajax submit button that sent the request counts as submitted too.
	 */
	/**
	 * Renders the element's ID as {@code data-element-id} on an input whose name is bound explicitly. A partial submit
	 * tells the server which field changed by the field's element ID (see {@link #_wasFormSubmitted()}); the client sends
	 * the name, which is the element ID unless a name is bound, and this attribute when there is one.
	 */
	public static void appendElementIDForPartialSubmit( final WOResponse response, final WOContext context, final boolean nameIsBound ) {
		if( nameIsBound ) {
			response._appendTagAttributeAndValue( "data-element-id", context.elementID(), false );
		}
	}

	@Override
	@Deprecated
	public boolean _wasFormSubmitted() {
		boolean wasFormSubmitted = super._wasFormSubmitted();
		if (wasFormSubmitted) {
			WORequest request = request();
			String partialSubmitSenderID = ERXAjaxApplication.partialFormSenderID(request);
			if (partialSubmitSenderID != null) {
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