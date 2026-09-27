/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr 
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.components;

import com.webobjects.appserver.WOContext;

/**
 * A stateless component: WO shares pooled instances between pages, requests and threads, and calls reset() after each
 * use, so a subclass keeps no state of its own between phases. Stateless components don't synchronize with their
 * bindings either (see ERXNonSynchronizingComponent).
 */
public abstract class ERXStatelessComponent extends ERXNonSynchronizingComponent {

	public ERXStatelessComponent(WOContext context) {
		super(context);
	}

	@Override
	public boolean isStateless() {
		return true;
	}
}