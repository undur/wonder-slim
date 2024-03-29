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
import java.util.Iterator;
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

import com.webobjects.foundation.NSArray;
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
	 * Command line arguments passed to the main method
	 */
	private static NSDictionary propertiesFromArgv;

	/**
	 * Holds the framework names during startup
	 */
	Set<String> allFrameworks;

	private Properties allBundleProps;

	/**
	 * CHECKME: Disabled since this never seems to be read // Hugi 2023-07-22
	 */
//	private Properties defaultProperties;

	/**
	 * URLs to loaded property files get added here, apparently, then nothing is done with them?
	 */
	private List<URL> urls = new ArrayList<>();
	
	private Properties mainProps;
	private Properties mainUserProps;

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
	 * Called prior to actually initializing the app. Defines framework load
	 * order, class path order, checks patches etc.
	 */
	public ERXLoader(String[] argv) {
		final List<String> cps = List.of( "java.class.path", "com.webobjects.classpath" );
		propertiesFromArgv = NSProperties.valuesFromArgv(argv);
//		defaultProperties = (Properties) NSProperties._getProperties().clone(); // CHECKME: Disabled since it never seems to be read // Hugi 2023-07-22
		allFrameworks = new HashSet<>();

		for (String cpName : cps ) {
			final String cp = System.getProperty(cpName);

			if (cp != null) {
				String parts[] = cp.split(File.pathSeparator);
				String normalLibs = "";
				String systemLibs = "";
				String jarLibs = "";
				String frameworkPattern = ".*?/(\\w+)\\.framework/Resources/Java/\\1.jar".toLowerCase();
				String appPattern = ".*?/(\\w+)\\.woa/Contents/Resources/Java/\\1.jar".toLowerCase();
				String folderPattern = ".*?/Resources/Java/?$".toLowerCase();
				String projectPattern = ".*?/(\\w+)/bin$".toLowerCase();

				for (int i = 0; i < parts.length; i++) {
					String jar = parts[i];
					// Windows has \, we need to normalize
					String fixedJar = jar.replace(File.separatorChar, '/').toLowerCase();
					debugMsg("Checking: " + jar);
					// all patched frameworks here
					if (isSystemJar(jar)) {
						systemLibs += jar + File.pathSeparator;
					}
					else if (fixedJar.matches(frameworkPattern) || fixedJar.matches(appPattern) || fixedJar.matches(folderPattern)) {
						normalLibs += jar + File.pathSeparator;
					}
					else if (fixedJar.matches(projectPattern) || fixedJar.matches(".*?/erfoundation.jar") || fixedJar.matches(".*?/erwebobjects.jar")) {
						normalLibs += jar + File.pathSeparator;
					}
					else {
						jarLibs += jar + File.pathSeparator;
					}

					String bundle = jar.replaceAll(".*?[/\\\\](\\w+)\\.framework.*", "$1");
					String excludes = "(JavaVM|JavaWebServicesSupport|JavaEODistribution|JavaWebServicesGeneration|JavaWebServicesClient)";

					if (bundle.matches("^\\w+$") && !bundle.matches(excludes)) {
						String info = jar.replaceAll("(.*?[/\\\\]\\w+\\.framework/Resources/).*", "$1Info.plist");
						if (new File(info).exists()) {
							allFrameworks.add(bundle);
							debugMsg("Added Real Bundle: " + bundle);
						}
						else {
							debugMsg("Omitted: " + info);
						}
					}
					else if (jar.endsWith(".jar")) {
						String info = stringFromJar(jar, "Resources/Info.plist");
						if (info != null) {
							NSDictionary dict = (NSDictionary) NSPropertyListSerialization.propertyListFromString(info);
							bundle = (String) dict.objectForKey("CFBundleExecutable");
							allFrameworks.add(bundle);
							debugMsg("Added Jar bundle: " + bundle);
						}
					}

					// MS: This is totally hacked in to make Wonder startup
					// properly with the new rapid turnaround. It's duplicating
					// (poorly)
					// code from NSProjectBundle. I'm not sure we actually need
					// this anymore, because NSBundle now fires an "all bundles
					// loaded" event.
					else if (jar.endsWith("/bin") && new File(new File(jar).getParentFile(), ".project").exists()) {
						// AK: I have no idea if this is checked anywhere else,
						// but this keeps is from having to set it in the VM
						// args.
						debugMsg("Plain bundle: " + jar);
						for (File classpathFolder = new File(bundle); classpathFolder != null && classpathFolder.exists(); classpathFolder = classpathFolder.getParentFile()) {
							File projectFile = new File(classpathFolder, ".project");
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
										// AK: we don't actually add apps to
										// the bundle process (Mike, why
										// not!?)
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
										debugMsg("Added Binary Bundle (Project bundle): " + bundleName);
									}
									else {
										debugMsg("Skipping binary bundle: " + jar);
									}
								}
								catch (Throwable t) {
									System.err.println("Skipping '" + projectFile + "': " + t);
								}
								break;
							}
							debugMsg("Skipping, no project: " + projectFile);
						}
					}
				}

				String newCP = "";

				if (normalLibs.length() > 1) {
					normalLibs = normalLibs.substring(0, normalLibs.length() - 1);
					newCP += normalLibs;
				}
				if (systemLibs.length() > 1) {
					systemLibs = systemLibs.substring(0, systemLibs.length() - 1);
					newCP += (newCP.length() > 0 ? File.pathSeparator : "") + systemLibs;
				}
				if (jarLibs.length() > 1) {
					jarLibs = jarLibs.substring(0, jarLibs.length() - 1);
					newCP += (newCP.length() > 0 ? File.pathSeparator : "") + jarLibs;
				}

				if (System.getProperty("_DisableClasspathReorder") == null) {
					System.setProperty(cpName, newCP);
				}
			}
		}

		NSNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector("bundleDidLoad"), "NSBundleDidLoadNotification", null);
	}

	// for logging before logging has been setup and configured by loading the
	// properties files
	private void debugMsg(String msg) {
		if ("DEBUG".equals(System.getProperty("er.extensions.appserver.projectBundleLoading"))) {
			System.out.println(msg);
		}
	}

	public boolean didLoad() {
		return (allFrameworks != null && allFrameworks.size() == 0);
	}

	private static NSBundle mainBundle() {
		NSBundle mainBundle = null;
		String mainBundleName = NSProperties._mainBundleName();
		if (mainBundleName != null) {
			mainBundle = NSBundle.bundleForName(mainBundleName);
		}
		if (mainBundle == null) {
			mainBundle = NSBundle.mainBundle();
		}
		if (mainBundle == null) {
			// AK: when we get here, the main bundle wasn't inited yet so we do
			// it ourself...

// Disabled this since ERXConfigurationManager.isDeployedAsServlet() no longer works // FIXME: Hugi 2021-12-18
//			if (ERXApplication.isDevelopmentModeSafe() && ERXConfigurationManager.defaultManager().isDeployedAsServlet()) {
			// bundle-less builds do not appear to work when running in
			// servlet mode, so make it prefer the legacy bundle style
//				NSBundleFactory.registerBundleFactory(new com.webobjects.foundation.development.NSLegacyBundle.Factory());
//					throw new RuntimeException( "This was using code from ERFoundation. Killed." );
//			}

			try {
				Field ClassPath = NSBundle.class.getDeclaredField("ClassPath");
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
			mainBundle = NSBundle.mainBundle();
		}
		return mainBundle;
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
		NSBundle bundle = (NSBundle) n.object();
		if (allFrameworks.contains(bundle.name())) {
			allFrameworks.remove(bundle.name());
			debugMsg("Loaded " + bundle.name() + ". Remaining: " + allFrameworks);
		}
		else if (bundle.isFramework()) {
			debugMsg("Loaded unexpected framework bundle '" + bundle.name() + "'. Ensure your build.properties settings like project.name match the bundle name (including case).");
		}
		if (allBundleProps == null) {
			allBundleProps = new Properties();
		}

		String userName = propertyFromCommandLineFirst("user.name");

		applyIfUnset(readProperties(bundle, "Properties." + userName));
		applyIfUnset(readProperties(bundle, null));

		if (allFrameworks.size() == 0) {
			mainProps = null;
			mainUserProps = null;

			collectMainProps(userName);

			allBundleProps.putAll(mainProps);
			if (mainUserProps != null) {
				allBundleProps.putAll(mainUserProps);
			}

			String userHome = propertyFromCommandLineFirst("user.home");
			Properties userHomeProps = null;
			if (userHome != null && userHome.length() > 0) {
				userHomeProps = readProperties(new File(userHome, "WebObjects.properties"));
			}

			if (userHomeProps != null) {
				allBundleProps.putAll(userHomeProps);
			}

			Properties props = NSProperties._getProperties();
			props.putAll(allBundleProps);

			NSProperties._setProperties(props);

			insertCommandLineArguments();
			if (userHomeProps != null) {
				urls.add(0, urls.remove(urls.size() - 1));
			}
			if (mainUserProps != null) {
				urls.add(0, urls.remove(urls.size() - 1));
			}
			urls.add(0, urls.remove(urls.size() - 1));
			// System.out.print(urls);
			NSNotificationCenter.defaultCenter().postNotification(new NSNotification(ERXApplication.AllBundlesLoadedNotification, NSKeyValueCoding.NullValue));
		}
	}

	private String propertyFromCommandLineFirst(String key) {
		String result = (String) propertiesFromArgv.valueForKey(key);
		if (result == null) {
			result = NSProperties.getProperty(key);
		}
		return result;
	}

	private void collectMainProps(String userName) {
		NSBundle mainBundle = mainBundle();

		if (mainBundle != null) {
			mainUserProps = readProperties(mainBundle, "Properties." + userName);
			mainProps = readProperties(mainBundle, "Properties");
		}
		if (mainProps == null) {
			String woUserDir = NSProperties.getProperty("webobjects.user.dir");
			if (woUserDir == null) {
				woUserDir = System.getProperty("user.dir");
			}
			mainUserProps = readProperties(new File(woUserDir, "Contents" + File.separator + "Resources" + File.separator + "Properties." + userName));
			mainProps = readProperties(new File(woUserDir, "Contents" + File.separator + "Resources" + File.separator + "Properties"));
		}

		if (mainProps == null) {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

			try {
				Enumeration<URL> jarBundles = classLoader.getResources("Resources/Properties");

				URL propertiesPath = null;
				URL userPropertiesPath = null;
				String mainBundleName = NSProperties._mainBundleName();

				// Look for a jar file name like: myapp[-1.0][-SNAPSHOT].jar
				Pattern mainBundleJarPattern = Pattern.compile("\\b" + mainBundleName.toLowerCase() + "[-\\.\\d]*(snapshot)?\\.jar");

				while (jarBundles.hasMoreElements()) {
					URL url = jarBundles.nextElement();

					String urlAsString = url.toString();

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

	private void applyIfUnset(Properties bundleProps) {

		if (bundleProps == null) {
			return;
		}

		for (Iterator iter = bundleProps.entrySet().iterator(); iter.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();

			if (!allBundleProps.containsKey(entry.getKey())) {
				allBundleProps.setProperty((String) entry.getKey(), (String) entry.getValue());
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
		catch (IOException e1) {
			throw NSForwardException._runtimeExceptionForThrowable(e1);
		}
	}

	/**
	 * Copies the props from the command line to the static dict propertiesFromArgv.
	 */
	private static void insertCommandLineArguments() {
		NSArray keys = propertiesFromArgv.allKeys();
		int count = keys.count();
		for (int i = 0; i < count; i++) {
			Object key = keys.objectAtIndex(i);
			Object value = propertiesFromArgv.objectForKey(key);
			NSProperties._setProperty((String) key, (String) value);
		}
	}
}