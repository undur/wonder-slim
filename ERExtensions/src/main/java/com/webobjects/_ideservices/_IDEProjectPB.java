package com.webobjects._ideservices;

import java.io.File;
import java.net.URL;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;

public class _IDEProjectPB implements _IDEProject {
	private static final String LANGUAGE_RESOURCE_SEPARATOR = "_";

	private volatile _PBProject _pbProject;

	private volatile String _pathToPBProjectFile;

	private String _languageFromKey(String fileKey) {
		String language = "";
		String suffix = null;
		if (fileKey.endsWith("WEBSERVER_RESOURCES")) {
			suffix = "WEBSERVER_RESOURCES";
		}
		else if (fileKey.endsWith("WOAPP_RESOURCES")) {
			suffix = "WOAPP_RESOURCES";
		}
		else if (fileKey.endsWith("OTHER_RESOURCES")) {
			suffix = "OTHER_RESOURCES";
		}
		else if (fileKey.endsWith("WO_COMPONENTS")) {
			suffix = "WO_COMPONENTS";
		}
		if (suffix != null)
			language = fileKey.substring(0, fileKey.indexOf(suffix));
		int i = language.indexOf("_");
		if (i > 0)
			language = language.substring(0, i);
		return language;
	}

	public static _IDEProjectPB pbProjectAtPath(String path) {
		_PBProject rootProject = _PBProject.pbProjectAtPath(path);
		if (rootProject != null)
			return new _IDEProjectPB(rootProject);
		return null;
	}

	public _IDEProjectPB(_PBProject rootProject) {
		this._pbProject = rootProject;
	}

	public String projectDir() {
		return NSPathUtilities.stringByNormalizingExistingPath(this._pbProject.projectDir());
	}

	public String projectDirNotNormalized() {
		return this._pbProject.projectDir();
	}

	public String languageDir() {
		return this._pbProject.languageDir();
	}

	public String projectName() {
		return this._pbProject.projectName();
	}

	public String projectTypeName() {
		return this._pbProject.projectTypeName();
	}

	public String languageName() {
		return this._pbProject.languageName();
	}

	public NSMutableArray fileListForKey(String aKey, boolean createIt) {
		return this._pbProject.fileListForKey(aKey, createIt);
	}

	public String pathForFrameworkNamed(String fwName) {
		return "unimplemented";
	}

	public void setPathForFramework(String path, String fwName) {
	}

	public void addComponent(String componentDirectoryString, String javaFileString) {
		addFileKey(javaFileString, "CLASSES");
		addFileKey(componentDirectoryString, "WO_COMPONENTS");
	}

	public String pathToBucket(String aKey) {
		if (aKey.equals("EJB_META_INFO"))
			return "";
		if (aKey.equals("EJB_SERVER_CLASSES"))
			return "EJBServer.subproj";
		if (aKey.equals("EJB_CLIENT_CLASSES"))
			return "EJBClient.subproj";
		if (aKey.equals("EJB_COMMON_CLASSES"))
			return "";
		return "";
	}

	public void addFileKey(String aFile, String aKey) {
		_PBProject project = this._pbProject;
		String projectName = NSPathUtilities.lastPathComponent(project.projectDir());
		String newKey = aKey;
		if (aKey.equals("WO_COMPONENTS")) {
			newKey = "WEBCOMPONENTS";
		}
		else if (aKey.equals("EJB_META_INFO")) {
			newKey = "RESOURCES";
		}
		else if (aKey.equals("EJB_SERVER_CLASSES")) {
			projectName = String.valueOf(projectName) + "/EJBServer";
			newKey = "CLASSES";
		}
		else if (aKey.equals("EJB_CLIENT_CLASSES")) {
			projectName = String.valueOf(projectName) + "/EJBClient";
			newKey = "CLASSES";
		}
		else if (aKey.equals("EJB_COMMON_CLASSES")) {
			newKey = "CLASSES";
		}
		_PBProject.addFileToPBBucket(projectName, aFile, newKey);
	}

