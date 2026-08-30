package er.ajax;

import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.appserver.ERXWOContext;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.appserver.ajax.ERXAjaxApplication.ERXAjaxResponseDelegate;

/**
 * AjaxResponse provides support for performing an AjaxUpdate in the same response
 * as an ajax action.
 *
 * @author mschrag
 */
public class AjaxResponse extends WOResponse {
	private static final Logger log = LoggerFactory.getLogger(AjaxResponse.class);
	public static final String AJAX_UPDATE_PASS = "_ajaxUpdatePass";
	private static final java.util.List<AjaxResponseAppender> _responseAppenders = new java.util.concurrent.CopyOnWriteArrayList<>();

	/**
	 * Add a response appender to the list of response appender. At the end of
	 * every AjaxResponse, the AjaxResponseAppenders are given an opportunity to
	 * tag along. For instance, if you have an area at the top of your pages that
	 * show errors or notifications, you may want all of your ajax responses to have
	 * a chance to trigger an update of this area, so you could register an
	 * {@link er.ajax.AjaxResponseAppender} that renders a javascript block that calls
	 * MyNotificationsUpdate() only if there are notifications to be shown. Without
	 * response appenders, you would have to include a check in all of your
	 * components to do this.
	 *
	 * @param responseAppender the appender to add
	 */
	public static void addAjaxResponseAppender(AjaxResponseAppender responseAppender) {
		_responseAppenders.add(responseAppender);
	}

	private WORequest _request;
	private WOContext _context;

	public AjaxResponse(WORequest request, WOContext context) {
		super();
		_request = request;
		_context = context;
	}

	@Override
	public WOResponse generateResponse() {
		if (AjaxUpdateProtocol.hasUpdateContainerID(_request)) {
			String originalSenderID = _context.senderID();
			_context._setSenderID("");
			try {
				CharSequence originalContent = _content;
				_content = new StringBuilder();
				NSMutableDictionary userInfo = ERXWOContext.contextDictionary();
				userInfo.setObjectForKey(Boolean.TRUE, AjaxResponse.AJAX_UPDATE_PASS);
				WOActionResults woactionresults = WOApplication.application().invokeAction(_request, _context);
				_content.append(originalContent);
				for (AjaxResponseAppender responseAppender : _responseAppenders) {
					responseAppender.appendToResponse(this, _context);
				}
				// An empty response is a mistake only when a container was actually TARGETED but nothing
				// rendered for it - typically a misspelled updateContainerID that matched no container on
				// the page. An EMPTY target set (e.g. clearUpdateContainers) producing no content is a
				// deliberate, valid "nothing changed, update nothing" outcome, not an error.
				// FIXME: still a content-length heuristic; the precise check is "a targeted id matched no
				// container on the page" - build that and this becomes exact. // 2026-06
				boolean targetedSomething = !AjaxUpdateProtocol.requestedUpdateContainerIDs(_request).isEmpty();
				if (_contentLength() == 0 && targetedSomething) {
					setStatus(HTTP_STATUS_INTERNAL_ERROR);
					final String targeted = AjaxUpdateProtocol.requestedUpdateContainerIDs(_request).toString();
					final String pageName = _context.page() == null ? "(no page)" : _context.page().name();
					final String message = "Ajax update produced NO content for targeted container(s) " + targeted + " on page '" + pageName + "'."
							+ " Two known causes: (1) the update container did not render during this update pass - typically because it sits inside a"
							+ " conditional that this very action turned off (deleting the last item of the list the container is conditioned on, say)."
							+ " The container element must stay rendered even when it has nothing to show, so put the conditional INSIDE the container."
							+ " (2) The container id is misspelled and matches no container on the page.";
					// An ERROR, not a warning: the user just got a 500.
					log.error(message);
					// And the failure gets a BODY: the client logs the response body on ajax failure, so
					// the browser console explains WHY instead of showing an empty 500. Sent in production
					// too - the message holds nothing sensitive (the container id is already in the request
					// URL), and a blank 500 in a production incident is precisely the unusable kind.
					setHeader("text/plain; charset=utf-8", "content-type");
					setContent(message);
				}
			}
			finally {
				_context._setSenderID(originalSenderID);
			}
		}
		return this;
	}

	public static boolean isAjaxUpdatePass(WORequest request) {
		return ERXWOContext.contextDictionary().valueForKey(AjaxResponse.AJAX_UPDATE_PASS) != null;
	}

	/**
	 * If you click on, for instance, an AjaxInPlace, that sends a request to the server that
	 * you want to be in edit mode. The server flips its state so it is now in edit mode, but
	 * if you click a second time before the response comes back, the state has changed, so
	 * your click doesn't actually get delivered to an Ajax component. If there was no
	 * recipient of your click, it means that there also is no update container specified
	 * to be updated and your response was not turned into an AjaxResponse. This means that
	 * the second click causes the contents to be replaced with a blank.
	 *
	 * AjaxResponseDelegate looks for the confluence of all of these events and uses its
	 * fallback of pulling the update container ID out of the query parameters (generated on
	 * the Javascript side) and forces an AjaxResponse process to occur.
	 *
	 * @author mschrag
	 */
	public static class AjaxResponseDelegate implements ERXAjaxResponseDelegate {
		public WOActionResults handleNullActionResults(WORequest request, WOResponse response, WOContext context) {
			WOActionResults finalActionResults = response;
			// If it's not an AjaxResponse, it's suspect ... It means it did not
			// go through an Ajax update pass, so it might be messed up
			if (!(response instanceof AjaxResponse)) {
				// This check is pretty much trickery. It turns out that when the problem
				// that we're looking for happens, you end up with a null content length, and
				// it seems to be the fastest way to determine that we're in this state.
				String contentLength = response.headerForKey("content-length");
				if (contentLength == null) {
					// So updateContainerID never got set by the triggered action, so let's
					// pull it out of the query parameters.  This isn't ideal, but it's
					// better than sending you back a big blank component to replace whatever
					// it is you double-clicked on.
					String updateContainerID = request.stringFormValueForKey(ERXAjaxApplication.KEY_UPDATE_CONTAINER_ID);
					if (updateContainerID != null) {
						// .. .and let's make an AjaxResponse so we get our update container
						// to be evaluated
						AjaxUpdateProtocol.setUpdateContainerID(request, updateContainerID);
						finalActionResults = AjaxUtils.createResponse(request, context);
					}
				}
			}
			return finalActionResults;
		}
	}

	/**
	 * Convenience method that calls <code>AjaxUtils.appendScriptHeaderIfNecessary</code> with this request.
	 * @see er.ajax.AjaxUtils#appendScriptHeaderIfNecessary(WORequest, WOResponse)
	 */
	public void appendScriptHeaderIfNecessary() {
		AjaxUtils.appendScriptHeaderIfNecessary(_request, this);
	}

	/**
	 * Convenience method that calls <code>AjaxUtils.appendScriptFooterIfNecessary</code> with this request.
	 * @see er.ajax.AjaxUtils#appendScriptFooterIfNecessary(WORequest, WOResponse)
	 */
	public void appendScriptFooterIfNecessary() {
		AjaxUtils.appendScriptFooterIfNecessary(_request, this);
	}
}
