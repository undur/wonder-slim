package com.webobjects.foundation.development;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation._NSStringUtilities;
import com.webobjects.foundation._NSUtilities;

public abstract class NSProjectBundle extends NSBundle {
	protected static final String NONLOCALIZED_LOCALE = "Nonlocalized.lproj";

	protected static final String NONLOCALIZED_LOCALE_PREFIX = "Nonlocalized.lproj" + File.separator;

	private static ConcurrentHashMap<String, NSResourceType> _resourceTypeExtensions = new ConcurrentHashMap<String, NSResourceType>();

	private String _projectPath;

	private String _bundleURLPrefix;

	private LinkedHashSet<String> _classpathPaths;

	private Properties _properties;

	private Class _principalClass;

	private NSArray<String> _packageNames;

	private NSArray<String> _classNames;

	static {
		_resourceTypeExtensions.put("wo", NSResourceType.Component);
		_resourceTypeExtensions.put("eomodeld", NSResourceType.Model);
		_resourceTypeExtensions.put("d2wmodel", NSResourceType.D2WModel);
		_resourceTypeExtensions.put("gif", NSResourceType.WebServer);
		_resourceTypeExtensions.put("png", NSResourceType.WebServer);
		_resourceTypeExtensions.put("jpg", NSResourceType.WebServer);
		_resourceTypeExtensions.put("jpeg", NSResourceType.WebServer);
		_resourceTypeExtensions.put("tiff", NSResourceType.WebServer);
		_resourceTypeExtensions.put("js", NSResourceType.WebServer);
		_resourceTypeExtensions.put("css", NSResourceType.WebServer);
	}

	public static void registerResourceTypeForExtension(NSResourceType resourceType, String extension) {
		_resourceTypeExtensions.put(extension, resourceType);
	}

	public NSProjectBundle(String projectPath) {
		this._projectPath = projectPath;
		this._bundleURLPrefix = _NSStringUtilities.concat(NSPathUtilities._fileURLPrefix, this._projectPath, "/");
		this._classpathPaths = new LinkedHashSet<String>();
	}

	public void _bundleLoadedFromPath(String path) {
		this._classpathPaths.add(path);
	}

	public void _bundlesDidLoad() {
		ensureClassAndPackageNamesLoaded();
	}

	public String projectPath() {
		return this._projectPath;
	}

	public String bundlePath() {
		return this._projectPath;
	}

