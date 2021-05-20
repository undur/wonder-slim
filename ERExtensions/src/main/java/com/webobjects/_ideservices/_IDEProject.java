package com.webobjects._ideservices;

import java.net.URL;

import com.webobjects.foundation.NSArray;

public interface _IDEProject {
	public static final String IDE_WebComponentsKey = "WO_COMPONENTS";

	public static final String IDE_WebServerResourcesKey = "WEBSERVER_RESOURCES";

	public static final String IDE_PBWO_WOAppResourcesKey = "WOAPP_RESOURCES";

	public static final String IDE_WOAppResourcesKey = "RESOURCES";

	public static final String IDE_FrameworksKey = "FRAMEWORKS";

	public static final String IDE_OtherResourcesKey = "OTHER_RESOURCES";

	public static final String IDE_ClassesKey = "CLASSES";

	public static final String IDE_EJBServerClassesKey = "EJB_SERVER_CLASSES";

	public static final String IDE_EJBClientClassesKey = "EJB_CLIENT_CLASSES";

	public static final String IDE_EJBCommonClassesKey = "EJB_COMMON_CLASSES";

	public static final String IDE_EJBMetaInfoKey = "EJB_META_INFO";

	public static final String IDE_HeadersKey = "H_FILES";

	public static final String IDE_OtherSourcesKey = "OTHER_LINKED";

	public static final String WBJavaExtension = "java";

	public static final String WBEOModelExtension = "eomodeld";

	String projectDir();

	String projectDirNotNormalized();

	String languageDir();

	String projectName();

	String projectTypeName();

	String languageName();

	void addComponent(String paramString1, String paramString2);

	String pathToBucket(String paramString);

	void addFileKey(String paramString1, String paramString2);

	void openFile(String paramString1, int paramInt, String paramString2);

	String pathForFrameworkNamed(String paramString);

	void setPathForFramework(String paramString1, String paramString2);

	void extractFilesIntoWOProject(_WOProject param_WOProject);

	String ideApplicationName();

	String ideProjectPath();

	@Deprecated
	NSArray frameworkBundlePaths();

	NSArray frameworkBundlePathURLs();

	void addFilenameExtensionToListOfKnowns(String paramString);

	void refreshUnderlyingProjectCache();

	@Deprecated
	String bundlePath();

	URL bundlePathURL();

	boolean shouldPreloadResources();
}