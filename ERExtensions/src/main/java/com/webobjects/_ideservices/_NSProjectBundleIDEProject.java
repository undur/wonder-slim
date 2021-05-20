package com.webobjects._ideservices;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSRange;
import com.webobjects.foundation.development.NSProjectBundle;
import com.webobjects.foundation.development.NSResourceType;

public class _NSProjectBundleIDEProject implements _IDEProject {
	private NSProjectBundle _bundle;

	private List<Resource> _resources;

	private File _versionFile;

	private long _lastRefreshTime;

	public _NSProjectBundleIDEProject(NSProjectBundle bundle) {
		this._bundle = bundle;
		this._versionFile = new File(this._bundle.projectPath(), ".version");
		if (!this._versionFile.exists()) {
			this._versionFile = new File(this._bundle.projectPath(), "woproject/.version");
			if (!this._versionFile.exists())
				this._versionFile = null;
		}
	}

	public String projectDir() {
		return this._bundle.bundlePath();
	}

	public String projectDirNotNormalized() {
		return this._bundle.bundlePath();
	}

	public String languageDir() {
		return null;
	}

	public String projectName() {
		return this._bundle.name();
	}

	public String projectTypeName() {
		return "NSProjectBundle";
	}

	public String languageName() {
		return System.getProperty("user.language");
	}

	public void addComponent(String componentDirectoryString, String javaFileString) {
		System.out.println("_NSProjectBundleIDEProject.addComponent: " + componentDirectoryString + "," + javaFileString);
	}

	public String pathToBucket(String aKey) {
		System.out.println("_NSProjectBundleIDEProject.pathToBucket: " + aKey);
		return null;
	}

	public void addFileKey(String aFile, String aKey) {
		System.out.println("_NSProjectBundleIDEProject.addFileKey: " + aFile + ", " + aKey);
	}

	public void openFile(String aFile, int lineNumber, String message) {
		System.out.println("_NSProjectBundleIDEProject.openFile: " + aFile + "," + lineNumber + "," + message);
	}

	public String pathForFrameworkNamed(String fwName) {
		System.out.println("_NSProjectBundleIDEProject.pathForFrameworkNamed: " + fwName);
		return null;
	}

	public void setPathForFramework(String path, String fwName) {
		System.out.println("_NSProjectBundleIDEProject.setPathForFramework: " + path + "," + fwName);
	}

	public synchronized void extractFilesIntoWOProject(_WOProject woProject) {
		boolean shouldRefreshResources = (this._resources == null);
		if (shouldRefreshResources) {
			Set<String> relativePaths = new HashSet<String>();
			relativePaths.addAll(this._bundle.relativePathForResourceType(NSResourceType.Other));
			List<Resource> resources = new LinkedList<Resource>();
			for (String relativePath : relativePaths)
				extractFilesIntoWOProject(woProject, relativePath, relativePath, resources);
			this._resources = resources;
			this._lastRefreshTime = System.currentTimeMillis();
		}
		for (Resource resource : this._resources)
			woProject.addResource(resource.name(), resource.resourcePath(), resource.language());
	}

	protected boolean isBundleFolder(File folder) {
		String name = folder.getName();
		return !(!name.endsWith(".wo") && !name.endsWith(".eomodeld"));
	}

	protected boolean isIgnoredFolder(File folder) {
		String name = folder.getName();
		return !(!name.equals("CVS") && !name.equals(".git") && !name.equals(".svn"));
	}

	public void extractFilesIntoWOProject(_WOProject woProject, String initialPath, String relativePath, List<Resource> resources) {
		File folder = new File(this._bundle.bundlePath(), relativePath);
		File[] files = folder.listFiles();
		if (files != null) {
			byte b;
			int i;
			File[] arrayOfFile;
			for (i = (arrayOfFile = files).length, b = 0; b < i;) {
				File file = arrayOfFile[b];
				if (file.isDirectory() && !isBundleFolder(file)) {
					if (!isIgnoredFolder(file))
						extractFilesIntoWOProject(woProject, initialPath, String.valueOf(relativePath) + File.separator + file.getName(), resources);
				}
				else {
					String resourcePath = file.getPath();
					String name = NSPathUtilities.lastPathComponent(resourcePath);
					String language = _WOProject.languageFromResourcePath(resourcePath);
					resources.add(new Resource(name, resourcePath, language));
					if (initialPath.length() > 0 && relativePath.length() > 0 && initialPath.length() != relativePath.length()) {
						String subdirName = String.valueOf(relativePath.substring(initialPath.length() + 1)) + File.separator + name;
						NSArray<String> pathComponents = NSArray.componentsSeparatedByString(subdirName, File.separator);
						if (((String) pathComponents.objectAtIndex(0)).endsWith(".lproj"))
							if (pathComponents.count() > 2) {
								pathComponents = pathComponents.subarrayWithRange(new NSRange(1, pathComponents.count() - 1));
								subdirName = pathComponents.componentsJoinedByString(File.separator);
							}
							else {
								subdirName = null;
							}
						if (subdirName != null)
							resources.add(new Resource(subdirName, resourcePath, language));
					}
				}
				b++;
			}
		}
	}

	public String ideApplicationName() {
		return this._bundle.getClass().getSimpleName();
	}

	public String ideProjectPath() {
		return this._bundle.bundlePath();
	}

	public NSArray frameworkBundlePaths() {
		return null;
	}

	public NSArray frameworkBundlePathURLs() {
		return null;
	}

	public void addFilenameExtensionToListOfKnowns(String anExtension) {
	}

	public synchronized void refreshUnderlyingProjectCache() {
		if (this._versionFile != null && this._versionFile.lastModified() > this._lastRefreshTime) {
			this._resources = null;
		}
		else if (System.currentTimeMillis() - this._lastRefreshTime > NSProperties.longForKeyWithDefault("NSProjectBundleRefreshTime", 60000L)) {
			this._resources = null;
		}
	}

	public String bundlePath() {
		return this._bundle.bundlePath();
	}

	public URL bundlePathURL() {
		return this._bundle.bundlePathURL();
	}

	public boolean shouldPreloadResources() {
		return false;
	}

	private static class Resource {
		private String _name;

		private String _resourcePath;

		private String _language;

		public Resource(String name, String resourcePath, String language) {
			this._name = name;
			this._resourcePath = resourcePath;
			this._language = language;
		}

		public String name() {
			return this._name;
		}

		public String resourcePath() {
			return this._resourcePath;
		}

		public String language() {
			return this._language;
		}
	}
}