	public void openFile(String filename, int line, String errorMessage) {
		_PBProject.openFile(filename, line, errorMessage);
	}

	public void extractFilesIntoWOProject(_WOProject woProject) {
		extractFilesFromProjectIntoWOProject(this._pbProject, woProject);
	}

	public void extractFilesFromProjectIntoWOProject(_PBProject project, _WOProject woProject) {
		if (project == null)
			return;
		extractFilesForKeyFromProjectIntoWOProject("H_FILES", project, woProject);
		extractFilesForKeyFromProjectIntoWOProject("WOAPP_RESOURCES", project, woProject);
		extractFilesForKeyFromProjectIntoWOProject("CLASSES", project, woProject);
		extractFilesForKeyFromProjectIntoWOProject("OTHER_LINKED", project, woProject);
		if (woProject.includeFrameworks())
			extractFrameworksFromProjectIntoWOProject(project, woProject);
		extractResourcesFromProjectIntoWOProject(project, woProject);
		extractEOModelsFromProjectIntoWOProject(project, woProject);
		NSArray subprojects;
		if ((subprojects = project.parseSubprojects()) != null)
			for (int i = 0, count = subprojects.count(); i < count; i++) {
				_PBProject subproject = (_PBProject) subprojects.objectAtIndex(i);
				extractFilesFromProjectIntoWOProject(subproject, woProject);
			}
	}

	public void extractFrameworksFromProjectIntoWOProject(_PBProject project, _WOProject woProject) {
		NSMutableArray<String> nSMutableArray = project.fileListForKey("FRAMEWORKS", false);
		if (nSMutableArray != null)
			for (int i = 0, count = nSMutableArray.count(); i < count; i++) {
				String frameworkName = (String) nSMutableArray.objectAtIndex(i);
				woProject.extractFrameworkNamed(frameworkName);
			}
	}

	public void extractEOModelsFromProjectIntoWOProject(_PBProject project, _WOProject woProject) {
		String[] keys = { "OTHER_RESOURCES", "WOAPP_RESOURCES" };
		int k = 0;
		while (keys[k] != null) {
			NSMutableArray<String> nSMutableArray = project.fileListForKey(keys[k], false);
			if (nSMutableArray != null)
				for (int i = 0, count = nSMutableArray.count(); i < count; i++) {
					String fileName = (String) nSMutableArray.objectAtIndex(i);
					if ("eomodeld".equals(NSPathUtilities.pathExtension(fileName))) {
						String modelPath = String.valueOf(project.projectDir()) + File.separator + fileName;
						woProject.addModelFilePath(modelPath);
					}
				}
			k++;
		}
	}

	public void extractResourcesFromProjectIntoWOProject(_PBProject project, _WOProject woProject) {
		NSMutableDictionary<String, NSMutableArray<String>> nSMutableDictionary = project.filesTable();
		NSArray fileKeys = nSMutableDictionary.allKeys();
		for (int i = 0, count = fileKeys.count(); i < count; i++) {
			String fileKey = (String) fileKeys.objectAtIndex(i);
			if (fileKey.endsWith("WEBSERVER_RESOURCES") || fileKey.endsWith("WOAPP_RESOURCES") || fileKey.endsWith("OTHER_RESOURCES") || fileKey.endsWith("WO_COMPONENTS"))
				extractResourcesFromProjectWithKeyIntoWOProject(project, fileKey, woProject);
		}
	}

	public void extractResourcesFromProjectWithKeyIntoWOProject(_PBProject project, String key, _WOProject woProject) {
		NSMutableArray<String> nSMutableArray = project.fileListForKey(key, false);
		if (nSMutableArray != null) {
			String language = _languageFromKey(key);
			for (int i = 0, count = nSMutableArray.count(); i < count; i++) {
				String filename = (String) nSMutableArray.objectAtIndex(i);
				String parentDir = _WOProject.resourcePathByAppendingLanguageFileName(project.projectDir(), language, "");
				String resourcePath = String.valueOf(parentDir) + File.separator + filename;
				File resourceFile = new File(resourcePath);
				if (NSPathUtilities.pathExtension(filename).length() <= 0 && resourceFile.exists() && resourceFile.isDirectory()) {
					woProject.extractResourcesFromPath(parentDir, filename);
				}
				else {
					woProject.addResource(filename, resourcePath, language);
				}
			}
		}
	}

