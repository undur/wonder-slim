package er.extensions.appserver;


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
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSNotificationCenter;

/**
 * Patch to prevent direct access to components via /wo/pagename.wo URLs
 */

public class ERXComponentRequestHandler extends WORequestHandler {

	public static NSDictionary requestHandlerValuesForRequest(WORequest aRequest) {
		NSMutableDictionary aDictionary = new NSMutableDictionary();
		NSArray pathArray = aRequest.requestHandlerPathArray();
		String lastObject = null;
		String penultElement = null;
		String aSessionID = null;
		String aContextID = null;
		String aSenderID = null;
		int p = 0;
		int count = 0;
		int length = 0;
		int pageNameLength = 0;
		boolean _lookForIDsInCookiesFirst = WORequest._lookForIDsInCookiesFirst();

		if (_lookForIDsInCookiesFirst) {
			aSessionID = aRequest.cookieValueForKey(WOApplication.application().sessionIdKey());
		}
		if (pathArray != null) {
			count = pathArray.count();
		}
		if ((pathArray != null) && (count != 0))
		{
			lastObject = (String)pathArray.lastObject();
			if (count > 1) {
				penultElement = (String)pathArray.objectAtIndex(count - 2);
			}

			length = lastObject.length();

			while ((p < length) && (Character.isDigit(lastObject.charAt(p)))) {
				p++;
			}
			if ((p < length) && (lastObject.charAt(p) == '.'))
			{
				aContextID = lastObject.substring(0, p);

				p++;
				aSenderID = lastObject.substring(p);

				if ((penultElement != null) && (penultElement.endsWith(".wo")))
				{
					pageNameLength = count - 2;
				} else if (penultElement != null)
				{
					if ((!_lookForIDsInCookiesFirst) || (aSessionID == null)) {
						aSessionID = penultElement;
					}

					pageNameLength = count - 2;
				}
				else {
					pageNameLength = 0;
				}

				if (aContextID != null) {
					aDictionary.setObjectForKey(aContextID, "wocid");
					aDictionary.setObjectForKey(aSenderID, "woeid");
				}

			}
			else {
				if (lastObject.endsWith(".wo")) {
					pageNameLength = count;
				}
				else
				{
					aSessionID = lastObject;

					pageNameLength = count - 1;
				}
			}
			
			if ((aSessionID == null) && (!_lookForIDsInCookiesFirst))
			{
				aSessionID = aRequest.stringFormValueForKey(WOApplication.application().sessionIdKey());
				if (aSessionID == null) {
					aSessionID = aRequest.cookieValueForKey(WOApplication.application().sessionIdKey());
				}
			}

		}
		else if (WOApplication.application().shouldRestoreSessionOnCleanEntry(aRequest))
		{
			aSessionID = aRequest.cookieValueForKey(WOApplication.application().sessionIdKey());
		}

		if ((aSessionID != null) && (aSessionID.length() != 0)) {
			aDictionary.setObjectForKey(aSessionID, WOApplication.application().sessionIdKey());
		}
		return aDictionary;
	}

	private WOComponent _restorePageForContextID(String oldContextID, WOSession aSession)
	{
		return aSession.restorePageForContextID(oldContextID);
	}

	private WOResponse _dispatchWithPreparedPage(WOComponent aPage, WOSession aSession, WOContext aContext, NSDictionary someElements) {
		WORequest aRequest = aContext.request();
		WOApplication anApplication = WOApplication.application();
		WOResponse aResponse = anApplication.createResponseInContext(aContext);
		String aSenderID = aContext.senderID();

		String oldContextID = aSession._contextIDMatchingIDs(aContext);
		aResponse.setHTTPVersion(aRequest.httpVersion());

		aResponse.setHeader("text/html", "content-type");

		aContext._setResponse(aResponse);

		if (oldContextID == null)
		{
			if (aSenderID != null)
			{
				if (aRequest._hasFormValues()) {
					anApplication.takeValuesFromRequest(aRequest, aContext);
				}
			}

			aContext._setPageChanged(false);
			if (aSenderID != null)
			{
				WOActionResults anActionResults = anApplication.invokeAction(aRequest, aContext);

				if ((anActionResults == null) || ((anActionResults instanceof WOComponent)))
				{
					WOComponent aResultComponent = (WOComponent)anActionResults;
					if ((aResultComponent != null) && (aResultComponent.context() != aContext)) {
						aResultComponent._awakeInContext(aContext);
					}
					boolean didPageChange = false;
					if ((aResultComponent != null) && (aResultComponent != aContext._pageElement())) {
						didPageChange = true;
					}
					aContext._setPageChanged(didPageChange);
					if (didPageChange) {
						aContext._setPageElement(aResultComponent);
					}

				}
				else
				{
					WOResponse theResponse = anActionResults.generateResponse();

					return theResponse;
				}
			}

		}
		else
		{
			WOComponent responsePage = _restorePageForContextID(oldContextID, aSession);
			aContext._setPageElement(responsePage);
		}

		anApplication.appendToResponse(aResponse, aContext);

		return aResponse;
	}

