/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSMutableSet;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSSelector;

import er.extensions.appserver.ERXApplication;

/**
 * The file notification center is only used in development systems. It provides
 * a nice repository about files and their last modified dates. So instead of
 * every dynamic spot having to keep track of the files' dates, register and
 * check at the end of every request-response loop, instead you can just add an
 * observer to this center and be notified when the file changes. Files' last
 * modification dates are checked at the end of every request-response loop.
 * 
 * It should be noted that the current version of the file notification center
 * will retain a reference to each registered observer. This is not ideal and
 * will be corrected in the future.
 */

public class ERXFileNotificationCenter {

	private static final Logger log = LoggerFactory.getLogger(ERXFileNotificationCenter.class);

	/**
	 * Contains the name of the notification that is posted when a file changes.
	 */
	private static final String FileDidChange = "FileDidChange";

	/**
	 * Flag to indicate if caching is enabled
	 */
	private static final boolean _isDevelopmentMode = ERXApplication.isDevelopmentModeSafe();

	/**
	 * holds a reference to the default file notification center
	 */
	private static final ERXFileNotificationCenter _defaultCenter = new ERXFileNotificationCenter();

	/**
	 * collections of observers by file path
	 */
	private final NSMutableDictionary _observersByFilePath = new NSMutableDictionary();

	/**
	 * cache for last modified dates of files by file path
	 */
	private final NSMutableDictionary _lastModifiedByFilePath = new NSMutableDictionary();

	/**
	 * The last time we checked files. We only check if !WOCachingEnabled or if there is a CheckFilesPeriod set
	 */
	private long _lastCheckMillis = System.currentTimeMillis();

	/**
	 * FIXME: Docs // Hugi 2025-10-19
	 */
	private final boolean _symlinkSupport;

	/**
	 * Default constructor. If you are in development mode then this object will
	 * register for the notification
	 * {@link com.webobjects.appserver.WOApplication#ApplicationWillDispatchRequestNotification}
	 * which will enable it to check if files have changed at the end of every
	 * request-response loop. If WOCaching is enabled then this object will not
	 * register for anything and will generate warning messages if observers are
	 * registered with caching enabled.
	 */
	private ERXFileNotificationCenter() {

		if (_isDevelopmentMode || checkFilesPeriod() > 0) {
			log.debug("Caching disabled.  Registering for notification: {}", WOApplication.ApplicationWillDispatchRequestNotification);
			NSNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector("checkIfFilesHaveChanged"), WOApplication.ApplicationWillDispatchRequestNotification, null);
		}

