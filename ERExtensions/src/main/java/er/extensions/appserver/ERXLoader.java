package er.extensions.appserver;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;

import er.extensions.foundation.ERXUtilities;

/**
 * Responsible for classpath munging and ensuring all bundles are loaded.
 * 
 * To be precise about what this does.
 * 
 * 1. Reorders the classpath, ensuring libraries and frameworks are loaded and initialized in a "WO-friendly order"
 * 2. Checks classpath entries to see which are framework bundles. These will get added to 'allFrameworks' and will then receive some further special treatment during bundle loading 
 * 3. Apparently attempts to check if one of the classpath entries is a WOLips project, if so, assumes we're doing development and sets NSProjectBundleEnabled=true
 * 4. Loads properties from jar files where present (Whether this is happening in the correct order needs to be checked)
 * 5. Keeps track of all loaded property files in [urls]. But nothing seems to be done with this variable, so I have no idea why
 * 6. More stuff...
 * 
 * Since this class does it's stuff before logging has been initialized, it has it's own logging.
 * To enable logging, set -Der.extensions.appserver.projectBundleLoading=DEBUG or invoke enableLogging()
 * 
 * Classpath reordering can be disabled by setting -D_DisableClasspathReorder=true
 * 
 * @author ak
 */

public class ERXLoader {

	/**
	 * Names of properties storing classpaths.
	 * 
	 * FIXME: I'm not actually sure why we're including com.webobjects.classpath? I mean... It doesn't do any harm, but does it ever have a value? 	// Hugi 2025-05-29
	 */
	private static final List<String> CLASSPATH_PROPERTY_NAMES = List.of( "java.class.path", "com.webobjects.classpath" );

	/**
	 * Properties parsed from the application's main method arguments (command line arguments) 
	 */
	private static Map propertiesFromArgv;

	/**
	 * All properties loaded from bundle properties (gets populated as each bundle gets loaded) 
	 */
	private Properties allBundleProperties = new Properties();

	/**
	 * Holds the framework names during startup.
	 */
	private final Set<String> allFrameworks = new HashSet<>();

	/**
	 * URLs to loaded property files get added here, apparently, then nothing is done with them?
	 * 
	 * FIXME: What exactly is the point of keeping track if this? // Hugi 2025-05-29
	 */
	private List<URL> urls = new ArrayList<>();
	
	/**
	 * Properties loaded from main property files (Properties)
	 */
	private Properties mainProps;
	
	/**
	 * Properties loaded from user property files (Properties.[username])
	 */
	private Properties mainUserProps;

	/**
	 * Allows us to specify in code if debug logging should be enabled for this awesomeness
	 */
	private static boolean _loggingEnabled = false;

	/**
	 * An optional logfile we can use for storing our log 
	 */
	private static Path _logfile;

