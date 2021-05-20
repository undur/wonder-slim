package com.webobjects.foundation.development;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSMutableSet;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation._NSStringUtilities;
import com.webobjects.foundation._NSThreadsafeMutableDictionary;
import com.webobjects.foundation._NSUtilities;

public class NSLegacyBundle extends NSBundle {
	public static final String NS_GLOBAL_PROPERTIES_PATH = "NSGlobalPropertiesPath";

	private static final String LEGACY_GLOBAL_PROPERTIES_PATH = "WebObjectsPropertiesReplacement";

	public static final String CFBUNDLESHORTVERSIONSTRINGKEY = "CFBundleShortVersionString";

	public static final String MANIFESTIMPLEMENTATIONVERSIONKEY = "Implementation-Version";

	public static class Factory extends NSBundleFactory {
		public static NSBundle _bundleWithPathShouldCreateIsJar(String aPath, boolean shouldCreateBundle, boolean newIsJar) {
			NSBundle bundle = null;
			String normalizedPath = null;
			String cleanedPath = null;
			NSBundle mainBundle = NSBundle.mainBundle();
			if (mainBundle != null && mainBundle.name().equals(aPath))
				bundle = mainBundle;
			if (bundle == null)
				bundle = NSBundle.bundleForName(aPath);
			if (bundle == null) {
				normalizedPath = NSBundle._normalizeExistingBundlePath(aPath);
				bundle = NSBundle._lookupBundleWithPath(normalizedPath);
			}
			if (bundle == null) {
				cleanedPath = NSBundle._cleanNormalizedBundlePath(normalizedPath);
				bundle = NSBundle._lookupBundleWithPath(cleanedPath);
			}
			if (bundle == null && shouldCreateBundle)
				if (newIsJar) {
					try {
						URL url = new URL(_NSStringUtilities.concat("jar:", NSPathUtilities._fileURLPrefix, aPath, "!/").concat(NSLegacyBundle.ResourcesInfoPlist));
						JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
						jarConnection.getJarFile();
						bundle = new NSLegacyBundle(aPath, true);
						((NSLegacyBundle) bundle)._init();
					}
					catch (Exception exception) {
					}
				}
				else {
					bundle = new NSLegacyBundle(cleanedPath, false);
					((NSLegacyBundle) bundle)._init();
				}
			return bundle;
		}

		public NSBundle bundleForPath(String path, boolean shouldCreateBundle, boolean newIsJar) {
			NSBundle bundle = null;
			if (newIsJar) {
				bundle = _bundleWithPathShouldCreateIsJar(path, shouldCreateBundle, true);
			}
			else if (!shouldCreateBundle) {
				bundle = _bundleWithPathShouldCreateIsJar(path, false, false);
			}
			else {
				int index = path.lastIndexOf(NSLegacyBundle.RSUFFIX);
				NSMutableArray<String> resourcesSubdirs = new NSMutableArray();
				if (index == -1) {
					resourcesSubdirs.addObject(path.concat(NSLegacyBundle.RSUFFIX));
					resourcesSubdirs.addObject(path.concat(NSLegacyBundle.CSUFFIX));
				}
				else {
					resourcesSubdirs.addObject(path.substring(0, index + NSLegacyBundle.RSUFFIX.length()));
				}
				for (Iterator<String> iterator = resourcesSubdirs.iterator(); iterator.hasNext();) {
					String oneSubdir = iterator.next();
					String infoDictPath = String.valueOf(oneSubdir) + File.separator + "Info.plist";
					File file = new File(infoDictPath);
					if (file.exists()) {
						bundle = _bundleWithPathShouldCreateIsJar(path, true, false);
						break;
					}
				}
			}
			return bundle;
		}
	}

	public static final Class _CLASS = _NSUtilities._classWithFullySpecifiedName("com.webobjects.foundation.NSBundle");

	public static final String BundleDidLoadNotification = "NSBundleDidLoadNotification";

	public static final String LoadedClassesNotification = "NSLoadedClassesNotification";

	private static final String JSUFFIX = String.valueOf(File.separator) + "Java";

	private static final String MAIN_BUNDLE_NAME = "MainBundle";

	private static final String NONLOCALIZED_LOCALE = "Nonlocalized.lproj";

	private static final String NONLOCALIZED_LOCALE_PREFIX = "Nonlocalized.lproj" + File.separator;

	private static final String RESOURCES = "Resources";

	public static final String RSUFFIX = String.valueOf(File.separator) + "Resources";

	public static final String RJSUFFIX = String.valueOf(RSUFFIX) + File.separator + "Java";

	private static final String CONTENTS = "Contents";

	public static final String CSUFFIX = String.valueOf(File.separator) + "Contents";

	public static final String CRSUFFIX = String.valueOf(CSUFFIX) + RSUFFIX;

	private static final int NSBUNDLE = 1;

	private static final int CFBUNDLE = 2;

	private static final _NSThreadsafeMutableDictionary OldResourceFilters = new _NSThreadsafeMutableDictionary(
			new NSMutableDictionary());

	public static final String InfoPlistFilename = "Info.plist";

	private static final _NSUtilities.JavaArchiveFilter TheJavaArchiveFilter = new _NSUtilities.JavaArchiveFilter();

	private static final _NSUtilities.JavaClassFilter TheJavaClassFilter = new _NSUtilities.JavaClassFilter();

	public static String ResourcesInfoPlist = "Resources/Info.plist";

	private static String JarResourcesInfoPlist = "!/" + ResourcesInfoPlist;

	private static String ResourcesProperties = "Resources/Properties";

	private static NSMutableDictionary TheFileDict = new NSMutableDictionary(1);

	private static boolean safeInvokeDeprecatedJarBundleAPI = false;

	private boolean isJar;

	private JarFile jarFile;

	private NSMutableArray<JarEntry> jarFileEntries;

	private NSDictionary jarFileLayout;

	private String _bundleURLPrefix;

	private String bundlePath;

	private int bundleType;

	private boolean classesHaveBeenLoaded;

