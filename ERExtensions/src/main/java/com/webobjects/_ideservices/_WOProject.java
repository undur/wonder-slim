package com.webobjects._ideservices;

import java.io.File;

import com.webobjects.appserver._private.WODeployedBundle;
import com.webobjects.appserver._private.WOProjectBundle;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation._NSStringUtilities;
import com.webobjects.foundation._NSUtilities;
import com.webobjects.foundation.development.NSProjectBundle;

public class _WOProject {
	public static final String WBComponentExtension = "wo";

	public static final String WBArchiveExtension = "woo";

	public static final String WBAPIExtension = "api";

	public static final String WBDeclarationExtension = "wod";

	public static final String WBWebScriptExtension = "wos";

	public static final String WBJavaExtension = "java";

	public static final String WBObjCExtension = "h";

	public static final String WBObjCImplementationExtension = "m";

	public static final String WBHTMLExtension = "html";

	public static final String WBHTMExtension = "htm";

	public static final String WBEOModelExtension = "eomodeld";

	public static final String WBApplicationScriptName = "Application";

	public static final String WBSessionScriptName = "Session";

	public static final String EOCheckProjectsNotification = "EOCheckProjectsNotification";

	public static final String EOObjCPreindexExtension = "minx";

	public static final String EOJavaPreindexExtension = "jinx";

	public static final String WBLanguageProjectExtension = "lproj";

	static NSMutableDictionary _openProjects = new NSMutableDictionary();

	_IDEProject _ideProject;

	NSMutableArray _interfaceFilePaths;

	NSMutableArray _modelFilePaths;

	NSMutableArray _frameworkProjects;

	NSMutableArray _frameworkNames;

	NSMutableDictionary _frameworkResources;

	NSMutableArray _projectResources;

	NSMutableDictionary _resourceNameToPath;

	NSMutableArray _frameworkPaths;

	NSArray _allComponentNames;

	boolean _includeFrameworks;

	public static _IDEProject ideProjectAtPath(String path) {
		_IDEProject ideProject = null;
		for (NSBundle bundle : NSBundle._allBundlesReally()) {
			if (bundle instanceof NSProjectBundle && (new File(bundle.bundlePath())).equals(new File(path))) {
				ideProject = new _NSProjectBundleIDEProject((NSProjectBundle) bundle);
				break;
			}
		}
		if (ideProject == null) {
			ideProject = _IDEProjectPBX.pbxProjectAtPath(path);
			if (ideProject != null && NSLog.debugLoggingAllowedForLevelAndGroups(2, 32L))
				NSLog.debug.appendln("*****Found PBX project at " + path);
		}
		if (ideProject == null) {
			ideProject = _IDEProjectPB.pbProjectAtPath(path);
			if (ideProject != null && NSLog.debugLoggingAllowedForLevelAndGroups(2, 32L))
				NSLog.debug.appendln("*****Found PBWO project at " + path);
		}
		if (ideProject == null) {
			ideProject = _IDEProjectWOLips.wolipsProjectAtPath(path);
			if (ideProject != null && NSLog.debugLoggingAllowedForLevelAndGroups(2, 32L))
				NSLog.debug.appendln("*****Found WOLips project at " + path);
		}
		if (ideProject == null) {
			ideProject = _WOAntProject.antProjectAtPath(path);
			if (ideProject != null && NSLog.debugLoggingAllowedForLevelAndGroups(2, 32L))
				NSLog.debug.appendln("*****Found WO Ant project at " + ideProject.projectDir());
		}
		return ideProject;
	}

	public static _WOProject loadedProjectAtPath(String path) {
		_IDEProject ideProject = ideProjectAtPath(path);
		_WOProject woProject = null;
		if (ideProject != null)
			woProject = (_WOProject) _openProjects.objectForKey(ideProject.projectDir());
		return woProject;
	}

	public static _WOProject projectAtPath(String path) {
		return projectAtPath(path, false);
	}

	public static _WOProject projectAtPath(String path, boolean includeFrameworks) {
		_WOProject woProject = null;
		_IDEProject ideProject = ideProjectAtPath(path);
		if (ideProject != null) {
			woProject = (_WOProject) _openProjects.objectForKey(ideProject.projectDir());
			if (woProject == null) {
				woProject = new _WOProject(ideProject, includeFrameworks);
				_openProjects.setObjectForKey(woProject, ideProject.projectDir());
			}
		}
		return woProject;
	}

	_WOProject(_IDEProject ideProject, boolean includeFrameworks) {
		this._ideProject = ideProject;
		this._includeFrameworks = includeFrameworks;
		updateFileList();
	}

	private void updateFileList() {
		this._interfaceFilePaths = new NSMutableArray();
		this._modelFilePaths = new NSMutableArray();
		this._resourceNameToPath = new NSMutableDictionary();
		this._frameworkProjects = new NSMutableArray();
		this._frameworkNames = new NSMutableArray();
		this._frameworkResources = new NSMutableDictionary();
		this._frameworkPaths = new NSMutableArray();
		this._projectResources = new NSMutableArray();
		this._ideProject.extractFilesIntoWOProject(this);
	}

