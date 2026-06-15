package er.ajax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;

import er.extensions.ERXFrameworkPrincipal;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.foundation.ERXUtilities;

/**
 * Framework principal for AjaxSlim, the modern, slim, morph-native Ajax framework.
 * <p>
 * Modeled on the legacy {@code er.ajax.Ajax} principal, but deliberately leaner:
 * <ul>
 * <li>registers the {@link AjaxRequestHandler} (so ajax actions get their own, page-cache-disabling
 *     request handler), and</li>
 * <li>installs the {@link AjaxResponse.AjaxResponseDelegate} on {@link ERXAjaxApplication} so the
 *     double-click / null-action border cases are repaired.</li>
 * </ul>
 * It does <b>not</b> register the legacy comet/push request handler - server-push is out of scope
 * for AjaxSlim.
 */
public class AjaxSlim extends ERXFrameworkPrincipal {

	public static Class[] REQUIRES = new Class[0];

	private static final Logger log = LoggerFactory.getLogger(AjaxSlim.class);

	static {
		setUpFrameworkPrincipalClass(AjaxSlim.class);
	}

	public AjaxSlim() {
		NSNotificationCenter center = NSNotificationCenter.defaultCenter();
		// This is needed when ERXAjaxApplication is sub-classed
		center.addObserver(this,
				ERXUtilities.notificationSelector("finishAjaxInitialization"),
				WOApplication.ApplicationWillFinishLaunchingNotification,
				null);
	}

	/**
	 * This is called directly only for when ERXApplication is sub-classed.
	 */
	@Override
	public void finishInitialization() {
		WOApplication application = WOApplication.application();
		if (!AjaxRequestHandler.useAjaxRequestHandler()) {
			application.registerRequestHandler(new AjaxRequestHandler(), AjaxRequestHandler.AjaxRequestHandlerKey);
			log.debug("AjaxRequestHandler installed");
		}

		// Register the AjaxResponseDelegate if you're using an ERXAjaxApplication ... This allows us
		// to fix some weird border cases caused by structural page changes.
		if (application instanceof ERXAjaxApplication) {
			((ERXAjaxApplication) application).setResponseDelegate(new AjaxResponse.AjaxResponseDelegate());
		}
		log.debug("AjaxSlim loaded");
	}

	/**
	 * The constructor sets this up to receive a notification. This is used when ERXAjaxApplication is sub-classed.
	 *
	 * @param notification ApplicationWillFinishLaunchingNotification
	 */
	public void finishAjaxInitialization(NSNotification notification) {
		finishInitialization();
	}
}
