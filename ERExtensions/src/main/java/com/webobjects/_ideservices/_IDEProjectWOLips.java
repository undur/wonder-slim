package com.webobjects._ideservices;

import java.io.File;
import java.net.URL;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;

public class _IDEProjectWOLips implements _IDEProject {
	private static final String LANGUAGE_RESOURCE_SEPARATOR = "_";

	private volatile _WOLipsProject _wolipsProject;

	public _IDEProjectWOLips(_WOLipsProject wolipsProject) {
		this._wolipsProject = wolipsProject;
	}

	public static _WOLipsProject wolipsProjectFromEclipseProject(String bundlePath) {
		try {
			_WOLipsProject project = null;
			File bundleFolder = new File(bundlePath);
			File buildFolder = bundleFolder.getParentFile();
			if (buildFolder != null && buildFolder.exists()) {
				File projectFolder = buildFolder.getParentFile();
				if (projectFolder != null && projectFolder.exists()) {
					File eclipseProjectFile = new File(projectFolder, ".project");
					if (eclipseProjectFile.exists()) {
						project = new _WOLipsProject(bundleFolder);
						String bundleName = bundleFolder.getName();
						if (bundleName.endsWith(".framework")) {
							project.setProjectType("JavaWebObjectsFramework");
						}
						else {
							project.setProjectType("JavaWebObjectsApplication");
						}
						project.setProjectName(bundleName.substring(0, bundleName.lastIndexOf('.')));
						project.setProjectDir(bundleFolder.getAbsolutePath());
						project.setProjectVersion("2.8");
					}
				}
			}
			return project;
		}
		catch (Throwable e) {
			throw new NSForwardException(e);
		}
	}

	public static _IDEProjectWOLips wolipsProjectAtPath(String bundlePath) {
		_WOLipsProject project = wolipsProjectFromEclipseProject(bundlePath);
		_IDEProjectWOLips ideProjectWOLips = null;
		if (project != null)
			ideProjectWOLips = new _IDEProjectWOLips(project);
		return ideProjectWOLips;
	}

	public void addComponent(String componentDirectoryString, String javaFileString) {
		addFileKey(javaFileString, "CLASSES");
		addFileKey(componentDirectoryString, "WO_COMPONENTS");
	}

	public void addFileKey(String file, String key) {
		String projectName = NSPathUtilities.lastPathComponent(this._wolipsProject.projectDir());
		String newKey = key;
		if (key.equals("WO_COMPONENTS")) {
			newKey = "WEBCOMPONENTS";
		}
		else if (key.equals("EJB_META_INFO")) {
			newKey = "RESOURCES";
		}
		else if (key.equals("EJB_SERVER_CLASSES")) {
			projectName = String.valueOf(projectName) + "/EJBServer";
			newKey = "CLASSES";
		}
		else if (key.equals("EJB_CLIENT_CLASSES")) {
			projectName = String.valueOf(projectName) + "/EJBClient";
			newKey = "CLASSES";
		}
		else if (key.equals("EJB_COMMON_CLASSES")) {
			newKey = "CLASSES";
		}
		_WOLipsProject.addFileToPBBucket(projectName, file, newKey);
	}

	public void addFilenameExtensionToListOfKnowns(String anExtension) {
	}

	@Deprecated
	public String bundlePath() {
		String path = null;
		String projectType = this._wolipsProject.projectTypeName();
		if (projectType.equalsIgnoreCase("JavaWebObjectsFramework")) {
			path = NSBundle.bundleForName(this._wolipsProject.projectName()).bundlePath();
		}
		else if (projectType.equalsIgnoreCase("JavaWebObjectsApplication")) {
			path = NSBundle.mainBundle().bundlePath();
		}
		return NSPathUtilities.stringByNormalizingExistingPath(path);
	}

	public URL bundlePathURL() {
		URL path = null;
		String projectType = this._wolipsProject.projectTypeName();
		if (projectType.equalsIgnoreCase("JavaWebObjectsFramework")) {
			path = NSBundle.bundleForName(this._wolipsProject.projectName()).bundlePathURL();
		}
		else if (projectType.equalsIgnoreCase("JavaWebObjectsApplication")) {
			path = NSBundle.mainBundle().bundlePathURL();
		}
		return path;
	}

	public void extractFilesIntoWOProject(_WOProject woProject) {
		extractFilesFromProjectIntoWOProject(this._wolipsProject, woProject);
	}

	private void extractFilesFromProjectIntoWOProject(_WOLipsProject project, _WOProject woProject) {
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
		NSArray<_PBProject> subProjects = project.parseSubprojects();
		if (subProjects != null)
			for (_PBProject _lpbproject : subProjects) {
				if (_lpbproject instanceof _WOLipsProject)
					extractFilesFromProjectIntoWOProject((_WOLipsProject) _lpbproject, woProject);
			}
	}

	private void extractFrameworksFromProjectIntoWOProject(_WOLipsProject project, _WOProject woProject) {
		NSMutableArray<String> nSMutableArray = project.fileListForKey("FRAMEWORKS", false);
		if (nSMutableArray != null)
			for (String name : nSMutableArray)
				woProject.extractFrameworkNamed(name);
	}

	private void extractEOModelsFromProjectIntoWOProject(_WOLipsProject project, _WOProject woProject) {
		String[] keys = { "OTHER_RESOURCES", "RESOURCES" };
		for (int i = 0; keys[i] != null; i++) {
			NSMutableArray<String> nSMutableArray = project.fileListForKey(keys[i], false);
			if (nSMutableArray != null)
				for (String name : nSMutableArray) {
					if ("eomodeld".equals(NSPathUtilities.pathExtension(name))) {
						String modelPath = String.valueOf(project.contentsFolder().getAbsolutePath()) + File.separator + "Resources" + File.separator + name;
						woProject.addModelFilePath(modelPath);
					}
				}
		}
	}

