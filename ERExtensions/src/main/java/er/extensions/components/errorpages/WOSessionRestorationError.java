/*
 * WOSessionRestorationError.java
 * (c) Copyright 2001 Apple Computer, Inc. All rights reserved.
 * This a modified version.
 * Original license: http://www.opensource.apple.com/apsl/
 */

package er.extensions.components.errorpages;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

/**
 * Found by name: WOApplication.handleSessionRestorationErrorInContext() renders the page named "WOSessionRestorationError". WO's own lives in
 * JavaWOExtensions, which wonder-slim doesn't use, so this is the one it finds. Like the original, it's excluded from
 * event logging and not cached by the browser.
 */
public class WOSessionRestorationError extends WOComponent {

	public WOSessionRestorationError(WOContext aContext) {
		super(aContext);
	}

	@Override
	public boolean isEventLoggingEnabled() {
		return false;
	}

	/**
	 * @return The session timeout in whole minutes, for telling the user how long inactivity is tolerated
	 */
	public int sessionTimeoutMinutes() {
		return application().sessionTimeOut().intValue() / 60;
	}

	@Override
	public void appendToResponse(WOResponse aResponse, WOContext aContext) {
		super.appendToResponse(aResponse, aContext);
		aResponse.disableClientCaching();
	}
}