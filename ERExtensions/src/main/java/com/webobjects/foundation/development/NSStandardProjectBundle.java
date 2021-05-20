package com.webobjects.foundation.development;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSValueUtilities;

public abstract class NSStandardProjectBundle extends NSProjectBundle {
	private NSDictionary<String, Object> _infoDictionary;

	private Properties _buildProperties;

	private Document _eclipseProjectDocument;

	public static class Factory extends NSBundleFactory {
		private static Properties _primordialProperties;

		private static NSArray<String> _projectSearchPaths;

		public NSBundle bundleForPath(String path, boolean shouldCreateBundle, boolean newIsJar) {
			NSBundle bundle = _bundleForPath(path, shouldCreateBundle, newIsJar);
			if (bundle == null) {
				String normalizedPath = NSBundle._normalizeExistingBundlePath(path);
				NSBundle existingBundle = NSBundle._lookupBundleWithPath(normalizedPath);
				if (existingBundle != null) {
					bundle = existingBundle;
				}
				else {
					existingBundle = NSBundle._lookupBundleWithPath(NSBundle._cleanNormalizedBundlePath(normalizedPath));
					if (existingBundle != null) {
						bundle = existingBundle;
					}
					else {
						if (_primordialProperties == null) {
							_primordialProperties = NSBundle._userAndBundleProperties();
							_projectSearchPaths = NSValueUtilities.arrayValue(_primordialProperties.getProperty("NSProjectSearchPath"));
						}
						if (_projectSearchPaths != null) {
							String projectBuildPath = _primordialProperties.getProperty("NSProjectBuildPath");
							String bundleName = null;
							if (projectBuildPath != null) {
								String normalizedBuildPath = NSPathUtilities.stringByNormalizingPath(projectBuildPath);
								String normalizedBundlePath = NSPathUtilities.stringByNormalizingPath(path);
								if (normalizedBundlePath.startsWith(normalizedBuildPath)) {
									NSArray<String> buildPathComponents = NSArray.componentsSeparatedByString(normalizedBuildPath, File.separator);
									NSArray<String> bundlePathComponents = NSArray.componentsSeparatedByString(normalizedBundlePath, File.separator);
									if (bundlePathComponents.count() > buildPathComponents.count())
										bundleName = (String) bundlePathComponents.objectAtIndex(buildPathComponents.count());
								}
							}
							else {
								NSArray<String> pathComponents = NSArray.componentsSeparatedByString(path, File.separator);
								for (String pathComponent : pathComponents) {
									if (pathComponent.endsWith(".framework") || pathComponent.endsWith(".woa")) {
										bundleName = pathComponent.substring(0, pathComponent.lastIndexOf('.'));
										break;
									}
								}
							}
							if (bundleName != null)
								for (String projectSearchPath : _projectSearchPaths) {
									File projectPath = new File(projectSearchPath, bundleName);
									if (projectPath.exists()) {
										bundle = _bundleForPath(projectPath.getAbsolutePath(), shouldCreateBundle, newIsJar);
										break;
									}
								}
						}
					}
				}
			}
			if (bundle != null) {
				NSBundle existingBundle = NSBundle._bundleOrAppForName(bundle.name());
				if (existingBundle != null) {
					bundle = existingBundle;
				}
				else if (!shouldCreateBundle) {
					bundle = null;
				}
			}
			if (bundle instanceof NSProjectBundle)
				((NSProjectBundle) bundle)._bundleLoadedFromPath(path);
			return bundle;
		}

