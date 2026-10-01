package er.extensions.foundation;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSProperties;

import er.extensions.ERXP;
import er.extensions.ERXPlugin;
import er.extensions.ERXPlugins;
import er.extensions.appserver.ERXApplication;

/**
 * The application's configuration: the properties composed from an ordered list of sources, each overriding the ones
 * before it, in a single pass.
 *
 * {@link #compose(String[])} runs in {@code ERXApplication.main()}, before the application is constructed, so the
 * constructor sees the configuration it will run with. The composed properties are written to the system properties,
 * where {@link ERXProperties} and {@code NSProperties} read them. The sources, lowest precedence first:
 *
 * <ol>
 * <li>each framework's {@code Properties}, then its {@code Properties.dev} (in development mode) and
 * {@code Properties.<user.name>}: first the frameworks without a plugin, in reverse {@code NSBundle.frameworkBundles()}
 * order, then those with one, in plugin order, so a framework overrides the frameworks it requires</li>
 * <li>the application's {@code Properties}</li>
 * <li>{@code ~/WebObjects.properties}</li>
 * <li>each file listed in {@code er.extensions.ERXProperties.OptionalConfigurationFiles}</li>
 * <li>the application's optional variants ({@code Properties.log4j}, {@code .database} and the like), with
 * {@code er.extensions.ERXProperties.loadOptionalProperties}</li>
 * <li>{@code /etc/WebObjects/Properties} or {@code /etc/WebObjects/<App>/Properties}</li>
 * <li>the application's {@code Properties.dev} (in development mode) and {@code Properties.<user.name>}</li>
 * <li>the JVM's {@code -D} options</li>
 * <li>the application's arguments</li>
 * <li>properties set on the running instance, see {@link #setProperty(String, String)}</li>
 * </ol>
 *
 * Beneath them all are the system properties as they were when the application launched: the JVM's defaults.
 *
 * The files an application or a machine usually configures with are recorded as sources even when they aren't there
 * (see {@link Source#found()}): the application's own Properties files, {@code ~/WebObjects.properties}, the machine's
 * and each optional configuration file. The frameworks' variants are only recorded when found.
 *
 * The properties that decide which files are read ({@code OptionalConfigurationFiles}, {@code loadOptionalProperties},
 * {@code machinePropertiesPath}, {@code devPropertiesName}) are read from the composed properties, so they may be set
 * in any source: the files are found again after composing, and composed again, until they stop changing.
 *
 * The files are found through {@code NSBundle}, which initializes WebObjects' principal classes while it initializes
 * itself; they run before the properties are composed. See #151.
 */
public final class ERXConfigurationManager {

	private static final Logger log = LoggerFactory.getLogger( ERXConfigurationManager.class );

	/**
	 * Posted when logging has been configured from the configuration: at startup, and after each reload
	 */
	public static final String ConfigurationDidChangeNotification = "ConfigurationDidChangeNotification";

	/**
	 * The placeholder a touch file's path may hold for the application's name, see {@link #watchForChanges(WOApplication)}
	 */
	private static final String APP_NAME_PLACEHOLDER = "/{AppName}/";

	/**
	 * How many times the files are found and composed at most, see the class comment
	 */
	private static final int MAXIMUM_ROUNDS = 3;

	/**
	 * A source of properties: its name, where it was read from (a path or a URL, empty for one that isn't a file), the
	 * properties it set, and whether it was found. A file that was looked for and isn't there is a source too, with no
	 * properties, so where the configuration could have come from is on record.
	 */
	public record Source( String name, String location, Map<String, String> properties, boolean found ) {

		public Source( final String name, final String location, final Map<String, String> properties ) {
			this( name, location, properties, true );
		}

		/**
		 * @return true if this is the source holding the properties set on the running instance
		 */
		public boolean isInstanceSource() {
			return INSTANCE_SOURCE_NAME.equals( name ) && location.isEmpty();
		}

		/**
		 * @return A file that was looked for at the given location and isn't there
		 */
		private static Source missing( final String name, final String location ) {
			return new Source( name, location, Map.of(), false );
		}

		/**
		 * @return true if this source was found at the same place as the given one, whatever it read
		 */
		private boolean isSameAs( final Source other ) {
			return name.equals( other.name ) && location.equals( other.location );
		}
	}

	/**
	 * The name of the source holding the properties set on the running instance, see {@link #setProperty(String, String)}
	 */
	public static final String INSTANCE_SOURCE_NAME = "Set on this instance";

	/**
	 * The properties set on the running instance (see {@link #setProperty(String, String)}): the last source, overriding
	 * every other, until unset or the instance stops
	 */
	private static final Map<String, String> _instanceProperties = new LinkedHashMap<>();

	/**
	 * The configuration in effect, null until composed
	 */
	private static volatile ERXConfigurationManager _current;

	/**
	 * The system properties when the application launched, before any source was applied
	 */
	private static Properties _launchProperties;

	/**
	 * The application's arguments, kept for recomposing
	 */
	private static String[] _argv;

