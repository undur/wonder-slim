package er.extensions.appserver;

/**
 * Notifications posted by us
 * 
 * FIXME: Go through these and see which, if any, deserve to survive // Hugi 2025-10-12
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
	ApplicationDidFinishInitializationNotification ( "NSApplicationDidFinishInitializationNotification" );
	
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
}