	private WOResponse _dispatchWithPreparedSession(WOSession aSession, WOContext aContext, NSDictionary someElements) {
		WOComponent aPage = null;
		WOResponse aResponse = null;
		String aPageName = (String)someElements.objectForKey("wopage");
		String oldContextID = aContext._requestContextID();
		String oldSessionID = (String)someElements.objectForKey(WOApplication.application().sessionIdKey());
		WOApplication anApplication = WOApplication.application();
		boolean clearIDsInCookies = false;

		if ((oldSessionID == null) || (oldContextID == null))
		{
			if ((aPageName == null) && (!aSession.storesIDsInCookies()))
			{
				WORequest request = aContext.request();

				String cookieHeader = request.headerForKey("cookie");

				if ((cookieHeader != null) && (cookieHeader.length() > 0)) {
					NSDictionary cookieDict = request.cookieValues();

					if ((cookieDict.objectForKey(WOApplication.application().sessionIdKey()) != null) || (cookieDict.objectForKey(WOApplication.application().instanceIdKey()) != null)) {
						clearIDsInCookies = true;
					}
				}
			}

			aPage = anApplication.pageWithName(aPageName, aContext);
		}
		else
		{
			aPage = _restorePageForContextID(oldContextID, aSession);
			if (aPage == null) {
				if (anApplication._isPageRecreationEnabled())
					aPage = anApplication.pageWithName(aPageName, aContext);
				else {
					return anApplication.handlePageRestorationErrorInContext(aContext);
				}
			}
		}
		aContext._setPageElement(aPage);
		aResponse = _dispatchWithPreparedPage(aPage, aSession, aContext, someElements);

		if (anApplication.isPageRefreshOnBacktrackEnabled()) {
			aResponse.disableClientCaching();
		}

		aSession._saveCurrentPage();
		if ((clearIDsInCookies) && (!aSession.storesIDsInCookies())) {
			aSession._clearCookieFromResponse(aResponse);
		}
		return aResponse;
	}

	private WOResponse _dispatchWithPreparedApplication(WOApplication anApplication, WOContext aContext, NSDictionary someElements) {
		WOSession aSession = null;
		WOResponse aResponse = null;
		WOResponse errorResponse = null;
		String aSessionID = (String)someElements.objectForKey(WOApplication.application().sessionIdKey());

		if (aSessionID == null) {
			
			// FIXME: OK, so I don't *think* the component request handler should ever perform session creation.
			// When allowed to create a session, it really overrides the actual mechanism for handling a missing session AND the defaultRequestHandler mechanism, and just tries to construct a component with the name "Main" (potentially just resulting in a WOPageNotFoundException). 
			// During regular use of this handler, a session should already exist. The only situation where I think it's required is if we're performing direct named page access (which this handler is not supposed to allow).
			// So, instead of creating a session if one is missing, we just go directly to handling a session restoration error.
			// This isn't really optimal because we didn't actually fail to "restore" a session, there should be separate error handling for a missing session when it should be present. But I guess this is close enough.
			// This might fail if this request handler is your application's default request handler (haven't tried it). But that's something you'd explicitly have to do,
			// and I don't know why you'd ever do that (until we decide for some reason that we need to completely replace WO's component request handler). 
			// Hugi 2025-08-02

			errorResponse = anApplication.handleSessionRestorationErrorInContext( aContext );

			/*
			 * Old logic, where this request handler was allowed to create a new session
			 *
			aSession = anApplication._initializeSessionInContext( aContext );
			if( aSession == null ) {
				errorResponse = anApplication.handleSessionCreationErrorInContext( aContext );
			}	
			*/
		}
		else {
			aSession = anApplication.restoreSessionWithID(aSessionID, aContext);
			if (aSession == null) {
				errorResponse = anApplication.handleSessionRestorationErrorInContext(aContext);
			}
		}

		if (errorResponse == null)
		{
			aResponse = _dispatchWithPreparedSession(aSession, aContext, someElements);
		}
		else aResponse = errorResponse;

		aContext._putAwakeComponentsToSleep();
		anApplication.saveSessionForContext(aContext);
		return aResponse;
	}

