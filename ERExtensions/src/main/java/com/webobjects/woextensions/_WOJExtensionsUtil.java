/*
 * _WOJExtensionsUtil.java
 * (c) Copyright 2001 Apple Computer, Inc. All rights reserved.
 * This a modified version.
 * Original license: http://www.opensource.apple.com/apsl/
 */

package com.webobjects.woextensions;

import com.webobjects.appserver.WOComponent;

public class _WOJExtensionsUtil {

	public static Object valueForBindingOrNull(String binding, WOComponent component) {
		// wod bindings of the type binding = null are converted to False
		// Boolean
		// associations, which isn't always what we want. This utility method
		// assumes that a Boolean value means the binding value was intended to
		// be null
		if (binding == null) {
			return null;
		}
		Object result = component.valueForBinding(binding);
		if (result instanceof Boolean) {
			result = null;
		}
		return result;
	}
}