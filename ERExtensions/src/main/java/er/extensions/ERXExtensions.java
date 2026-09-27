/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions;

import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.foundation.ERXProperties;

public class ERXExtensions extends ERXFrameworkPrincipal {

	/**
	 * Loads the configuration (the Properties cascade, then the logging configuration it describes) once the
	 * application has been created. Until then, logging goes to the console appender ERXApplication.main() installs.
	 */
	@Override
	public void finishInitialization() {
		ERXConfigurationManager.defaultManager().loadConfiguration();
		ERXConfigurationManager.defaultManager().configureRapidTurnAround();
		ERXProperties.pathsForUserAndBundleProperties(true);
	}
}