	/**
	 * Reloads the configuration when a watched file changes, null until {@link #watchForChanges(WOApplication)}
	 */
	private static Watcher _watcher;

	/**
	 * How often the watched files are checked, see {@link #watchForChanges(WOApplication)}
	 */
	private static final Duration WATCH_INTERVAL = Duration.ofSeconds( 1 );

	private final List<Source> _sources;

	/**
	 * The properties the sources set, as composed
	 */
	private final Map<String, String> _properties;

	/**
	 * For each property the sources set, the source it came from
	 */
	private final Map<String, Source> _origins;

	private ERXConfigurationManager( final List<Source> sources ) {
		_sources = List.copyOf( sources );
		_properties = new HashMap<>();
		_origins = new HashMap<>();

		for( final Source source : _sources ) {
			for( final Map.Entry<String, String> entry : source.properties().entrySet() ) {
				_properties.put( entry.getKey(), entry.getValue() );
				_origins.put( entry.getKey(), source );
			}
		}
	}

	/**
	 * @return The configuration in effect, null before {@link #compose(String[])}
	 */
	public static ERXConfigurationManager current() {
		return _current;
	}

	/**
	 * @return The sources, lowest precedence first
	 */
	public List<Source> sources() {
		return _sources;
	}

	/**
	 * @return The properties the sources set, as composed (not the JVM's defaults beneath them)
	 */
	public Map<String, String> properties() {
		return Collections.unmodifiableMap( _properties );
	}

	/**
	 * @return The source the value of the given property came from, null if no source set it (a JVM default, or set
	 *         by code)
	 */
	public Source origin( final String key ) {
		return _origins.get( key );
	}

	/**
	 * @return The sources that set the given property, lowest precedence first: the last is its origin, the others it overrides
	 */
	public List<Source> sourcesSetting( final String key ) {
		return _sources
				.stream()
				.filter( source -> source.properties().containsKey( key ) )
				.toList();
	}

	/**
	 * @return The keys a source set
	 */
	public Set<String> keys() {
		return Collections.unmodifiableSet( _origins.keySet() );
	}

	/**
	 * @return The files on disk the configuration was read from, and those it looked for where no file is present
	 */
	public List<File> watchedFiles() {
		return _sources
				.stream()
				.filter( source -> !source.location().isEmpty() && new File( source.location() ).isAbsolute() )
				.map( source -> new File( source.location() ) )
				.toList();
	}

	/**
	 * Composes the configuration and applies it to the system properties. Invoked once, from {@code ERXApplication.main()}.
	 */
	public static ERXConfigurationManager compose( final String[] argv ) {
		return change( () -> {

			if( _current != null ) {
				throw new IllegalStateException( "The configuration has already been composed. Use reload() to compose it again." );
			}

			_launchProperties = (Properties)System.getProperties().clone();
			_argv = argv == null ? new String[0] : argv.clone();

			// The arguments go in first, so that finding the files sees them (as it sees the JVM's options, already there)
			putAll( argumentsSource().properties() );

			// NSProperties' own cache gives wrong values when a key is read as one type and then another (see ERXProperties).
			// NSProperties reads this once, before main() runs, so only a JVM option can have turned it on: the launch properties.
			if( Boolean.parseBoolean( _launchProperties.getProperty( "NSProperties.cacheEnabled", "false" ).trim() ) ) {
				log.warn( "NSProperties.cacheEnabled is on. Reading a property as one type and then another can give wrong values or exceptions with it; turn it off." );
			}

			return composeAndApply( NSProperties.getProperty( "user.name" ), Map.of() );
		} );
	}

	/**
	 * Composes the configuration again, from the same sources read again, and applies it to the system properties. A
	 * property that a source no longer sets gets its value from launch back, or is removed if it had none.
	 */
	public static ERXConfigurationManager reload() {
		return change( ERXConfigurationManager::recompose );
	}

	/**
	 * Composes the configuration again and applies it. Invoked within {@link #change(Supplier)}.
	 */
	private static ERXConfigurationManager recompose() {

		if( _current == null ) {
			throw new IllegalStateException( "The configuration hasn't been composed yet" );
		}

		return composeAndApply( NSProperties.getProperty( "user.name" ), _current.properties() );
	}

	/**
	 * Sets a property on the running instance, overriding every other source, the application's arguments included, until
	 * it's unset ({@link #unsetProperty(String)}) or the instance stops. Recorded as a source of its own
	 * ({@link #INSTANCE_SOURCE_NAME}), so it survives a reload and shows where it came from. Nothing is written to disk.
	 */
	public static ERXConfigurationManager setProperty( final String key, final String value ) {
		return change( () -> {
			_instanceProperties.put( key, value );
			return recompose();
		} );
	}

	/**
	 * Removes a property set on the running instance: it gets the value the other sources give it back, or its value from
	 * launch, or none
	 */
	public static ERXConfigurationManager unsetProperty( final String key ) {
		return change( () -> {
			_instanceProperties.remove( key );
			return recompose();
		} );
	}

