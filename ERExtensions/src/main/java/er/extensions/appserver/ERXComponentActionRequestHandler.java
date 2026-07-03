package er.extensions.appserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOSession;
import com.webobjects.appserver.WOStatisticsStore;
import com.webobjects.foundation.NSArray;

import er.extensions.appserver.ajax.ERXAjaxSession;

/**
 * Component-action dispatch (the /wo/ handler), written from requirements as owned code, replacing
 * {@link ERXComponentRequestHandler} - a patched copy of the stock handler, carrying that code's
 * idiom and vestiges. This handler is the DEFAULT; registration is decided in ERXApplication, and
 * {@code er.extensions.ERXComponentActionRequestHandler.enabled=false} is the escape hatch back to
 * the old handler while this one earns trust in production.
 * <p>
 * The job, in order: parse the action URL ({@code /wo/[sessionID/]contextID.senderID}), restore the
 * session (NEVER create one - session-creating access to this handler is what allowed session-less
 * direct component access), consult the repeated-request guard, restore the page by contextID, run
 * the request phases (takeValues / invokeAction / appendToResponse), save the page, and check the
 * session back in - exactly once, on every path, including exception paths.
 * <p>
 * Deliberate divergences from the old handler, beyond style - the complete list, from a line-by-line
 * differential audit:
 * <ul>
 * <li>A malformed action URL (no contextID) is answered with the page-restoration error instead of
 * being fed to {@code pageWithName(null)} - which in the old handler produced an exception page, a
 * vestige of named-page access that neither handler allows.</li>
 * <li>The stale-cookie-clearing dance that only ran on that dead branch is gone with it.</li>
 * <li>{@code WOApplication.awake()}/{@code sleep()} are paired (the old handler skipped
 * {@code sleep()} when an exception hit a session-less request), and the session check-in cannot run
 * twice (the old handler could check in both in its exception handler and its normal path). An
 * exception thrown by {@code handleException} itself still reaches the finally here, so the session
 * is checked in and the application put to sleep even then - the old handler leaked both.</li>
 * <li>The happy-path session check-in is guarded on a session actually being present; the old handler
 * called {@code saveSessionForContext} unconditionally, including for responses produced without any
 * session (its own exception path used the same guard as here).</li>
 * <li>{@code DidHandleRequestNotification} is posted before the session check-in rather than after it
 * (a consequence of the exactly-once check-in living in the finally). Observers touch the response,
 * not session checkout, so the order is immaterial to them.</li>
 * <li>On a repeated-request match, a null result from the page cache falls through to re-rendering
 * the already-restored page; the old handler set a null page element and let the render fail. (A
 * match's page is by construction in the cache, so neither branch should ever run.)</li>
 * <li>The page-recreation branch ({@code _isPageRecreationEnabled()} - the predicate for WO's
 * cacheless component-action mode, observed to hold only at {@code pageCacheSize == 0}, where
 * action URLs carry the page name for fresh recreation) is gone. That mode has been dead in this
 * lineage since named-page parsing was removed: the embedded page name parses as a session id, so
 * a cacheless action dies with a session-restoration error before either handler's recreation
 * branch could run - verified identical through both. A failed restore is therefore always the
 * page-restoration error.</li>
 * <li>Errors are logged through slf4j like the rest of the framework, not NSLog.</li>
 * </ul>
 * Everything else is behavior-parity with the old handler - including bug-for-bug edges like the
 * bare-session-id path element overwriting a cookie-derived session id under
 * {@code _lookForIDsInCookiesFirst} - verified by the playground suite, the cache/replay harnesses,
 * the expired-session and notification probes, and a differential URL-matrix probe
 * (tools/playwright-bridge/examples/dispatch-differential.mjs) run against both handlers.
 */
public class ERXComponentActionRequestHandler extends WORequestHandler {

	private static final Logger log = LoggerFactory.getLogger(ERXComponentActionRequestHandler.class);

	/**
	 * The identity a component-action URL carries: {@code /wo/[sessionID/]contextID.senderID}.
	 * Any part can be null when absent/malformed; dispatch decides what that means.
	 */
	record ActionURL(String sessionID, String contextID, String senderID) {}

