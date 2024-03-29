/*
 * WOSessionRestorationError.java
 * (c) Copyright 2001 Apple Computer, Inc. All rights reserved.
 * This a modified version.
 * Original license: http://www.opensource.apple.com/apsl/
 */

package com.webobjects.woextensions.error;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

public class WOSessionRestorationError extends WOComponent {

	public WOSessionRestorationError(WOContext aContext) {
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