	private NSArray<String> classNames;

	private NSDictionary<String, Object> infoDictionary;

	private boolean isFramework;

	private Properties properties;

	private String name;

	private NSArray<String> packages;

	private NSMutableArray<String> resourceBuckets;

	private String resourcePath;

	private String contentsPath;

	private String _resourceLocation;

	private static final String jarEndsWithString = ".jar".concat(JarResourcesInfoPlist);

	private static final String __exctractStringFromURL(URL anURL) {
		String url2Path = null;
		try {
			String urlPath = anURL.getPath();
			if (urlPath.endsWith(jarEndsWithString)) {
				url2Path = urlPath.substring(0, urlPath.length() - JarResourcesInfoPlist.length());
				URL url2 = new URL(url2Path);
				url2Path = url2.getPath();
			}
		}
		catch (Exception exception) {
		}
		return url2Path;
	}

	protected NSLegacyBundle(String bundlePath, boolean isJar) {
		initIsJar(isJar);
		initBundlePath(bundlePath);
	}

	protected NSLegacyBundle() {
	}

	public void _init() {
		initBundleURLPrefix();
		initBundleType();
		initJarFileLayout();
		initContentsPath();
		initResourcePath();
		initResourceBuckets();
		initInfoDictionary();
		initName();
		this.isFramework = couldBeAFramework();
		initProperties();
		initClassNames();
		initPackages();
	}

	public void _bundlesDidLoad() {
	}

	private static NSBundle.OldResourceFilter OldResourceFilterForExtension(String anExtension) {
		NSBundle.OldResourceFilter rf = null;
		if (anExtension == null)
			throw new IllegalArgumentException("Illegal resource search: cannot search using a null extension");
		String correctedExtension = anExtension.startsWith(".") ? anExtension.substring(1) : anExtension;
		rf = (NSBundle.OldResourceFilter) OldResourceFilters.objectForKey(correctedExtension);
		if (rf == null) {
			rf = new NSBundle.OldResourceFilter(correctedExtension);
			OldResourceFilters.setObjectForKey(rf, correctedExtension);
		}
		return rf;
	}

	public NSArray<String> bundleClassPackageNames() {
		return this.packages;
	}

	@Deprecated
	public String bundlePath() {
		return this.bundlePath;
	}

	public URL bundlePathURL() {
		return NSPathUtilities._URLWithPath(this.bundlePath);
	}

	public String _bundleURLPrefix() {
		return this._bundleURLPrefix;
	}

	public NSArray<String> bundleClassNames() {
		return this.classNames;
	}

	@Deprecated
	public NSDictionary<String, Object> infoDictionary() {
		return this.infoDictionary;
	}

	public NSDictionary<String, Object> _infoDictionary() {
		return this.infoDictionary;
	}

	public URL pathURLForResourcePath(String aResourcePath) {
		return _pathURLForResourcePath(aResourcePath, true);
	}

	public URL _pathURLForResourcePath(String aResourcePath, boolean returnDirectories) {
		URL url = null;
		if (aResourcePath != null && aResourcePath.length() > 0 &&
				this.resourceBuckets.indexOfIdenticalObject(this._resourceLocation) != -1) {
			String realPath;
			boolean isLocalized = true;
			if (aResourcePath.startsWith("Nonlocalized.lproj"))
				isLocalized = false;
			if (isLocalized) {
				realPath = aResourcePath;
			}
			else {
				realPath = aResourcePath.substring("Nonlocalized.lproj".length());
			}
			if (this.isJar) {
				if (!realPath.startsWith("/"))
					realPath = "/".concat(realPath);
				ZipEntry ze = this.jarFile.getEntry(this._resourceLocation.concat(realPath));
				if (ze != null && (returnDirectories || !ze.isDirectory()))
					try {
						url = new URL(this._bundleURLPrefix.concat(ze.getName()));
					}
					catch (Exception e) {
						throw NSForwardException._runtimeExceptionForThrowable(e);
					}
			}
			else {
				if (!realPath.startsWith(File.separator))
					realPath = File.separator.concat(realPath);
				try {
					File f = new File(this.resourcePath.concat(realPath));
					if (f.exists() && (f.isFile() || returnDirectories))
						url = NSPathUtilities._URLWithPath(f.getCanonicalPath());
				}
				catch (Exception exception) {
					throw NSForwardException._runtimeExceptionForThrowable(exception);
				}
			}
		}
		return url;
	}

	public boolean isFramework() {
		return this.isFramework;
	}

	public boolean _isCFBundle() {
		return (this.bundleType == 2);
	}

	public boolean isJar() {
		return this.isJar;
	}

	public JarFile _jarFile() {
		return this.jarFile;
	}

	public NSDictionary _jarFileLayout() {
		return this.jarFileLayout;
	}

	@Deprecated
	public boolean load() {
		return this.classesHaveBeenLoaded;
	}

	public String name() {
		return this.name;
	}

	@Deprecated
	public String pathForResource(String aName, String anExtension) {
		return pathForResource(aName, anExtension, (String) null);
	}

