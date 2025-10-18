package er.extensions.appserver;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

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
	 * Posted before WOApplication.dispatchRequest() actually handles the request it's main work. Notification object is the request about to be handled.
	 */
	ApplicationWillDispatchRequestNotification( WOApplication.ApplicationWillDispatchRequestNotification ),

	/**
	 * Posted before WOApplication.dispatchRequest() returns. Notification object is the response about to be returned.
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
	 * Register an [observer] that will invoke [methodName] when the notification is posted
	 */
	public void addObserver( final Object observer, final String methodName ) {
		NSNotificationCenter.defaultCenter().addObserver(observer, ERXUtilities.notificationSelector(methodName), id(), null);
	}

	/**
	 * Keeps references to observers added using lambda-style syntax (so they don't get garbage collected)
	 * 
	 * FIXME: Experimental. Primitive. Ooga Booga Booga // Hugi 2025-10-18
	 */
	private static final Set<Object> _retainedObservers = new CopyOnWriteArraySet<>();

	/**
	 * Register the given Consumer to be invoked when the notification is posted
	 * 
	 * FIXME: Don't think NSNotificationCenter retains observers so we're explicitly retaining them ourselves _forever_ at the moment // Hugi 2025-10-18
	 */
	public void addObserver( final Consumer<NSNotification> notificationConsumer ) {
		final GenericObserver observer = new GenericObserver( notificationConsumer );
		_retainedObservers.add( observer );
		NSNotificationCenter.defaultCenter().addObserver(observer, ERXUtilities.notificationSelector("consume"), id(), null);
	}

	/**
	 * Post a notification with the attached [object]
	 */
	public void postNotification( final Object object ) {
		NSNotificationCenter.defaultCenter().postNotification(new NSNotification(id(), object));
	}
	
	/**
	 * Wraps a Notification Consumer so we can register observers using lambda syntax
	 */
	public record GenericObserver( Consumer<NSNotification> consumer ) {
		
		public void consume( NSNotification n ) {
			consumer.accept(n);
		}
	}
}