	/**
	 * Initializes and runs this thing
	 * 
	 * @param argv The arguments passed to the application's man method (a.k.a. command line arguments)
	 */
	public ERXLoader(String[] argv) {
		log( "Constructing ERXLoader with arguments: " + Arrays.asList( argv ) );

		propertiesFromArgv = NSProperties.valuesFromArgv(argv);

		if (System.getProperty("_DisableClasspathReorder") == null) {
			reorderClasspath();
		}

		// FIXME:
		// Note that in the old Loader, operations here were invoked as a part of classpath ordering,
		// meaning they were invoked in the order specified by the classpath _before_ it was reordered.
		// Invoking this separately means these operations now occur in the order specified by the reordered classpath.
		// I doubt this is significant, but it bears keeping in mind while we're working on this.
		// Hugi 2025-05-29
		doRandomStuffToClasspathElements();

		NSNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector("bundleDidLoad"), "NSBundleDidLoadNotification", null);
	}

	/**
	 * A pattern describing a type of classpath entry 
	 */
	private enum CPEPattern {
		System( ClasspathEntry::isSystemJar ),
		Framework( ".*?/(\\w+)\\.framework/Resources/Java/\\1.jar".toLowerCase() ),
		App( ".*?/(\\w+)\\.woa/Contents/Resources/Java/\\1.jar".toLowerCase() ),
		Folder( ".*?/Resources/Java/?$".toLowerCase() ),
		AntProject( ".*?/(\\w+)/bin$".toLowerCase() ),
		MavenProject( str -> str.contains("target/classes") ),
		ERFoundation( str -> str.contains("ERFoundation") ),
		ERWebObjects( str -> str.contains("ERWebObjects") ),
		ERExtensions( str -> str.contains("ERExtensions") );
		
		Function<String,Boolean> _function;
		
		CPEPattern( String regex ) {
			_function = str -> str.matches( regex );
		}
		
		CPEPattern( Function<String,Boolean> function ) {
			_function = function;
		}
		
		public boolean matches( String cpeString ) {
			return _function.apply(cpeString);
		}
	}

	private enum CPEType {
		Normal(10), // FIXME: I have absolutely no idea what is meant by "normal", not all that descriptive // Hugi 2025-05-29
		WebObjectsHack(20), // Defines any library that overrides WO functionality and thus must be aded on the classpath before WebObjects
		WebObjects(30), // Defines the built-in WebObjects libraries
		Jar(40); // FIXME: These are not "jars", it's a catch-all for "everything else", really // Hugi 2025-05-29
		
		int _order;
		
		CPEType( int order ) {
			_order = order;
		}
		
		/**
		 * @return The int we use to order the classpath. Lower values mean we're earlier in the classpath
		 */
		public int order() {
			return _order;
		}
	}

	/**
	 * Represents a single classpath entry and allows us to query that entry for various metadata 
	 */
	public record ClasspathEntry( String string ) {

		/**
		 * Windows uses backslashes so we  might need to normalize the element 
		 */
		public String normalizedString() {
			return string().replace(File.separatorChar, '/').toLowerCase();
		}
		
		public CPEType type() {
			// FIXME: Note that in only this case, we're not working with the normalized string. That should be easier to work with than the platform-dependent string, so we should fix that // Hugi 2025-05-29
			if (matchesAny( string(), CPEPattern.System ) ) {
				return CPEType.WebObjects;
			}

			if ( matchesAny( normalizedString(), CPEPattern.Framework, CPEPattern.App, CPEPattern.Folder, CPEPattern.AntProject, CPEPattern.MavenProject ) ) {
				return CPEType.Normal;
			}

			if (matchesAny( string(), CPEPattern.ERFoundation, CPEPattern.ERWebObjects, CPEPattern.ERExtensions ) ) {
				return CPEType.WebObjectsHack;
			}

			return CPEType.Jar;
		}
		
		public int order() {
			return type().order();
		}
		
		public static boolean isSystemJar(String jar) {

			// check system path
			String systemRoot = System.getProperty("WORootDirectory");

			if (systemRoot != null && !systemRoot.isBlank()) {
				if (jar.startsWith(systemRoot)) {
					return true;
				}
			}

			// check maven path
			if (jar.indexOf("com/webobjects") > 0) {
				return true;
			}

			// check mac path
			if (jar.indexOf("System" + File.separator + "Library") > 0) {
				return true;
			}

			// check win path
			if (jar.indexOf("Apple" + File.separator + "Library") > 0) {
				return true;
			}

			// if embedded, check explicit names
			if (jar.matches("Frameworks[/\\\\]Java(Foundation|EOControl|EOAccess|WebObjects).*")) {
				return true;
			}

			return false;
		}

		private static boolean matchesAny( String string, CPEPattern... patterns ) {
			for (CPEPattern cpePattern : patterns) {
				if( cpePattern.matches(string) ) {
					return true;
				}
			}
			
			return false;
		}
	}

	private void reorderClasspath() {
		for (final String classpathPropertyName : CLASSPATH_PROPERTY_NAMES ) {
			final String classpathString = System.getProperty(classpathPropertyName);

			if( classpathString == null ) {
				log( "Reording classpath property '%s'. It is null, so nothing done".formatted(classpathPropertyName) );
			}
			else {
				log( "Reording classpath property '%s'. Unmodified entries are:\n%s".formatted( classpathPropertyName, String.join("\n", classpathString.split(File.pathSeparator) ) ) );

				final String[] classpathElements = classpathString.split(File.pathSeparator);

				// Construct a list of ClasspathEntries from the string elements of the original classpath.
				// Order it by the ordering specified by each ClasspathEntry's type.
				final List<ClasspathEntry> classPathEntries = Arrays
					.stream(classpathElements)
					.map(ClasspathEntry::new) // Generate a new ClasspathEntry from each string on the classpath
					.sorted( Comparator.comparing(ClasspathEntry::order)) // Order the ClasspathEntries using the ordering specified by the jar's type
					.toList();

				log( "We've reordered the classpath, here's the ordered list of classpath entries along with their associated types" );

				classPathEntries.forEach( cpe -> log( cpe.type() + " :: " + cpe.string() ) );

				// Generate a new classpath string from the ordered list and set it
				final String reorderedClasspathString = classPathEntries
					.stream()
					.map(ClasspathEntry::string)
					.collect( Collectors.joining(File.pathSeparator));
	
				System.setProperty(classpathPropertyName, reorderedClasspathString);

				// Finally, everything below here is just logging
				log( "Handled classpath from property '%s'. Modified entries are:\n%s".formatted( classpathPropertyName, String.join("\n", reorderedClasspathString.split(File.pathSeparator) ) ) );
				
				if( classpathString.equals(reorderedClasspathString ) ) {
					log( "CLASSPATH '%s' WAS NOT MODIFIED AT ALL".formatted(classpathPropertyName) );
				}
				else {
					log( "CLASSPATH '%s' WAS MODIFIED".formatted(classpathPropertyName) );
				}
			}
		}
	}

	private void doRandomStuffToClasspathElements() {
		for (final String classpathPropertyName : CLASSPATH_PROPERTY_NAMES ) {
			final String classpath = System.getProperty(classpathPropertyName);

			log( "Doing random stuff to classpath property '%s'".formatted(classpathPropertyName) );

			if( classpath != null ) {
				final String[] classpathElements = classpath.split(File.pathSeparator);
				
				for (final String classpathElement : classpathElements) {
					doRandomStuffToClasspathElement(new ClasspathEntry( classpathElement ));
				}
			}
		}
	}

	/**
	 * FIXME: Here's where we're going to have to do a lot of cleanup // Hugi 2025-05-29
	 */
	private void doRandomStuffToClasspathElement(final ClasspathEntry classpathEntry) {
		
		// FIXME: We want to try to eliminate direct operations on the string // Hugi 2025-05-29
		final String classpathElement = classpathEntry.string();

		String bundle = classpathElement.replaceAll(".*?[/\\\\](\\w+)\\.framework.*", "$1");
		final String excludes = "(JavaVM|JavaWebServicesSupport|JavaEODistribution|JavaWebServicesGeneration|JavaWebServicesClient)";

		if (bundle.matches("^\\w+$") && !bundle.matches(excludes)) {
			log( "doRandomStuffToClasspathElement() : first : " + classpathEntry );
			final String info = classpathElement.replaceAll("(.*?[/\\\\]\\w+\\.framework/Resources/).*", "$1Info.plist");

			if (new File(info).exists()) {
				allFrameworks.add(bundle);
				log("Added Real Bundle: " + bundle);
			}
			else {
				log("Omitted: " + info);
			}
		}
		else if (classpathElement.endsWith(".jar")) {
			log( "doRandomStuffToClasspathElement() : second : " + classpathEntry );
			final String infoPlistString = stringFromJar(classpathElement, "Resources/Info.plist");

			if (infoPlistString != null) {
				NSDictionary dict = (NSDictionary) NSPropertyListSerialization.propertyListFromString(infoPlistString);
				bundle = (String) dict.objectForKey("CFBundleExecutable");
				allFrameworks.add(bundle);
				log("Added Jar bundle: " + bundle);
			}
		}
		else {
			log( "doRandomStuffToClasspathElement() : third : " + classpathEntry );

			final boolean isAntProject = classpathElement.endsWith("/bin") && new File(new File(classpathElement).getParentFile(), ".project").exists();
			final boolean isMavenProject = classpathElement.endsWith("/target/classes") && new File(new File(classpathElement).getParentFile().getParent(), ".project").exists();

			log( "isAntProject: " + isAntProject );
			log( "isMavenProject: " + isMavenProject );
			log("bundle: " + bundle );

			if (isAntProject || isMavenProject) {
				// AK: I have no idea if this is checked anywhere else,
				// but this keeps is from having to set it in the VM args.

				for (File classpathFolder = new File(bundle); classpathFolder != null && classpathFolder.exists(); classpathFolder = classpathFolder.getParentFile()) {
					final File projectFile = new File(classpathFolder, ".project");

					if (projectFile.exists()) {
						try {
							boolean isBundle = false;
							Document projectDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(projectFile);
							projectDocument.normalize();
							NodeList natureNodeList = projectDocument.getElementsByTagName("nature");

							for (int natureNodeNum = 0; !isBundle && natureNodeNum < natureNodeList.getLength(); natureNodeNum++) {
								Element natureContainerNode = (Element) natureNodeList.item(natureNodeNum);
								Node natureNode = natureContainerNode.getFirstChild();
								String nodeValue = natureNode.getNodeValue();

								// AK: we don't actually add apps to the bundle process (Mike, why not!?)
								if (nodeValue != null && nodeValue.startsWith("org.objectstyle.wolips.") && !nodeValue.contains("application")) {
									isBundle = true;
								}
							}

							if (isBundle) {
								System.setProperty("NSProjectBundleEnabled", "true");
								String bundleName = classpathFolder.getName();

								File buildPropertiesFile = new File(classpathFolder, "build.properties");
								if (buildPropertiesFile.exists()) {
									Properties buildProperties = new Properties();
									buildProperties.load(new FileReader(buildPropertiesFile));
									if (buildProperties.get("project.name") != null) {
										// the project folder might
										// be named differently than
										// the actual bundle name
										bundleName = (String) buildProperties.get("project.name");
									}
								}

								allFrameworks.add(bundleName);
								log("Added Binary Bundle (Project bundle): " + bundleName);
							}
							else {
								log("Skipping binary bundle: " + classpathElement);
							}
						}
						catch (IOException | SAXException | ParserConfigurationException t) {
							System.err.println("Skipping '" + projectFile + "': " + t);
						}
						break;
					}
					log("Skipping, no project: " + projectFile);
				}
			}
		}
	}

	/**
	 * @return The main bundle, attempting various creative methods
	 */
	private static NSBundle mainBundle() {
		NSBundle mainBundle = null;

		final String mainBundleName = NSProperties._mainBundleName();

		if (mainBundleName != null) {
			mainBundle = NSBundle.bundleForName(mainBundleName);
		}

		if (mainBundle == null) {
			mainBundle = NSBundle.mainBundle();
		}

		// If the main bundle hasn't been initalized yet we do it ourselves
		if (mainBundle == null) {
			// bundle-less builds do not appear to work when running in servlet mode, so make it prefer the legacy bundle style
			// Disabled since ERXConfigurationManager.isDeployedAsServlet() no longer works. We need a different method to differentiate between regular/servlet environments // FIXME: Hugi 2021-12-18
			//			if (ERXApplication.isDevelopmentModeSafe() && ERXConfigurationManager.defaultManager().isDeployedAsServlet()) {
			//				NSBundleFactory.registerBundleFactory(new com.webobjects.foundation.development.NSLegacyBundle.Factory());
			//					throw new RuntimeException( "This was using code from ERFoundation. Killed." );
			//			}

			initMainBundle();

			mainBundle = NSBundle.mainBundle();
		}

		return mainBundle;
	}

	/**
	 * Attempts to initialize the main bundle by stabbing at NSBundle's private internals through reflection
	 */
	private static void initMainBundle() {
		try {
			final Field ClassPath = NSBundle.class.getDeclaredField("ClassPath");
			ClassPath.setAccessible(true);

			if (ClassPath.get(NSBundle.class) != null) {
				Method init = NSBundle.class.getDeclaredMethod("InitMainBundle");
				init.setAccessible(true);
				init.invoke(NSBundle.class);
			}
		}
		catch (Exception e) {
			System.err.println(e);
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Will be called after each bundle load. We use it to know when the last
	 * bundle loaded so we can post a notification for it. Note that the bundles
	 * will get loaded in the order of the classpath but the main bundle will
	 * get loaded last. So in order to set the properties correctly, we first
	 * add all the props that are not already set, then we add the main bundle
	 * and the WebObjects.properties and finally the command line props.
	 */
	public void bundleDidLoad(NSNotification n) {
		final NSBundle bundle = (NSBundle) n.object();

		log( "Invoking bundleDidLoad() for : " + bundle.name());

		if (allFrameworks.contains(bundle.name())) {
			allFrameworks.remove(bundle.name());
			log("Loaded " + bundle.name() + ". Remaining: " + allFrameworks);
		}
		else if (bundle.isFramework()) {
			log("Loaded unexpected framework bundle '" + bundle.name() + "'. Ensure your build.properties settings like project.name match the bundle name (including case).");
		}

		final String userName = propertyCheckingArgvFirst("user.name");

		applyIfUnset(readProperties(bundle, "Properties." + userName), allBundleProperties);
		applyIfUnset(readProperties(bundle, null), allBundleProperties);

		if (allFrameworks.isEmpty()) {
			log( "We've loaded all framework bundles. Now handling '%s'".formatted(bundle.name()) );

			mainProps = null;
			mainUserProps = null;

			collectMainProps(userName);

			allBundleProperties.putAll(mainProps);

			if (mainUserProps != null) {
				allBundleProperties.putAll(mainUserProps);
			}

			final String userHome = propertyCheckingArgvFirst("user.home");
			Properties userHomeProps = null;

			if (userHome != null && userHome.length() > 0) {
				userHomeProps = readProperties(new File(userHome, "WebObjects.properties"));
			}

			if (userHomeProps != null) {
				allBundleProperties.putAll(userHomeProps);
			}

			Properties props = NSProperties._getProperties();
			props.putAll(allBundleProperties);

			NSProperties._setProperties(props);

			// Add properties from the command line
			for( Object key : propertiesFromArgv.keySet() ) {
				Object value = propertiesFromArgv.get(key);
				NSProperties._setProperty((String) key, (String) value);
			}

			if (userHomeProps != null) {
				urls.add(0, urls.remove(urls.size() - 1));
			}

			if (mainUserProps != null) {
				urls.add(0, urls.remove(urls.size() - 1));
			}

			urls.add(0, urls.remove(urls.size() - 1));

			NSNotificationCenter.defaultCenter().postNotification(new NSNotification(ERXApplication.AllBundlesLoadedNotification, NSKeyValueCoding.NullValue));
		}
	}

	/**
	 * FIXME: This should probably return what it's read // Hugi 2025-05-28 
	 */
	private void collectMainProps(String username) {

		log("Invoking collectMainProps()");

		final NSBundle mainBundle = mainBundle();

		if (mainBundle != null) {
			mainUserProps = readProperties(mainBundle, "Properties." + username);
			mainProps = readProperties(mainBundle, "Properties");
		}

		if (mainProps == null) {
			String woUserDir = NSProperties.getProperty("webobjects.user.dir");

			if (woUserDir == null) {
				woUserDir = System.getProperty("user.dir");
			}
			
			// Start by trying to read Properties as if our working directory is a .woa bundle 
			mainUserProps = readProperties(new File(woUserDir, "Contents" + File.separator + "Resources" + File.separator + "Properties." + username));
			mainProps = readProperties(new File(woUserDir, "Contents" + File.separator + "Resources" + File.separator + "Properties"));

			// If no main Properties were found, try assuming our working directory is a maven project with standard structure
			if (mainProps == null) {
				mainUserProps = readProperties(new File(woUserDir, "src" + File.separator + "main" + File.separator + "resources" + File.separator + "Properties." + username));
				mainProps = readProperties(new File(woUserDir, "src" + File.separator + "main" + File.separator + "resources" + File.separator + "Properties"));
			}
			
			// And finally, check for a Properties file in a maven project using the the "woresources" resource folder name.
			// FIXME: This is getting ridiculous, we need to change this lookup to a loop (or clean this up in some other way) // Hugi 2025-05-26
			if (mainProps == null) {
				mainUserProps = readProperties(new File(woUserDir, "src" + File.separator + "main" + File.separator + "woresources" + File.separator + "Properties." + username));
				mainProps = readProperties(new File(woUserDir, "src" + File.separator + "main" + File.separator + "woresources" + File.separator + "Properties"));
			}
		}

		if (mainProps == null) {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

			try {
				final Enumeration<URL> propertiesFromClassPath = classLoader.getResources("Resources/Properties");

				final String mainBundleName = NSProperties._mainBundleName();

				// Look for a jar file name like: myapp[-1.0][-SNAPSHOT].jar
				final Pattern mainBundleJarPattern = Pattern.compile("\\b" + mainBundleName.toLowerCase() + "[-\\.\\d]*(snapshot)?\\.jar");

				URL propertiesPath = null;
				URL userPropertiesPath = null;

				while (propertiesFromClassPath.hasMoreElements()) {
					final URL url = propertiesFromClassPath.nextElement();
					final String urlAsString = url.toString();

					if (mainBundleJarPattern.matcher(urlAsString.toLowerCase()).find()) {
						try {
							propertiesPath = new URL(URLDecoder.decode(urlAsString, StandardCharsets.UTF_8));
							userPropertiesPath = new URL(propertiesPath.toExternalForm() + username);
						}
						catch (MalformedURLException exception) {
							exception.printStackTrace();
						}

						break;
					}
				}

				mainProps = readProperties(propertiesPath);
				mainUserProps = readProperties(userPropertiesPath);
			}
			catch (IOException exception) {
				exception.printStackTrace();
			}
		}

		if (mainProps == null) {
			throw new IllegalStateException("Main bundle 'Properties' file can't be read.  Did you run as a Java Application instead of a WOApplication in WOLips?\nPlease post your deployment configuration in the Wonder mailing list.");
		}
	}

	/**
	 * @return The value of the given property, preferring command line arguments
	 */
	private static String propertyCheckingArgvFirst(String key) {
		final String result = (String) propertiesFromArgv.get(key);

		if (result != null) {
			return result;
		}

		return NSProperties.getProperty(key);
	}

	/**
	 * Applies properties from [newProperties] to [properties] that are not currently set
	 */
	private static void applyIfUnset(Properties newProperties, Properties properties) {

		if (newProperties == null) {
			return;
		}

		for (Map.Entry entry : newProperties.entrySet()) {
			if (!properties.containsKey(entry.getKey())) {
				final String key = (String) entry.getKey();
				final String value = (String) entry.getValue();
				properties.setProperty(key, value);
			}
		}
	}

	/**
	 * @return The utf-8 encoded string contained in the given jar file
	 */
	private static String stringFromJar(final String pathToJar, final String pathInJar) {

		if (!new File(pathToJar).exists()) {
			log("Will not process jar '%s' because it cannot be found ...".formatted( pathToJar));
			return null;
		}

		try (JarFile jarFile = new JarFile(pathToJar)) {
			final JarEntry jarEntry = (JarEntry) jarFile.getEntry(pathInJar);

			if (jarEntry == null) {
				return null;
			}

			try (InputStream is = jarFile.getInputStream(jarEntry)) {
				return new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}
		}
		catch (IOException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}

	private Properties readProperties(File file) {
		
		if (!file.exists()) {
			return null;
		}

		try {
			URL url = file.toURI().toURL();
			return readProperties(url);
		}
		catch (MalformedURLException e) {
			throw new UncheckedIOException(e);
		}
	}

	private Properties readProperties(URL url) {

		if (url == null) {
			return null;
		}

		try {
			Properties result = new Properties();
			result.load(url.openStream());
			urls.add(url);
			return result;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * FIXME: Why are we going two apparently identical ways to load properties here, based on if [name] is null? Why not just invoke with "Properties"? // Hugi 2025-05-29 
	 */
	private Properties readProperties(NSBundle bundle, String name) {

		if (bundle == null) {
			return null;
		}

		if (name == null) {
			URL url = bundle.pathURLForResourcePath("Properties");

			if (url != null) {
				urls.add(url);
			}

			return bundle.properties();
		}

		try (InputStream inputStream = bundle.inputStreamForResourcePath(name)) {

			if (inputStream == null) {
				return null;
			}

			Properties result = new Properties();
			result.load(inputStream);
			urls.add(bundle.pathURLForResourcePath(name));
			return result;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Since most of the work here is performed before any actual logging has been set up or configured, we have to do it ourselves
	 */
	private static void log(String message) {
		if (loggingEnabled()) {
			String logmessage = "== ERXLoader : " + message;

			System.out.println( logmessage);
			
			// If a logfile is set, we also append to that.
			if( _logfile != null ) {
				try {
					logmessage += "\n";
					Files.write(_logfile, logmessage.getBytes(), StandardOpenOption.APPEND, StandardOpenOption.CREATE );
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * @return true if debugging is enabled for ERXLoader
	 * 
	 * CHECKME: We should probably start by initializing _debugEnabled from the property // Hugi 2025-05-28
	 */
	private static boolean loggingEnabled() {
		// FIXME: While we're working on this, we're enabling logging globally. Disable once we're done // Hugi 2025-05-29
		return true;
		/*
		if( _loggingEnabled ) {
			return true;
		}

		return "DEBUG".equals(System.getProperty("er.extensions.appserver.projectBundleLoading"));
		*/
	}

	/**
	 * Allows us to specify in code if debug logging should be enabled for this awesomeness 
	 */
	public static void enableLogging() {
		_loggingEnabled = true;
	}

	/**
	 * Allows us to specify in code if debug logging should be enabled for this awesomeness 
	 */
	public static void setLogFile( final Path path ) {
		_logfile = path;
	}
	/**
	 * @return True if we've processed every framework bundle we're avare of
	 */
	public boolean didLoad() {
		return allFrameworks.isEmpty();
	}

	/**
	 * Exposed for logging only
	 */
	public Set<String> allFrameworks() {
		return allFrameworks;
	}
}