	/**
	 * A change in a property's value, as seen by {@link ERXConfigurationManager#onChange(String, Consumer)}. A value is
	 * null where the property had none, or has none any more.
	 */
	public record PropertyChange( String propertyName, String oldValue, String newValue ) {

		/**
		 * Masks secrets, see {@link #maskedValue(String, String)}, so logging a change can't leak one
		 */
		@Override
		public String toString() {
			return propertyName + ": " + maskedValue( propertyName, oldValue ) + " -> " + maskedValue( propertyName, newValue );
		}
	}

	/**
	 * A listener registered with {@link ERXConfigurationManager#onChange(Predicate, Consumer)}
	 */
	public static final class ChangeListener {

		private final Predicate<String> _propertyNames;
		private final Consumer<PropertyChange> _listener;

		private ChangeListener( final Predicate<String> propertyNames, final Consumer<PropertyChange> listener ) {
			_propertyNames = Objects.requireNonNull( propertyNames );
			_listener = Objects.requireNonNull( listener );
		}

		/**
		 * Stops listening
		 */
		public void remove() {
			_listeners.remove( this );
		}
	}

	private static final List<ChangeListener> _listeners = new CopyOnWriteArrayList<>();

	/**
	 * Invokes the listener whenever the named property's value changes, see {@link #onChange(Predicate, Consumer)}
	 */
	public static ChangeListener onChange( final String propertyName, final Consumer<PropertyChange> listener ) {
		Objects.requireNonNull( propertyName );
		return onChange( propertyName::equals, listener );
	}

	/**
	 * Invokes the listener for each change in the value of a property whose name matches ({@code key -> key.startsWith(
	 * "log4j." )}), until the returned listener is removed.
	 *
	 * A change is a difference in the value in effect: when the configuration is reloaded (a watched file changed), or a
	 * property is set or unset on the running instance. A reload that leaves a value as it was is no change. A property
	 * set by code with {@code System.setProperty()} isn't seen, since it doesn't go through the configuration.
	 *
	 * Listeners run on the thread that changed the configuration (the file watcher's, or the one that set a property),
	 * once the configuration is fully applied, so the new values are what {@link ERXProperties} reads. A listener that
	 * throws is logged, and the others still run.
	 */
	public static ChangeListener onChange( final Predicate<String> propertyNames, final Consumer<PropertyChange> listener ) {
		final ChangeListener changeListener = new ChangeListener( propertyNames, listener );
		_listeners.add( changeListener );
		return changeListener;
	}

	/**
	 * Makes a change to the configuration, one at a time, then tells the listeners what changed, after the change is
	 * complete and outside the lock, so a listener can read or change the configuration itself
	 */
	private static ERXConfigurationManager change( final Supplier<ERXConfigurationManager> change ) {
		final ERXConfigurationManager configuration;
		final List<PropertyChange> changes;

		synchronized( ERXConfigurationManager.class ) {
			final Map<String, String> before = systemProperties();
			configuration = change.get();
			changes = changesBetween( before, systemProperties() );
		}

		for( final PropertyChange propertyChange : changes ) {
			for( final ChangeListener listener : _listeners ) {
				if( listener._propertyNames.test( propertyChange.propertyName() ) ) {
					try {
						listener._listener.accept( propertyChange );
					}
					catch( RuntimeException e ) {
						log.error( "A listener to changes in {} failed", propertyChange.propertyName(), e );
					}
				}
			}
		}

		return configuration;
	}

	/**
	 * @return The system properties as they are, by name
	 */
	private static Map<String, String> systemProperties() {
		final Map<String, String> result = new HashMap<>();
		final Properties system = System.getProperties();

		for( final String key : system.stringPropertyNames() ) {
			result.put( key, system.getProperty( key ) );
		}

		return result;
	}

	/**
	 * @return The changes from one set of values to the other, ordered by property name
	 */
	private static List<PropertyChange> changesBetween( final Map<String, String> before, final Map<String, String> after ) {
		final TreeSet<String> keys = new TreeSet<>( before.keySet() );
		keys.addAll( after.keySet() );

		return keys
				.stream()
				.filter( key -> !Objects.equals( before.get( key ), after.get( key ) ) )
				.map( key -> new PropertyChange( key, before.get( key ), after.get( key ) ) )
				.toList();
	}
	/**
	 * Reloads the configuration when a file changes (logging follows through its listener to changes, see ERXLoggingSupport). Invoked once, when the application
	 * has been constructed.
	 *
	 * With WOCachingEnabled off (in development), the configuration's files are watched: those it was read from, and
	 * those it looked for where no file is present, so a file created there is picked up. With it on (in deployment),
	 * they aren't, since much of the configuration is read once, at startup, and a reload would seem to change what it
	 * doesn't; a touch file can be watched instead, named by {@code er.extensions.ERXConfigurationManager.PropertiesTouchFile}.
	 * If its path holds {@code /{AppName}/}, two files are watched: the path with the application's name in its place,
	 * and the path without it (one for every application on the machine).
	 *
	 * The files are checked on a thread of their own, every {@link #WATCH_INTERVAL}, independent of requests.
	 */
	public static synchronized void watchForChanges( final WOApplication application ) {

		if( _watcher != null ) {
			return;
		}

		final List<File> touchFiles = application.isCachingEnabled() ? touchFiles( application.name() ) : null;

		// In deployment, without a touch file, there's nothing to watch
		if( touchFiles != null && touchFiles.isEmpty() ) {
			return;
		}

		_watcher = new Watcher( touchFiles );
		_watcher.start();
	}

