/*
 * WOEventSetup.java
 * (c) Copyright 2001 Apple Computer, Inc. All rights reserved.
 * This a modified version.
 * Original license: http://www.opensource.apple.com/apsl/
 */

package com.webobjects.woextensions.events;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WODirectAction;
import com.webobjects.appserver.WORequest;

public class WOEventSetup extends WODirectAction {

	public WOEventSetup(WORequest aRequest) {
		super(aRequest);
	}

	@Override
	public WOActionResults defaultAction() {
		return pageWithName("WOEventSetupPage");
	}
}