	@Override
	public WOResponse handleRequest(WORequest request) {
		WOApplication application = WOApplication.application();

		// When concurrent request handling is off, the application serializes all request handling
		// through this lock.
		Object globalLock = application.requestHandlingLock();

		if (globalLock != null) {
			synchronized (globalLock) {
				return handleComponentActionRequest(request, application);
			}
		}

		return handleComponentActionRequest(request, application);
	}

	private WOResponse handleComponentActionRequest(WORequest request, WOApplication application) {
		final ActionURL url = parseUrl(request, application);

		// An instance refusing new sessions (graceful shutdown / load balancing) bounces requests
		// that carry no session to another instance.
		if (application.isRefusingNewSessions() && url.sessionID() == null) {
			return redirectToNewLocation(request, application);
		}

		final WOStatisticsStore statistics = application.statisticsStore();

		if (statistics != null) {
			statistics.applicationWillHandleComponentActionRequest();
		}

		WOContext context = null;
		WOResponse response;
		boolean applicationAwakened = false;

		try {
			try {
				context = application.createContextForRequest(request);
				context._setRequestContextID(url.contextID());
				context._setSenderID(url.senderID());

				application.awake();
				applicationAwakened = true;

				response = dispatch(context, url, application);
				context._putAwakeComponentsToSleep();
				ERXNotification.DidHandleRequestNotification.postNotification(context);
			}
			catch (Exception e) {
				log.error("Exception while handling component action request {}", request.uri(), e);

				// Even a failure during context creation must produce an error page, not escape the
				// handler - and handleException needs a context to render in.
				if (context == null) {
					context = application.createContextForRequest(request);
				}
				else {
					context._putAwakeComponentsToSleep();
				}

				response = application.handleException(e, context);
			}
		}
		finally {
			// The session was checked out by restoreSessionWithID; check it back in exactly once,
			// whatever path we took. A failure here must not replace the response we already have.
			if (context != null && context._session() != null) {
				try {
					application.saveSessionForContext(context);
				}
				catch (Exception e) {
					log.error("Failed to check the session back in after handling {}", request.uri(), e);
				}
			}

			if (applicationAwakened) {
				application.sleep();
			}
		}

		if (response != null) {
			response._finalizeInContext(context);
		}

		if (statistics != null) {
			WOComponent page = context.page();
			statistics.applicationDidHandleComponentActionRequestWithPageNamed(page == null ? null : page.name());
		}

		return response;
	}

	/**
	 * Session and page restoration. Every return is a complete response; the caller owns component
	 * sleep, session check-in and finalization.
	 */
	private WOResponse dispatch(WOContext context, ActionURL url, WOApplication application) {
		// Component actions only make sense against an existing session. We never create one here:
		// the application's actual entry points (direct actions, routes) own session creation.
		if (url.sessionID() == null) {
			return application.handleSessionRestorationErrorInContext(context);
		}

		final WOSession session = application.restoreSessionWithID(url.sessionID(), context);

		// The session id didn't resolve - typically an expired session.
		if (session == null) {
			return application.handleSessionRestorationErrorInContext(context);
		}

		// An action URL without a contextID is malformed; there is no page it could mean. (The old
		// handler served the default page here - a leftover from named-page access.)
		if (url.contextID() == null) {
			return application.handlePageRestorationErrorInContext(context);
		}

		final WOComponent page = session.restorePageForContextID(url.contextID());

		// The page's contextID has aged out of the page cache ("backtracked too far").
		if (page == null) {
			return application.handlePageRestorationErrorInContext(context);
		}

		context._setPageElement(page);

		final WOResponse response = runAction(context, url, application, session);

		if (application.isPageRefreshOnBacktrackEnabled()) {
			response.disableClientCaching();
		}

		session._saveCurrentPage();

		return response;
	}

