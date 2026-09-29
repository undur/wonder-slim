package er.ajax;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import er.extensions.ERXExtensions;
import er.extensions.ERXPlugin;
import er.extensions.appserver.ERXApplication;
import er.extensions.appserver.ajax.ERXAjaxApplication;

/**
 * The Ajax framework's plugin: registers the Ajax and push request handlers, and the response delegate that repairs
 * the border cases structural page changes cause
 */
public class Ajax implements ERXPlugin {

	private static final Logger log = LoggerFactory.getLogger(Ajax.class);

	@Override
	public List<Class<? extends ERXPlugin>> requires() {
		return List.of(ERXExtensions.class);
	}

	/**
	 * Registers the request handlers and the response delegate once the application is constructed, before it
	 * accepts requests. The response delegate replaces any the application set while constructing.
	 */
	@Override
	public void finishInitialization(final ERXApplication application) {
		if (!AjaxRequestHandler.useAjaxRequestHandler()) {
			application.registerRequestHandler(new AjaxRequestHandler(), AjaxRequestHandler.AjaxRequestHandlerKey);
			log.debug("AjaxRequestHandler installed");
		}
		application.registerRequestHandler(new AjaxPushRequestHandler(), AjaxPushRequestHandler.AjaxCometRequestHandlerKey);

		// Register the AjaxResponseDelegate if you're using an ERXAjaxApplication ... This allows us
		// to fix some weird border cases caused by structural page changes.
		if (application instanceof ERXAjaxApplication ajaxApplication) {
			ajaxApplication.setResponseDelegate(new AjaxResponse.AjaxResponseDelegate());
		}
		log.debug("Ajax loaded");
	}
}
