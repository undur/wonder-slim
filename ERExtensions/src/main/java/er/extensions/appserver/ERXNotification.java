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
	 * Posted when ERXApplication.terminate() is called (and before WOApplication.terminate() is invoked)
	 */
	ApplicationWillTerminateNotification( "ApplicationWillTerminateNotification" ),

	/**
	 * Posted when all bundles were loaded but before their principal was called
	 * 
	 * FIXME: This notification was posted by the (ERX)Loader. We need to implement at least that part again if we want to keep it // Hugi 2025-06-22 
	 */
	AllBundlesLoadedNotification( "NSBundleAllBundlesLoaded" ),

	/**
	 * Posted at the very end of the ERXApplication constructor
	 */
	ApplicationDidCreateNotification( "NSApplicationDidCreateNotification" ),

	/**
	 * Posted when all application initialization processes are complete (after ERXApplication.finishInitialization() has been run)
	 */
	ApplicationDidFinishInitializationNotification ( "NSApplicationDidFinishInitializationNotification" ),
	
	/**
	 * FIXME: Docs?	// Hugi 2025-10-14
	 */
	ApplicationWillFinishLaunchingNotification( WOApplication.ApplicationWillFinishLaunchingNotification ),
	
	/**
	 * FIXME: Docs?	// Hugi 2025-10-14
	 */
	ApplicationDidFinishLaunchingNotification( WOApplication.ApplicationDidFinishLaunchingNotification ),

	/**
	 * Posted just before WOApplication.dispatchRequest() returns
	 */
	ApplicationDidDispatchRequestNotification( WOApplication.ApplicationDidDispatchRequestNotification ),

	/**
	 * Posted by request handler's at some point when they consider the request handled (what constitutes "handled" is for them to decide)
	 */
	DidHandleRequestNotification( WORequestHandler.DidHandleRequestNotification );

	private String _id;

	private ERXNotification( String id ) {
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
		NSNotificationCenter.defaultCenter().addObserver(observer, ERXUtilities.notificationSelector(methodName), id(), null);
	}
	
	/**
	 * Post a notification with the attached [object]
	 */
	public void postNotification( final Object object ) {
		NSNotificationCenter.defaultCenter().postNotification(new NSNotification(id(), object));
	}
}