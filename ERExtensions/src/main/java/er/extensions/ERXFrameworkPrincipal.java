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
import er.extensions.foundation.ERXUtilities;

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
 *     public static final Logger log = Logger.getLogger(ExampleFrameworkPrincipal.class);
 * 
 *     protected static ExampleFrameworkPrincipal sharedInstance;
 *     
 *     public final static Class REQUIRES[] = new Class[] {ERXExtensions.class, ERDirectToWeb.class, ERJavaMail.class};
 * 
 *     // Registers the class as the framework principal
 *     static {
 *         setUpFrameworkPrincipalClass(ExampleFrameworkPrincipal.class);
 *     }
 * 
 *     public static ExampleFrameworkPrincipal sharedInstance() {
 *         if (sharedInstance == null) {
 *             sharedInstance = (ExampleFrameworkPrincipal)sharedInstance(ExampleFrameworkPrincipal.class);
 *         }
 *         return sharedInstance;
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

    public static class Observer {
        
        /**
         * Notification method called when the WOApplication posts the notification 'ApplicationDidCreateNotification'.
         * This method handles de-registering for notifications and releasing any references to observer so that it can be released for garbage collection.
         * 
         * @param n notification that is posted after the WOApplication has been constructed, but before the application is ready for accepting requests.
         */
        public final void willFinishInitialization(NSNotification n) {
            NSNotificationCenter.defaultCenter().removeObserver(this, ERXNotification.ApplicationDidCreateNotification.id(), null);

            for (ERXFrameworkPrincipal principal : launchingFrameworks) {
                principal.finishInitialization();
                log("finishInitialization for " + principal.getClass().getSimpleName());
            }
        }
        
        /**
         * Notification method called when the WOApplication posts the notification 'ApplicationDidFinishInitializationNotification'.
         * This method handles de-registering for notifications and releasing any references to observer so that it can be released for garbage collection.
         * 
         * @param n notification that is posted after the WOApplication has been constructed, but before the application is ready for accepting requests.
         */
        public final void didFinishInitialization(NSNotification n) {
            NSNotificationCenter.defaultCenter().removeObserver(this);

            for (ERXFrameworkPrincipal principal : launchingFrameworks) {
                principal.didFinishInitialization();
            }
            
            log("didFinishInitialization");
        }
    }
    
    private static Observer observer;
    
    /**
     * Gets the shared framework principal instance for a given class.
     * 
     * @param c principal class for a given framework
     * @return framework principal initializer
     */
    public static<T extends ERXFrameworkPrincipal> T sharedInstance(Class<T> c) {
        return (T)initializedFrameworks.get(c.getName());
    }
    
    /**
     * Sets up a given framework principal class to receive notification when it is safe for the framework to be initialized.
     * 
     * @param c principal class
     */
    public static void setUpFrameworkPrincipalClass(Class c) {

    	log("setUpFrameworkPrincipalClass for " + c.getSimpleName());

        if (initializedFrameworks.get(c.getName()) != null) {
        	return;
        }

        try {
            if(observer == null) {
                observer = new Observer();

                final NSNotificationCenter center = NSNotificationCenter.defaultCenter();

                center.addObserver(observer,
                        ERXUtilities.notificationSelector("willFinishInitialization"),
                        ERXNotification.ApplicationDidCreateNotification.id(),
                        null);

                center.addObserver(observer,
                		ERXUtilities.notificationSelector("didFinishInitialization"),
                		ERXNotification.ApplicationDidFinishInitializationNotification.id(),
                        null);
            }

            if (initializedFrameworks.get(c.getName()) == null) {
                try {
                    Field f = c.getField("REQUIRES");
                    Class requires[] = (Class[]) f.get(c);
                    for (int i = 0; i < requires.length; i++) {
                    	Class requirement = requires[i];
                    	if(initializedFrameworks.get(requirement.getName()) == null) {
                    		setUpFrameworkPrincipalClass(requirement);
                    	}
                    }
                }
                catch (NoSuchFieldException e) {
                    // No REQUIRES field present
                }
                catch (IllegalAccessException e) {
                    log("Can't read field REQUIRES from " + c.getName() + ", check if it is 'public static Class[] REQUIRES= new Class[] {...}' in this class");
                    throw NSForwardException._runtimeExceptionForThrowable(e);
                }

                if(initializedFrameworks.get(c.getName()) == null) {
                	ERXFrameworkPrincipal principal = (ERXFrameworkPrincipal)c.newInstance();
                	initializedFrameworks.put(c.getName(),principal);
                	principal.initialize();
                	launchingFrameworks.add(principal);

                	log("Initialized : " + c.getName());
                }

            }
            else {
            	log("Was already inited: " + c.getName());
            }
        }
        catch (InstantiationException | IllegalAccessException e) {
            throw NSForwardException._runtimeExceptionForThrowable(e);
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