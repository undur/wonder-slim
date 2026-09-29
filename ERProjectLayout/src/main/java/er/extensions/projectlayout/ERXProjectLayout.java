package er.extensions.projectlayout;

import java.util.Properties;

/**
 * Where a project keeps its components and resources, as declared in its build.properties.
 *
 * <pre>
 * dir.components=src/main/components
 * dir.woresources=src/main/woresources
 * dir.webserverResources=src/main/webserver-resources
 * </pre>
 *
 * Every project run from its project folder gets a {@link ERXProjectLayoutBundle}, and keys it doesn't declare take the
 * Maven-layout defaults above. A project is a folder whose build.properties names it and gives its type (project.name and
 * project.type). Paths are relative to the project folder.
 *
 * Only the declared folders are searched, never a folder the build doesn't package: resources kept anywhere else
 * wouldn't be in the built application either.
 *
 * Deliberately free of WebObjects references, so that {@link #register()} can run before NSBundle is loaded.
 */
public final class ERXProjectLayout {

	/**
	 * Key for the project-relative path to the folder holding the components
	 */
	public static final String COMPONENTS_KEY = "dir.components";

	/**
	 * Key for the project-relative path to the folder holding the WebObjects bundle resources: models, Properties, strings and the like
	 */
	public static final String WORESOURCES_KEY = "dir.woresources";

	/**
	 * Key for the project-relative path to the folder holding the resources served to the browser: CSS, images, JavaScript
	 */
	public static final String WEBSERVER_RESOURCES_KEY = "dir.webserverResources";

	/**
	 * The components folder when build.properties doesn't declare one
	 */
	public static final String DEFAULT_COMPONENTS = "src/main/components";

	/**
	 * The WebObjects bundle resources folder when build.properties doesn't declare one
	 */
	public static final String DEFAULT_WORESOURCES = "src/main/woresources";

	/**
	 * The web server resources folder when build.properties doesn't declare one
	 */
	public static final String DEFAULT_WEBSERVER_RESOURCES = "src/main/webserver-resources";

	/**
	 * Key for the project's name, which is also its bundle's name
	 */
	static final String PROJECT_NAME_KEY = "project.name";

	/**
	 * Key for the project's type: application or framework
	 */
	static final String PROJECT_TYPE_KEY = "project.type";

	/**
	 * The system property NSBundle reads its extra bundle factories from, once, when it's initialized
	 */
	static final String BUNDLE_FACTORIES_PROPERTY = "NSBundleFactories";

	/**
	 * The bundle factory's class name, as NSBundleFactories names it. A string, so naming it doesn't load WebObjects classes
	 */
	static final String FACTORY_CLASS_NAME = "er.extensions.projectlayout.ERXProjectLayoutBundleFactory";

	private final String _components;

	private final String _woresources;

	private final String _webserverResources;

	private ERXProjectLayout( final String components, final String woresources, final String webserverResources ) {
		_components = components;
		_woresources = woresources;
		_webserverResources = webserverResources;
	}

	/**
	 * @return true if the given build.properties is a project's: it names the project and gives its type
	 */
	public static boolean isProject( final Properties buildProperties ) {
		return isSet( buildProperties, PROJECT_NAME_KEY ) && isSet( buildProperties, PROJECT_TYPE_KEY );
	}

	private static boolean isSet( final Properties properties, final String key ) {
		final String value = properties.getProperty( key );
		return value != null && !value.isBlank();
	}

	/**
	 * @return The layout declared in the given build.properties, with the defaults for what it doesn't declare
	 */
	public static ERXProjectLayout fromBuildProperties( final Properties buildProperties ) {
		final String components = buildProperties.getProperty( COMPONENTS_KEY );
		final String woresources = buildProperties.getProperty( WORESOURCES_KEY );
		final String webserverResources = buildProperties.getProperty( WEBSERVER_RESOURCES_KEY );

		return new ERXProjectLayout(
				components != null ? components : DEFAULT_COMPONENTS,
				woresources != null ? woresources : DEFAULT_WORESOURCES,
				webserverResources != null ? webserverResources : DEFAULT_WEBSERVER_RESOURCES );
	}

	/**
	 * @return The project-relative path to the components folder
	 */
	public String components() {
		return _components;
	}

	/**
	 * @return The project-relative path to the WebObjects bundle resources folder
	 */
	public String woresources() {
		return _woresources;
	}

	/**
	 * @return The project-relative path to the web server resources folder
	 */
	public String webserverResources() {
		return _webserverResources;
	}

	/**
	 * Adds {@link ERXProjectLayoutBundleFactory} to NSBundle's bundle factories. Must run before anything touches NSBundle,
	 * since NSBundle reads its factories once, when it's initialized. Factories already named in the property keep
	 * precedence over this one. Calling it more than once has no further effect.
	 */
	public static void register() {
		final String existing = System.getProperty( BUNDLE_FACTORIES_PROPERTY );
		System.setProperty( BUNDLE_FACTORIES_PROPERTY, factoriesWithOurs( existing ) );
	}

	/**
	 * NSBundle asks the factories in the reverse of the order they're named, so ours goes first, to be asked last of them.
	 *
	 * @return The value of NSBundleFactories with our factory added
	 */
	static String factoriesWithOurs( final String existing ) {
		if( existing == null || existing.isBlank() ) {
			return FACTORY_CLASS_NAME;
		}

		if( existing.contains( FACTORY_CLASS_NAME ) ) {
			return existing;
		}

		final String trimmed = existing.trim();

		// A property list array, "(a.Factory, b.Factory)"
		if( trimmed.startsWith( "(" ) ) {
			final String contents = trimmed.substring( 1 ).trim();
			return contents.startsWith( ")" ) ? "(" + FACTORY_CLASS_NAME + ")" : "(" + FACTORY_CLASS_NAME + ", " + contents;
		}

		return FACTORY_CLASS_NAME + "," + trimmed;
	}
}