	@Deprecated
	public String pathForResource(String aName, String anExtension, String aSubDirPath) {
		if (this.isJar) {
			if (safeInvokeDeprecatedJarBundleAPI)
				return null;
			throw new IllegalStateException("pathsForResoures cannot be invoked on a jar-based NSBundle");
		}
		String path = null;
		if (aName != null) {
			String fileName;
			Enumeration<String> en = this.resourceBuckets.objectEnumerator();
			String localePrefix = NSBundle._DefaultLocalePrefix();
			String[] pathFragments = new String[2];
			String pathPrefix = null;
			if (anExtension == null) {
				fileName = aName;
			}
			else if (anExtension.startsWith(".") || aName.endsWith(".")) {
				fileName = String.valueOf(aName) + anExtension;
			}
			else {
				fileName = String.valueOf(aName) + "." + anExtension;
			}
			if (aSubDirPath == null) {
				pathFragments[0] = "";
				pathFragments[1] = localePrefix;
			}
			else {
				pathFragments[0] = aSubDirPath;
				pathFragments[1] = String.valueOf(aSubDirPath) + File.separator + localePrefix;
			}
			while (en.hasMoreElements() && path == null) {
				String nextDir = en.nextElement();
				if (nextDir.equals("")) {
					pathPrefix = this.bundlePath;
				}
				else {
					pathPrefix = String.valueOf(this.bundlePath) + File.separator + nextDir;
				}
				for (int i = 0; i < pathFragments.length && path == null; i++) {
					String possiblePath;
					if (pathFragments[i].equals("")) {
						possiblePath = String.valueOf(pathPrefix) + File.separator + fileName;
					}
					else {
						possiblePath = String.valueOf(pathPrefix) + File.separator + pathFragments[i] + File.separator + fileName;
					}
					try {
						File possibleResource = new File(possiblePath);
						if (possibleResource.exists())
							path = possibleResource.getCanonicalPath();
					}
					catch (Exception exception) {
						throw NSForwardException._runtimeExceptionForThrowable(exception);
					}
				}
			}
			if (path == null && anExtension == null) {
				NSBundle.SpecificResourceFilter srf = new NSBundle.SpecificResourceFilter(aName);
				en = this.resourceBuckets.objectEnumerator();
				while (en.hasMoreElements() && path == null) {
					String nextDir = en.nextElement();
					if (nextDir.equals("")) {
						pathPrefix = this.bundlePath;
					}
					else {
						pathPrefix = String.valueOf(this.bundlePath) + File.separator + nextDir;
					}
					for (int i = 0; i < pathFragments.length && path == null; i++) {
						String possiblePath;
						if (pathFragments[i].equals("")) {
							possiblePath = pathPrefix;
						}
						else {
							possiblePath = String.valueOf(pathPrefix) + pathFragments[i];
						}
						File possibleResourceDir = new File(possiblePath);
						if (possibleResourceDir.isDirectory()) {
							String[] fileNames = possibleResourceDir.list((FilenameFilter) srf);
							if (fileNames.length > 0)
								try {
									path = String.valueOf(possibleResourceDir.getCanonicalPath()) + File.separator + fileNames[0];
								}
								catch (IOException e) {
									throw NSForwardException._runtimeExceptionForThrowable(e);
								}
						}
					}
				}
			}
		}
		return path;
	}

	@Deprecated
	public NSArray pathsForResources(String anExtension, String aSubDirPath) {
		if (this.isJar) {
			if (safeInvokeDeprecatedJarBundleAPI)
				return NSArray.EmptyArray;
			throw new IllegalStateException("pathsForResources cannot be invoked on a jar-based NSBundle");
		}
		Enumeration<String> en = this.resourceBuckets.objectEnumerator();
		NSMutableArray<String> fileArray = new NSMutableArray();
		String localePrefix = NSBundle._DefaultLocalePrefix();
		String[] pathFragments = new String[2];
		NSBundle.OldResourceFilter rf = null;
		if (anExtension != null && !anExtension.equals(""))
			rf = OldResourceFilterForExtension(anExtension);
		if (aSubDirPath == null) {
			pathFragments[0] = "";
			pathFragments[1] = localePrefix;
		}
		else {
			pathFragments[0] = aSubDirPath;
			pathFragments[1] = String.valueOf(aSubDirPath) + File.separator + localePrefix;
		}
		while (en.hasMoreElements()) {
			String pathPrefix, nextDir = en.nextElement();
			if (nextDir.equals("")) {
				pathPrefix = this.bundlePath;
			}
			else {
				pathPrefix = String.valueOf(this.bundlePath) + File.separator + nextDir;
			}
			for (int i = 0; i < pathFragments.length; i++) {
				String possiblePath;
				if (pathFragments[i].equals("")) {
					possiblePath = pathPrefix;
				}
				else {
					possiblePath = String.valueOf(pathPrefix) + File.separator + pathFragments[i];
				}
				File possibleResourceDir = new File(possiblePath);
				if (possibleResourceDir.isDirectory()) {
					String[] resourceNames;
					if (rf == null) {
						resourceNames = possibleResourceDir.list();
					}
					else {
						resourceNames = possibleResourceDir.list((FilenameFilter) rf);
					}
					if (resourceNames.length > 0) {
						String basePath;
						try {
							basePath = possibleResourceDir.getCanonicalPath();
						}
						catch (IOException e) {
							throw NSForwardException._runtimeExceptionForThrowable(e);
						}
						for (int j = 0; j < resourceNames.length; j++)
							fileArray.addObject(String.valueOf(basePath) + File.separator + resourceNames[j]);
					}
				}
			}
		}
		return (NSArray) fileArray;
	}

	public Properties properties() {
		return this.properties;
	}

	@Deprecated
	public String resourcePath() {
		return this.resourcePath;
	}

	public String resourcePathForLocalizedResourceNamed(String aName, String aSubDirPath) {
		return resourcePathForLocalizedResourceNamed(aName, aSubDirPath, (List) this.resourceBuckets);
	}

