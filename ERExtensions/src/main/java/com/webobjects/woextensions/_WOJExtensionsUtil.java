/*
 * _WOJExtensionsUtil.java
 * (c) Copyright 2001 Apple Computer, Inc. All rights reserved.
 * This a modified version.
 * Original license: http://www.opensource.apple.com/apsl/
 */

package com.webobjects.woextensions;

import com.webobjects.appserver.WOComponent;

/**
 * FIXME: Replaceable with something from ERXValueUtilities or ERXComponentUtilities? 
 */

public class _WOJExtensionsUtil {

	/**
	 * bindings of the type binding = null are converted to a false Boolean associations, which isn't always what we want.
	 * This utility method assumes that a Boolean value means the binding value was intended to be null
	 */
	public static Object valueForBindingOrNull(String binding, WOComponent component) {

		// FIXME: I don't actually think we want to allow this to be null // Hugi 2022-03-21
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