	/**
	 * The request phases against the restored page: either the repeated-request guard answers the
	 * request from the stored result (no action invocation), or takeValues/invokeAction run and the
	 * resulting page renders. A non-component action result (a redirect, a raw response) becomes the
	 * response as-is.
	 */
	private WOResponse runAction(WOContext context, ActionURL url, WOApplication application, WOSession session) {
		final WORequest request = context.request();

		final WOResponse response = application.createResponseInContext(context);
		response.setHTTPVersion(request.httpVersion());
		response.setHeader("text/html", "content-type");
		context._setResponse(response);

		// The repeated-request guard (active only under page-refresh-on-backtrack): a byte-identical
		// repeat of an already-handled request re-renders that request's stored result page instead
		// of re-invoking the action. ERXAjaxSession owns the page cache and answers this; a plain
		// WOSession falls back to WO's own matching.
		final String repeatedRequestContextID = session instanceof ERXAjaxSession ajaxSession
				? ajaxSession.contextIDForRepeatedRequest(context)
				: session._contextIDMatchingIDs(context);

		if (repeatedRequestContextID != null) {
			WOComponent storedPage = session.restorePageForContextID(repeatedRequestContextID);

			if (storedPage != null) {
				context._setPageElement(storedPage);
			}
		}
		else if (url.senderID() != null) {
			if (request._hasFormValues()) {
				application.takeValuesFromRequest(request, context);
			}

			context._setPageChanged(false);

			final WOActionResults results = application.invokeAction(request, context);

			if (results == null || results instanceof WOComponent) {
				final WOComponent resultPage = (WOComponent)results;

				if (resultPage != null && resultPage.context() != context) {
					resultPage._awakeInContext(context);
				}

				final boolean pageChanged = resultPage != null && resultPage != context._pageElement();
				context._setPageChanged(pageChanged);

				if (pageChanged) {
					context._setPageElement(resultPage);
				}
			}
			else {
				return results.generateResponse();
			}
		}

		application.appendToResponse(response, context);
		return response;
	}

	/**
	 * Pulls the session/context/sender identity out of a component-action URL. Handled forms (the
	 * path array is everything after the handler key):
	 * {@code [sessionID, contextID.senderID]}, {@code [contextID.senderID]} (session in a cookie or
	 * form value) and {@code [sessionID]} (no action element - dispatch answers with an error).
	 */
	private ActionURL parseUrl(WORequest request, WOApplication application) {
		final String sessionIdKey = application.sessionIdKey();
		final boolean cookiesFirst = WORequest._lookForIDsInCookiesFirst();

		String sessionID = cookiesFirst ? request.cookieValueForKey(sessionIdKey) : null;
		String contextID = null;
		String senderID = null;

		final NSArray<?> path = request.requestHandlerPathArray();

		if (path != null && path.count() > 0) {
			final String last = (String)path.lastObject();

			// The action element is <contextID>.<senderID>, contextID being the leading digits.
			int digits = 0;

			while (digits < last.length() && Character.isDigit(last.charAt(digits))) {
				digits++;
			}

			if (digits < last.length() && last.charAt(digits) == '.') {
				contextID = last.substring(0, digits);
				senderID = last.substring(digits + 1);

				if (sessionID == null && path.count() > 1) {
					final String penultimate = (String)path.objectAtIndex(path.count() - 2);

					// A ".wo" element is a component name, never a session id (named-page URLs are
					// not served by this handler, but their shape is still recognized).
					if (!penultimate.endsWith(".wo")) {
						sessionID = penultimate;
					}
				}
			}
			else if (!last.endsWith(".wo")) {
				// No action element; the last path element can only be a session id. Parity note: like
				// the old handler, this overwrites a cookie-derived session id even under
				// _lookForIDsInCookiesFirst - bug-for-bug on this edge, deliberately.
				sessionID = last;
			}

			if (sessionID == null && !cookiesFirst) {
				sessionID = request.stringFormValueForKey(sessionIdKey);

				if (sessionID == null) {
					sessionID = request.cookieValueForKey(sessionIdKey);
				}
			}
		}
		else if (application.shouldRestoreSessionOnCleanEntry(request)) {
			sessionID = request.cookieValueForKey(sessionIdKey);
		}

		if (sessionID != null && sessionID.isEmpty()) {
			sessionID = null;
		}

		return new ActionURL(sessionID, contextID, senderID);
	}

	/**
	 * The refusing-new-sessions bounce: redirect the request to wherever the application says new
	 * traffic should go, with a human-readable fallback body.
	 */
	private WOResponse redirectToNewLocation(WORequest request, WOApplication application) {
		final String newLocationURL = application._newLocationForRequest(request);
		final String contentString = "Sorry, your request could not immediately be processed. Please try this URL: <a href=\"" + newLocationURL + "\">" + newLocationURL + "</a>";
		final WOResponse response = application.createResponseInContext(null);
		WOResponse._redirectResponse(response, newLocationURL, contentString);
		response._finalizeInContext(null);
		return response;
	}
}
