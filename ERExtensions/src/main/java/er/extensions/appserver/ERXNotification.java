package er.extensions.appserver;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
	 * The observers registered as lambdas. NSNotificationCenter holds its observers weakly, and an observer wrapping a
	 * lambda has no other owner, so without this reference it would be garbage collected and silently stop firing. An
	 * entry lives until its {@link Registration} is removed.
	 */
	private static final Set<GenericObserver> _retainedObservers = ConcurrentHashMap.newKeySet();

	/**
	 * Registers the given Consumer to be invoked when the notification is posted.
	 *
	 * The observer lives until the returned registration is removed: an observer registered once for the application's
	 * lifetime can ignore it, but anything shorter-lived (a session, a component, a handler) must remove it when done, or
	 * the observer stays registered, and keeps firing, forever.
	 *
	 * @return The registration, for removing the observer again
	 */
	public Registration addObserver( final Consumer<NSNotification> notificationConsumer ) {
		final GenericObserver observer = new GenericObserver( notificationConsumer );
		_retainedObservers.add( observer );
		NSNotificationCenter.defaultCenter().addObserver( observer, ERXUtilities.notificationSelector( "consume" ), id(), null );
		return new Registration( this, observer );
	}

	/**
	 * An observer registered with {@link #addObserver(Consumer)}. {@link #remove()} (or {@link #close()}) unregisters it
	 * from the notification center and releases it; removing it again does nothing.
	 */
	public static final class Registration implements AutoCloseable {

		private final ERXNotification _notification;
		private final GenericObserver _observer;

		private Registration( final ERXNotification notification, final GenericObserver observer ) {
			_notification = notification;
			_observer = observer;
		}

		public void remove() {
			NSNotificationCenter.defaultCenter().removeObserver( _observer, _notification.id(), null );
			_retainedObservers.remove( _observer );
		}

		@Override
		public void close() {
			remove();
		}
	}

	/**
	 * Post a notification with the attached [object]
	 */
	public void postNotification( final Object object ) {
		NSNotificationCenter.defaultCenter().postNotification(new NSNotification(id(), object));
	}
	
	/**
	 * Wraps a Notification Consumer so observers can be registered using lambda syntax. Compared by identity, so the same
	 * lambda registered twice is two observers, each removed by its own registration. Public only because the notification
	 * center invokes {@link #consume(NSNotification)} reflectively; created only by {@link ERXNotification#addObserver(Consumer)}.
	 */
	public static final class GenericObserver {

		private final Consumer<NSNotification> _consumer;

		private GenericObserver( final Consumer<NSNotification> consumer ) {
			_consumer = consumer;
		}

		public void consume( final NSNotification n ) {
			_consumer.accept( n );
		}
	}
}