	public NSArray<String> resourcePathsForDirectories(String extension, String aSubDirPath) {
		NSArray<String> list = null;
		if (this.resourceBuckets.indexOfIdenticalObject(this._resourceLocation) != -1)
			if (this.isJar) {
				String anExtension = fixExtension(extension);
				if (aSubDirPath == null || aSubDirPath.length() == 0) {
					NSMutableArray<String> allPaths = new NSMutableArray(resourcePathsForDirectoriesInDirectoryInJar(this._resourceLocation, anExtension, false));
					NSArray<String> lProjDirs = resourcePathsForDirectoriesInDirectoryInJar(this._resourceLocation, ".lproj", false);
					int count = lProjDirs.count();
					for (int i = 0; i < count; i++) {
						String lProjDir = (String) lProjDirs.objectAtIndex(i);
						allPaths.addObjectsFromArray(resourcePathsForDirectoriesInDirectoryInJar(_NSStringUtilities.concat(this._resourceLocation, "/", lProjDir), anExtension, false));
					}
					NSMutableArray<String> nSMutableArray1 = allPaths;
				}
				else {
					String startPath = _NSStringUtilities.concat(this._resourceLocation, "/", aSubDirPath);
					list = resourcePathsForDirectoriesInDirectoryInJar(startPath, anExtension, false);
				}
			}
			else {
				NSBundle.ResourceDirectoryFilter resourceDirectoryFilter;
				if (extension == null) {
					NSBundle.DirectoryFilter directoryFilter = TheDirectoryFilter;
				}
				else {
					resourceDirectoryFilter = ResourceDirectoryFilterForExtension(extension);
				}
				if (aSubDirPath == null) {
					NSMutableArray<String> allPaths = new NSMutableArray(resourcePathsForDirectoriesInDirectory(this.resourcePath, this.resourcePath, (FilenameFilter) resourceDirectoryFilter, false));
					NSArray lProjDirs = resourcePathsForDirectoriesInDirectory(this.resourcePath, this.resourcePath, (FilenameFilter) ResourceDirectoryFilterForExtension(".lproj"), false);
					int count = lProjDirs.count();
					for (int i = 0; i < count; i++) {
						String lProjDir = (String) lProjDirs.objectAtIndex(i);
						allPaths.addObjectsFromArray(resourcePathsForDirectoriesInDirectory(_NSStringUtilities.concat(this.resourcePath, File.separator, lProjDir), this.resourcePath, (FilenameFilter) resourceDirectoryFilter, false));
					}
					NSMutableArray<String> nSMutableArray1 = allPaths;
				}
				else {
					String absolutePath = NSPathUtilities.stringByNormalizingExistingPath(_NSStringUtilities.concat(this.resourcePath, File.separator, aSubDirPath));
					if (absolutePath.startsWith(this.resourcePath.concat(File.separator)))
						list = resourcePathsForDirectoriesInDirectory(absolutePath, this.resourcePath, (FilenameFilter) resourceDirectoryFilter, false);
				}
			}
		if (list == null || list.count() == 0)
			return NSArray.emptyArray();
		return list;
	}

	public NSArray<String> resourcePathsForLocalizedResources(String extension, String aSubDirPath) {
		NSMutableArray<String> nSMutableArray1, localizedPaths = null;
		NSArray<String> returnPaths = null;
		String anExtension = extension;
		if (this.resourceBuckets.indexOfIdenticalObject(this._resourceLocation) != -1) {
			if (this.isJar) {
				String localePrefix = NSBundle._DefaultLocalePrefix();
				String lpSuffix = "/".concat(localePrefix);
				anExtension = fixExtension(anExtension);
				if (aSubDirPath == null || aSubDirPath.length() == 0) {
					localizedPaths = new NSMutableArray(resourcePathsForResourcesInDirectoryInJar(_NSStringUtilities.concat(this._resourceLocation, lpSuffix), anExtension, false));
				}
				else {
					String startPath = _NSStringUtilities.concat(this._resourceLocation, lpSuffix, "/", aSubDirPath);
					localizedPaths = new NSMutableArray(resourcePathsForResourcesInDirectoryInJar(startPath, anExtension, false));
				}
				if (aSubDirPath == null || aSubDirPath.length() == 0) {
					NSMutableArray<String> dirNames = new NSMutableArray();
					NSMutableArray<String> fileNames = new NSMutableArray();
					_simplePathsInDirectoryInJar(this._resourceLocation, "", dirNames, anExtension, fileNames);
					int dirNamesCount = dirNames.count();
					int fileNamesCount = fileNames.count();
					NSMutableArray<String> nlPaths = new NSMutableArray();
					int i;
					for (i = 0; i < fileNamesCount; i++) {
						boolean identicalPath = false;
						String nextName = _NSStringUtilities.concat(localePrefix, "/", (String) fileNames.objectAtIndex(i));
						if (localizedPaths.indexOfObject(nextName) != -1)
							identicalPath = true;
						if (!identicalPath)
							localizedPaths.addObject(_NSStringUtilities.concat("Nonlocalized.lproj", "/", (String) fileNames.objectAtIndex(i)));
					}
					for (i = 0; i < dirNamesCount; i++) {
						boolean useThisDir = !((String) dirNames.objectAtIndex(i)).toString().endsWith(".lproj");
						if (useThisDir)
							nlPaths.addObjectsFromArray(resourcePathsForResourcesInDirectoryInJar(_NSStringUtilities.concat(this._resourceLocation, "/", (String) dirNames.objectAtIndex(i)), anExtension, true));
					}
					int nlPathsCount = nlPaths.count();
					for (i = 0; i < nlPathsCount; i++) {
						boolean identicalPath = false;
						String nextName = localePrefix.concat(((String) nlPaths.objectAtIndex(i)).substring("Nonlocalized.lproj".length()));
						if (localizedPaths.indexOfObject(nextName) != -1)
							identicalPath = true;
						if (!identicalPath)
							localizedPaths.addObject(nlPaths.objectAtIndex(i));
					}
				}
			}
			else {
				NSBundle.ResourceFilter resourceFilter;
				String localePrefix = NSBundle._DefaultLocalePrefix();
				String lpSuffix = File.separator.concat(localePrefix);
				if (anExtension == null) {
					NSBundle.FilesFilter filesFilter = TheFilesFilter;
				}
				else {
					resourceFilter = ResourceFilterForExtension(anExtension);
				}
				if (aSubDirPath == null) {
					localizedPaths = new NSMutableArray(resourcePathsForResourcesInDirectory(this.resourcePath.concat(lpSuffix), this.resourcePath, (FilenameFilter) resourceFilter, false));
				}
				else {
					String absolutePath = NSPathUtilities.stringByNormalizingExistingPath(_NSStringUtilities.concat(this.resourcePath, lpSuffix, File.separator, aSubDirPath));
					if (absolutePath.startsWith(this.resourcePath.concat(File.separator)))
						localizedPaths = new NSMutableArray(resourcePathsForDirectoriesInDirectory(absolutePath, this.resourcePath, (FilenameFilter) resourceFilter, false));
				}
				File nlSubdir = new File(this.resourcePath);
				if (aSubDirPath == null && nlSubdir.isDirectory()) {
					String[] dirNames = nlSubdir.list((FilenameFilter) TheDirectoryFilter);
					int dirNamesCount = dirNames.length;
					String[] fileNames = nlSubdir.list((FilenameFilter) resourceFilter);
					int fileNamesCount = fileNames.length;
					NSMutableArray<String> nlPaths = new NSMutableArray();
					int i;
					for (i = 0; i < fileNamesCount; i++) {
						boolean identicalPath = false;
						String nextName = _NSStringUtilities.concat(localePrefix, File.separator, fileNames[i]);
						if (localizedPaths.indexOfObject(nextName) != -1)
							identicalPath = true;
						if (!identicalPath)
							localizedPaths.addObject(NONLOCALIZED_LOCALE_PREFIX.concat(fileNames[i]));
					}
					for (i = 0; i < dirNamesCount; i++) {
						boolean useThisDir = !dirNames[i].endsWith(".lproj");
						if (useThisDir)
							nlPaths.addObjectsFromArray(resourcePathsForResourcesInDirectory(_NSStringUtilities.concat(this.resourcePath, File.separator, dirNames[i]), this.resourcePath, (FilenameFilter) resourceFilter, true));
					}
					int nlPathsCount = nlPaths.count();
					for (i = 0; i < nlPathsCount; i++) {
						boolean identicalPath = false;
						String nextName = localePrefix.concat(((String) nlPaths.objectAtIndex(i)).substring("Nonlocalized.lproj".length()));
						if (localizedPaths.indexOfObject(nextName) != -1)
							identicalPath = true;
						if (!identicalPath)
							localizedPaths.addObject(nlPaths.objectAtIndex(i));
					}
				}
			}
			if (localizedPaths == null || localizedPaths.count() == 0) {
				returnPaths = NSArray.emptyArray();
			}
			else {
				nSMutableArray1 = localizedPaths;
			}
		}
		return (NSArray<String>) nSMutableArray1;
	}