	public void addInterfaceFilePath(String path) {
		this._interfaceFilePaths.addObject(path);
	}

	public NSArray interfaceFilePaths() {
		return (NSArray) this._interfaceFilePaths;
	}

	public void addModelFilePath(String path) {
		this._modelFilePaths.addObject(path);
	}

	public NSArray modelFilePaths() {
		return (NSArray) this._modelFilePaths;
	}

	public boolean includeFrameworks() {
		return this._includeFrameworks;
	}

	public static String frameworkPathForResource(String path) {
		if (path.length() < 1)
			return null;
		if (NSPathUtilities.pathExtension(path).equals("framework"))
			return NSPathUtilities.stringByNormalizingExistingPath(path);
		String containingDirectory = _NSStringUtilities.stringByDeletingLastComponent(path, File.separatorChar);
		return frameworkPathForResource(containingDirectory);
	}

	String getPath() {
		return this._ideProject.projectDir();
	}

	public _IDEProject ideProject() {
		return this._ideProject;
	}

	public void addResourcePath(String resourcePath) {
		addResource(NSPathUtilities.lastPathComponent(resourcePath), resourcePath, languageFromResourcePath(resourcePath));
	}

	NSArray frameworkPaths() {
		return (NSArray) this._frameworkPaths;
	}

	public void addResource(String aResourceName, String aResourcePath, String aResourceLanguage) {
		String resourceName = NSPathUtilities._standardizedPath(aResourceName);
		String resourcePath = NSPathUtilities._standardizedPath(aResourcePath);
		String resourceLanguage = aResourceLanguage;
		NSMutableDictionary languageToPath = (NSMutableDictionary) this._resourceNameToPath.objectForKey(resourceName);
		String resourcePathExtension = NSPathUtilities.pathExtension(resourceName);
		NSArray frameworkPaths = frameworkPaths();
		if (NSLog.debugLoggingAllowedForLevelAndGroups(3, 32L))
			NSLog.debug.appendln("addResource " + resourceName + " : " + resourcePath + " : " + resourceLanguage);
		resourcePath = NSPathUtilities.stringByNormalizingExistingPath(resourcePath);
		if (resourceLanguage == null)
			resourceLanguage = "";
		if (languageToPath != null) {
			languageToPath.takeValueForKey(resourcePath, resourceLanguage);
		}
		else {
			languageToPath = new NSMutableDictionary();
			languageToPath.takeValueForKey(resourcePath, resourceLanguage);
			this._resourceNameToPath.takeValueForKey(languageToPath, resourceName);
		}
		if (frameworkPaths != null) {
			String frameworkPath = null;
			int i, c;
			for (i = 0, c = frameworkPaths.count(); i < c; i++) {
				frameworkPath = (String) frameworkPaths.objectAtIndex(i);
				if (resourcePath.startsWith(frameworkPath))
					break;
			}
			if (i < c && frameworkPath != null) {
				NSMutableArray resourceArray = (NSMutableArray) this._frameworkResources.objectForKey(frameworkPath);
				if (!resourceArray.containsObject(resourceName))
					resourceArray.addObject(resourceName);
			}
			else if (!this._projectResources.containsObject(resourceName)) {
				this._projectResources.addObject(resourceName);
			}
		}
		if (resourcePathExtension.equals("wo")) {
			this._allComponentNames = null;
		}
		else {
			resourcePathExtension.equals("eomodeld");
		}
	}

	public String pathForResourceNamed(String resourceName, boolean refreshOnCacheMiss) {
		return pathForResourceNamed(resourceName, "", refreshOnCacheMiss);
	}

	public String pathForResourceNamed(String aResourceName, String aResourceLanguage, boolean refreshOnCacheMiss) {
		String resourcePath = null;
		NSMutableDictionary languageToPath = null;
		String resourceName = NSPathUtilities._standardizedPath(aResourceName);
		String resourceLanguage = aResourceLanguage;
		synchronized (this) {
			if (resourceLanguage == null)
				resourceLanguage = this._ideProject.languageName();
			languageToPath = (NSMutableDictionary) this._resourceNameToPath.objectForKey(resourceName);
			if (languageToPath != null)
				if (languageToPath.count() == 1) {
					resourcePath = (String) languageToPath.allValues().objectAtIndex(0);
				}
				else if (languageToPath.count() > 1) {
					if (resourceLanguage != null)
						resourcePath = (String) languageToPath.objectForKey(resourceLanguage);
					if (resourcePath == null)
						resourcePath = (String) languageToPath.objectForKey("");
					if (resourcePath == null && refreshOnCacheMiss) {
						this._ideProject.refreshUnderlyingProjectCache();
						this._ideProject.addFilenameExtensionToListOfKnowns(NSPathUtilities.pathExtension(resourceName));
						updateFileList();
						resourcePath = pathForResourceNamed(resourceName, resourceLanguage, false);
					}
				}
			if (resourcePath == null && refreshOnCacheMiss) {
				this._ideProject.refreshUnderlyingProjectCache();
				this._ideProject.addFilenameExtensionToListOfKnowns(NSPathUtilities.pathExtension(resourceName));
				updateFileList();
				resourcePath = pathForResourceNamed(resourceName, resourceLanguage, false);
			}
		}
		return resourcePath;
	}

