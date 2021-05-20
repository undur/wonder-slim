package com.webobjects._ideservices;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSPathUtilities;

public final class _WOAntProject implements _IDEProject {
	private static String _projectPath = null;

	private static String _normalizedProjectPath = null;

	private static String _buildFilePath = null;

	private static String _projectName = null;

	private static String _resourcesDir = null;

	private static String _webserverResourcesDir = null;

	private static NSMutableArray _frameworkBundlePaths = null;

	public _WOAntProject(String projectPath) {
		_projectPath = projectPath;
		_normalizedProjectPath = NSPathUtilities.stringByNormalizingExistingPath(_projectPath);
		_buildFilePath = String.valueOf(_normalizedProjectPath) + File.separator + "build.xml";
		_resourcesDir = String.valueOf(_normalizedProjectPath) + File.separator + "wo-resources" + File.separator + "Resources";
		_webserverResourcesDir = String.valueOf(_normalizedProjectPath) + File.separator + "wo-resources" + File.separator + "WebServerResources";
		_frameworkBundlePaths = new NSMutableArray();
	}

	public static _WOAntProject antProjectAtPath(String path) {
		if (path.endsWith(".jar") || path.endsWith(".framework"))
			return null;
		String mainBundleName = NSBundle.mainBundle().name();
		int idx = path.indexOf(mainBundleName);
		int nameLength = mainBundleName.length();
		String projDir = path.substring(0, idx + nameLength);
		if (projDir != null && !projDir.endsWith(".woa") && (!pathContainsAntBuildFile(projDir) || !pathContainsWOResources(projDir)))
			return null;
		return new _WOAntProject(projDir);
	}

	private static boolean pathContainsAntBuildFile(String path) {
		boolean fileExists = false;
		try {
			File file = new File(String.valueOf(path) + File.separator + "build.xml");
			fileExists = (file.exists() && file.isFile());
		}
		catch (Exception e) {
			return false;
		}
		return fileExists;
	}

	private static boolean pathContainsWOResources(String path) {
		boolean fileExists = false;
		try {
			File file = new File(String.valueOf(path) + File.separator + "wo-resources");
			fileExists = (file.exists() && file.isDirectory());
		}
		catch (Exception e) {
			return false;
		}
		return fileExists;
	}

	public String projectDir() {
		return _normalizedProjectPath;
	}

	public String projectDirNotNormalized() {
		return _projectPath;
	}

	public String languageDir() {
		return "languageDir unimplemented";
	}

	public String projectName() {
		if (_projectName == null)
			_projectName = extractProjectName(_buildFilePath);
		return _projectName;
	}

	public String projectTypeName() {
		return "WebObjects Application";
	}

	public String languageName() {
		return "languageName unimplemented";
	}

	public void addComponent(String componentDirectoryString, String javaFileString) {
	}

	public String pathToBucket(String aKey) {
		return null;
	}

	public void addFileKey(String aFile, String aKey) {
	}

	public void openFile(String aFile, int lineNumber, String message) {
	}

	public String pathForFrameworkNamed(String fwName) {
		return null;
	}

	public void setPathForFramework(String path, String fwName) {
	}

	public void extractFilesIntoWOProject(_WOProject woProject) {
		try {
			File rf = new File(_resourcesDir);
			extractResourceFilesIntoWOProject(woProject, rf);
		}
		catch (Exception exception) {
		}
		try {
			File rf = new File(_webserverResourcesDir);
			extractResourceFilesIntoWOProject(woProject, rf);
		}
		catch (Exception exception) {
		}
	}

	public void extractResourceFilesIntoWOProject(_WOProject woproject, File startingDir) {
		List<File> filesDirs = Arrays.asList(startingDir.listFiles());
		for (File file : filesDirs) {
			try {
				woproject.addResourcePath(file.getCanonicalPath());
			}
			catch (IOException iOException) {
			}
			if (file.isDirectory())
				extractResourceFilesIntoWOProject(woproject, file);
		}
	}

	public String ideApplicationName() {
		return null;
	}

	public String ideProjectPath() {
		return projectDir();
	}

	@Deprecated
	public NSArray frameworkBundlePaths() {
		return (NSArray) _frameworkBundlePaths;
	}

	public NSArray frameworkBundlePathURLs() {
		return (NSArray) _frameworkBundlePaths;
	}

	public void addFilenameExtensionToListOfKnowns(String anExtension) {
	}

	public void refreshUnderlyingProjectCache() {
	}

	@Deprecated
	public String bundlePath() {
		return NSBundle.mainBundle().bundlePath();
	}

	public URL bundlePathURL() {
		return NSBundle.mainBundle().bundlePathURL();
	}

	public Document documentForFile(String filePath) {
		Document document = null;
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			File file = new File(filePath);
			document = builder.parse(file);
		}
		catch (Exception exception) {
		}
		return document;
	}

	public String extractProjectName(String filePath) {
		String projectName = "";
		try {
			Document doc = documentForFile(filePath);
			NodeList list = doc.getElementsByTagName("project");
			Node aNode = null;
			NamedNodeMap map = null;
			for (int i = 0; i < list.getLength(); i++) {
				aNode = list.item(i);
				map = aNode.getAttributes();
				projectName = _parseNodeMap(map, "name");
			}
		}
		catch (Exception exception) {
		}
		return projectName;
	}

	private String _parseNodeMap(NamedNodeMap map, String attName) {
		Node aNode = null;
		for (int i = 0; i < map.getLength(); i++) {
			aNode = map.item(i);
			if (aNode.getNodeName().equals(attName))
				return aNode.getNodeValue();
		}
		return null;
	}

	public boolean shouldPreloadResources() {
		return true;
	}
}