	public NSArray<String> resourcePathsForResources(String extension, String aSubDirPath) {
		NSArray<String> list = null;
		String anExtension = extension;
		if (this.resourceBuckets.indexOfIdenticalObject(this._resourceLocation) != -1)
			if (this.isJar) {
				anExtension = fixExtension(anExtension);
				if (aSubDirPath == null || aSubDirPath.length() == 0) {
					list = resourcePathsForResourcesInDirectoryInJar(this._resourceLocation, anExtension, false);
				}
				else {
					String startPath = _NSStringUtilities.concat(this._resourceLocation, "/", aSubDirPath);
					boolean prependNonLocalizedLproj = (aSubDirPath.indexOf(".lproj") == -1);
					list = resourcePathsForResourcesInDirectoryInJar(startPath, anExtension, prependNonLocalizedLproj);
				}
			}
			else {
				NSBundle.ResourceFilter resourceFilter;
				if (anExtension == null) {
					NSBundle.FilesFilter filesFilter = TheFilesFilter;
				}
				else {
					resourceFilter = ResourceFilterForExtension(anExtension);
				}
				if (aSubDirPath == null) {
					list = resourcePathsForResourcesInDirectory(this.resourcePath, this.resourcePath, (FilenameFilter) resourceFilter, false);
				}
				else {
					String absolutePath = NSPathUtilities.stringByNormalizingExistingPath(_NSStringUtilities.concat(this.resourcePath, File.separator, aSubDirPath));
					if (absolutePath.startsWith(this.resourcePath.concat(File.separator))) {
						boolean prependNonLocalizedLproj = (aSubDirPath.indexOf(".lproj") == -1);
						list = resourcePathsForResourcesInDirectory(absolutePath, this.resourcePath, (FilenameFilter) resourceFilter, prependNonLocalizedLproj);
					}
				}
			}
		if (list == null || list.count() == 0)
			return NSArray.emptyArray();
		return list;
	}

	private void addResourceBucket(String aBundleSubDirPath) {
		if (aBundleSubDirPath != null &&
				this.resourceBuckets.indexOfObject(aBundleSubDirPath) == -1)
			if (this.isJar) {
				ZipEntry ze = this.jarFile.getEntry(aBundleSubDirPath.concat("/"));
				if (ze != null && ze.isDirectory())
					this.resourceBuckets.addObject(aBundleSubDirPath);
				if (aBundleSubDirPath.length() == 0)
					this.resourceBuckets.addObject(aBundleSubDirPath);
			}
			else {
				String resourceDirPath = _NSStringUtilities.concat(this.bundlePath, File.separator, aBundleSubDirPath);
				File resourceDir = new File(resourceDirPath);
				if (resourceDir.isDirectory())
					this.resourceBuckets.addObject(aBundleSubDirPath);
			}
	}

	private NSArray<String> classNamesFromDirectory(File aDirectory) {
		String[] classes = aDirectory.list((FilenameFilter) TheJavaClassFilter);
		NSMutableArray<String> theClassNames = new NSMutableArray();
		String[] directories = aDirectory.list((FilenameFilter) TheDirectoryFilter);
		if (classes != null) {
			int l = classes.length;
			for (int i = 0; i < l; i++) {
				String className;
				try {
					className = _NSStringUtilities.concat(aDirectory.getCanonicalPath(), File.separator, classes[i]);
				}
				catch (IOException e) {
					throw NSForwardException._runtimeExceptionForThrowable(e);
				}
				if (this.resourcePath == this.bundlePath) {
					className = className.substring(this.resourcePath.length() + 1, className.lastIndexOf('.'));
				}
				else {
					className = className.substring(this.resourcePath.length() + JSUFFIX.length() + 1, className.lastIndexOf('.'));
				}
				theClassNames.addObject(className.replace(File.separatorChar, '.'));
			}
		}
		if (directories != null) {
			int l = directories.length;
			for (int i = 0; i < l; i++) {
				File f;
				try {
					f = new File(_NSStringUtilities.concat(aDirectory.getCanonicalPath(), File.separator, directories[i]));
				}
				catch (IOException e) {
					throw NSForwardException._runtimeExceptionForThrowable(e);
				}
				theClassNames.addObjectsFromArray(classNamesFromDirectory(f));
			}
		}
		if (theClassNames.count() == 0)
			return NSArray.emptyArray();
		return (NSArray<String>) theClassNames;
	}