	/**
	 * @return The touch files to watch for the named application, empty if none is configured
	 */
	private static List<File> touchFiles( final String applicationName ) {
		final String touchFile = ERXProperties.stringForKey( ERXP.PROPERTIES_TOUCH_FILE.id() );

		if( touchFile == null || touchFile.isBlank() ) {
			return List.of();
		}

		final int placeholder = touchFile.lastIndexOf( APP_NAME_PLACEHOLDER );

		if( placeholder == -1 ) {
			return List.of( new File( touchFile ) );
		}

		final String before = touchFile.substring( 0, placeholder + 1 );
		final String after = touchFile.substring( placeholder + APP_NAME_PLACEHOLDER.length() );
		return List.of( new File( before + applicationName + "/" + after ), new File( before + after ) );
	}

	/**
	 * Checks the watched files' modification times and sizes, and reloads the configuration when one changes. Watches the
	 * given touch files or, with none, the configuration's files, following the configuration: after a reload, the files
	 * it was composed from.
	 */
	static final class Watcher {

		/**
		 * A file's state, as far as noticing a change goes. A file that isn't there has its own.
		 */
		private record Stamp( long lastModified, long length ) {

			private static Stamp of( final File file ) {
				return file.exists() ? new Stamp( file.lastModified(), file.length() ) : new Stamp( 0, -1 );
			}
		}

		/**
		 * The touch files, null to watch the configuration's files
		 */
		private final List<File> _touchFiles;

		private Map<File, Stamp> _stamps;

		Watcher( final List<File> touchFiles ) {
			_touchFiles = touchFiles;
			_stamps = stamps();
		}

		private void start() {
			Thread
					.ofPlatform()
					.daemon()
					.name( "ERXConfigurationManager file watcher" )
					.start( () -> {
						while( true ) {
							try {
								Thread.sleep( WATCH_INTERVAL );
								check();
							}
							catch( InterruptedException e ) {
								return;
							}
						}
					} );
		}

		/**
		 * Reloads the configuration if a watched file has changed since the last check
		 *
		 * @return true if it reloaded
		 */
		synchronized boolean check() {
			final Map<File, Stamp> stamps = stamps();

			if( stamps.equals( _stamps ) ) {
				return false;
			}

			final List<File> changed = stamps
					.keySet()
					.stream()
					.filter( file -> !stamps.get( file ).equals( _stamps.get( file ) ) )
					.toList();

			try {
				reload();
				log.info( "Reloaded the configuration, since {} changed", changed );
			}
			catch( RuntimeException e ) {
				log.error( "Unable to reload the configuration after {} changed", changed, e );
			}

			// The files may be different ones now, and the ones that changed are taken in by the reload
			_stamps = stamps();
			return true;
		}

		private Map<File, Stamp> stamps() {
			final Map<File, Stamp> stamps = new LinkedHashMap<>();

			for( final File file : _touchFiles != null ? _touchFiles : _current.watchedFiles() ) {
				stamps.put( file, Stamp.of( file ) );
			}

			return stamps;
		}
	}

	/**
	 * @return The configuration the given user would get, without applying it: the same sources, with that user's
	 *         {@code Properties.<user>} variants
	 */
	public static synchronized ERXConfigurationManager previewForUser( final String userName ) {
		return new ERXConfigurationManager( sources( userName ) );
	}

	/**
	 * Finds the sources, composes them and applies the result to the system properties, against the previously applied
	 * properties. Then finds the sources again with the result applied, until they stop changing (see the class comment).
	 */
	private static ERXConfigurationManager composeAndApply( final String userName, final Map<String, String> previous ) {
		Map<String, String> applied = previous;
		ERXConfigurationManager configuration = null;

		for( int round = 1; round <= MAXIMUM_ROUNDS; round++ ) {
			final ERXConfigurationManager candidate = new ERXConfigurationManager( sources( userName ) );
			applyToSystemProperties( candidate.properties(), applied );
			applied = candidate.properties();

			final boolean stable = configuration != null && sameSources( configuration.sources(), candidate.sources() );
			configuration = candidate;

			if( stable ) {
				break;
			}
		}

		_current = configuration;
		return configuration;
	}

	/**
	 * Writes the given properties to the system properties. Those in the previously written set that aren't in the new
	 * one get their value from launch back, or are removed.
	 */
	private static void applyToSystemProperties( final Map<String, String> properties, final Map<String, String> previous ) {
		final Properties system = System.getProperties();

		for( final String key : previous.keySet() ) {
			if( !properties.containsKey( key ) ) {
				final String launchValue = _launchProperties.getProperty( key );

				if( launchValue != null ) {
					system.setProperty( key, launchValue );
				}
				else {
					system.remove( key );
				}
			}
		}

		putAll( properties );
	}