		// MS: In case we are touching properties before they're fully materialized or messed up from a failed reload, lets use System.props here
		_symlinkSupport = Boolean.valueOf(System.getProperty("ERXFileNotificationCenter.symlinkSupport", "true"));
	}

	/**
	 * @return the singleton instance of file notification center
	 */
	public static ERXFileNotificationCenter defaultCenter() {
		return _defaultCenter;
	}

	/**
	 * In seconds. 0 means we will not regularly check files.
	 */
	private static int checkFilesPeriod() {
		return ERXProperties.intForKeyWithDefault("er.extensions.ERXFileNotificationCenter.CheckFilesPeriod", 0);
	}

	/**
	 * Register file observer for a file
	 * 
	 * @param observer object to be notified when a file changes
	 * @param selector selector to be invoked on the observer when the file changes.
	 * @param filePath location of the file
	 */
	public void addObserver(Object observer, NSSelector selector, String filePath) {
		Objects.requireNonNull(filePath);

		addObserver(observer, selector, new File(filePath));
	}

	/**
	 * Register file observer for a File
	 * 
	 * @param observer object to be notified when a file changes
	 * @param selector selector to be invoked on the observer when the file changes.
	 * @param file file to watch for changes
	 */
	public void addObserver(Object observer, NSSelector selector, File file) {
		Objects.requireNonNull(observer, "Attempting to register null observer for file: " + file);
		Objects.requireNonNull(selector, "Attempting to register null selector for file: " + file);
		Objects.requireNonNull(file, "Attempting to register a null file." );

		if (!_isDevelopmentMode && checkFilesPeriod() == 0) {
			log.info("Registering an observer when file checking is disabled (WOCaching must be " +
					"disabled or the er.extensions.ERXFileNotificationCenter.CheckFilesPeriod " +
					"property must be set).  This observer will not ever by default be called: {}", file);
		}

		String filePath = cacheKeyForFile(file);

		log.debug("Registering Observer for file at path: {}", filePath);

		// Register last modified date.
		registerLastModifiedDateForFile(file);

		// FIXME: This retains the observer. This is not ideal. With the 1.3 JDK we can use a ReferenceQueue to maintain weak references.
		NSMutableSet observerSet = (NSMutableSet) _observersByFilePath.objectForKey(filePath);

		if (observerSet == null) {
			observerSet = new NSMutableSet();
			_observersByFilePath.setObjectForKey(observerSet, filePath);
		}

		observerSet.addObject(new _ObserverSelectorHolder(observer, selector));
	}

	/**
	 * Returns the path that should be used as the cache key for the given file.
	 * This will return the absolute path of the file (specifically NOT the
	 * canonical path) so that we make sure to lookup files using their original
	 * sym links rather than resolving them at registration time.
	 * 
	 * @param file the file to lookup a cache key for
	 * @return the absolute path of the file
	 */
	private String cacheKeyForFile(File file) {
		return file.getAbsolutePath();
	}

	/**
	 * Returns the value to cache to detect changes to this file. Currently this
	 * returns the lastModified date of the canonicalized version of this file,
	 * meaning that we compare the lastModified of the target of symlinks.
	 * 
	 * @param file the file to lookup a cache value for
	 * @return a value representing the current version of this file
	 */
	private Object cacheValueForFile(File file) {
		if (_symlinkSupport) {
			try {
				// MS: We want to compute the last modified time on the destination of a (possibly)
				// symlinked file. On OS X, the lastModified of the sym link itself matches the
				// lastModified of the referenced file, but I didn't want to presume that behavior.
				File canonicalFile = file.getCanonicalFile();
				return canonicalFile.getPath() + ":" + Long.valueOf(canonicalFile.lastModified());
			}
			catch (IOException e) {

				// MS: return a zero to match the previous semantics from calling file.lastModified() on a missing file.
				log.warn("Failed to determine the lastModified time on '{}': {}", file, e.getMessage());

				return Long.valueOf(0);
			}
		}
		return Long.valueOf(file.lastModified());
	}

	/**
	 * Records the last modified date of the file for future comparison.
	 * 
	 * @param file file to record the last modified date
	 */
	private void registerLastModifiedDateForFile(File file) {
		if (file != null) {
			// Note that if the file doesn't exist, it will be registered with a 0 lastModified time by virtue of the semantics of File.lastModified.
			_lastModifiedByFilePath.setObjectForKey(cacheValueForFile(file), cacheKeyForFile(file));
		}
	}

	/**
	 * Compares the last modified date of the file with the last recorded modification date.
	 * 
	 * @param file file to compare last modified date.
	 * @return if the file has changed since the last time the <code>lastModified</code> value was recorded.
	 */
	private boolean hasFileChanged(File file) {
		Objects.requireNonNull( file, "Attempting to check if a null file has been changed");

		Object previousCacheValue = _lastModifiedByFilePath.objectForKey(cacheKeyForFile(file));
		return previousCacheValue == null || !previousCacheValue.equals(cacheValueForFile(file));
	}

	/**
	 * Only used internally. Notifies all of the observers who have been
	 * registered for the given file.
	 * 
	 * @param file file that has changed
	 */
	private void fileHasChanged(File file) {
		final NSMutableSet observers = (NSMutableSet) _observersByFilePath.objectForKey(cacheKeyForFile(file));

		if (observers == null) {
			log.warn("Unable to find observers for file: {}", file);
		}
		else {
			final NSNotification notification = new NSNotification(FileDidChange, file);

			for (Enumeration e = observers.objectEnumerator(); e.hasMoreElements();) {
				_ObserverSelectorHolder holder = (_ObserverSelectorHolder) e.nextElement();

				try {
					holder.selector.invoke(holder.observer, notification);
				}
				catch (Exception ex) {
					log.error("Catching exception when invoking method on observer: {}", ex, ex);
				}
			}

			registerLastModifiedDateForFile(file);
		}
	}

	/**
	 * Notified by the NSNotificationCenter at the end of every request-response
	 * loop. It is here that all of the currently watched files are checked to
	 * see if they have any changes.
	 * 
	 * @param n notification posted from the NSNotificationCenter.
	 */
	public void checkIfFilesHaveChanged(NSNotification n) {
		int checkPeriod = checkFilesPeriod();

		if (!_isDevelopmentMode && (checkPeriod == 0 || System.currentTimeMillis() - _lastCheckMillis < 1000 * checkPeriod)) {
			return;
		}

		_lastCheckMillis = System.currentTimeMillis();

		log.debug("Checking if files have changed");

		for (Enumeration e = _lastModifiedByFilePath.keyEnumerator(); e.hasMoreElements();) {
			File file = new File((String) e.nextElement());

			if (file.exists() && hasFileChanged(file)) {
				fileHasChanged(file);
			}
		}
	}

	/**
	 * Simple observer-selector holder class.
	 * 
	 * @param observer Observing object FIXME: Should be a weak reference
	 * @param selector Selector to call on observer 
	 */
	public record _ObserverSelectorHolder( Object observer, NSSelector selector) {}
}