	private boolean couldBeAFramework() {
		if (this.infoDictionary != null)
			if (this.infoDictionary.objectForKey("Has_WOComponents") != null) {
				this.isFramework = true;
			}
			else {
				String value = (String) this.infoDictionary.objectForKey("CFBundlePackageType");
				if (value != null && value.equalsIgnoreCase("FMWK"))
					this.isFramework = true;
			}
		return this.isFramework;
	}

	private void initIsJar(boolean newIsJar) {
		this.isJar = newIsJar;
	}

	private void initBundlePath(String newBundlePath) {
		this.bundlePath = newBundlePath;
	}

	private void initBundleURLPrefix() {
		if (this.isJar) {
			this._bundleURLPrefix = _NSStringUtilities.concat("jar:", NSPathUtilities._fileURLPrefix, this.bundlePath, "!/");
		}
		else {
			this._bundleURLPrefix = _NSStringUtilities.concat(NSPathUtilities._fileURLPrefix, this.bundlePath, "/");
		}
	}

	private void initJarFileLayout() {
		if (!this.isJar)
			return;
		NSMutableDictionary root = new NSMutableDictionary();
		Enumeration<JarEntry> e = this.jarFile.entries();
		while (e.hasMoreElements()) {
			ZipEntry ze = e.nextElement();
			String zePath = ze.getName();
			NSArray zeArray = NSArray.componentsSeparatedByString(zePath, "/");
			NSMutableDictionary currentDict = root;
			Enumeration<String> e2 = zeArray.objectEnumerator();
			while (e2.hasMoreElements()) {
				String element = e2.nextElement();
				if (element.length() > 0) {
					NSMutableDictionary aDict = (NSMutableDictionary) currentDict.objectForKey(element);
					if (aDict == null) {
						if (e2.hasMoreElements()) {
							NSMutableDictionary newDict = new NSMutableDictionary();
							currentDict.setObjectForKey(newDict, element);
							currentDict = newDict;
							continue;
						}
						currentDict.setObjectForKey(TheFileDict, element);
						continue;
					}
					if (aDict != TheFileDict)
						currentDict = aDict;
				}
			}
		}
		this.jarFileLayout = (NSDictionary) root;
	}

	private void initBundleType() {
		if (this.isJar) {
			this.bundleType = 1;
			try {
				URL url = new URL(this._bundleURLPrefix.concat(ResourcesInfoPlist));
				JarURLConnection jarConnection = (JarURLConnection) url.openConnection();
				this.jarFile = jarConnection.getJarFile();
				this.jarFileEntries = new NSMutableArray();
				for (Enumeration<JarEntry> e = this.jarFile.entries(); e.hasMoreElements();)
					this.jarFileEntries.addObject(e.nextElement());
			}
			catch (Exception e) {
				throw NSForwardException._runtimeExceptionForThrowable(e);
			}
		}
		else {
			File contentsDir = new File(this.bundlePath.concat(CSUFFIX));
			if (contentsDir.exists()) {
				this.bundleType = 2;
			}
			else {
				this.bundleType = 1;
			}
			this.jarFile = null;
		}
	}

	private void initClassNames() {
		NSMutableSet<String> classes = new NSMutableSet();
		this.classNames = (NSArray<String>) new NSMutableArray();
		if (this.isJar) {
			if (this.jarFile != null) {
				Enumeration<ZipEntry> e = this.jarFileEntries.objectEnumerator();
				while (e.hasMoreElements()) {
					ZipEntry entry = e.nextElement();
					String path = entry.getName();
					if (path.endsWith(".class") && !path.startsWith("WebServerResources") && !path.startsWith("Resources")) {
						String nextClassName = path.substring(0, path.lastIndexOf('.'));
						nextClassName = nextClassName.replace('/', '.');
						nextClassName = nextClassName.intern();
						classes.addObject(nextClassName);
					}
				}
			}
		}
		else {
			for (Enumeration<String> en = NSBundle._classPath().objectEnumerator(); en.hasMoreElements();) {
				String nextPath = en.nextElement();
				if (nextPath.startsWith(this.resourcePath)) {
					File f = new File(nextPath);
					if (f.isDirectory()) {
						try {
							if (this.bundlePath.equals(NSBundle._userDirPath()) || f.getCanonicalPath().endsWith(RJSUFFIX))
								classes.addObjectsFromArray(classNamesFromDirectory(f));
						}
						catch (IOException iOException) {
						}
						continue;
					}
					if (TheJavaArchiveFilter.accept(null, nextPath))
						classes.addObjectsFromArray(_NSUtilities.classNamesFromArchive(f));
				}
			}
		}
		if (classes.count() == 0) {
			this.classesHaveBeenLoaded = false;
			this.classNames = NSArray.emptyArray();
		}
		else {
			this.classesHaveBeenLoaded = true;
			setClassNames(classes.allObjects());
		}
	}

	private void initInfoDictionary() {
		this.infoDictionary = NSDictionary.emptyDictionary();
		String infoPlistPath = this._bundleURLPrefix.concat((this.bundleType == 2) ? "Contents/Info.plist" : ResourcesInfoPlist);
		try {
			URL infoDictURL = new URL(infoPlistPath);
			this.infoDictionary = (NSDictionary<String, Object>) NSPropertyListSerialization.propertyListWithPathURL(infoDictURL);
		}
		catch (Exception e) {
			NSLog.err.appendln("Failed to load " + infoPlistPath + ". Treating as empty. " + e);
		}
	}

