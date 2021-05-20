package com.webobjects.foundation.development;

import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSMutableArray;

public abstract class NSBundleFactory {
	private static NSMutableArray<NSBundleFactory> _bundleFactories = new NSMutableArray();

	public static void registerBundleFactory(NSBundleFactory bundleFactory) {
		_bundleFactories.insertObjectAtIndex(bundleFactory, 0);
	}

	public static NSBundle bundleForPathWithRegistry(String path, boolean shouldCreateBundle, boolean newIsJar) {
		synchronized (NSBundle.class) {
			NSBundle bundle = null;
			for (NSBundleFactory bundleFactory : _bundleFactories) {
				bundle = bundleFactory.bundleForPath(path, shouldCreateBundle, newIsJar);
				if (bundle != null)
					break;
			}
			if (bundle != null)
				NSBundle.addBundle(bundle);
			return bundle;
		}
	}

	public abstract NSBundle bundleForPath(String paramString, boolean paramBoolean1, boolean paramBoolean2);
}