	public URL bundlePathURL() {
		try {
			return (new File(this._projectPath)).toURI().toURL();
		}
		catch (MalformedURLException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}

	public String _bundleURLPrefix() {
		return this._bundleURLPrefix;
	}

	protected void fillInClassNames(Set<String> packageNames, Set<String> classNames, File folder, NSMutableArray<String> packageNameArray) {
		File[] files = folder.listFiles();
		if (files != null) {
			String packageName = null;
			byte b;
			int i;
			File[] arrayOfFile;
			for (i = (arrayOfFile = files).length, b = 0; b < i;) {
				File file = arrayOfFile[b];
				String name = file.getName();
				if (file.isDirectory()) {
					if (!name.equals("CVS") && !name.equals(".svn")) {
						packageNameArray.addObject(name);
						try {
							fillInClassNames(packageNames, classNames, file, packageNameArray);
						}
						finally {
							packageNameArray.removeLastObject();
						}
					}
				}
				else if (name.endsWith(".class")) {
					if (packageName == null) {
						packageName = packageNameArray.componentsJoinedByString(".");
						packageNames.add(packageName);
					}
					String className = String.valueOf(packageName) + "." + name.substring(0, name.length() - ".class".length());
					classNames.add(className);
					NSBundle._registerClassNameForBundle(className, this);
				}
				b++;
			}
		}
	}

	protected void fillInClassNamesFromJar(Set<String> packageNames, Set<String> classNames, File jarFile) {
		NSArray<String> jarClassNames = _NSUtilities.classNamesFromArchive(jarFile);
		for (String className : jarClassNames) {
			int lastDotIndex = className.lastIndexOf('.');
			if (lastDotIndex != -1) {
				String packageName = className.substring(0, lastDotIndex);
				packageNames.add(packageName);
			}
			classNames.add(className);
			NSBundle._registerClassNameForBundle(className, this);
		}
	}

	protected void fillInClassAndPackageNames() {
		Set<String> packageNames = new HashSet<String>();
		Set<String> classNames = new HashSet<String>();
		for (String classpathPath : this._classpathPaths) {
			File classpathFile = new File(classpathPath);
			if (classpathFile.isDirectory()) {
				NSMutableArray<String> packageName = new NSMutableArray();
				fillInClassNames(packageNames, classNames, classpathFile, packageName);
				continue;
			}
			fillInClassNamesFromJar(packageNames, classNames, classpathFile);
		}
		synchronized (this) {
			this._packageNames = new NSArray(packageNames);
			this._classNames = new NSArray(classNames);
		}
	}

	protected void ensureClassAndPackageNamesLoaded() {
		if (this._packageNames == null || this._classNames == null)
			fillInClassAndPackageNames();
	}

	public synchronized NSArray<String> bundleClassPackageNames() {
		ensureClassAndPackageNamesLoaded();
		return this._packageNames;
	}

	public synchronized NSArray<String> bundleClassNames() {
		ensureClassAndPackageNamesLoaded();
		return this._classNames;
	}

	public URL _pathURLForResourcePath(String aResourcePath, boolean returnDirectories) {
		URL url = null;
		if (aResourcePath != null && aResourcePath.length() > 0) {
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
			if (!realPath.startsWith(File.separator))
				realPath = File.separator.concat(realPath);
			for (String resourcePath : relativePathForResourceType(resourceTypeForResourceNamed(aResourcePath))) {
				try {
					File f = new File(String.valueOf(this._projectPath) + File.separator + resourcePath + realPath);
					if (f.exists() && (f.isFile() || returnDirectories)) {
						url = NSPathUtilities._URLWithPath(f.getCanonicalPath());
						break;
					}
				}
				catch (Exception exception) {
					throw NSForwardException._runtimeExceptionForThrowable(exception);
				}
			}
		}
		return url;
	}

	public boolean isFramework() {
		return "FMWK".equals(infoDictionary().objectForKey("CFBundlePackageType"));
	}

	public boolean _isCFBundle() {
		return false;
	}

	public boolean isJar() {
		return false;
	}

	public JarFile _jarFile() {
		return null;
	}

	public NSDictionary _jarFileLayout() {
		return null;
	}

	public boolean load() {
		return false;
	}

	public String name() {
		return (String) infoDictionary().objectForKey("NSExecutable");
	}

	public String pathForResource(String aName, String anExtension, String aSubDirPath) {
		throw new RuntimeException("NSProjectBundle.pathForResource: " + aName + ":" + anExtension + ":" + aSubDirPath);
	}

	public NSArray pathsForResources(String anExtension, String aSubDirPath) {
		throw new RuntimeException("NSProjectBundle.pathsForResources: " + anExtension + ":" + aSubDirPath);
	}

	public Class principalClass() {
		return this._principalClass;
	}

	public Properties properties() {
		if (this._properties == null) {
			NSProperties.NestedProperties properties = new NSProperties.NestedProperties(null);
			String propertiesPath = resourcePathForLocalizedResourceNamed("Properties", (String) null);
			try {
				InputStream propertiesStream = inputStreamForResourcePath(propertiesPath);
				if (propertiesStream != null)
					try {
						properties.load(propertiesStream);
					}
					finally {
						propertiesStream.close();
					}
			}
			catch (FileNotFoundException fnfe) {
				if (NSLegacyBundle.shouldValidateProperties())
					try {
						if (NSBundle._bundleUrlExists(new URL(propertiesPath)))
							throw new RuntimeException("Failed to load '" + propertiesPath + "'.", fnfe);
					}
					catch (MalformedURLException e) {
						throw new RuntimeException("Failed to load '" + propertiesPath + "'.", e);
					}
			}
			catch (Exception e) {
				if (NSLegacyBundle.shouldValidateProperties())
					throw new RuntimeException("Failed to load '" + propertiesPath + "'.", e);
				NSLog.err.appendln("Error reading properties file " + propertiesPath + ". Exception was " + e);
				NSLog.err.appendln("Ignoring this file.");
			}
			this._properties = (Properties) properties;
		}
		return this._properties;
	}

	public String resourcePath() {
		return bundlePath();
	}

	public String resourcePathForLocalizedResourceNamed(String aName, String aSubDirPath) {
		return resourcePathForLocalizedResourceNamed(aName, aSubDirPath, relativePathForResourceType(resourceTypeForResourceNamed(aName)));
	}

	public NSArray<String> resourcePathsForDirectories(String extension, String aSubDirPath) {
		NSBundle.ResourceDirectoryFilter resourceDirectoryFilter;
		NSArray<String> list = null;
		if (extension == null) {
			NSBundle.DirectoryFilter directoryFilter = TheDirectoryFilter;
		}
		else {
			resourceDirectoryFilter = ResourceDirectoryFilterForExtension(extension);
		}
		NSMutableArray<String> masterList = new NSMutableArray();
		for (String relativeResourcePath : relativePathForResourceType(resourceTypeForResourceWithExtension(extension))) {
			String resourcePath = _NSStringUtilities.concat(this._projectPath, File.separator, relativeResourcePath);
			if (aSubDirPath == null) {
				NSMutableArray<String> allPaths = new NSMutableArray(resourcePathsForDirectoriesInDirectory(resourcePath, resourcePath, (FilenameFilter) resourceDirectoryFilter, false));
				NSArray lProjDirs = resourcePathsForDirectoriesInDirectory(resourcePath, resourcePath, (FilenameFilter) ResourceDirectoryFilterForExtension(".lproj"), false);
				int count = lProjDirs.count();
				for (int i = 0; i < count; i++) {
					String lProjDir = (String) lProjDirs.objectAtIndex(i);
					allPaths.addObjectsFromArray(resourcePathsForDirectoriesInDirectory(_NSStringUtilities.concat(resourcePath, File.separator, lProjDir), resourcePath, (FilenameFilter) resourceDirectoryFilter, false));
				}
				NSMutableArray<String> nSMutableArray1 = allPaths;
			}
			else {
				String absolutePath = NSPathUtilities.stringByNormalizingExistingPath(_NSStringUtilities.concat(resourcePath, File.separator, aSubDirPath));
				if (absolutePath.startsWith(resourcePath.concat(File.separator)))
					list = resourcePathsForDirectoriesInDirectory(absolutePath, resourcePath, (FilenameFilter) resourceDirectoryFilter, false);
			}
			if (list != null)
				masterList.addObjectsFromArray(list);
		}
		if (masterList == null || masterList.count() == 0)
			return NSArray.emptyArray();
		return (NSArray<String>) masterList;
	}

	public NSArray<String> resourcePathsForLocalizedResources(String extension, String aSubDirPath) {
		throw new RuntimeException("NSProjectBundle.resourcePathsForLocalizedResources: " + extension + ":" + aSubDirPath);
	}

	public NSArray<String> resourcePathsForResources(String extension, String aSubDirPath) {
		NSBundle.ResourceFilter resourceFilter;
		String anExtension = extension;
		if (anExtension == null) {
			NSBundle.FilesFilter filesFilter = TheFilesFilter;
		}
		else {
			resourceFilter = ResourceFilterForExtension(anExtension);
		}
		NSMutableArray<String> masterList = new NSMutableArray();
		for (String relativeResourcePath : relativePathForResourceType(resourceTypeForResourceWithExtension(extension))) {
			String resourcePath = _NSStringUtilities.concat(this._projectPath, File.separator, relativeResourcePath);
			NSArray<String> list = null;
			if (aSubDirPath == null) {
				list = resourcePathsForResourcesInDirectory(resourcePath, resourcePath, (FilenameFilter) resourceFilter, false);
			}
			else {
				String absolutePath = NSPathUtilities.stringByNormalizingExistingPath(_NSStringUtilities.concat(resourcePath, File.separator, aSubDirPath));
				if (absolutePath.startsWith(resourcePath.concat(File.separator))) {
					boolean prependNonLocalizedLproj = (aSubDirPath.indexOf(".lproj") == -1);
					list = resourcePathsForResourcesInDirectory(absolutePath, resourcePath, (FilenameFilter) resourceFilter, prependNonLocalizedLproj);
				}
			}
			if (list != null)
				masterList.addObjectsFromArray(list);
		}
		if (masterList == null || masterList.count() == 0)
			return NSArray.emptyArray();
		return (NSArray<String>) masterList;
	}

	public void _simplePathsInDirectoryInJar(String startPath, String dirExtension, NSMutableArray<String> dirs, String fileExtension, NSMutableArray<String> files) {
		throw new RuntimeException("NSProjectBundle._simplePathsInDirectoryInJar: " + startPath + ", " + dirExtension + "," + dirs + "," + fileExtension + "," + files);
	}

	public NSResourceType resourceTypeForResourceWithExtension(String extension) {
		NSResourceType resourceType = null;
		if (extension != null)
			resourceType = _resourceTypeExtensions.get(extension);
		if (resourceType == null)
			resourceType = NSResourceType.Other;
		return resourceType;
	}

	public NSResourceType resourceTypeForResourceNamed(String resourceName) {
		return resourceTypeForResourceWithExtension(NSPathUtilities.pathExtension(resourceName));
	}

	public abstract List<String> relativePathForResourceType(NSResourceType paramNSResourceType);
}