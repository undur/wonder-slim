package er.extensions.appserver;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
 * Responsible for classpath munging and ensuring all bundles are loaded
 * 
 * @property er.extensions.appserver.projectBundleLoading - to see logging this
 *           has to be set on the command line by using
 *           -Der.extensions.appserver.projectBundleLoading=DEBUG
 * 
 * @author ak
 */

public class ERXLoader {

	private static final Logger log = LoggerFactory.getLogger(ERXLoader.class);

	/**
	 * Names of properties storing classpaths.
	 * 
	 * FIXME: I'm not actually sure why we're including com.webobjects.classpath? I mean... It doesn't do any harm, but does it ever have a value? 	// Hugi 2025-05-29
	 */
	private static final List<String> CLASSPATH_PROPERTY_NAMES = List.of( "java.class.path", "com.webobjects.classpath" );

	/**
	 * Properties passed to the application's main method
	 */
	private static NSDictionary propertiesFromArgv;

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
	 * Called prior to actually initializing the app.
	 * Defines framework load order, class path order, checks patches etc.
	 */
	public ERXLoader(String[] argv) {
		propertiesFromArgv = NSProperties.valuesFromArgv(argv);

		reorderClasspath();
		doRandomStuffToClasspathElements();

		NSNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector("bundleDidLoad"), "NSBundleDidLoadNotification", null);
	}

	private void reorderClasspath() {
		for (final String classpathPropertyName : CLASSPATH_PROPERTY_NAMES ) {
			final String classpath = System.getProperty(classpathPropertyName);

			if( classpath == null ) {
				log( "Reording classpath property '%s'. It is null, so nothing done".formatted(classpathPropertyName) );
			}
			else {
				log( "Reording classpath property '%s'. Unmodified value is:\n%s".formatted( classpathPropertyName, String.join("\n", classpath.split(File.pathSeparator) ) ) );

				final String frameworkPattern = ".*?/(\\w+)\\.framework/Resources/Java/\\1.jar".toLowerCase();
				final String appPattern = ".*?/(\\w+)\\.woa/Contents/Resources/Java/\\1.jar".toLowerCase();
				final String folderPattern = ".*?/Resources/Java/?$".toLowerCase();
				final String projectPattern = ".*?/(\\w+)/bin$".toLowerCase();

				final List<String> normalLibs = new ArrayList<>();
				final List<String> systemLibs = new ArrayList<>();
				final List<String> jarLibs = new ArrayList<>();

				final String[] classpathElements = classpath.split(File.pathSeparator);

				for (final String classpathElement : classpathElements) {

					// Windows uses backslashes so we need to normalize the element
					final String normalizedClasspathElement = classpathElement.replace(File.separatorChar, '/').toLowerCase();

					log("Checking: " + classpathElement);

					// all patched frameworks here
					if (isSystemJar(classpathElement)) {
						systemLibs.add( classpathElement );
					}
					else if (normalizedClasspathElement.matches(frameworkPattern) || normalizedClasspathElement.matches(appPattern) || normalizedClasspathElement.matches(folderPattern)) {
						normalLibs.add( classpathElement );
					}
					else if (normalizedClasspathElement.matches(projectPattern) || normalizedClasspathElement.matches(".*?/erfoundation.jar") || normalizedClasspathElement.matches(".*?/erwebobjects.jar")) {
						normalLibs.add( classpathElement );
					}
					else {
						jarLibs.add( classpathElement );
					}
				}

				// Now collect all our re-ordered classpath element
				final List<String> allLibs = new ArrayList<>();
				allLibs.addAll(normalLibs);
				allLibs.addAll(systemLibs);
				allLibs.addAll(jarLibs);
				
				final String reorderedClasspath = String.join( File.pathSeparator, allLibs );

				if (System.getProperty("_DisableClasspathReorder") == null) {
					System.setProperty(classpathPropertyName, reorderedClasspath);
				}
				
				log( "Handled classpath from property '%s'. Modified value is:\n%s".formatted( classpathPropertyName, String.join("\n", reorderedClasspath.split(File.pathSeparator) ) ) );
				
				if( classpath.equals(reorderedClasspath ) ) {
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
					doRandomStuffToClasspathElement(classpathElement);
				}
			}
		}
	}

	/**
	 * FIXME: Here's where we're going to have to do a lot of cleanup // Hugi 2025-05-29
	 */
	private void doRandomStuffToClasspathElement(final String classpathElement) {
		String bundle = classpathElement.replaceAll(".*?[/\\\\](\\w+)\\.framework.*", "$1");
		final String excludes = "(JavaVM|JavaWebServicesSupport|JavaEODistribution|JavaWebServicesGeneration|JavaWebServicesClient)";

		if (bundle.matches("^\\w+$") && !bundle.matches(excludes)) {
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
			final String infoPlistString = stringFromJar(classpathElement, "Resources/Info.plist");

			if (infoPlistString != null) {
				NSDictionary dict = (NSDictionary) NSPropertyListSerialization.propertyListFromString(infoPlistString);
				bundle = (String) dict.objectForKey("CFBundleExecutable");
				allFrameworks.add(bundle);
				log("Added Jar bundle: " + bundle);
			}
		}

		// MS: This is totally hacked in to make Wonder startup properly with the new rapid turnaround.
		// It's duplicating (poorly) code from NSProjectBundle.
		// I'm not sure we actually need this anymore, because NSBundle now fires an "all bundles loaded" event.
		else if (classpathElement.endsWith("/bin") && new File(new File(classpathElement).getParentFile(), ".project").exists()) {

			// AK: I have no idea if this is checked anywhere else,
			// but this keeps is from having to set it in the VM args.
			log("Plain bundle: " + classpathElement);

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
					catch (Throwable t) {
						System.err.println("Skipping '" + projectFile + "': " + t);
					}
					break;
				}
				log("Skipping, no project: " + projectFile);
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

			insertCommandLineArguments();

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
	private void collectMainProps(String userName) {
		final NSBundle mainBundle = mainBundle();

		if (mainBundle != null) {
			mainUserProps = readProperties(mainBundle, "Properties." + userName);
			mainProps = readProperties(mainBundle, "Properties");
		}

		if (mainProps == null) {
			String woUserDir = NSProperties.getProperty("webobjects.user.dir");

			if (woUserDir == null) {
				woUserDir = System.getProperty("user.dir");
			}
			
			// Start by trying to read Properties as if our working directory is a .woa bundle 
			mainUserProps = readProperties(new File(woUserDir, "Contents" + File.separator + "Resources" + File.separator + "Properties." + userName));
			mainProps = readProperties(new File(woUserDir, "Contents" + File.separator + "Resources" + File.separator + "Properties"));

			// If no main Properties were found, try assuming our working directory is a maven project with standard structure
			if (mainProps == null) {
				mainUserProps = readProperties(new File(woUserDir, "src" + File.separator + "main" + File.separator + "resources" + File.separator + "Properties." + userName));
				mainProps = readProperties(new File(woUserDir, "src" + File.separator + "main" + File.separator + "resources" + File.separator + "Properties"));
			}
			
			// And finally, check for a Properties file in a maven project using the the "woresources" resource folder name.
			// FIXME: This is getting ridiculous, we need to change this lookup to a loop (or clean this up in some other way) // Hugi 2025-05-26
			if (mainProps == null) {
				mainUserProps = readProperties(new File(woUserDir, "src" + File.separator + "main" + File.separator + "woresources" + File.separator + "Properties." + userName));
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
							userPropertiesPath = new URL(propertiesPath.toExternalForm() + userName);
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
		final String result = (String) propertiesFromArgv.valueForKey(key);

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

	private boolean isSystemJar(String jar) {

		// check system path
		String systemRoot = System.getProperty("WORootDirectory");

		if (systemRoot != null) {
			if (jar.startsWith(systemRoot)) {
				return true;
			}
		}

		// check maven path
		if (jar.indexOf("webobjects" + File.separator + "apple") > 0) {
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

	/**
	 * @return The utf-8 encoded string contained in the given jar file
	 */
	private static String stringFromJar(final String pathToJar, final String pathInJar) {

		if (!new File(pathToJar).exists()) {
			log.warn("Will not process jar '" + pathToJar + "' because it cannot be found ...");
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

	/**
	 * Copies properties from the command line to the static dictionary propertiesFromArgv.
	 */
	private static void insertCommandLineArguments() {
		for( Object key : propertiesFromArgv.allKeys() ) {
			Object value = propertiesFromArgv.get(key);
			NSProperties._setProperty((String) key, (String) value);
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
			e.printStackTrace();
			return null;
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
		catch (MalformedURLException exception) {
			exception.printStackTrace();
			return null;
		}
		catch (IOException exception) {
			return null;
		}
	}

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
		catch (MalformedURLException exception) {
			exception.printStackTrace();
			return null;
		}
		catch (IOException exception) {
			return null;
		}
	}

	/**
	 * Since most of the work here is performed before any actual logging has been set up or configured, we have to do it ourselves
	 */
	private static void log(String message) {
		if (loggingEnabled()) {
			System.out.println( "== ERXLoader : " + message);
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