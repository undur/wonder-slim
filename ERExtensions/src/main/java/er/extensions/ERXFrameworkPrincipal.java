package er.extensions;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;

import er.extensions.appserver.ERXNotification;

/** 
 * Designated starter class for frameworks, adds support for dependency management.
 * 
 * Allows you to disregard your framework order in the class path (at least where startup is concerned, if you override actual classes you still need to take care.)
 * 
 * The <code>initialize()</code> method will be called directly after your principal is instantiated.
 * The <code>finishInitialization()</code> method will be called when the app finishes startup but before it will begin to process requests.
 * 
 * If you define <pre><code>public static Class[] REQUIRES = Class[] {...}</code></pre> all the classes (which must be assignable from this class) will get loaded before your principal.
 * 
 * NOTE: try to avoid putting code in static initializers. These may lead to unpredictable behaviour when launching. Use one of the methods above to do what you need to do.
 * 
 * Here is an example:
 * 
 * <pre><code>
 * public class ExampleFrameworkPrincipal extends ERXFrameworkPrincipal {
 * 
 *     public final static Class REQUIRES[] = new Class[] {ERXExtensions.class, ERDirectToWeb.class, ERJavaMail.class};
 * 
 *     // Registers the class as the framework principal
 *     static {
 *         setUpFrameworkPrincipalClass(ExampleFrameworkPrincipal.class);
 *     }
 * 
 *     public void initialize() {
 *         // code during startup
 *     }
 * 
 *     public void finishInitialization() {
 *         // Initialized shared data
 *     }
 * }</code></pre>
 * Finally, you also need to add an entry to your framework build.properties file to set the principal class:
 * <pre><code>
 * principalClass = com.sample.ExampleFrameworkPrincipal
 * </code></pre>
 */

public abstract class ERXFrameworkPrincipal {

    /**
     * Mapping between framework principal classes and ERXFrameworkPrincipal objects
     */
    private static final Map<String, ERXFrameworkPrincipal> initializedFrameworks = new HashMap<>();

    private static final List<ERXFrameworkPrincipal> launchingFrameworks = new ArrayList<>();

    private static Observer observer;

    public static class Observer {
        
    	private Observer() {
    		ERXNotification.ApplicationDidCreateNotification.addObserver(this, "finishInitialization");
    		ERXNotification.ApplicationDidFinishInitializationNotification.addObserver(this, "didFinishInitialization");
    	}

        /**
         * Invoked when WOApplication posts 'ApplicationDidCreateNotification'.
         * Handles de-registering for notifications and releasing any references to observer so that it can be released for garbage collection.
         * 
         * @param n notification that is posted after the WOApplication has been constructed, but before the application is ready for accepting requests.
         */
        public final void finishInitialization(NSNotification n) {
            NSNotificationCenter.defaultCenter().removeObserver(this, ERXNotification.ApplicationDidCreateNotification.id(), null);

            for (ERXFrameworkPrincipal principal : launchingFrameworks) {
                principal.finishInitialization();
                log("finishInitialization() on " + principal.getClass().getSimpleName());
            }
            
            log("finishInitialization() completed" );
        }
        
        /**
         * Invoked when WOApplication posts 'ApplicationDidFinishInitializationNotification'.
         * Handles de-registering for notifications and releasing any references to observer so that it can be released for garbage collection.
         */
        public final void didFinishInitialization(NSNotification n) {
            NSNotificationCenter.defaultCenter().removeObserver(this);

            for (ERXFrameworkPrincipal principal : launchingFrameworks) {
                principal.didFinishInitialization();
                log("didFinishInitialization() on " + principal.getClass().getSimpleName());
            }
            
            log("didFinishInitialization() completed");
        }
    }
    
    /**
     * @return The shared framework principal instance for the given class.
     */
    public static <T extends ERXFrameworkPrincipal> T sharedInstance(Class<T> principalClass) {
        return (T)initializedFrameworks.get(principalClass.getName());
    }
    
    /**
     * @return true if the given principal class has been initialized
     */
    private static boolean isInitialized( Class<? extends ERXFrameworkPrincipal> principalClass ) {
    	return initializedFrameworks.containsKey(principalClass.getName());
    }

	/**
	 * Sets up the given framework principal class to receive notification when it is safe for the framework to be initialized.
	 */
	public static void setUpFrameworkPrincipalClass(Class<? extends ERXFrameworkPrincipal> principalClass) {

		log("setUpFrameworkPrincipalClass for " + principalClass.getSimpleName());

		if (isInitialized(principalClass)) {
			return;
		}

		if (observer == null) {
			observer = new Observer();
		}

		if (!isInitialized(principalClass)) {
			try {
				final Field requiresField = principalClass.getField("REQUIRES");
				final Class requires[] = (Class[]) requiresField.get(principalClass);

				for (final Class requiredPrincipalClass : requires) {
					if (!isInitialized(requiredPrincipalClass)) {
						setUpFrameworkPrincipalClass(requiredPrincipalClass);
					}
				}
			}
			catch (NoSuchFieldException e) {
				// No REQUIRES field present
			}
			catch (IllegalAccessException e) {
				throw new RuntimeException("Can't read REQUIRES field on framework principal" + principalClass.getName(), e);
			}

			if (!isInitialized(principalClass)) {
				try {
					final ERXFrameworkPrincipal principalInstance = principalClass.newInstance();
					initializedFrameworks.put(principalClass.getName(), principalInstance);
					principalInstance.initialize();
					launchingFrameworks.add(principalInstance);

					log("initialize() on " + principalClass.getName());
				}
				catch (InstantiationException | IllegalAccessException e) {
					throw NSForwardException._runtimeExceptionForThrowable(e);
				}
			}
		}
		else {
			log("Already initialized: " + principalClass.getName());
		}
	}

    /**
     * Called directly after the constructor.
     */
    protected void initialize() {}

    /**
     * Overridden by subclasses to provide framework initialization.
     */
    public abstract void finishInitialization();
    
    /**
     * Overridden by subclasses to finalize framework initialization.
     */
    public void didFinishInitialization() {}

    /**
     * Log to System.out while we don't have the logging system set up.
     */
    private static final void log( Object s ) {
    	String message = s == null ? "[null]" : s.toString();
    	System.out.println( " == ERXFrameworkPrincipal.log == > " + message);
    }
}