	private static void putAll( final Map<String, String> properties ) {
		System.getProperties().putAll( properties );
		ERXProperties.systemPropertiesChanged();
	}

	private static boolean sameSources( final List<Source> a, final List<Source> b ) {

		if( a.size() != b.size() ) {
			return false;
		}

		for( int i = 0; i < a.size(); i++ ) {
			if( !a.get( i ).isSameAs( b.get( i ) ) ) {
				return false;
			}
		}

		return true;
	}

	/**
	 * @return Every source, lowest precedence first, found with the system properties as they are now
	 */
	private static List<Source> sources( final String userName ) {
		final List<Source> sources = new ArrayList<>();

		// Each framework's Properties, and its variants
		final List<String> frameworkNames = frameworkOrder();

		for( final String frameworkName : frameworkNames ) {
			final String path = pathForResourceNamed( "Properties", frameworkName );

			if( path != null ) {
				addFile( sources, frameworkName + ".framework", path );
			}
			else {
				// A framework deployed as a jar has no file path for its Properties, so they're read from the jar
				final NSBundle bundle = NSBundle.bundleForName( frameworkName );

				if( bundle != null && bundle.isJar() ) {
					final String resourcePath = bundle.resourcePathForLocalizedResourceNamed( "Properties", null );
					final URL url = resourcePath != null ? bundle.pathURLForResourcePath( resourcePath ) : null;

					if( url != null ) {
						addURL( sources, frameworkName + ".framework", url );
					}
				}
			}

			if( ERXApplication.isDevelopmentModeSafe() ) {
				addFile( sources, frameworkName + ".framework.dev", pathForResourceNamed( "Properties.dev", frameworkName ) );
			}

			addFile( sources, frameworkName + ".framework.user", variantPath( userName, frameworkName ) );
		}

		// The application's Properties, and the folder its variants would be in
		final String applicationPropertiesPath = pathForResourceNamed( "Properties", "app" );
		final String applicationResources = applicationPropertiesPath != null ? new File( applicationPropertiesPath ).getParent() : NSBundle.mainBundle() != null ? NSBundle.mainBundle().resourcePath() : null;

		if( NSBundle.mainBundle() != null ) {
			addFileOrMissing( sources, NSBundle.mainBundle().name() + ".app", applicationPropertiesPath, applicationResources, "Properties" );
		}

		// WebObjects.properties in the user's home directory
		final String userHome = NSProperties.getProperty( "user.home" );

		if( userHome != null && !userHome.isEmpty() ) {
			final File file = new File( userHome, "WebObjects.properties" );

			if( file.isFile() && file.canRead() ) {
				addFile( sources, "{$user.home}/WebObjects.properties", file.getPath() );
			}
			else {
				sources.add( Source.missing( "{$user.home}/WebObjects.properties", file.getPath() ) );
			}
		}

		// Optional configuration files
		final NSArray<String> optionalConfigurationFiles = ERXProperties.arrayForKey( ERXP.OPTIONAL_CONFIGURATION_FILES.id() );

		if( optionalConfigurationFiles != null ) {
			for( final String optionalConfigurationFile : optionalConfigurationFiles ) {
				File file = new File( optionalConfigurationFile );

				// Not a file: an application resource, then
				if( !file.exists() ) {
					final String resourcePath = pathForResourceNamed( optionalConfigurationFile, "app" );

					if( resourcePath != null ) {
						file = new File( resourcePath );
					}
				}

				if( file.isFile() && file.canRead() ) {
					addFile( sources, "Optional Configuration", file.getPath() );
				}
				else {
					log.error( "The optional configuration file '{}' either does not exist or could not be read.", file );
					sources.add( Source.missing( "Optional Configuration", file.getPath() ) );
				}
			}
		}

		// The application's optional variants
		if( ERXProperties.booleanForKeyWithDefault( ERXP.LOAD_OPTIONAL_PROPERTIES.id(), false ) ) {
			for( final String variant : List.of( "log4j", "database", "multilanguage", "migration" ) ) {
				addFile( sources, "Application " + variant + " Properties", variantPath( variant, "app" ) );
				addFile( sources, "Application-User " + variant + " Properties", variantPath( variant + "." + userName, "app" ) );
			}

			for( final String frameworkName : frameworkNames ) {
				addFile( sources, frameworkName + ".framework.common", variantPath( frameworkName, "app" ) );
				addFile( sources, frameworkName + ".framework.user", variantPath( frameworkName + "." + userName, "app" ) );
			}
		}

		// /etc/WebObjects/Properties, or failing that /etc/WebObjects/<AppName>/Properties
		final File machineDirectory = new File( NSProperties.getProperty( ERXP.MACHINE_PROPERTIES_PATH.id(), "/etc/WebObjects" ) );
		final File machineFile = new File( machineDirectory, "Properties" );

		if( machineFile.exists() ) {
			addFile( sources, "Application-Machine Properties", machineFile.getPath() );
		}
		else {
			sources.add( Source.missing( "Application-Machine Properties", machineFile.getPath() ) );
			final File applicationMachineFile = new File( new File( machineDirectory, applicationName() ), "Properties" );

			if( applicationMachineFile.exists() ) {
				addFile( sources, "Application-Machine Properties", applicationMachineFile.getPath() );
			}
			else {
				sources.add( Source.missing( "Application-Machine Properties", applicationMachineFile.getPath() ) );
			}
		}

		// The application's Properties.dev and Properties.<user>
		if( ERXApplication.isDevelopmentModeSafe() ) {
			final String devName = NSProperties.getProperty( ERXP.DEV_PROPERTIES_NAME.id(), "dev" );
			addFileOrMissing( sources, "Application-Developer Properties", variantPath( devName, "app" ), applicationResources, "Properties." + devName );
		}

		if( userName != null && !userName.isEmpty() ) {
			addFileOrMissing( sources, "Application-User Properties", variantPath( userName, "app" ), applicationResources, "Properties." + userName );
		}

		// The JVM's options and the application's arguments
		addIfNotEmpty( sources, jvmOptionsSource() );
		addIfNotEmpty( sources, argumentsSource() );

		// Properties set on the running instance
		addIfNotEmpty( sources, new Source( INSTANCE_SOURCE_NAME, "", new LinkedHashMap<>( _instanceProperties ) ) );

		return sources;
	}

