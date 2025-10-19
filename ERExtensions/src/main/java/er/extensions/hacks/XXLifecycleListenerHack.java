package er.extensions.hacks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;

import er.extensions.appserver.ERXNotification;
import er.extensions.foundation.ERXExceptionUtilities;
import er.extensions.foundation.ERXUtilities;

/**
 * FIXME: Ugly hack for us to observe the application's initialization lifecycle // Hugi 2025-10-19 
 */

public class XXLifecycleListenerHack {


	public static void activate() {
		System.out.println("Enable lifecycle event logging");

		List.of(
				ERXNotification.ApplicationWillFinishLaunchingNotification.id(),
				ERXNotification.ApplicationDidFinishLaunchingNotification.id(),
				ERXNotification.AllBundlesLoadedNotification.id(),
				ERXNotification.ApplicationDidCreateNotification.id(),
				ERXNotification.ApplicationDidFinishInitializationNotification.id(),
				ERXNotification.ApplicationDidFinishLaunchingNotification.id() )
		.forEach( notificationName -> {
			NSNotificationCenter.defaultCenter().addObserver(new LifeCycleObserver(), ERXUtilities.notificationSelector("logLifecycleEvent"), notificationName, null);
		});
	}
	
	public static class LifeCycleObserver {
		private static final Set<LifeCycleObserver> OBSERVERS = new HashSet<>();
		
		public LifeCycleObserver() {
			OBSERVERS.add( this );
		}

		public void logLifecycleEvent( NSNotification n ) {
			System.out.println();
			System.out.println( "<<<< =========================================================================================================== >>>>" );
			System.out.println( "<<<< == LIFECYCLE : " + n.name() );
			System.out.println( ERXExceptionUtilities.stackTrace());
			System.out.println( "<<<< =========================================================================================================== >>>>" );
			System.out.println();
		}
	}
}