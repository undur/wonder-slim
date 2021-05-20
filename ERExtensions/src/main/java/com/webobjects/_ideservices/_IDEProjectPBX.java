package com.webobjects._ideservices;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSSet;
import com.webobjects.foundation._NSStringUtilities;

public class _IDEProjectPBX implements _IDEProject {
	private static final String _extensionsFilename = "ExtensionsForResources.plist";

	private static final NSMutableArray<String> _fileTypeArray;

	private final String _cookie;

	private final NSMutableArray<String> _targets = new NSMutableArray();

	private final NSMutableDictionary<String, String> _targetsByNameDict = new NSMutableDictionary();

	private static final String SERVER_TARGET_NAME = "Application Server";

	private static final String CLIENT_TARGET_NAME = "Web Server";

	private static final String EJB_SERVER_TARGET_NAME = "EJB Deployment";

	private static final String EJB_CLIENT_TARGET_NAME = "EJB Client Interfaces";

	private static final String COMPONENTS_GROUP_NAME = "Web Components";

	private static final String RESOURCES_GROUP_NAME = "Resources";

	private static final String WEB_SERVER_RESOURCES_GROUP_NAME = "Web Server Resources";

	private static final String CLASSES_GROUP_NAME = "Classes";

	static {
		NSMutableArray<String> mainBundleArray = null;
		NSMutableArray<String> wofArray = null;
		NSBundle wofBundle = NSBundle.bundleForName("JavaWebObjects");
		if (wofBundle == null) {
			NSLog.err.appendln("NSBundle is unable to find JavaWebObjects.framework -- unable to load \"ExtensionsForResources.plist\" from there.  Ignoring.");
		}
		else {
			String wofFileRelativePath = wofBundle.resourcePathForLocalizedResourceNamed("ExtensionsForResources.plist", "");
			if (wofFileRelativePath == null) {
				NSLog.err.appendln("NSBundle is unable to find \"ExtensionsForResources.plist\" in JavaWebObjects.framework.  Ignoring.");
			}
			else {
				try {
					InputStream is = wofBundle.inputStreamForResourcePath(wofFileRelativePath);
					String wofFileRelativeString = _NSStringUtilities.stringFromInputStream(is);
					Object o = NSPropertyListSerialization.propertyListFromString(wofFileRelativeString);
					if (o instanceof NSArray) {
						wofArray = new NSMutableArray((NSArray) o);
						int waCount = wofArray.count();
						if (waCount == 0) {
							NSLog.err.appendln("The array parsed from \"ExtensionsForResources.plist\" in JavaWebObjects.framework is empty.  Ignoring.");
							wofArray = null;
						}
						else {
							for (int i = 0; i < waCount; i++) {
								Object element = wofArray.objectAtIndex(i);
								if (!(element instanceof String)) {
									NSLog.err.appendln("Found non-string element in the array parsed from \"ExtensionsForResources.plist\" in JavaWebObjects.framework is empty.  Ignoring  \"" +
											element + "\".");
									wofArray.removeObjectAtIndex(i);
									waCount--;
								}
							}
							if (wofArray.count() == 0) {
								NSLog.err.appendln("The array parsed from \"ExtensionsForResources.plist\" in JavaWebObjects.framework had no valid elements.  Ignoring.");
								wofArray = null;
							}
						}
					}
					else {
						NSLog.err.appendln("NSBundle is unable to parse an array from \"ExtensionsForResources.plist\" in JavaWebObjects.framework.  Ignoring.");
					}
				}
				catch (Throwable t) {
					NSLog.err.appendln("Unable to parse \"ExtensionsForResources.plist\" in JavaWebObjects.framework.  Ignoring.");
				}
			}
		}
		NSBundle mainBundle = NSBundle.mainBundle();
		if (mainBundle == null) {
			NSLog.err.appendln("NSBundle is unable to find the main bundle -- unable to load \"ExtensionsForResources.plist\" from there.");
		}
		else {
			String mainBundleFileRelativePath = mainBundle.resourcePathForLocalizedResourceNamed("ExtensionsForResources.plist", "");
			if (mainBundleFileRelativePath == null) {
				if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 36L))
					NSLog.debug.appendln("NSBundle is unable to find \"ExtensionsForResources.plist\" in the main bundle.  Ignoring optional configuration file.");
			}
			else {
				try {
					InputStream is = mainBundle.inputStreamForResourcePath(mainBundleFileRelativePath);
					String mainBundleFileRelativeString = _NSStringUtilities.stringFromInputStream(is);
					Object o = NSPropertyListSerialization.propertyListFromString(mainBundleFileRelativeString);
					if (o instanceof NSArray) {
						mainBundleArray = new NSMutableArray((NSArray) o);
						int mbaCount = mainBundleArray.count();
						if (mbaCount == 0) {
							if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 36L))
								NSLog.debug.appendln("The array parsed from \"ExtensionsForResources.plist\" in the main bundle is empty.  Ignoring optional configuration file.");
							mainBundleArray = null;
						}
						else {
							for (int i = 0; i < mbaCount; i++) {
								Object element = mainBundleArray.objectAtIndex(i);
								if (!(element instanceof String)) {
									if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 36L))
										NSLog.debug.appendln("Found non-string element in the array parsed from optional configuration file \"ExtensionsForResources.plist\" in the main bundle.  Ignoring  \"" +
												element + "\".");
									mainBundleArray.removeObjectAtIndex(i);
									mbaCount--;
								}
							}
							if (mainBundleArray.count() == 0) {
								if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 36L))
									NSLog.debug.appendln("The array parsed from \"ExtensionsForResources.plist\" in the main bundle had no valid elements.  Ignoring optional configuration file.");
								mainBundleArray = null;
							}
						}
					}
					else if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 36L)) {
						NSLog.debug.appendln("NSBundle is unable to parse an array from \"ExtensionsForResources.plist\" in the main bundle.  Ignoring optional configuration file.");
					}
				}
				catch (Throwable t) {
					if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 36L))
						NSLog.debug.appendln("Unable to parse \"ExtensionsForResources.plist\" in the main bundle.  Ignoring optional configuration file.");
				}
			}
		}
		if (wofArray == null && mainBundleArray == null)
			throw new IllegalStateException("Unable to find any resource extensions in either JavaWebObjects.framework or the main bundle.");
		if (wofArray != null) {
			if (mainBundleArray != null)
				wofArray.addObjectsFromArray((NSArray) mainBundleArray);
			_fileTypeArray = wofArray;
		}
		else if (mainBundleArray != null) {
			_fileTypeArray = mainBundleArray;
		}
		else {
			_fileTypeArray = null;
		}
	}

	public static _IDEProjectPBX pbxProjectAtPath(String path) {
		_IDEProjectPBX project = null;
		String aPath = path;
		if (aPath != null)
			try {
				NSArray openProjects = _PBXProjectWatcher.openProjectsAppropriateForFile(aPath);
				if (openProjects.count() == 0) {
					if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
						NSLog.debug.appendln("_IDEProjectPBX.pbxProjectAtPath() -- failed to find an open, development-mode project at: " + aPath +
								" ... Trying to find a pbdevelopment.plist at this path instead.");
					String pbdevpath = aPath;
					NSDictionary pbdevdict = null;
					boolean isJar = aPath.endsWith(".jar");
					if (!isJar) {
						pbdevpath = NSPathUtilities.stringByAppendingPathComponent(aPath, "Contents");
						pbdevpath = NSPathUtilities.stringByAppendingPathComponent(pbdevpath, "pbdevelopment.plist");
						if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
							NSLog.debug.appendln("_IDEProjectPBX.pbxProjectAtPath() -- Trying: " + pbdevpath);
						try {
							pbdevdict = (NSDictionary) NSPropertyListSerialization.propertyListWithPathURL((new File(pbdevpath)).toURL());
						}
						catch (Exception exception) {
						}
					}
					if (pbdevdict == null)
						if (isJar) {
							try {
								URL url = new URL("jar:" + NSPathUtilities._fileURLPrefix + aPath + "!/Resources/pbdevelopment.plist");
								NSData aData = new NSData(_NSStringUtilities.bytesFromInputStream(url.openStream()));
								pbdevdict = (NSDictionary) NSPropertyListSerialization.propertyListFromData(aData, null);
							}
							catch (Exception exception) {
							}
						}
						else {
							pbdevpath = NSPathUtilities.stringByAppendingPathComponent(aPath, "Resources");
							pbdevpath = NSPathUtilities.stringByAppendingPathComponent(pbdevpath, "pbdevelopment.plist");
							if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
								NSLog.debug.appendln("_IDEProjectPBX.pbxProjectAtPath() -- Trying instead: " + pbdevpath);
							try {
								pbdevdict = (NSDictionary) NSPropertyListSerialization.propertyListWithPathURL((new File(pbdevpath)).toURL());
							}
							catch (Exception exception) {
							}
						}
					if (pbdevdict == null) {
						if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
							NSLog.debug.appendln("_IDEProjectPBX.pbxProjectAtPath() -- Unable to find/parse a valid NSDictionary at either location.");
						return null;
					}
					if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
						NSLog.debug.appendln("_IDEProjectPBX.pbxProjectAtPath() -- Found and parsed a valid NSDictionary at: " + pbdevpath);
					aPath = (String) pbdevdict.valueForKey("PBXProjectSourcePath");
					if (aPath == null) {
						if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
							NSLog.debug.appendln("_IDEProjectPBX.pbxProjectAtPath() -- Unfortunately, there's no value for the key 'PBXProjectSourcePath'.");
						return null;
					}
					if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
						NSLog.debug.appendln("_IDEProjectPBX.pbxProjectAtPath() -- value for the key 'PBXProjectSourcePath': " + aPath);
					if (!aPath.endsWith(".pbproj") && !aPath.endsWith(".xcode")) {
						aPath = NSPathUtilities.stringByAppendingPathComponent(aPath, NSPathUtilities.lastPathComponent(aPath));
						aPath = NSPathUtilities.stringByAppendingPathExtension(aPath, "pbproj");
					}
					openProjects = _PBXProjectWatcher.openProjectsAppropriateForFile(aPath);
				}
				if (openProjects != null && openProjects.count() > 0) {
					if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
						NSLog.debug.appendln("_IDEProjectPBX.pbxProjectAtPath() -- openProjects == " + openProjects);
					project = new _IDEProjectPBX((String) openProjects.objectAtIndex(0), aPath);
				}
				else if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L)) {
					NSLog.debug.appendln("_IDEProjectPBX.pbxProjectAtPath() -- failed to find an open, development-mode project at: " + aPath);
				}
			}
			catch (Exception e) {
				if (NSLog.debugLoggingAllowedForLevel(1)) {
					NSLog.err.appendln("_IDEProjectPBX: exception in pbxProjectAtPath:" + e);
					NSLog._conditionallyLogPrivateException(e);
				}
			}
		return project;
	}

	public _IDEProjectPBX(String cookie, String path) {
		this._cookie = cookie;
		createTargetList();
	}

	public void createTargetList() {
		this._targets.addObjectsFromArray(_PBXProjectWatcher.targetsInProject(this._cookie));
		for (Iterator<String> iterator = this._targets.iterator(); iterator.hasNext();) {
			String target = iterator.next();
			String name = _PBXProjectWatcher.nameOfTargetInProject(target, this._cookie);
			this._targetsByNameDict.takeValueForKey(target, name);
		}
	}

	public String projectDir() {
		return NSPathUtilities.stringByNormalizingExistingPath(NSPathUtilities.stringByDeletingLastPathComponent(this._cookie));
	}

	public String projectDirNotNormalized() {
		return NSPathUtilities.stringByDeletingLastPathComponent(this._cookie);
	}

	public String projectName() {
		return _PBXProjectWatcher.nameOfProject(this._cookie);
	}

	public String languageDir() {
		return "languageDir unimplemented";
	}

	public String projectTypeName() {
		return "projectTypeName unimplemented";
	}

	public String languageName() {
		return "languageName unimplemented";
	}

	public void addComponent(String componentDirectoryString, String javaFileString) {
		String apiFilePath = NSPathUtilities.stringByAppendingPathExtension(NSPathUtilities.stringByDeletingPathExtension(componentDirectoryString), "api");
		String groupName = NSPathUtilities.lastPathComponent(NSPathUtilities.stringByDeletingPathExtension(componentDirectoryString));
		NSMutableArray targets = new NSMutableArray();
		String serverTarget = (String) this._targetsByNameDict.objectForKey("Application Server");
		String nearFile = NSPathUtilities.stringByDeletingLastPathComponent(componentDirectoryString);
		if (serverTarget != null)
			targets.addObject(serverTarget);
		_PBXProjectWatcher.addGroup("Web Components", null, this._cookie, nearFile);
		_PBXProjectWatcher.addGroupToPreferredInsertionGroup(groupName, null, this._cookie, nearFile, "Web Components");
		if (componentDirectoryString != null && NSPathUtilities.fileExistsAtPathURL(NSPathUtilities._URLWithPath(componentDirectoryString))) {
			NSArray<String> paths = new NSArray(componentDirectoryString);
			_PBXProjectWatcher.addFilesToProjectNearFilePreferredInsertionGroupNameAddToTargetsCopyIntoGroupFolderCreateGroupsRecursively(paths, this._cookie, nearFile, groupName, (NSArray) targets, true, false);
		}
		if (javaFileString != null && NSPathUtilities.fileExistsAtPathURL(NSPathUtilities._URLWithPath(javaFileString))) {
			NSArray<String> paths = new NSArray(javaFileString);
			_PBXProjectWatcher.addFilesToProjectNearFilePreferredInsertionGroupNameAddToTargetsCopyIntoGroupFolderCreateGroupsRecursively(paths, this._cookie, nearFile, groupName, (NSArray) targets, true, false);
		}
		if (apiFilePath != null && NSPathUtilities.fileExistsAtPathURL(NSPathUtilities._URLWithPath(apiFilePath))) {
			NSArray<String> paths = new NSArray(apiFilePath);
			_PBXProjectWatcher.addFilesToProjectNearFilePreferredInsertionGroupNameAddToTargetsCopyIntoGroupFolderCreateGroupsRecursively(paths, this._cookie, nearFile, groupName, (NSArray) targets, false, false);
		}
	}

	public String pathToBucket(String aKey) {
		if (aKey.equals("EJB_META_INFO"))
			return "META-INF";
		return "";
	}

	public void addFileKey(String aFile, String aKey) {
		String target = null;
		String group = "Resources";
		NSArray<String> paths = new NSArray(aFile);
		String nearFile = NSPathUtilities.stringByDeletingLastPathComponent(aFile);
		if (aKey.equals("WO_COMPONENTS")) {
			target = (String) this._targetsByNameDict.objectForKey("Application Server");
			group = "Web Components";
		}
		else if (aKey.equals("WEBSERVER_RESOURCES")) {
			target = (String) this._targetsByNameDict.objectForKey("Web Server");
			group = "Web Server Resources";
		}
		else if (aKey.equals("WEBSERVER_RESOURCES")) {
			target = (String) this._targetsByNameDict.objectForKey("Application Server");
			group = "Web Components";
		}
		else if (aKey.equals("RESOURCES")) {
			target = (String) this._targetsByNameDict.objectForKey("Application Server");
			group = "Resources";
		}
		else if (aKey.equals("OTHER_RESOURCES")) {
			target = (String) this._targetsByNameDict.objectForKey("Application Server");
			group = "Resources";
		}
		else if (aKey.equals("CLASSES")) {
			target = (String) this._targetsByNameDict.objectForKey("Application Server");
			group = "Classes";
		}
		else if (aKey.equals("H_FILES")) {
			target = (String) this._targetsByNameDict.objectForKey("Application Server");
			group = "Web Components";
		}
		else if (aKey.equals("OTHER_LINKED")) {
			target = (String) this._targetsByNameDict.objectForKey("Application Server");
			group = "Classes";
		}
		else if (aKey.equals("EJB_SERVER_CLASSES")) {
			target = (String) this._targetsByNameDict.objectForKey("EJB Deployment");
			group = "Classes";
		}
		else if (aKey.equals("EJB_CLIENT_CLASSES")) {
			target = (String) this._targetsByNameDict.objectForKey("EJB Client Interfaces");
			group = "Classes";
		}
		else if (aKey.equals("EJB_META_INFO")) {
			target = (String) this._targetsByNameDict.objectForKey("EJB Deployment");
			group = "Resources";
		}
		else if (aKey.equals("EJB_COMMON_CLASSES")) {
			group = "Classes";
			NSMutableArray nSMutableArray = new NSMutableArray();
			target = (String) this._targetsByNameDict.objectForKey("EJB Deployment");
			nSMutableArray.addObject(target);
			target = (String) this._targetsByNameDict.objectForKey("EJB Client Interfaces");
			nSMutableArray.addObject(target);
			_PBXProjectWatcher.addFilesToProjectNearFilePreferredInsertionGroupNameAddToTargetsCopyIntoGroupFolderCreateGroupsRecursively(paths, this._cookie, nearFile, group, (NSArray) nSMutableArray, true, false);
			return;
		}
		if (target == null)
			target = (String) this._targetsByNameDict.objectForKey("Application Server");
		NSArray<String> targetCookies = new NSArray(target);
		_PBXProjectWatcher.addFilesToProjectNearFilePreferredInsertionGroupNameAddToTargetsCopyIntoGroupFolderCreateGroupsRecursively(paths, this._cookie, nearFile, group, targetCookies, true, false);
	}

	public void openFile(String filename, int line, String errorMessage) {
		_PBXProjectWatcher.openFile(filename, line, errorMessage);
	}

	public String pathForFrameworkNamed(String fwName) {
		return "pathForFrameworkNamed unimplemented";
	}

	public void setPathForFramework(String path, String fwName) {
	}

	public boolean isPureJavaProject() {
		return true;
	}

	public String ideApplicationName() {
		return "Xcode";
	}

	public String ideProjectPath() {
		String projectPath = projectDir();
		projectPath = String.valueOf(projectPath) + File.separator + projectName() + ".xcodeproj";
		projectPath = String.valueOf(projectPath) + File.separator + "project.pbxproj";
		return NSPathUtilities.stringByNormalizingExistingPath(projectPath);
	}

	public NSArray<String> _filesOfTypes(NSArray<String> typesArray) {
		NSMutableArray<String> pathArray = new NSMutableArray();
		if (this._targets != null)
			for (Iterator<String> iterator = this._targets.iterator(); iterator.hasNext();) {
				NSArray<String> targetResources = _PBXProjectWatcher.filesOfTypesInTargetOfProject(typesArray, iterator.next(), this._cookie);
				pathArray.addObjectsFromArray(targetResources);
			}
		return (new NSSet((NSArray) pathArray)).allObjects();
	}

	private static final NSArray<String> _languageTypeArray = new NSArray((Object[]) new String[] { "wos", "java", "h", "m" });

	public void extractFilesIntoWOProject(_WOProject woProject) {
		NSArray<String> pathArray = _filesOfTypes(_languageTypeArray);
		if (pathArray != null)
			for (Iterator<String> iterator = pathArray.iterator(); iterator.hasNext();)
				woProject.addInterfaceFilePath(iterator.next());
		if (woProject.includeFrameworks())
			extractFrameworksIntoWOProject(woProject);
		extractResourcesIntoWOProject(woProject);
		extractModelsIntoWOProject(woProject);
	}

	private static final NSArray<String> _modelArray = new NSArray((Object[]) new String[] { "eomodel", "eomodeld" });

	public void extractModelsIntoWOProject(_WOProject woProject) {
		NSArray<String> pathArray = _filesOfTypes(_modelArray);
		if (pathArray.count() > 0)
			for (Iterator<String> iterator = pathArray.iterator(); iterator.hasNext();)
				woProject.addModelFilePath(iterator.next());
	}

	private static final NSArray<String> _frameworkArray = new NSArray((Object[]) new String[] { "framework" });

	public void extractFrameworksIntoWOProject(_WOProject woProject) {
		NSArray<String> pathArray = _filesOfTypes(_frameworkArray);
		if (pathArray.count() > 0)
			for (Iterator<String> iterator = pathArray.iterator(); iterator.hasNext();)
				woProject.extractFrameworkAtPath(iterator.next());
	}

	@Deprecated
	public NSArray frameworkBundlePaths() {
		NSArray<String> frameworkArray = _filesOfTypes(_frameworkArray);
		NSMutableArray frameworkPathsArray = new NSMutableArray();
		int count = frameworkArray.count();
		if (count > 0)
			for (int i = 0; i < count; i++) {
				String framework = NSPathUtilities.lastPathComponent((String) frameworkArray.objectAtIndex(i));
				NSBundle frameworkBundle = NSBundle.bundleForName(framework);
				if (frameworkBundle == null) {
					if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
						NSLog.debug.appendln("_IDEProjectPBX.frameworkBundlePaths() -- unable to find an NSBundle named: " + framework);
				}
				else {
					frameworkPathsArray.addObject(frameworkBundle.bundlePath());
				}
			}
		return (NSArray) frameworkPathsArray;
	}

	public NSArray frameworkBundlePathURLs() {
		NSArray<String> frameworkArray = _filesOfTypes(_frameworkArray);
		NSMutableArray frameworkPathsArray = new NSMutableArray();
		int count = frameworkArray.count();
		if (count > 0)
			for (int i = 0; i < count; i++) {
				String framework = NSPathUtilities.lastPathComponent((String) frameworkArray.objectAtIndex(i));
				NSBundle frameworkBundle = NSBundle.bundleForName(framework);
				if (frameworkBundle == null) {
					if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
						NSLog.debug.appendln("_IDEProjectPBX.frameworkBundlePathURLs() -- unable to find an NSBundle named: " + framework);
				}
				else {
					frameworkPathsArray.addObject(frameworkBundle.bundlePathURL());
				}
			}
		return (NSArray) frameworkPathsArray;
	}

	public void addFilenameExtensionToListOfKnowns(String anExtension) {
		if (anExtension != null && anExtension.length() > 0) {
			String extension = anExtension.startsWith(".") ? anExtension.substring(1) : anExtension;
			NSMutableArray<String> fileTypesArray = _fileTypeArray;
			if (fileTypesArray.indexOfObject(extension) == -1)
				fileTypesArray.addObject(extension);
		}
	}

	public void extractResourcesIntoWOProject(_WOProject woProject) {
		NSArray<String> pathArray = _filesOfTypes((NSArray<String>) _fileTypeArray);
		if (pathArray.count() > 0) {
			int count = pathArray.count();
			for (int i = 0; i < count; i++) {
				String resourcePath = (String) pathArray.objectAtIndex(i);
				String name = NSPathUtilities.lastPathComponent(resourcePath);
				String language = _WOProject.languageFromResourcePath(resourcePath);
				woProject.addResource(name, resourcePath, language);
			}
		}
	}

	public void _projectFileObserverNotification(NSNotification notification) {
		NSNotificationCenter.defaultCenter().postNotification("TheUnnamedNotification", this, notification.userInfo());
	}

	public void _openProjectObserverNotification(NSNotification notification) {
		NSNotificationCenter.defaultCenter().postNotification("TheUnnamedNotification", this, notification.userInfo());
	}

	public void _targetObserverNotification(NSNotification notification) {
		NSNotificationCenter.defaultCenter().postNotification("TheUnnamedNotification", this, notification.userInfo());
	}

	public void _targetFileObserverNotification(NSNotification notification) {
		NSNotificationCenter.defaultCenter().postNotification("TheUnnamedNotification", this, notification.userInfo());
	}

	public void refreshUnderlyingProjectCache() {
	}

	@Deprecated
	public String bundlePath() {
		return null;
	}

	public URL bundlePathURL() {
		return null;
	}

	public boolean shouldPreloadResources() {
		return true;
	}
}