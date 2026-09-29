package er.ajax;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import er.extensions.ERXExtensions;
import er.extensions.ERXPlugin;
import er.extensions.appserver.ERXApplication;
import er.extensions.appserver.ajax.ERXAjaxApplication;

/**
 * AjaxSlim's plugin, for the modern, slim, morph-native Ajax framework.
 * <p>
 * Modeled on the legacy {@code er.ajax.Ajax} plugin, but deliberately leaner:
 * <ul>
 * <li>registers the {@link AjaxRequestHandler} (so ajax actions get their own, page-cache-disabling
 *     request handler), and</li>
 * <li>installs the {@link AjaxResponse.AjaxResponseDelegate} on {@link ERXAjaxApplication} so the
 *     double-click / null-action border cases are repaired.</li>
 * </ul>
 * It does <b>not</b> register the legacy comet/push request handler - server-push is out of scope
 * for AjaxSlim.
 */
public class AjaxSlim implements ERXPlugin {

	private static final Logger log = LoggerFactory.getLogger(AjaxSlim.class);

	@Override
	public List<Class<? extends ERXPlugin>> requires() {
		return List.of(ERXExtensions.class);
	}

	/**
	 * Registers the request handler and the response delegate once the application is constructed, before it
	 * accepts requests. The response delegate replaces any the application set while constructing.
	 */
	@Override
	public void finishInitialization(final ERXApplication application) {
		if (!AjaxRequestHandler.useAjaxRequestHandler()) {
			application.registerRequestHandler(new AjaxRequestHandler(), AjaxRequestHandler.AjaxRequestHandlerKey);
			log.debug("AjaxRequestHandler installed");
		}

		// Register the AjaxResponseDelegate if you're using an ERXAjaxApplication ... This allows us
		// to fix some weird border cases caused by structural page changes.
		if (application instanceof ERXAjaxApplication ajaxApplication) {
			ajaxApplication.setResponseDelegate(new AjaxResponse.AjaxResponseDelegate());
		}
		log.debug("AjaxSlim loaded");
	}
}