	private void extractResourcesFromProjectIntoWOProject(_WOLipsProject project, _WOProject woProject) {
		NSMutableDictionary<String, NSMutableArray<String>> nSMutableDictionary = project.filesTable();
		for (String fileKey : nSMutableDictionary.allKeys()) {
			if (fileKey.endsWith("WEBSERVER_RESOURCES") || fileKey.endsWith("WOAPP_RESOURCES") || fileKey.endsWith("OTHER_RESOURCES") || fileKey.endsWith("WO_COMPONENTS"))
				extractResourcesFromProjectWithKeyIntoWOProject(project, fileKey, woProject);
		}
	}

	private void extractResourcesFromProjectWithKeyIntoWOProject(_WOLipsProject project, String key, _WOProject woProject) {
		String basePath;
		if ("WEBSERVER_RESOURCES".equals(key)) {
			basePath = String.valueOf(project.contentsFolder().getAbsolutePath()) + File.separator + "WebServerResources";
		}
		else if ("WOAPP_RESOURCES".equals(key)) {
			basePath = String.valueOf(project.contentsFolder().getAbsolutePath()) + File.separator + "Resources";
		}
		else if ("OTHER_RESOURCES".equals(key)) {
			basePath = String.valueOf(project.contentsFolder().getAbsolutePath()) + File.separator + "Resources";
		}
		else if ("WO_COMPONENTS".equals(key)) {
			basePath = String.valueOf(project.contentsFolder().getAbsolutePath()) + File.separator + "Resources";
		}
		else {
			basePath = project.contentsFolder().getAbsolutePath();
		}
		NSMutableArray<String> nSMutableArray = project.fileListForKey(key, false);
		if (nSMutableArray != null) {
			String language = null;
			for (String filename : nSMutableArray) {
				language = _WOProject.languageFromResourcePath(filename);
				String parentDir = _WOProject.resourcePathByAppendingLanguageFileName(basePath, language, "");
				String resourcePath = String.valueOf(basePath) + File.separator + filename;
				File resourceFile = new File(resourcePath);
				if (NSPathUtilities.pathExtension(filename).length() <= 0 && resourceFile.exists() && resourceFile.isDirectory()) {
					woProject.extractResourcesFromPath(parentDir, filename);
					continue;
				}
				woProject.addResource(filename, resourcePath, language);
			}
		}
	}

	private void extractFilesForKeyFromProjectIntoWOProject(String projectKey, _WOLipsProject project, _WOProject woProject) {
		NSMutableArray<String> nSMutableArray = project.fileListForKey(projectKey, false);
		if (nSMutableArray != null)
			for (String fileName : nSMutableArray) {
				if ("java".equals(NSPathUtilities.pathExtension(fileName))) {
					String filePath = String.valueOf(project.projectDir()) + File.separator + fileName;
					woProject.addInterfaceFilePath(filePath);
				}
			}
	}

	public NSArray<URL> frameworkBundlePathURLs() {
		NSMutableArray<URL> frameworkPaths = new NSMutableArray();
		NSMutableArray<String> nSMutableArray = this._wolipsProject.fileListForKey("FRAMEWORKS", false);
		if (nSMutableArray != null)
			for (String name : nSMutableArray) {
				NSBundle frameworkBundle = NSBundle.bundleForName(name);
				if (frameworkBundle != null)
					frameworkPaths.addObject(frameworkBundle.bundlePathURL());
			}
		return (NSArray<URL>) frameworkPaths;
	}

	@Deprecated
	public NSArray<String> frameworkBundlePaths() {
		NSMutableArray<String> frameworkPaths = new NSMutableArray();
		NSMutableArray<String> nSMutableArray1 = this._wolipsProject.fileListForKey("FRAMEWORKS", false);
		if (nSMutableArray1 != null)
			for (String name : nSMutableArray1) {
				NSBundle frameworkBundle = NSBundle.bundleForName(name);
				if (frameworkBundle != null)
					frameworkPaths.addObject(frameworkBundle.bundlePath());
			}
		return (NSArray<String>) frameworkPaths;
	}

	public String ideApplicationName() {
		return "unimplemented";
	}

	public String ideProjectPath() {
		return NSPathUtilities.stringByNormalizingExistingPath(this._wolipsProject.projectDir());
	}

	public String languageDir() {
		return this._wolipsProject.languageDir();
	}

	public String languageName() {
		return this._wolipsProject.languageName();
	}

	public void openFile(String filename, int lineNumber, String message) {
		_WOLipsProject.openFile(filename, lineNumber, message);
	}

	public String pathForFrameworkNamed(String fwName) {
		return "unimplemented";
	}

	public String pathToBucket(String key) {
		if (key.equals("EJB_META_INFO"))
			return "";
		if (key.equals("EJB_SERVER_CLASSES"))
			return "EJBServer.subproj";
		if (key.equals("EJB_CLIENT_CLASSES"))
			return "EJBClient.subproj";
		if (key.equals("EJB_COMMON_CLASSES"))
			return "";
		return "";
	}

	public String projectDir() {
		return NSPathUtilities.stringByNormalizingExistingPath(this._wolipsProject.projectDir());
	}

	public String projectDirNotNormalized() {
		return this._wolipsProject.projectDir();
	}

	public String projectName() {
		return this._wolipsProject.projectName();
	}

	public String projectTypeName() {
		return this._wolipsProject.projectTypeName();
	}

	public void refreshUnderlyingProjectCache() {
		this._wolipsProject.refreshIfNecessary();
	}

	public void setPathForFramework(String path, String fwName) {
	}

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

	public boolean shouldPreloadResources() {
		return true;
	}
}