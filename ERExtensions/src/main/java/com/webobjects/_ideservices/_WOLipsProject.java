package com.webobjects._ideservices;

import java.io.File;

import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;

public class _WOLipsProject extends _PBProject {
	private File _bundleFolder;

	private File _contentsFolder;

	private File _versionFile;

	private long _lastModified;

	public _WOLipsProject(File bundleFolder) {
		this._bundleFolder = bundleFolder;
		if (bundleFolder.getName().endsWith(".framework")) {
			this._contentsFolder = this._bundleFolder;
		}
		else {
			this._contentsFolder = new File(this._bundleFolder, "Contents");
		}
		this._versionFile = new File(this._bundleFolder.getParentFile(), ".version");
		this._dict = new NSMutableDictionary();
		this._lastModified = -1L;
	}

	public File contentsFolder() {
		return this._contentsFolder;
	}

	protected void refreshIfNecessary() {
		if (this._lastModified == -1L || this._versionFile.exists()) {
			long lastModified = this._versionFile.lastModified();
			if (lastModified != this._lastModified) {
				this._lastModified = lastModified;
				super.filesTable().removeAllObjects();
				mutableLocalFiles().removeAllObjects();
				File resourcesFolder = new File(this._contentsFolder, "Resources");
				if (resourcesFolder.exists())
					addResources(resourcesFolder, resourcesFolder.getAbsolutePath());
				File webserverResourcesFolder = new File(this._contentsFolder, "WebServerResources");
				if (webserverResourcesFolder.exists())
					addWebserverResources(webserverResourcesFolder, webserverResourcesFolder.getAbsolutePath());
			}
		}
	}

	protected void addResources(File resourcesFolder, String basePath) {
		boolean addChildren = true;
		String name = resourcesFolder.getName();
		if (name.endsWith(".wo")) {
			_addRelativeFileKey(resourcesFolder.getAbsolutePath(), "WO_COMPONENTS", basePath);
			addChildren = false;
		}
		else if (name.endsWith(".eomodeld")) {
			_addRelativeFileKey(resourcesFolder.getAbsolutePath(), "WOAPP_RESOURCES", basePath);
			addChildren = false;
		}
		else if (name.equals("Java")) {
			addChildren = false;
		}
		if (addChildren) {
			File[] files = resourcesFolder.listFiles();
			if (files != null) {
				byte b;
				int i;
				File[] arrayOfFile;
				for (i = (arrayOfFile = files).length, b = 0; b < i;) {
					File file = arrayOfFile[b];
					if (file.isDirectory()) {
						addResources(file, basePath);
					}
					else {
						String fileName = file.getName();
						if (!fileName.endsWith(".api"))
							_addRelativeFileKey(file.getAbsolutePath(), "WOAPP_RESOURCES", basePath);
					}
					b++;
				}
			}
		}
	}

	protected void addWebserverResources(File webresourcesFolder, String basePath) {
		File[] files = webresourcesFolder.listFiles();
		if (files != null) {
			byte b;
			int i;
			File[] arrayOfFile;
			for (i = (arrayOfFile = files).length, b = 0; b < i;) {
				File file = arrayOfFile[b];
				if (file.isDirectory()) {
					addWebserverResources(file, basePath);
				}
				else {
					_addRelativeFileKey(file.getAbsolutePath(), "WEBSERVER_RESOURCES", basePath);
				}
				b++;
			}
		}
	}

	protected void _addRelativeFileKey(String absolutePath, String key, String basePath) {
		if (absolutePath.startsWith(basePath)) {
			String relativePath = absolutePath.substring(basePath.length() + 1);
			addFileKey(relativePath, key);
		}
		else {
			addFileKey(absolutePath, key);
		}
	}

	public NSDictionary<String, String> localFiles() {
		refreshIfNecessary();
		return super.localFiles();
	}

	public NSMutableDictionary<String, NSMutableArray<String>> filesTable() {
		refreshIfNecessary();
		return super.filesTable();
	}
}