	private void initName() {
		String newName = null;
		if (this.infoDictionary != null)
			newName = (String) this.infoDictionary.objectForKey("NSExecutable");
		if (newName == null) {
			newName = NSPathUtilities.lastPathComponent(this.bundlePath);
			if (newName.length() > 3)
				newName = NSPathUtilities.stringByDeletingPathExtension(newName);
		}
		this.name = newName;
	}

	private void initPackages() {
		NSMutableArray<String> thePackages = new NSMutableArray(this.packages);
		this.packages = (NSArray<String>) new NSMutableArray();
		for (Enumeration<String> en = this.classNames.objectEnumerator(); en.hasMoreElements();) {
			String nextClass = en.nextElement();
			String newPackage = _NSStringUtilities.stringByDeletingLastComponent(nextClass, '.');
			if (!thePackages.containsObject(newPackage))
				thePackages.addObject(newPackage);
		}
		this.packages = thePackages.immutableClone();
	}

	private void initProperties() {
		String propertiesPath = this._bundleURLPrefix.concat((this.bundleType == 2) ? ("Contents/" + ResourcesProperties) : ResourcesProperties);
		try {
			URL propertiesURL = new URL(propertiesPath);
			this.properties = (Properties) new NSProperties.NestedProperties(null);
			InputStream propertiesStream = propertiesURL.openStream();
			try {
				this.properties.load(propertiesStream);
			}
			finally {
				propertiesStream.close();
			}
		}
		catch (FileNotFoundException fnfe) {
			if (shouldValidateProperties())
				try {
					if (NSBundle._bundleUrlExists(new URL(propertiesPath)))
						throw new RuntimeException("Failed to load '" + propertiesPath + "'.", fnfe);
				}
				catch (MalformedURLException e) {
					throw new RuntimeException("Failed to load '" + propertiesPath + "'.", e);
				}
		}
		catch (Exception e) {
			if (shouldValidateProperties())
				throw new RuntimeException("Failed to load '" + propertiesPath + "'.", e);
			NSLog.err.appendln("Error reading properties file " + propertiesPath + ". Exception was " + e);
			NSLog.err.appendln("Ignoring this file.");
			this.properties = null;
		}
	}

	private void initResourceBuckets() {
		this.resourceBuckets = new NSMutableArray();
		if (this.isJar) {
			this._resourceLocation = "Resources";
			if (this.jarFile.getEntry(this._resourceLocation) != null)
				addResourceBucket(this._resourceLocation);
		}
		else {
			String resourceLocation;
			switch (this.bundleType) {
			case 1:
				resourceLocation = "Resources";
				break;
			case 2:
				resourceLocation = _NSStringUtilities.concat("Contents", File.separator, "Resources");
				break;
			default:
				throw new IllegalStateException("Inconsistent Bundle type");
			}
			File rlFile = new File(_NSStringUtilities.concat(this.bundlePath, File.separator, resourceLocation));
			if (rlFile.exists()) {
				this._resourceLocation = NSPathUtilities.stringByNormalizingExistingPath(_NSStringUtilities.concat(this.bundlePath, File.separator, resourceLocation)).substring(this.bundlePath.length() + 1);
				addResourceBucket(this._resourceLocation);
			}
			else if (this.bundleType == 2) {
				throw new IllegalStateException("Bundle at path \"" + this.bundlePath + "\" is a CFBundle, but is missing its \"Contents" + File.separator + "Resources\" subdirectory.");
			}
		}
		addResourceBucket("");
	}

	private void initContentsPath() {
		if (this.isJar) {
			this.contentsPath = this._bundleURLPrefix;
		}
		else {
			File contentsDir;
			switch (this.bundleType) {
			case 1:
				this.contentsPath = this.bundlePath;
				return;
			case 2:
				contentsDir = new File(this.bundlePath.concat(CSUFFIX));
				if (contentsDir.exists()) {
					try {
						this.contentsPath = contentsDir.getCanonicalPath();
					}
					catch (IOException e) {
						throw NSForwardException._runtimeExceptionForThrowable(e);
					}
				}
				else {
					this.contentsPath = this.bundlePath;
				}
				return;
			}
			throw new IllegalStateException("Inconsistent Bundle type");
		}
	}

	private void initResourcePath() {
		if (this.isJar) {
			this.resourcePath = this.contentsPath.concat("Resources");
		}
		else {
			File resourceDir;
			switch (this.bundleType) {
			case 1:
				resourceDir = new File(this.bundlePath.concat(RSUFFIX));
				if (resourceDir.exists()) {
					try {
						this.resourcePath = resourceDir.getCanonicalPath();
					}
					catch (IOException e) {
						throw NSForwardException._runtimeExceptionForThrowable(e);
					}
				}
				else {
					this.resourcePath = this.bundlePath;
				}
				return;
			case 2:
				resourceDir = new File(this.bundlePath.concat(CRSUFFIX));
				if (resourceDir.exists()) {
					try {
						this.resourcePath = resourceDir.getCanonicalPath();
					}
					catch (IOException e) {
						throw NSForwardException._runtimeExceptionForThrowable(e);
					}
				}
				else {
					this.resourcePath = this.bundlePath;
				}
				return;
			}
			throw new IllegalStateException("Inconsistent Bundle type");
		}
	}

	private void postNotification() {
		NSNotificationCenter.defaultCenter().postNotification("NSBundleDidLoadNotification", this, new NSDictionary(this.classNames, "NSLoadedClassesNotification"));
	}

	protected boolean _prefixPathWithNonLocalizedPrefixJar(String aPath) {
		return aPath.equals(this._resourceLocation);
	}

	private String fixExtension(String anExtension) {
		return (anExtension == null) ? "" : (anExtension.startsWith(".") ? anExtension.substring(1) : anExtension);
	}

