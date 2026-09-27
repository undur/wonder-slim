/*
 * WOPageRestorationError.java
 * (c) Copyright 2001 Apple Computer, Inc. All rights reserved.
 * This a modified version.
 * Original license: http://www.opensource.apple.com/apsl/
 */

package er.extensions.components.errorpages;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

/**
 * Found by name: WOApplication.handlePageRestorationErrorInContext() renders the page named "WOPageRestorationError". WO's own lives in
 * JavaWOExtensions, which wonder-slim doesn't use, so this is the one it finds. Like the original, it's excluded from
 * event logging and not cached by the browser.
 */
public class WOPageRestorationError extends WOComponent {

	public WOPageRestorationError(WOContext aContext) {
		super(aContext);
	}

	@Override
	public boolean isEventLoggingEnabled() {
		return false;
	}

	@Override
	public void appendToResponse(WOResponse aResponse, WOContext aContext) {
		super.appendToResponse(aResponse, aContext);
		aResponse.disableClientCaching();
	}
}