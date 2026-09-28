package er.ajax;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXComponentActionRequestHandler;
import er.extensions.appserver.ajax.ERXAjaxApplication;

/**
 * The handler for Ajax requests ({@value #AjaxRequestHandlerKey}): a component action against a page restored from
 * the session, dispatched exactly as {@link ERXComponentActionRequestHandler} dispatches one, with the page marked as
 * not to be stored.
 */
public class AjaxRequestHandler extends ERXComponentActionRequestHandler {
	public static final String AjaxRequestHandlerKey = "ajax";
	private static boolean _useAjaxRequestHandler = false;

	public AjaxRequestHandler() {
		AjaxRequestHandler.setUseAjaxRequestHandler(true);
	}

	@Override
	public WOResponse handleRequest(WORequest request) {
		ERXAjaxApplication.enableShouldNotStorePage();
		WOResponse response = super.handleRequest(request);
		return response;
	}

	public static void setUseAjaxRequestHandler(boolean useAjaxRequestHandler) {
		_useAjaxRequestHandler = useAjaxRequestHandler;
	}

	public static boolean useAjaxRequestHandler() {
		return _useAjaxRequestHandler;
	}
}
