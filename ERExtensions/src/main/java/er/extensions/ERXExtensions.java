/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions;

import er.extensions.appserver.ERXApplication;
import er.extensions.control.ERXControlPages;
import er.extensions.logging.ERXLoggingControlPage;

/**
 * ERExtensions' plugin, which every other plugin of the framework requires. ERExtensions' startup is ERXApplication's;
 * the plugin registers the framework's control panel pages that live in ERExtensions.
 */
public class ERXExtensions implements ERXPlugin {

	/**
	 * Registers the Logging page. Once the application is constructed, so it's listed after the pages ERControl
	 * registers before construction.
	 */
	@Override
	public void finishInitialization( final ERXApplication application ) {
		ERXControlPages.register( new ERXControlPages.Page( ERXControlPages.FRAMEWORK_CATEGORY, "logging", "Logging", "The logging backend, where each logger's level comes from, and levels set on this instance.", ERXLoggingControlPage.class ) );
	}
}