	/**
	 * @return The frameworks, lowest precedence first: those without a plugin in reverse {@code NSBundle.frameworkBundles()}
	 *         order, then those with one in plugin order, so a framework's Properties override those of the frameworks
	 *         it requires
	 */
	private static List<String> frameworkOrder() {
		final NSArray<String> frameworkNames = (NSArray<String>)NSBundle.frameworkBundles().valueForKey( "name" );
		final List<String> withPlugin = new ArrayList<>();

		for( final ERXPlugin plugin : ERXPlugins.all() ) {
			final NSBundle bundle = NSBundle.bundleForClass( plugin.getClass() );

			if( bundle != null && frameworkNames.contains( bundle.name() ) && !withPlugin.contains( bundle.name() ) ) {
				withPlugin.add( bundle.name() );
			}
		}

		final List<String> order = new ArrayList<>();

		for( final String frameworkName : frameworkNames.reversed() ) {
			if( !withPlugin.contains( frameworkName ) ) {
				order.add( frameworkName );
			}
		}

		order.addAll( withPlugin );
		return order;
	}

	/**
	 * Adds the file at the given path, or if there's none, records the file as missing where it would be: the given
	 * name in the given folder (if the folder is known)
	 */
	private static void addFileOrMissing( final List<Source> sources, final String name, final String path, final String folder, final String fileName ) {
		if( path != null ) {
			addFile( sources, name, path );
		}
		else if( folder != null ) {
			sources.add( Source.missing( name, new File( folder, fileName ).getPath() ) );
		}
	}

	private static void addFile( final List<Source> sources, final String name, final String path ) {

		if( path == null || path.isEmpty() ) {
			return;
		}

		final File file = canonicalFile( new File( path ) );

		if( sources.stream().anyMatch( source -> source.location().equals( file.getPath() ) ) ) {
			log.error( "Path was already included: {}", file );
			return;
		}

		try {
			sources.add( new Source( name, file.getPath(), toMap( propertiesFromFile( file ) ) ) );
		}
		catch( IOException e ) {
			log.error( "Unable to load the configuration file: {}", file, e );
		}
	}

	private static void addURL( final List<Source> sources, final String name, final URL url ) {
		final Properties properties = new Properties();

		try( final InputStream stream = url.openStream() ) {
			properties.load( stream );
			sources.add( new Source( name, url.toString(), toMap( properties ) ) );
		}
		catch( IOException e ) {
			log.error( "Unable to load the configuration file: {}", url, e );
		}
	}

	private static void addIfNotEmpty( final List<Source> sources, final Source source ) {
		if( !source.properties().isEmpty() ) {
			sources.add( source );
		}
	}

	/**
	 * @return The JVM's {@code -D} options: the system properties given on the java command line, which take precedence
	 *         over every file
	 */
	private static Source jvmOptionsSource() {
		final Map<String, String> properties = new LinkedHashMap<>();

		for( final String key : jvmOptionKeys() ) {
			final String value = _launchProperties.getProperty( key );

			if( value != null ) {
				properties.put( key, value );
			}
		}

		return new Source( "JVM options", "", properties );
	}

