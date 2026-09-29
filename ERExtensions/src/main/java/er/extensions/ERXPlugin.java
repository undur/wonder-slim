package er.extensions;

import java.util.List;

import er.extensions.appserver.ERXApplication;

/**
 * A module of the application: a framework, or part of the application itself, that takes part in startup. Plugins are
 * found through {@link java.util.ServiceLoader}: a jar lists its plugin classes in
 * {@code META-INF/services/er.extensions.ERXPlugin}. The same model as ng-objects' {@code NGPlugin}.
 *
 * Startup, in order:
 *
 * <ol>
 * <li>{@code ERXApplication.main()} finds and constructs the plugins and orders them by {@link #requires()} (see
 * {@link ERXPlugins}). A plugin's constructor does nothing else: nothing is configured yet.</li>
 * <li>The configuration is composed (see {@code ERXConfigurationManager}).</li>
 * <li>{@link #beforeApplicationConstruction()}</li>
 * <li>The application is constructed.</li>
 * <li>{@link #finishInitialization(ERXApplication)}, then the application's own {@code finishInitialization()}</li>
 * <li>The adaptors start listening, and requests begin to arrive.</li>
 * <li>{@link #didFinishLaunching(ERXApplication)}, then the application's own {@code didFinishLaunching()}</li>
 * </ol>
 *
 * Each hook runs through the plugins in order, a plugin after the plugins it requires, and then on the application:
 * the hooks share their names with {@code ERXApplication}'s own, which always run last.
 */
public interface ERXPlugin {

	/**
	 * @return The plugins this plugin requires, whose hooks run before its own. A required plugin that isn't present
	 *         stops the launch.
	 */
	public default List<Class<? extends ERXPlugin>> requires() {
		return List.of();
	}

	/**
	 * Invoked from {@code ERXApplication.main()}, before the application object is constructed.
	 *
	 * <ul>
	 * <li>The configuration is composed: every property has the value the application will run with.</li>
	 * <li>Logging is configured from the configuration: what's logged from here on reaches the configured log.</li>
	 * <li>The bundles are loaded ({@code NSBundle}), and WebObjects has initialized their principal classes.</li>
	 * <li>There's no application object: {@code WOApplication.application()} is null.</li>
	 * </ul>
	 */
	public default void beforeApplicationConstruction() {}

	/**
	 * Invoked once the application object is fully constructed, its own class's constructor included, and before its
	 * adaptors start listening. No request has arrived or can arrive. The application's own
	 * {@code finishInitialization()} runs after every plugin's.
	 *
	 * <ul>
	 * <li>The configuration's logging settings are applied.</li>
	 * <li>The request handlers the framework and the application register in their constructors are registered.</li>
	 * <li>An adaptor added here ({@code application.adaptorWithName(…)}) is started with the others.</li>
	 * </ul>
	 *
	 * Invoked from {@code ERXApplication.run()}, before {@code WOApplication.run()} posts
	 * {@code ApplicationWillFinishLaunchingNotification}, so before any observer of that notification.
	 */
	public default void finishInitialization( final ERXApplication application ) {}

	/**
	 * Invoked once the application's adaptors are listening. Requests may already be arriving, and being handled
	 * concurrently with this. The application's own {@code didFinishLaunching()} runs after every plugin's.
	 *
	 * Invoked on {@code ApplicationDidFinishLaunchingNotification}; other observers of that notification run in the
	 * order they registered.
	 */
	public default void didFinishLaunching( final ERXApplication application ) {}
}