	public String pathForComponentNamed(String componentName) {
		return pathForResourceNamed(NSPathUtilities.stringByAppendingPathExtension(componentName, "wo"), true);
	}

	public void extractFrameworkAtPath(String filePath) {
	}

	public void extractFrameworkNamed(String frameworkName) {
		if (!this._frameworkNames.containsObject(frameworkName)) {
			String frameworkPath = this._ideProject.pathForFrameworkNamed(frameworkName);
			this._frameworkNames.addObject(frameworkName);
			if (frameworkPath != null && frameworkPath.length() > 0) {
				_IDEProject fwProj = ideProjectAtPath(frameworkPath);
				this._frameworkResources.takeValueForKey(new NSMutableArray(), frameworkPath);
				if (fwProj != null) {
					this._frameworkProjects.addObject(fwProj);
					fwProj.extractFilesIntoWOProject(this);
				}
				else {
					extractFilesFromFramework(frameworkPath);
				}
			}
		}
	}

	private void extractFilesFromFramework(String frameworkPath) {
		NSMutableArray searchableBundles = new NSMutableArray();
		NSBundle bundle = NSBundle.mainBundle();
		if (bundle != null)
			searchableBundles.addObject(bundle);
		bundle = NSBundle.bundleForClass(getClass());
		if (bundle != null)
			searchableBundles.addObject(bundle);
		bundle = NSBundle.bundleWithPath(frameworkPath);
		if (bundle != null)
			searchableBundles.addObject(bundle);
		for (int i = 0, count = searchableBundles.count(); i < count; i++)
			;
	}

	public void extractResourcesFromPath(String parentDir, String subdir) {
		String fullPath = String.valueOf(parentDir) + File.separator + subdir;
		File directory = new File(fullPath);
		if (directory.isDirectory()) {
			String[] subfiles = directory.list();
			for (int i = 0, count = subfiles.length; i < count; i++) {
				String resource = subfiles[i];
				String resourcePath = String.valueOf(parentDir) + File.separator + subdir;
				String resourcePathExtension = NSPathUtilities.pathExtension(resource);
				File theFile = new File(resourcePath);
				boolean isDirectory = theFile.isDirectory();
				if (theFile.exists() && !NSPathUtilities.pathIsEqualToString(resource, "Documentation") && !NSPathUtilities.pathIsEqualToString(resource, "Java"))
					if (isDirectory && resourcePathExtension.length() == 0) {
						extractResourcesFromPath(parentDir, String.valueOf(subdir) + File.separator + resource);
					}
					else if (isDirectory && resourcePathExtension.equals("lproj") && subdir.length() == 0) {
						extractResourcesFromPath(parentDir, String.valueOf(resource) + File.separator);
					}
					else {
						addResource(String.valueOf(subdir) + File.separator + resource, String.valueOf(resourcePath) + File.separator + resource, languageFromResourcePath(resourcePath));
					}
			}
		}
	}

	public static String resourcePathByAppendingLanguageFileName(String mySelf, String language, String file) {
		String self = mySelf;
		if (language.length() > 0) {
			self = NSPathUtilities.stringByAppendingPathComponent(self, language);
			self = NSPathUtilities.stringByAppendingPathExtension(self, "lproj");
		}
		self = NSPathUtilities.stringByAppendingPathComponent(self, file);
		return self;
	}

	public static String languageFromResourcePath(String self) {
		int minLen = "lproj".length() + 1;
		String mySelf = self;
		while (mySelf.length() > minLen && !NSPathUtilities.pathExtension(mySelf).equals("lproj"))
			mySelf = NSPathUtilities.stringByDeletingLastPathComponent(mySelf);
		if (mySelf.length() > minLen) {
			mySelf = NSPathUtilities.stringByDeletingPathExtension(mySelf);
			mySelf = NSPathUtilities.lastPathComponent(mySelf);
		}
		else {
			mySelf = "";
		}
		return mySelf;
	}

	public String _pathToSourceFileForClass(String fullClassName, String filename) {
		Class theClass = _NSUtilities._classWithFullySpecifiedName(fullClassName);
		NSBundle b = NSBundle.bundleForClass(theClass);
		if (b != null) {
			WODeployedBundle db = WODeployedBundle.bundleWithNSBundle(b);
			if (db instanceof WOProjectBundle) {
				NSArray paths = ((WOProjectBundle) db)._woProject().interfaceFilePaths();
				for (int i = 0, c = paths.count(); i < c; i++) {
					String path = (String) paths.objectAtIndex(i);
					if (NSPathUtilities.lastPathComponent(path).equals(filename))
						return path;
				}
			}
		}
		if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 36L))
			NSLog.debug.appendln("_WOProject: could not find path to source file for " + fullClassName + " " + filename);
		return null;
	}

	public String toString() {
		return "<" + getClass().getName() + ": projectName='" + this._ideProject.projectName() + "'>";
	}

	public String bundlePath() {
		return (this._ideProject.bundlePathURL() != null) ? this._ideProject.bundlePathURL().getPath() : null;
	}
}