	/**
	 * @return The keys of the {@code -D} options on the java command line
	 */
	private static List<String> jvmOptionKeys() {
		try {
			return java.lang.management.ManagementFactory
					.getRuntimeMXBean()
					.getInputArguments()
					.stream()
					.filter( argument -> argument.startsWith( "-D" ) && argument.length() > 2 )
					.map( argument -> argument.substring( 2 ).split( "=", 2 )[0] )
					.toList();
		}
		catch( LinkageError e ) {
			// A runtime without the java.management module: the -D options can't be told from the JVM's defaults
			log.warn( "Can't read the JVM's options (no java.management module). -D options won't take precedence over properties files." );
			return List.of();
		}
	}

	/**
	 * @return The application's arguments ({@code -Key value}, {@code -Dkey=value})
	 */
	private static Source argumentsSource() {
		return new Source( "Arguments", "", toMap( propertiesFromArgv( _argv ) ) );
	}

	private static Map<String, String> toMap( final Properties properties ) {
		final Map<String, String> map = new LinkedHashMap<>();

		for( final String key : properties.stringPropertyNames() ) {
			map.put( key, properties.getProperty( key ) );
		}

		return map;
	}

	private static File canonicalFile( final File file ) {
		try {
			return file.getCanonicalFile();
		}
		catch( IOException e ) {
			return file.getAbsoluteFile();
		}
	}

	/**
	 * @return The path of {@code Properties.<variant>} in the named bundle ("app" for the application), null if there's none
	 */
	private static String variantPath( final String variant, final String bundleName ) {
		return variant == null || variant.isEmpty() ? null : pathForResourceNamed( "Properties." + variant, bundleName );
	}

	/**
	 * @return The path of the named resource in the named bundle ("app" for the application), null if there's none or the
	 *         bundle is a jar (whose resources have no path)
	 */
	private static String pathForResourceNamed( final String fileName, final String bundleName ) {
		final NSBundle bundle = "app".equals( bundleName ) ? NSBundle.mainBundle() : NSBundle.bundleForName( bundleName );

		if( bundle == null || bundle.isJar() ) {
			return null;
		}

		final URL url = bundle.pathURLForResourcePath( fileName );
		return url == null ? null : url.getFile();
	}

	/**
	 * @return The application's name, before the application exists
	 */
	private static String applicationName() {
		final String name = NSProperties.getProperty( "WOApplicationName" );

		if( name != null ) {
			return name;
		}

		return NSBundle.mainBundle() != null ? NSBundle.mainBundle().name() : "Unknown";
	}

	/**
	 * The startup report on configuration: the sources in the order they were applied, then every property in effect,
	 * alphabetically, with the source that set it, so the application's own configuration stands out from
	 * WebObjects' and the JVM's defaults while those stay available (the effective session timeout, worker thread count,
	 * caching flag or handler keys are as operationally relevant as anything the application set itself). Printed in
	 * the same banner style as the rest of the startup output.
	 *
	 * Secrets are masked, also where they appear inside another value, see {@link #maskedValue(String, String)}. The classpath is
	 * the one value printed one entry per line: as a single line it is unreadable and dwarfs everything else.
	 */
	public void printStartupReport() {
		final StringBuilder out = new StringBuilder();

		out.append( "============== PROPERTIES FILES ================\n" );
		out.append( "(applied in this order - a later source overrides an earlier one)\n" );

		for( final Source source : _sources ) {
			final String location = source.location().isEmpty() ? "(" + source.properties().size() + ")" : source.location();
			out.append( String.format( "%-34s : %s%s%n", source.name(), location, source.found() ? "" : " (no file present)" ) );
		}

		final TreeMap<String, String> effective = new TreeMap<>();

		for( final String key : System.getProperties().stringPropertyNames() ) {
			final String value = NSProperties.getProperty( key );
			effective.put( key, value == null ? "" : value );
		}

		// The source column: as wide as the longest source name that set a property in effect
		final int sourceWidth = effective.keySet().stream().map( _origins::get ).filter( Objects::nonNull ).mapToInt( source -> source.name().length() ).max().orElse( 0 );
		final String line = "%-46s  %-" + Math.max( sourceWidth, 1 ) + "s = %s%n";

		out.append( '\n' );
		out.append( "================= PROPERTIES ===================\n" );
		out.append( "(each with the source that set it; those without one are WebObjects and JVM defaults)\n" );

		for( final Map.Entry<String, String> entry : effective.entrySet() ) {
			final String key = entry.getKey();
			final Source origin = _origins.get( key );
			final String source = origin == null ? "" : origin.name();

			if( "java.class.path".equals( key ) ) {
				final String[] elements = entry.getValue().split( Pattern.quote( File.pathSeparator ) );
				out.append( String.format( line, key, source, elements.length > 0 ? elements[0] : "" ) );

				for( int i = 1; i < elements.length; i++ ) {
					out.append( String.format( line.replace( " = ", "   " ), "", "", elements[i] ) );
				}
			}
			else {
				out.append( String.format( line, key, source, maskedValue( key, entry.getValue() ).replace( "\n", "\\n" ) ) );
			}
		}

		IO.print( out );
	}

	/**
	 * @return true if the key names something that must not be shown or logged: passwords, API keys, tokens, secrets and
	 *         credentials of any spelling
	 */
	public static boolean isSecretKey( final String key ) {
		return key != null && SECRET_KEY_PATTERN.matcher( key ).find();
	}