	public void _simplePathsInDirectoryInJar(String startPath, String dirExtension, NSMutableArray<String> dirs, String fileExtension, NSMutableArray<String> files) {
		if (startPath.length() == 0 || startPath.equals(".") || startPath.equals("/")) {
			Enumeration<String> e = this.jarFileLayout.keyEnumerator();
			while (e.hasMoreElements()) {
				String key = e.nextElement();
				NSDictionary value = (NSDictionary) this.jarFileLayout.objectForKey(key);
				if (value == TheFileDict) {
					if (key.endsWith(fileExtension) && files != null)
						files.addObject(key);
					continue;
				}
				if (key.endsWith(dirExtension) && dirs != null)
					dirs.addObject(key);
			}
		}
		else {
			String aPath = !startPath.endsWith("/") ? startPath.concat("/") : startPath;
			if (this.jarFile.getEntry(aPath) != null) {
				NSArray keyArray = NSArray.componentsSeparatedByString(aPath.substring(0, aPath.length() - 1), "/");
				NSDictionary filesDict = (NSDictionary) _NSUtilities.valueForKeyArray((NSKeyValueCoding) this.jarFileLayout, keyArray);
				if (filesDict != null) {
					Enumeration<String> e = filesDict.keyEnumerator();
					while (e.hasMoreElements()) {
						String key = e.nextElement();
						NSDictionary value = (NSDictionary) filesDict.objectForKey(key);
						if (value == TheFileDict) {
							if (key.endsWith(fileExtension) && files != null)
								files.addObject(key);
							continue;
						}
						if (key.endsWith(dirExtension) && dirs != null)
							dirs.addObject(key);
					}
				}
			}
		}
	}

	private NSArray<String> resourcePathsForDirectoriesInDirectoryInJar(String startPath, String anExtension, boolean prependNonlocalizedLProj) {
		NSMutableArray<String> returnList = new NSMutableArray();
		NSMutableArray<String> dirNames = new NSMutableArray();
		_simplePathsInDirectoryInJar(startPath, anExtension, dirNames, "", (NSMutableArray<String>) null);
		String prefix = _prefixPathWithNonLocalizedPrefixJar(startPath) ? "" : startPath.substring(this._resourceLocation.concat("/").length());
		int i;
		for (i = 0; i < dirNames.count(); i++) {
			String dirName = (String) dirNames.objectAtIndex(i);
			if (prefix.length() == 0) {
				if (dirName.endsWith(".lproj")) {
					returnList.addObject(dirName);
				}
				else {
					returnList.addObject(_NSStringUtilities.concat("Nonlocalized.lproj", "/", dirName));
				}
			}
			else if (prependNonlocalizedLProj) {
				returnList.addObject(_NSStringUtilities.concat("Nonlocalized.lproj", "/", prefix, "/", dirName));
			}
			else {
				returnList.addObject(_NSStringUtilities.concat(prefix, "/", dirName));
			}
		}
		for (i = 0; i < dirNames.count(); i++) {
			String dirName = (String) dirNames.objectAtIndex(i);
			if (prefix.length() == 0) {
				boolean endWithLPROJ = dirName.endsWith(".lproj");
				returnList.addObjectsFromArray(resourcePathsForDirectoriesInDirectoryInJar(_NSStringUtilities.concat(startPath, "/", dirName), anExtension, !endWithLPROJ));
			}
			else {
				returnList.addObjectsFromArray(resourcePathsForDirectoriesInDirectoryInJar(_NSStringUtilities.concat(startPath, "/", dirName), anExtension, prependNonlocalizedLProj));
			}
		}
		if (returnList.count() == 0)
			return NSArray.emptyArray();
		return (NSArray<String>) returnList;
	}

	private NSArray<String> resourcePathsForResourcesInDirectoryInJar(String aPath, String anExtension, boolean prependNonlocalizedLProj) {
		NSMutableArray<String> returnList = new NSMutableArray();
		NSMutableArray<String> fileNames = new NSMutableArray();
		NSMutableArray<String> dirNames = new NSMutableArray();
		_simplePathsInDirectoryInJar(aPath, "", dirNames, anExtension, fileNames);
		String prefix = _prefixPathWithNonLocalizedPrefixJar(aPath) ? "" : aPath.substring(this._resourceLocation.concat("/").length());
		int i;
		for (i = 0; i < fileNames.count(); i++) {
			String fileName = (String) fileNames.objectAtIndex(i);
			if (prefix.length() == 0) {
				returnList.addObject(_NSStringUtilities.concat("Nonlocalized.lproj", "/", fileName));
			}
			else if (prependNonlocalizedLProj) {
				returnList.addObject(_NSStringUtilities.concat("Nonlocalized.lproj", "/", prefix, "/", fileName));
			}
			else {
				returnList.addObject(_NSStringUtilities.concat(prefix, "/", fileName));
			}
		}
		for (i = 0; i < dirNames.count(); i++) {
			String dirName = (String) dirNames.objectAtIndex(i);
			boolean prepend = (prefix.length() == 0) ? (!dirName.endsWith(".lproj")) : prependNonlocalizedLProj;
			returnList.addObjectsFromArray(resourcePathsForResourcesInDirectoryInJar(_NSStringUtilities.concat(aPath, "/", dirName), anExtension, prepend));
		}
		if (returnList.count() == 0)
			return NSArray.emptyArray();
		return (NSArray<String>) returnList;
	}

	private void setClassNames(NSArray<String> classes) {
		if (classes != null) {
			NSMutableArray<String> theClasses = new NSMutableArray(this.classNames);
			for (Enumeration<String> en = classes.objectEnumerator(); en.hasMoreElements();) {
				String nextClassName = en.nextElement();
				if (!theClasses.containsObject(nextClassName)) {
					theClasses.addObject(nextClassName);
					NSBundle._registerClassNameForBundle(nextClassName, this);
				}
			}
			this.classNames = theClasses.immutableClone();
		}
	}

	protected static boolean shouldValidateProperties() {
		return Boolean.valueOf(System.getProperty("NSValidateProperties", "true")).booleanValue();
	}
}