		protected NSBundle createBundleFromProjectFolder(File projectFolder) {
			boolean isBundle = false;
			Properties buildProperties = null;
			File buildPropertiesFile = new File(projectFolder, "woantbuild.properties");
			if (!buildPropertiesFile.exists())
				buildPropertiesFile = new File(projectFolder, "build.properties");
			if (buildPropertiesFile.exists()) {
				String folderName = projectFolder.getName();
				if (!folderName.endsWith(".framework") && !folderName.endsWith(".woa")) {
					buildProperties = new Properties();
					try {
						InputStream buildPropertiesInputStream = new FileInputStream(buildPropertiesFile);
						try {
							buildProperties.load(buildPropertiesInputStream);
						}
						finally {
							buildPropertiesInputStream.close();
						}
						if (buildProperties.getProperty("framework.name") != null) {
							isBundle = true;
						}
						else {
							String projectType = buildProperties.getProperty("project.type");
							if ("framework".equals(projectType) || "application".equals(projectType)) {
								isBundle = true;
							}
							else if (projectType == null && buildProperties.getProperty("project.name") != null) {
								isBundle = true;
							}
							else if (projectType == null && buildProperties.getProperty("application.name") != null) {
								isBundle = true;
							}
						}
					}
					catch (Throwable t) {
						System.out.println("NSBundle: Can't read " + buildPropertiesFile + " (" + t + ")");
					}
				}
			}
			Document eclipseProjectDocument = null;
			boolean mavenProject = false;
			String projectPath = projectFolder.getPath();
			File eclipseProjectFile = new File(projectFolder, ".project");
			if (eclipseProjectFile.exists())
				try {
					eclipseProjectDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(eclipseProjectFile);
					eclipseProjectDocument.normalize();
					Element projectDescriptionElement = eclipseProjectDocument.getDocumentElement();
					Element naturesElement = (Element) projectDescriptionElement.getElementsByTagName("natures").item(0);
					NodeList naturesNodeList = naturesElement.getElementsByTagName("nature");
					for (int i = 0; i < naturesNodeList.getLength(); i++) {
						Element natureElement = (Element) naturesNodeList.item(i);
						String nature = null;
						NodeList natureChildNodeList = natureElement.getChildNodes();
						for (int natureChildNodeNum = 0; natureChildNodeNum < natureChildNodeList.getLength(); natureChildNodeNum++) {
							Node natureChildNode = natureChildNodeList.item(natureChildNodeNum);
							if (natureChildNode instanceof org.w3c.dom.Text)
								nature = natureChildNode.getNodeValue();
						}
						if (nature != null)
							if ("org.maven.ide.eclipse.maven2Nature".equals(nature)) {
								mavenProject = true;
							}
							else if (nature.startsWith("org.objectstyle.wolips.")) {
								isBundle = true;
							}
					}
				}
				catch (Throwable t) {
					System.out.println("NSBundle: Can't read " + eclipseProjectFile + " (" + t + ")");
				}
			NSBundle bundle = null;
			if (isBundle)
				if (mavenProject) {
					bundle = new NSMavenProjectBundle(projectPath, buildProperties, eclipseProjectDocument);
				}
				else {
					bundle = new NSFluffyBunnyProjectBundle(projectPath, buildProperties, eclipseProjectDocument);
				}
			return bundle;
		}

		protected NSBundle _bundleForPath(String path, boolean shouldCreateBundle, boolean newIsJar) {
			NSBundle bundle = null;
			File projectFolder = new File(path);
			for (; bundle == null && projectFolder != null; projectFolder = projectFolder.getParentFile())
				bundle = createBundleFromProjectFolder(projectFolder);
			return bundle;
		}
	}

	public NSStandardProjectBundle(String projectPath, Properties buildProperties, Document eclipseProject) {
		super(projectPath);
		this._buildProperties = buildProperties;
		this._eclipseProjectDocument = eclipseProject;
	}