	WOResponse _handleRequest(WORequest aRequest)
	{
		WOContext aContext = null;
		WOResponse aResponse;

		NSDictionary requestHandlerValues = requestHandlerValuesForRequest(aRequest);

		WOApplication anApplication = WOApplication.application();
		String aSessionID = (String)requestHandlerValues.objectForKey(WOApplication.application().sessionIdKey());

		if ((!anApplication.isRefusingNewSessions()) || (aSessionID != null)) {
			String aSenderID = (String)requestHandlerValues.objectForKey("woeid");

			String oldContextID = (String)requestHandlerValues.objectForKey("wocid");

			WOStatisticsStore aStatisticsStore = anApplication.statisticsStore();
			if (aStatisticsStore != null)
				aStatisticsStore.applicationWillHandleComponentActionRequest();

			try {
				aContext = anApplication.createContextForRequest(aRequest);

				aContext._setRequestContextID(oldContextID);
				aContext._setSenderID(aSenderID);
				anApplication.awake();
				aResponse = _dispatchWithPreparedApplication(anApplication, aContext, requestHandlerValues);
				NSNotificationCenter.defaultCenter().postNotification(WORequestHandler.DidHandleRequestNotification, aContext);

				anApplication.sleep();
			}
			catch (Exception exception)
			{
				try
				{
					NSLog.err.appendln("<" + getClass().getName() + ">: Exception occurred while handling request:\n" + exception.toString());
					if (NSLog.debugLoggingAllowedForLevelAndGroups(1, 4L)) {
						NSLog.debug.appendln(exception);
					}

					if (aContext == null)
						aContext = anApplication.createContextForRequest(aRequest);
					else {
						aContext._putAwakeComponentsToSleep();
					}
					WOSession aSession = aContext._session();
					aResponse = anApplication.handleException(exception, aContext);
					if (aSession != null)
					{
						try
						{
							anApplication.saveSessionForContext(aContext);
							anApplication.sleep();
						} catch (Exception eAgain) {
							NSLog.err.appendln("<WOApplication '" + anApplication.name() + "'>: Another Exception  occurred while trying to clean the application:\n" + eAgain.toString());
							if (NSLog.debugLoggingAllowedForLevelAndGroups(1, 4L))
								NSLog.debug.appendln(eAgain);
						}
					}
				}
				finally
				{
					if ((aContext != null) && (aContext._session() != null))
						anApplication.saveSessionForContext(aContext);
				}
			}
			if (aResponse != null) {
				aResponse._finalizeInContext(aContext);
			}

			if (aStatisticsStore != null) {
				WOComponent aPage = aContext.page();
				String aName = null;
				if (aPage != null) {
					aName = aPage.name();
				}
				aStatisticsStore.applicationDidHandleComponentActionRequestWithPageNamed(aName);
			}
		}
		else {
			String newLocationURL = anApplication._newLocationForRequest(aRequest);
			String contentString = "Sorry, your request could not immediately be processed. Please try this URL: <a href=\"" + newLocationURL + "\">" + newLocationURL + "</a>";
			aResponse = anApplication.createResponseInContext(null);
			WOResponse._redirectResponse(aResponse, newLocationURL, contentString);
			aResponse._finalizeInContext(null);
		}

		return aResponse;
	}

	@Override
	public WOResponse handleRequest(WORequest aRequest)
	{
		WOApplication anApplication = WOApplication.application();
		Object globalLock = anApplication.requestHandlingLock();
		WOResponse aResponse;
		if (globalLock != null)
		{
			synchronized (globalLock) {
				aResponse = _handleRequest(aRequest);
			}
		} else {
			aResponse = _handleRequest(aRequest);
		}

		return aResponse;
	}
}