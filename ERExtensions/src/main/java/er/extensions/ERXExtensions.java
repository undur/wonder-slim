/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions;

import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;

import er.extensions.appserver.ERXNotification;
import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXUtilities;
import er.extensions.localization.ERXLocalizer;

public class ERXExtensions extends ERXFrameworkPrincipal {

	private static boolean _initialized;

	@Override
	protected void initialize() {
		NSNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector("bundleDidLoad"), ERXNotification.AllBundlesLoadedNotification.id(), null);
	}

	public void bundleDidLoad(NSNotification n) {

		if (_initialized) {
			return;
		}

		_initialized = true;

		try {
			// This will load any optional configuration files, ensures that WOOutputPath's was processed with this @@ variable substitution.
			// WOApplication uses WOOutputPath in its constructor so we need to modify it before calling the constructor.
			ERXConfigurationManager.defaultManager().initialize();

			ERXLoggingSupport.configureLoggingWithSystemProperties();
		}
		catch (Exception e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}

	@Override
	public void finishInitialization() {
		// AK: we now setup the properties three times. At startup, in ERX.init and here.
		// Note that this sucks beyond belief, as this will produce unforeseen results in several cases,
		// but it's the only way to set up all parts of the property handling.
		// The first install only loads plain and user props the second has no good way
		// to set up the main bundle and this one comes too late for static inits
		ERXConfigurationManager.defaultManager().loadConfiguration();
		ERXConfigurationManager.defaultManager().configureRapidTurnAround();
		ERXLocalizer.initialize();
		ERXProperties.pathsForUserAndBundleProperties(true);
	}
}