	public void extractFilesForKeyFromProjectIntoWOProject(String projectKey, _PBProject project, _WOProject woProject) {
		NSMutableArray<String> nSMutableArray = project.fileListForKey(projectKey, false);
		if (nSMutableArray != null)
			for (int i = 0, c = nSMutableArray.count(); i < c; i++) {
				String fileName = (String) nSMutableArray.objectAtIndex(i);
				if ("java".equals(NSPathUtilities.pathExtension(fileName))) {
					String filePath = String.valueOf(project.projectDir()) + File.separator + fileName;
					woProject.addInterfaceFilePath(filePath);
				}
			}
	}

	public String ideApplicationName() {
		return "unimplemented";
	}

	public String ideProjectPath() {
		return NSPathUtilities.stringByNormalizingExistingPath(this._pbProject.projectDir());
	}

	@Deprecated
	public NSArray frameworkBundlePaths() {
		NSMutableArray frameworkPaths = new NSMutableArray();
		NSMutableArray<String> nSMutableArray = this._pbProject.fileListForKey("FRAMEWORKS", false);
		if (nSMutableArray != null) {
			int fnCount = nSMutableArray.count();
			for (int i = 0; i < fnCount; i++) {
				NSBundle frameworkBundle = NSBundle.bundleForName((String) nSMutableArray.objectAtIndex(i));
				if (frameworkBundle != null)
					frameworkPaths.addObject(frameworkBundle.bundlePath());
			}
		}
		return (NSArray) frameworkPaths;
	}

	public NSArray frameworkBundlePathURLs() {
		NSMutableArray frameworkPaths = new NSMutableArray();
		NSMutableArray<String> nSMutableArray = this._pbProject.fileListForKey("FRAMEWORKS", false);
		if (nSMutableArray != null) {
			int fnCount = nSMutableArray.count();
			for (int i = 0; i < fnCount; i++) {
				NSBundle frameworkBundle = NSBundle.bundleForName((String) nSMutableArray.objectAtIndex(i));
				if (frameworkBundle != null)
					frameworkPaths.addObject(frameworkBundle.bundlePathURL());
			}
		}
		return (NSArray) frameworkPaths;
	}

	public void addFilenameExtensionToListOfKnowns(String anExtension) {
	}

	public void refreshUnderlyingProjectCache() {
		this._pbProject.setRememberFileAttributes(false);
		if (this._pathToPBProjectFile == null)
			this._pathToPBProjectFile = String.valueOf(projectDir()) + File.separator + "PB.project";
		_PBProject.parse(this._pathToPBProjectFile);
	}

	@Deprecated
	public String bundlePath() {
		String path = null;
		String projectType = this._pbProject.projectTypeName();
		if (projectType.equalsIgnoreCase("JavaWebObjectsFramework")) {
			path = NSBundle.bundleForName(this._pbProject.projectName()).bundlePath();
		}
		else if (projectType.equalsIgnoreCase("JavaWebObjectsApplication")) {
			path = NSBundle.mainBundle().bundlePath();
		}
		return NSPathUtilities.stringByNormalizingExistingPath(path);
	}

	public URL bundlePathURL() {
		URL path = null;
		String projectType = this._pbProject.projectTypeName();
		if (projectType.equalsIgnoreCase("JavaWebObjectsFramework")) {
			path = NSBundle.bundleForName(this._pbProject.projectName()).bundlePathURL();
		}
		else if (projectType.equalsIgnoreCase("JavaWebObjectsApplication")) {
			path = NSBundle.mainBundle().bundlePathURL();
		}
		return path;
	}

	public boolean shouldPreloadResources() {
		return true;
	}
}