	public NSDictionary<String, Object> infoDictionary() {
		NSMutableDictionary<String, Object> nSMutableDictionary = null;
		NSDictionary<String, Object> infoDictionary = this._infoDictionary;
		if (infoDictionary == null) {
			infoDictionary = NSDictionary.emptyDictionary();
			Boolean isApplication = null;
			String projectName = null;
			String principalClass = null;
			String eoAdaptorClass = null;
			String dependencies = null;
			if (this._buildProperties != null) {
				projectName = this._buildProperties.getProperty("framework.name");
				if (projectName != null) {
					isApplication = Boolean.FALSE;
				}
				else {
					projectName = this._buildProperties.getProperty("application.name");
					if (projectName != null) {
						isApplication = Boolean.TRUE;
					}
					else {
						projectName = this._buildProperties.getProperty("project.name");
						if ("framework".equals(this._buildProperties.getProperty("project.type"))) {
							isApplication = Boolean.FALSE;
						}
						else if ("application".equals(this._buildProperties.getProperty("project.type"))) {
							isApplication = Boolean.TRUE;
						}
					}
				}
				principalClass = this._buildProperties.getProperty("principalClass");
				eoAdaptorClass = this._buildProperties.getProperty("eoAdaptorClassName");
				dependencies = this._buildProperties.getProperty("dependencies");
			}
			if (this._eclipseProjectDocument != null) {
				if (projectName == null) {
					Element projectDescriptionElement = this._eclipseProjectDocument.getDocumentElement();
					NodeList nameList = projectDescriptionElement.getElementsByTagName("name");
					if (nameList.getLength() > 0)
						projectName = ((Element) nameList.item(0)).getFirstChild().getNodeValue();
				}
				if (isApplication == null) {
					Element projectDescriptionElement = this._eclipseProjectDocument.getDocumentElement();
					NodeList natureNodeList = projectDescriptionElement.getElementsByTagName("nature");
					for (int i = 0; i < natureNodeList.getLength(); i++) {
						String nature = ((Element) natureNodeList.item(i)).getFirstChild().getNodeValue();
						if (nature != null)
							if (nature.startsWith("org.objectstyle.wolips.") && nature.endsWith("applicationnature")) {
								isApplication = Boolean.TRUE;
							}
							else if (nature.startsWith("org.objectstyle.wolips.") && nature.endsWith("frameworknature")) {
								isApplication = Boolean.FALSE;
							}
					}
				}
			}
			if (projectName == null) {
				projectName = (new File(projectPath())).getName();
				System.err.println("NSBundle: Unable to determine the project name for '" + projectPath() + "'. Guessing '" + projectName + "'.");
			}
			if (isApplication == null) {
				isApplication = Boolean.FALSE;
				System.err.println("NSBundle: Unable to determine the project type for '" + projectPath() + "'. Guessing it is a framework.");
			}
			NSMutableDictionary<String, Object> mockInfoPlist = new NSMutableDictionary();
			mockInfoPlist.setObjectForKey(projectName, "NSExecutable");
			mockInfoPlist.setObjectForKey("webo", "CFBundleSignatureKey");
			if (isApplication.booleanValue()) {
				mockInfoPlist.setObjectForKey("APPL", "CFBundlePackageType");
			}
			else {
				mockInfoPlist.setObjectForKey("FMWK", "CFBundlePackageType");
			}
			if (principalClass != null)
				mockInfoPlist.setObjectForKey(principalClass, "NSPrincipalClass");
			if (eoAdaptorClass != null)
				mockInfoPlist.setObjectForKey(eoAdaptorClass, "EOAdaptorClassName");
			mockInfoPlist.setObjectForKey("true", "Has_WOComponents");
			NSMutableArray<String> requiredBundleNames = new NSMutableArray();
			if (dependencies != null)
				for (String dependency : NSArray.componentsSeparatedByString(dependencies, ","))
					requiredBundleNames.addObject(dependency.trim());
			mockInfoPlist.setObjectForKey("1.2.3", "CFBundleShortVersionString");
			mockInfoPlist.setObjectForKey("0", "Implementation-Version");
			nSMutableDictionary = mockInfoPlist;
			this._infoDictionary = (NSDictionary<String, Object>) nSMutableDictionary;
		}
		return (NSDictionary<String, Object>) nSMutableDictionary;
	}
}