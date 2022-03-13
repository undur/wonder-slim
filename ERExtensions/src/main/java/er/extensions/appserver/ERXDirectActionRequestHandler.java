package er.extensions.appserver;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOSession;
import com.webobjects.appserver._private.WODirectActionRequestHandler;
import com.webobjects.appserver._private.WOServerSessionStore;

import er.extensions.foundation.ERXProperties;

/**
 * Improved direct action request handler. Will automatically handle character encodings.
 *
 * @property er.extensions.ERXMessageEncoding.Enabled
 */

public class ERXDirectActionRequestHandler extends WODirectActionRequestHandler {

	private static Boolean automaticMessageEncodingEnabled;

	public ERXDirectActionRequestHandler() {}

	public ERXDirectActionRequestHandler(String anActionClassName, String aDefaultActionName, boolean ifShouldAddToStatistics) {
		super(anActionClassName, aDefaultActionName, ifShouldAddToStatistics);
	}

	/**
	 * Disabling automatic message encoding can be useful for back-end services where you just want to use the default encoding.
	 */
	private static boolean automaticMessageEncodingEnabled() {
		if (automaticMessageEncodingEnabled == null) {
			automaticMessageEncodingEnabled = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXMessageEncoding.Enabled", true) ? Boolean.TRUE : Boolean.FALSE;
		}

		return automaticMessageEncodingEnabled.booleanValue();
	}

	/**
	 * @return true if you want to handle the request even though the app is refusing new sessions. Currently, this includes all urls with "stats" in them.
	 */
	protected boolean isSystemRequest(WORequest request) {
		return request.requestHandlerPath() != null && request.requestHandlerPath().toLowerCase().indexOf("stats") >= 0;
	}

	@Override
	public WOResponse handleRequest(WORequest request) {
		WOResponse response = null;

		// ak: when addressed with a DA link with this instance's ID (and
		// an expired session) and the app is refusing new sessions, the
		// default implementation will create a session anyway, which will
		// wreak havoc if the app is memory starved.
		// Search engines are a nuisance in that regard
		WOApplication app = WOApplication.application();
		if (app.isRefusingNewSessions() && request.isUsingWebServer() && !isSystemRequest(request)) {
			if (isSessionIDInRequest(request)) {
				// we know the imp of the server session store simply
				// looks up the ID in the registered sessions,
				// so we don't need to do the check-out/check-in
				// yadda-yadda.
				if (app.sessionStore().getClass() == WOServerSessionStore.class) {
					if (app.sessionStore().restoreSessionWithID(request.sessionID(), request) == null) {
						response = generateRequestRefusal(request);
						// AK: should be a permanent redirect, as the session is gone for good.
						// However, the adaptor checks explicitly on 302 so we return that...
						// It shouldn't matter which instance we go to now.
						response.setStatus(302);
					}
				}
			}
			else {
				// if no session was supplied, what are we doing here in the
				// first place? The adaptor shouldn't have linked to us as
				// we are refusing new sessions.
				response = generateRequestRefusal(request);
			}
		}
		if (response == null) {
			response = super.handleRequest(request);
		}

		if (automaticMessageEncodingEnabled()) {
			ERXMessageEncoding messageEncoding = null;

			final WOSession session = ERXSession.anySession();

			if (session != null ) {
				ERXSession erxSession = (ERXSession) session;
				messageEncoding = erxSession.messageEncoding();
				erxSession.lastActionWasDA = true;
			}
			else {
				// FIXME: This is kind of bullshitish at the moment
				messageEncoding = ERXMessageEncoding.instance();
			}

			messageEncoding.setEncodingToResponse(response);
		}

		return response;
	}
}