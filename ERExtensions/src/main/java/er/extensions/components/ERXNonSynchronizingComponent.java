/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr 
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.components;

import com.webobjects.appserver.WOContext;

/**
 * A component that doesn't synchronize its variables with its bindings. By default WO pushes every binding's value into
 * the same-named variable, and pulls it back out, around each phase of the request; a subclass reads its bindings
 * when it needs them, with valueForBinding().
 */
public abstract class ERXNonSynchronizingComponent extends ERXComponent {

	public ERXNonSynchronizingComponent(WOContext context) {
		super(context);
	}

	@Override
	public boolean synchronizesVariablesWithBindings() {
		return false;
	}
}