	/**
	 * @return The value as it may be shown or logged: masked entirely if the key names a secret (see
	 *         {@link #isSecretKey(String)}), otherwise with the value of every secret property masked wherever it appears
	 *         in it. The JVM's own record of the command line ({@code sun.java.command}) carries the application's
	 *         arguments, passwords included.
	 */
	public static String maskedValue( final String key, final String value ) {

		if( value == null ) {
			return null;
		}

		if( isSecretKey( key ) ) {
			return MASK;
		}

		String result = value;

		for( final String otherKey : System.getProperties().stringPropertyNames() ) {
			if( isSecretKey( otherKey ) ) {
				final String secret = System.getProperty( otherKey );

				// Very short values would mask ordinary text, and aren't worth hiding anyway
				if( secret != null && secret.length() >= 4 ) {
					result = result.replace( secret, MASK );
				}
			}
		}

		return result;
	}

	/**
	 * @return The given properties as lines of {@code key=value}, sorted by key, secrets masked (see
	 *         {@link #maskedValue(String, String)})
	 */
	public static String logString( final Properties properties ) {
		final StringBuilder message = new StringBuilder();

		for( final String key : new TreeSet<>( properties.stringPropertyNames() ) ) {
			message.append( "  " + key + "=" + maskedValue( key, properties.getProperty( key ) ) + "\n" );
		}

		return message.toString();
	}

	private static final String MASK = "********";

	private static final Pattern SECRET_KEY_PATTERN = Pattern.compile( "(?i)(password|passwd|secret|api[._-]?key|access[._-]?key|private[._-]?key|token|credential)" );

	/**
	 * @return The properties in the given file, its {@code .includeProps} followed (see {@link IncludingProperties})
	 */
	private static Properties propertiesFromFile( final File file ) throws IOException {
		final IncludingProperties properties = new IncludingProperties();
		properties.load( file );
		return properties;
	}

	/**
	 * @return The application's arguments ({@code -Key value}, {@code -Dkey=value}) as properties, as WebObjects parses them
	 */
	private static Properties propertiesFromArgv( final String[] argv ) {
		final Properties properties = new Properties();
		final NSDictionary<?, ?> values = NSProperties.valuesFromArgv( argv );

		for( final Object key : values.allKeys() ) {
			properties.put( key, values.objectForKey( key ) );
		}

		return properties;
	}

	/**
	 * IncludingProperties is a subclass of Properties that provides support for including other
	 * Properties files on the fly.  If you create a property named .includeProps, the value
	 * will be interpreted as a file to load.  If the path is absolute, it will just load it
	 * directly.  If it's relative, the path will be loaded relative to the current user's
	 * home directory.  Multiple .includeProps can be included in a Properties file and they
	 * will be loaded in the order they appear within the file.
	 */
	private static class IncludingProperties extends Properties {

		private static final Logger log = LoggerFactory.getLogger(ERXConfigurationManager.class);

		public static final String IncludePropsKey = ".includeProps";
		
		private Stack<File> _files = new Stack<>();
		
		@Override
		public synchronized Object put(Object key, Object value) {
			if (IncludingProperties.IncludePropsKey.equals(key)) {
				String propsFileName = (String)value;
                File propsFile = new File(propsFileName);
                if (!propsFile.isAbsolute()) {
                    // if we don't have any context for a relative (non-absolute) props file,
                    // we presume that it's relative to the user's home directory
    				File cwd = null;
    				if (_files.size() > 0) {
    					cwd = _files.peek();
    				}
    				else {
    					cwd = new File(System.getProperty("user.home"));
                	}
                    propsFile = new File(cwd, propsFileName);
                }

                // Detect mutually recursing props files by tracking what we've already loaded:
                String existingIncludeProps = getProperty(IncludingProperties.IncludePropsKey);
                if (existingIncludeProps == null) {
                	existingIncludeProps = "";
                }
                if (existingIncludeProps.indexOf(propsFile.getPath()) > -1) {
                    log.error("_Properties.load(): recursive includeProps detected! {} in {}", propsFile, existingIncludeProps);
                    log.error("_Properties.load() cannot proceed - QUITTING!");
                    System.exit(1);
                }
                if (existingIncludeProps.length() > 0) {
                	existingIncludeProps += ", ";
                }
                existingIncludeProps += propsFile;
                super.put(IncludingProperties.IncludePropsKey, existingIncludeProps);

                try {
                    log.info("_Properties.load(): Including props file: {}", propsFile);
					load(propsFile);
				} catch (IOException e) {
					throw new RuntimeException("Failed to load the property file '" + value + "'.", e);
				}
				return null;
			}
			return super.put(key, value);
		}

		public synchronized void load(File propsFile) throws IOException {
			_files.push(propsFile.getParentFile());
			try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(propsFile))) {
	            load(is);
			}
			finally {
				_files.pop();
			}
		}
	}
}
