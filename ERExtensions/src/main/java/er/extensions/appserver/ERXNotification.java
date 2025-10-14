package er.extensions.appserver;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;

import er.extensions.foundation.ERXUtilities;

/**
 * Static references to notification types.
 * Nicer than a string to keep track of where which notifications get observed and posted
 */

public enum ERXNotification {
	
	/**
	 * Notification to get posted when terminate() is called.
	 */
	ApplicationWillTerminateNotification( "ApplicationWillTerminateNotification" ),

	/**
	 * Notification to post when all bundles were loaded but before their principal was called
	 * 
	 * FIXME: This notification was posted by the (ERX)Loader. We need to implement at least that part again if we want to keep it // Hugi 2025-06-22 
	 */
	AllBundlesLoadedNotification( "NSBundleAllBundlesLoaded" ),

	/**
	 * Notification to post when all bundles were loaded but before their principal was called
	 */
	ApplicationDidCreateNotification( "NSApplicationDidCreateNotification" ),

	/**
	 * Notification to post when all application initialization processes are complete
	 */
	ApplicationDidFinishInitializationNotification ( "NSApplicationDidFinishInitializationNotification" ),
	
	/**
	 * FIXME: Docs 	// Hugi 2025-10-14
	 */
	ApplicationDidDispatchRequestNotification( WOApplication.ApplicationDidDispatchRequestNotification ),

	/**
	 * FIXME: Docs 	// Hugi 2025-10-14
	 */
	DidHandleRequestNotification( WORequestHandler.DidHandleRequestNotification ),
	/**
	 * FIXME: Docs 	// Hugi 2025-10-14
	 */
	ApplicationWillFinishLaunchingNotification( WOApplication.ApplicationWillFinishLaunchingNotification ),

	/**
	 * FIXME: Docs 	// Hugi 2025-10-14
	 */
	ApplicationDidFinishLaunchingNotification( WOApplication.ApplicationDidFinishLaunchingNotification );

	private String _id;

	ERXNotification( String id ) {
		_id = id;
	}
	
	/**
	 * @return The string identifier of the notification
	 */
	public String id() {
		return _id;
	}
	
	/**
	 * Register an [observer] that will invoke [methodName] when a notification is posted
	 */
	public void addObserver( final Object observer, final String methodName ) {
		NSNotificationCenter.defaultCenter().addObserver(observer, ERXUtilities.notificationSelector(methodName), this.id(), null);
	}
	
	/**
	 * Post a notification with the attached [object]
	 */
	public void postNotification( final Object object ) {
		NSNotificationCenter.defaultCenter().postNotification(new NSNotification(this.id(), object));
	}
}