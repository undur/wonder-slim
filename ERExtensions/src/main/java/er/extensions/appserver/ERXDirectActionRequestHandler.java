//
// ERXDirectActionRequestHandler.java
// Project ERExtensions
//
// Created by tatsuya on Thu Aug 15 2002
//
package er.extensions.appserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOSession;
import com.webobjects.appserver._private.WODirectActionRequestHandler;
import com.webobjects.appserver._private.WOServerSessionStore;

import er.extensions.foundation.ERXProperties;

/**
 * Improved direct action request handler. Will automatically handle
 * character encodings and checks the {@link ERXWOResponseCache} before
 * actually calling the action.
 * 
 * NOTE: This class is multi thread safe. 
 *
 * @property er.extensions.ERXMessageEncoding.Enabled
 */
public class ERXDirectActionRequestHandler extends WODirectActionRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(ERXDirectActionRequestHandler.class);

    /** caches if automatic message encoding is enabled, defaults to true */
    protected static Boolean automaticMessageEncodingEnabled;
    
    /**
     * Allows the disabling of automatic message encoding. Useful for
     * backend services where you want to just use the default encoding.
     * @return if automatic message encoding is enabled.
     */
    public static boolean automaticMessageEncodingEnabled() {
        if (automaticMessageEncodingEnabled == null) {
            automaticMessageEncodingEnabled = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXMessageEncoding.Enabled", true) ? Boolean.TRUE : Boolean.FALSE;
        }
        return automaticMessageEncodingEnabled.booleanValue();
    }
    
    public ERXDirectActionRequestHandler() {
        super();
    }
    
    public ERXDirectActionRequestHandler(String actionClassName,
                                         String defaultActionName,
					boolean shouldAddToStatistics) {
        super(actionClassName, defaultActionName, shouldAddToStatistics);
    }

    /**
     * Return true if you want to handle the request even though the app is refusing new sessions.
     * Currently, this includes all urls with "stats" in them
     * @param request
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
    		} else {
    			// if no session was supplied, what are we doing here in the
				// first place? The adaptor shouldn't have linked to us as
				// we are refusing new sessions.
				response = generateRequestRefusal(request);
    		}
    	}
    	if(response == null) {
    		response = super.handleRequest(request);
    	}

        if (automaticMessageEncodingEnabled()) {
            ERXMessageEncoding messageEncoding = null;
            
            final WOSession session = ERXSession.anySession();

            if (session != null  &&  session instanceof ERXSession) {
                ERXSession erxSession = (ERXSession)session;
                messageEncoding = erxSession.messageEncoding();
                erxSession.lastActionWasDA = true;
            } else if (request instanceof ERXRequest) {
                ERXBrowser browser = ((ERXRequest)request).browser();
                messageEncoding = browser.messageEncodingForRequest(request);
            } else {
                messageEncoding = new ERXMessageEncoding(request.browserLanguages());
            }
            messageEncoding.setEncodingToResponse(response);
        }

        return response;
    }
}