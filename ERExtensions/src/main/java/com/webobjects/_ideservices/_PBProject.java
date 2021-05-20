package com.webobjects._ideservices;

import java.io.File;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Iterator;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation._NSStringUtilities;

public class _PBProject {
	static String PBProjectWillUpgradeNotification = "Project Will Upgrade";

	static String PBProjectDidUpgradeNotification = "Project Upgraded";

	static String PBProjectDidChangeNotification = "Project Changed";

	static String PBProjectWillSaveNotification = "Project Will Save";

	static String PBProjectDidSaveNotification = "Project Saved";

	static String PBProjectSaveDidFailNotification = "Project Save Failed";

	static String PBFileAddedToProjectNotification = "File Added to Project";

	static String PBFileRemovedFromProjectNotification = "File Removed from Project";

	static NSMutableDictionary canonicalFileToProject = new NSMutableDictionary();

	static NSNotificationCenter notificationCenter = NSNotificationCenter.defaultCenter();

	static String latestProjectVersion = "2.6";

	static NSMutableDictionary _canonicalFileToProject = new NSMutableDictionary();

	static Object _projectParseLock = new Object();

	NSMutableArray<String> _resolvedNSProjectSearchPath = null;

	NSMutableArray<String> _lastNSProjectSearchPath = null;

	static {
		String value = NSProperties.getProperty("ProjectBuilderWOPort", "8546");
		try {
			_PBPort = Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			if (NSLog.debugLoggingAllowedForLevel(1))
				NSLog.err.appendln("_PBProject: exception while reading property 'ProjectBuilderPort'. The value '" + value + "' is not an integer. Using port 8546 by default.");
			_PBPort = 8546;
		}
	}

	public static String _PBHostname = NSProperties.getProperty("ProjectBuilderWOHost", "localhost");

	private static String pbProjectSuffix = String.valueOf(File.separator) + "PB.project";

	public static _PBProject pbProjectAtPath(String path) {
		_PBProject pbProject = null;
		String canonicalPath = NSPathUtilities.stringByNormalizingExistingPath(path);
		if (!canonicalPath.endsWith(pbProjectSuffix))
			canonicalPath = String.valueOf(canonicalPath) + pbProjectSuffix;
		pbProject = parse(canonicalPath);
		return pbProject;
	}

	public void setObjectForKey(Object anObject, String key) {
		this._dict.setObjectForKey(anObject, key);
	}

	public Object objectForKey(String key) {
		return this._dict.objectForKey(key);
	}

	public void renameProject(String pathName) {
	}

	public void removeObjectForKey(String key) {
		this._dict.removeObjectForKey(key);
	}

	public String _canonicalFile() {
		return this._canonicalFile;
	}

	public String _projectDir() {
		return this._projectDir;
	}

	public _PBProject rootProject() {
		_PBProject superProj = superProject();
		if (superProj != null)
			return superProj.rootProject();
		return this;
	}

	public _PBProject superProject() {
		return this._superProject;
	}

	public String languageDir() {
		if (this._languageDirCache == null)
			this._languageDirCache = NSPathUtilities.stringByAppendingPathComponent(this._projectDir, String.valueOf(languageName()) + ".lproj");
		return this._languageDirCache;
	}

	public long _touched() {
		return this._touched;
	}

	public void setTouched(long touch) {
		if (touch == 0L) {
			this._touched = 0L;
		}
		else {
			this._touched |= touch;
		}
	}

	public String projectType() {
		return "WebObjects Application (fixme)";
	}

	public String projectTypeName() {
		return (String) this._dict.objectForKey("PROJECTTYPE");
	}

	public String projectName() {
		return (String) this._dict.objectForKey("PROJECTNAME");
	}

	public String appIconFileForOSType(int anOS) {
		return (String) this._dict.objectForKey(prefixStringWithOSType("APPICON", anOS));
	}

	public String appHelpFileForOSType(int anOS) {
		return (String) this._dict.objectForKey(prefixStringWithOSType("HELPFILE", anOS));
	}

	public String versionNb() {
		return (String) this._dict.objectForKey("VERSIONNB");
	}

	public String bundleExtension() {
		String extension = (String) this._dict.objectForKey("BUNDLE_EXTENSION");
		return (extension != null) ? extension : "bundle";
	}

	public String languageName() {
		return System.getProperty("user.language");
	}

	public String applicationClass() {
		String key = "APPCLASS";
		Object appClass = this._dict.objectForKey(key);
		if (appClass == null && projectTypeName().equals("Application")) {
			appClass = "NSApplication";
			this._dict.setObjectForKey(appClass, key);
		}
		return (String) appClass;
	}

	public String mainNibFileForOSType(int anOSType) {
		return (String) this._dict.objectForKey(prefixStringWithOSType("MAINNIB", anOSType));
	}

	public static String prefixStringWithOSType(String s, int type) {
		return String.valueOf(s) + type;
	}

	public NSMutableArray docExtensionsForOSType(int anOSType) {
		String key = prefixStringWithOSType("DOCUMENTEXTENSIONS", anOSType);
		Object docIconFiles = this._dict.objectForKey(key);
		if (docIconFiles == null) {
			docIconFiles = new NSMutableArray();
			this._dict.setObjectForKey(docIconFiles, key);
		}
		return (NSMutableArray) docIconFiles;
	}

	public String shouldGenerateMain() {
		String key = "GENERATEMAIN";
		Object generate = this._dict.objectForKey(key);
		if (generate == null) {
			generate = "YES";
			this._dict.setObjectForKey(generate, key);
		}
		return (String) generate;
	}

	public String projectVersion() {
		String key = "PROJECTVERSION";
		Object version = this._dict.objectForKey(key);
		if (version == null) {
			version = "";
			this._dict.setObjectForKey(version, key);
		}
		return (String) version;
	}

	public void setProjectVersion(String version) {
		this._dict.setObjectForKey(version, "PROJECTVERSION");
	}

	public void addSystemExtensions(String ext) {
		if (!systemExtensions().containsObject(ext))
			((NSMutableArray) systemExtensions()).addObject(ext);
	}

	public NSArray systemExtensions() {
		String key = "SYSTEMEXTENSIONS";
		Object systemExtensions = this._dict.objectForKey(key);
		if (systemExtensions == null) {
			systemExtensions = new NSMutableArray();
			this._dict.setObjectForKey(systemExtensions, key);
		}
		return (NSArray) systemExtensions;
	}

	public void setShouldGenerateMain(String shoudI) {
		this._dict.setObjectForKey(shoudI, "GENERATEMAIN");
	}

	public void setProjectDir(String dir) {
		this._projectDir = dir;
	}

	public void setVersionNb(String vn) {
		this._dict.setObjectForKey(vn, "VERSIONNB");
	}

	public void setProjectType(String type) {
		this._dict.setObjectForKey(type, "PROJECTTYPE");
	}

	public void setProjectName(String name) {
		this._dict.setObjectForKey(name, "PROJECTNAME");
	}

	public void setLanguageName(String l) {
		this._dict.setObjectForKey(l, "LANGUAGE");
		this._languageDirCache = null;
	}

	public void setAppIconFileForOSType(String iconFile, int anOS) {
		String key = prefixStringWithOSType("APPICON", anOS);
		if (!this._dict.objectForKey(key).equals(iconFile))
			if (iconFile != null && iconFile.length() > 0) {
				this._dict.setObjectForKey(iconFile, key);
			}
			else {
				this._dict.removeObjectForKey(key);
			}
	}

	public void setAppHelpFileForOSType(String helpFile, int anOS) {
		String key = prefixStringWithOSType("HELPFILE", anOS);
		if (!this._dict.objectForKey(key).equals(helpFile))
			if (helpFile != null && helpFile.length() > 0) {
				this._dict.setObjectForKey(helpFile, key);
			}
			else {
				this._dict.removeObjectForKey(key);
			}
	}

	public void setApplicationClass(String appClass) {
		this._dict.setObjectForKey(appClass, "APPCLASS");
	}

	public void setMainNibFileForOSType(String mainNib, int anOS) {
		String key = prefixStringWithOSType("MAINNIB", anOS);
		if (mainNib != null && mainNib.length() > 0) {
			this._dict.setObjectForKey(mainNib, key);
		}
		else {
			this._dict.removeObjectForKey(key);
		}
	}

	public void setBundleExtension(String ext) {
		this._dict.setObjectForKey(ext, "BUNDLE_EXTENSION");
	}

	public NSMutableDictionary<String, NSMutableArray<String>> filesTable() {
		String key = "FILESTABLE";
		Object filesTable = this._dict.objectForKey(key);
		if (filesTable == null) {
			filesTable = new NSMutableDictionary();
			this._dict.setObjectForKey(filesTable, key);
		}
		return (NSMutableDictionary<String, NSMutableArray<String>>) filesTable;
	}

	public NSMutableArray<String> fileListForKey(String aKey) {
		return fileListForKey(aKey, true);
	}

	public NSMutableArray<String> fileListForKey(String aKey, boolean createIt) {
		NSMutableArray<String> fileList = (NSMutableArray<String>) filesTable().objectForKey(aKey);
		if (createIt && fileList == null && aKey != null && !aKey.equals("")) {
			fileList = new NSMutableArray();
			filesTable().setObjectForKey(fileList, aKey);
		}
		return fileList;
	}

	public NSArray<String> otherLinkedOFiles() {
		NSMutableArray<String> otherLinkedFiles = (NSMutableArray<String>) filesTable().objectForKey("OTHER_LINKED");
		if (otherLinkedFiles != null) {
			int count = otherLinkedFiles.count();
			NSMutableArray<String> otherLinkedOFiles = new NSMutableArray();
			for (int i = 0; i < count; i++)
				otherLinkedOFiles.addObject(String.valueOf(otherLinkedFiles.objectAtIndex(i)) + "o");
			return (NSArray<String>) otherLinkedOFiles;
		}
		return null;
	}

	public static NSDictionary projectFileDict(_PBProject proj, String file) {
		NSMutableDictionary dict = new NSMutableDictionary();
		dict.setObjectForKey(proj, "project");
		dict.setObjectForKey(file, "file");
		return (NSDictionary) dict;
	}

	public void addFileKey(String aFile, String aKey) {
		NSMutableArray<String> fileList = fileListForKey(aKey);
		if (!fileList.containsObject(aFile))
			fileList.addObject(aFile);
		notificationCenter.postNotification(PBFileAddedToProjectNotification, projectFileDict(this, aFile));
	}

	public void addFileToFrontKey(String aFile, String aKey) {
		NSMutableArray<String> fileList = fileListForKey(aKey);
		fileList.removeObject(aFile);
		fileList.insertObjectAtIndex(aFile, 0);
		notificationCenter.postNotification(PBFileAddedToProjectNotification, projectFileDict(this, aFile));
	}

	public void removeFileKey(String aFile, String aKey) {
		fileListForKey(aKey, false).removeObject(aFile);
		mutableLocalFiles().removeObjectForKey(aFile);
		notificationCenter.postNotification(PBFileRemovedFromProjectNotification, projectFileDict(this, aFile));
	}

	public String keyForFile(String aFile) {
		NSMutableDictionary<String, NSMutableArray<String>> nSMutableDictionary = filesTable();
		Enumeration<String> enumerator = nSMutableDictionary.keyEnumerator();
		while (enumerator.hasMoreElements()) {
			String key = enumerator.nextElement();
			NSArray array = (NSArray) nSMutableDictionary.objectForKey(key);
			if (array.containsObject(aFile))
				return key;
		}
		return null;
	}

	public NSDictionary localFiles() {
		String key = "LOCALIZABLE_FILES";
		Object localFiles = this._dict.objectForKey(key);
		if (localFiles == null) {
			localFiles = new NSMutableDictionary();
			this._dict.setObjectForKey(localFiles, key);
		}
		return (NSDictionary) localFiles;
	}

	public NSMutableDictionary mutableLocalFiles() {
		return (NSMutableDictionary) localFiles();
	}

	public boolean isLocalizable(String aFile) {
		return (mutableLocalFiles().objectForKey(aFile) != null);
	}

	public void makeFileLocalizable(String aFile, boolean loc) {
		NSMutableDictionary localFiles = mutableLocalFiles();
		if (loc) {
			localFiles.setObjectForKey(aFile, aFile);
		}
		else {
			localFiles.removeObjectForKey(aFile);
		}
	}

	public boolean rememberFileAttributes() {
		return this._rememberFileAttributes;
	}

	public void setRememberFileAttributes(boolean aValue) {
		this._rememberFileAttributes = aValue;
	}

	public void updateFileAttributesFromPath(String path) {
	}

	public boolean writeToPathSafely(String path, boolean safe) {
		return true;
	}

	public static _PBProject parse(String pathName) {
		String canFile = canonicalFileName(NSPathUtilities.stringByNormalizingExistingPath(pathName));
		if (canFile == null || canFile.length() == 0)
			return null;
		_PBProject project = (_PBProject) _canonicalFileToProject.objectForKey(canFile);
		synchronized (_projectParseLock) {
			try {
				if (project != null && !project.rememberFileAttributes()) {
					project._dict = readPBProjectFile(project._canonicalFile);
					project.setTouched(1024L);
				}
				if (project == null) {
					NSMutableDictionary fileContents = readPBProjectFile(canFile);
					if (fileContents != null && fileContents.count() > 0) {
						project = new _PBProject();
						project._dict = fileContents;
						project._touched = 0L;
						project._canonicalFile = canFile;
						project._superProject = null;
						_canonicalFileToProject.setObjectForKey(project, project._canonicalFile);
						project.setProjectDir(_NSStringUtilities.stringByDeletingLastComponent(canFile, File.separatorChar));
						project.setTouched(0L);
						project.setRememberFileAttributes(true);
						project.postParseProcess();
					}
				}
			}
			catch (Exception e) {
				if (NSLog.debugLoggingAllowedForLevel(3)) {
					NSLog.debug.appendln("Problem parsing " + pathName);
					NSLog._conditionallyLogPrivateException(e);
				}
			}
		}
		return project;
	}

	public NSArray parseSubprojects() {
		NSMutableArray<String> nSMutableArray = fileListForKey("SUBPROJECTS", false);
		if (nSMutableArray != null) {
			int count = nSMutableArray.count();
			for (int idx = 0; idx < count; idx++) {
				String proj = (String) nSMutableArray.objectAtIndex(idx);
				parseSubproject(proj);
			}
		}
		return (NSArray) this._subProjects;
	}

	public _PBProject parseSubproject(String subDir) {
		String aSubDir = String.valueOf(projectDir()) + File.separator + subDir;
		String subFile = String.valueOf(aSubDir) + File.separator + "PB.project";
		_PBProject subproject = parse(subFile);
		if (subproject == null) {
			if (NSLog.debugLoggingAllowedForLevel(1))
				NSLog.err.appendln("Unable to open project file: " + subFile);
			return null;
		}
		if (subproject._superProject == null)
			subproject._superProject = this;
		if (this._subProjects == null)
			this._subProjects = new NSMutableArray();
		if (!this._subProjects.containsObject(subproject))
			this._subProjects.addObject(subproject);
		return subproject;
	}

	public void postParseProcess() {
	}

	public static NSMutableDictionary readPBProjectFile(String path) throws MalformedURLException {
		return NSPropertyListSerialization.dictionaryWithPathURL((new File(path)).toURL()).mutableClone();
	}

	public String projectDir() {
		return this._projectDir;
	}

	public _PBProject nonAggregateRootProject() {
		return this;
	}

	public static String canonicalFileName(String standardizedPath) {
		return standardizedPath;
	}

	public String specifiedPathForFrameworkNamed(String fwName) {
		String fwPath = null;
		return fwPath;
	}

	public static String environmentVariableValueForString(String inString) {
		if (inString.equals("NEXT_ROOT"))
			return "D:\\Apple\\";
		if (inString.equals("LOCAL_LIBRARY_DIR"))
			return "Local\\Library";
		if (inString.equals("LIBRARY_DIR"))
			return "Library";
		return "*UNKNOWN*";
	}

	public static String stringByExpandingEnvironmentVariablesInString(String inString) {
		int length = inString.length();
		int varInd = inString.indexOf("$(");
		if (varInd < 0)
			return inString;
		String preFix = inString.substring(0, varInd);
		varInd += 2;
		int startInd = varInd;
		while (varInd < length) {
			if (inString.charAt(varInd) == ')') {
				String varName = inString.substring(startInd, varInd);
				String postFix = stringByExpandingEnvironmentVariablesInString(inString.substring(varInd + 1, length));
				return String.valueOf(preFix) + environmentVariableValueForString(varName) + postFix;
			}
			varInd++;
		}
		return inString;
	}

	static NSMutableArray<String> _defaultPaths = null;

	public static final int NSApplicationDirectory = 1;

	public static final int NSDemoApplicationDirectory = 2;

	public static final int NSDeveloperApplicationDirectory = 3;

	public static final int NSAdminApplicationDirectory = 4;

	public static final int NSLibraryDirectory = 5;

	public static final int NSDeveloperDirectory = 6;

	public static final int NSUserDirectory = 7;

	public static final int NSDocumentationDirectory = 8;

	public static final int NSAllApplicationsDirectory = 100;

	public static final int NSAllLibrariesDirectory = 101;

	public static final int NSUserDomainMask = 1;

	public static final int NSLocalDomainMask = 2;

	public static final int NSNetworkDomainMask = 4;

	public static final int NSSystemDomainMask = 8;

	public static final int NSAllDomainsMask = 65535;

	public static final int TOUCHED_NOTHING = 0;

	public static final int TOUCHED_EVERYTHING = 1;

	public static final int TOUCHED_PROJECT_NAME = 2;

	public static final int TOUCHED_LANGUAGE = 4;

	public static final int TOUCHED_PROJECT_TYPE = 8;

	public static final int TOUCHED_INSTALL_DIR = 16;

	public static final int TOUCHED_ICON_NAMES = 32;

	public static final int TOUCHED_FILES = 64;

	public static final int TOUCHED_MAINNIB = 128;

	public static final int TOUCHED_APPCLASS = 256;

	public static final int TOUCHED_TARGETS = 512;

	public static final int TOUCHED_PB_PROJECT = 1024;

	public static final int TOUCHED_SYST_EXT = 2048;

	public static final int TOUCHED_EXTENSION = 4096;

	public static final int TOUCHED_PATHS = 8192;

	public static final String PB_ClassesKey = "CLASSES";

	public static final String PB_HeadersKey = "H_FILES";

	public static final String PB_OtherSourcesKey = "OTHER_LINKED";

	public static final String PB_OtherResourcesKey = "OTHER_RESOURCES";

	public static final String PB_SupportingFilesKey = "OTHER_SOURCES";

	public static final String PB_ProjectHeadersKey = "PROJECT_HEADERS";

	public static final String PB_PublicHeadersKey = "PUBLIC_HEADERS";

	public static final String PB_PrecompiledHeadersKey = "PRECOMPILED_HEADERS";

	public static final String PB_SubprojectsKey = "SUBPROJECTS";

	public static final String PB_InterfacesKey = "INTERFACES";

	public static final String PB_ImagesKey = "IMAGES";

	public static final String PB_LocalizableFilesKey = "LOCALIZABLE_FILES";

	public static final String PB_FrameworksKey = "FRAMEWORKS";

	public static final String PB_OtherLibsKey = "OTHER_LIBS";

	public static final String PB_WOAppResourcesKey = "WOAPP_RESOURCES";

	public static final String PB_ComponentsKey = "WEBCOMPONENTS";

	public static final String PB_ResourcesKey = "RESOURCES";

	public static int _PBPort;

	NSMutableDictionary _dict;

	String _projectDir;

	String _languageDirCache;

	NSMutableArray _subProjects;

	NSMutableDictionary _userInfoDict;

	_PBProject _superProject;

	String _canonicalFile;

	long _fileModTime;

	long _touched;

	volatile boolean _rememberFileAttributes;

	static NSMutableArray _fwStack;

	public String deployedPathForFrameworkNamed(String fwName) {
		NSMutableArray<String> nSMutableArray = fileListForKey("FRAMEWORKSEARCH", false);
		if (nSMutableArray == null)
			return null;
		String fwPath = null;
		Iterator<String> pathIterator = nSMutableArray.iterator();
		String path = pathIterator.next();
		path = stringByExpandingEnvironmentVariablesInString(path);
		fwPath = String.valueOf(path) + File.separator + fwName;
		while (pathIterator.hasNext() && !(new File(fwPath)).exists())
			fwPath = null;
		if (fwPath == null) {
			if (_defaultPaths == null) {
				_defaultPaths = new NSMutableArray();
				while (pathIterator.hasNext()) {
					path = pathIterator.next();
					_defaultPaths.addObject(String.valueOf(path) + File.separator + "Frameworks");
					_defaultPaths.addObject(String.valueOf(path) + File.separator + "PrivateFrameworks");
				}
			}
			pathIterator = _defaultPaths.iterator();
			path = pathIterator.next();
			fwPath = String.valueOf(path) + File.separator + fwName;
			while (pathIterator.hasNext() && !(new File(fwPath)).exists())
				fwPath = null;
		}
		return fwPath;
	}

	public String searchedPathForFrameworkNamed(String fwName) {
		NSArray frameworks = NSBundle.frameworkBundles();
		Enumeration<NSBundle> enumerator = frameworks.objectEnumerator();
		while (enumerator.hasMoreElements()) {
			NSBundle bundle = enumerator.nextElement();
			String path = bundle.bundlePathURL().getPath();
			String lastcomponent = NSPathUtilities.lastPathComponent(path);
			if (lastcomponent.equals(fwName))
				return path;
		}
		return null;
	}

	public String pathForFrameworkNamed(String fwName) {
		String fwPath = specifiedPathForFrameworkNamed(fwName);
		if (fwPath == null)
			fwPath = searchedPathForFrameworkNamed(fwName);
		return fwPath;
	}

	public void _addFrameworkDependenciesToArray(NSMutableArray array) {
		Enumeration<_PBProject> enumerator = _allFrameworkDependencies().objectEnumerator();
		while (enumerator.hasMoreElements()) {
			_PBProject fwProj = enumerator.nextElement();
			if (!array.containsObject(fwProj))
				array.addObject(fwProj);
		}
	}

	private NSArray _allFrameworkDependencies() {
    NSMutableArray array = new NSMutableArray();
    if (!_fwStack.containsObject(this)) {
      NSMutableArray<String> nSMutableArray = fileListForKey("FRAMEWORKS", false);
      Enumeration<String> enumerator = fileListForKey("SUBPROJECTS", false).objectEnumerator();
      _fwStack.addObject(this);
      while (enumerator.hasMoreElements())
        String str = enumerator.nextElement(); 
      enumerator = nSMutableArray.objectEnumerator();
      while (enumerator.hasMoreElements()) {
        String filename = enumerator.nextElement();
        String fwPath = pathForFrameworkNamed(filename);
        String fwProjectPath = String.valueOf(fwPath) + File.separator + "PB.project";
        if (!NSPathUtilities.pathExtension(fwPath).equals("framework") && (new File(fwProjectPath)).exists()) {
          _PBProject fwProj = parse(fwProjectPath);
          if (array.containsObject(fwProj)) {
            fwProj._addFrameworkDependenciesToArray(array);
            array.addObject(fwProj);
          } 
        } 
      } 
    } 
    return (NSArray)array;
  }

	public NSArray pathsForProjectNamed(String projectName) {
		NSMutableArray<String> foundProjectsArray = new NSMutableArray();
		NSMutableArray<String> projectSearchPath = new NSMutableArray();
		NSMutableArray<String> frameworkEntries = new NSMutableArray();
		String rootProjectDir = rootProject().projectDir();
		String projectParentDir = _NSStringUtilities.stringByDeletingLastComponent(rootProjectDir, File.separatorChar);
		if (projectSearchPath == null)
			;
		if (!projectSearchPath.isEqualToArray((NSArray) this._lastNSProjectSearchPath)) {
			this._lastNSProjectSearchPath = projectSearchPath.mutableClone();
			this._resolvedNSProjectSearchPath = new NSMutableArray();
			for (Iterator<String> iterator1 = projectSearchPath.iterator(); iterator1.hasNext();) {
				String projectPath = iterator1.next();
				if (!(new File(projectPath)).isAbsolute())
					projectPath = String.valueOf(rootProjectDir) + File.separator + projectPath;
				projectPath = NSPathUtilities.stringByStandardizingPath(projectPath);
				this._resolvedNSProjectSearchPath.addObject(projectPath);
			}
			if (!this._resolvedNSProjectSearchPath.containsObject(projectParentDir))
				this._resolvedNSProjectSearchPath.addObject(projectParentDir);
		}
		for (Iterator<String> iterator = this._resolvedNSProjectSearchPath.iterator(); iterator.hasNext();) {
			String projectPath = iterator.next();
			String[] contents = (new File(projectPath)).list();
			for (int j = 0; contents != null && j < contents.length; j++) {
				String file = contents[j];
				String pbProjectDir = String.valueOf(projectPath) + File.separator + file;
				String pbPath = String.valueOf(pbProjectDir) + File.separator + "PB.project";
				if (NSPathUtilities.pathIsEqualToString(file, projectName) && (new File(pbPath)).exists())
					foundProjectsArray.addObject(pbProjectDir);
			}
			for (Iterator<String> frameworkIterator = frameworkEntries.iterator(); frameworkIterator.hasNext();) {
				String file = frameworkIterator.next();
				String pbProjectDir = String.valueOf(projectPath) + File.separator + file;
				String pbPath = String.valueOf(pbProjectDir) + File.separator + "PB.project";
				if ((new File(pbPath)).exists())
					foundProjectsArray.insertObjectAtIndex(pbProjectDir, 0);
			}
		}
		return (NSArray) foundProjectsArray;
	}

	public NSArray allFrameworkDependencies() {
		_fwStack = new NSMutableArray();
		NSArray fwDepends = _allFrameworkDependencies();
		_fwStack = null;
		return fwDepends;
	}

	private void _initializeUserInfoDict() {
		String userInfoFilePath = _currentUserCurrentOSPathForInfoFile();
		String OSName = _userInfoCurrentOSName();
		if ((new File(userInfoFilePath)).exists())
			try {
				String contents = _NSStringUtilities.stringFromFile(userInfoFilePath, null);
				NSDictionary plist = (NSDictionary) NSPropertyListSerialization.propertyListFromString(contents);
				this._userInfoDict = plist.mutableClone();
				NSDictionary origOSDict = (NSDictionary) this._userInfoDict.objectForKey(OSName);
				if (origOSDict != null) {
					NSMutableDictionary newOSDict = origOSDict.mutableClone();
					this._userInfoDict.takeValueForKey(newOSDict, OSName);
				}
			}
			catch (Exception e) {
				NSLog._conditionallyLogPrivateException(e);
				this._userInfoDict = null;
			}
		if (this._userInfoDict == null)
			this._userInfoDict = new NSMutableDictionary();
		if (this._userInfoDict.objectForKey(OSName) == null)
			this._userInfoDict.takeValueForKey(new NSMutableDictionary(), OSName);
	}

	private String _userInfoFileName() {
		String userName = System.getProperty("user.name");
		String fileName = "PBUserInfo_" + userName + ".plist";
		return fileName;
	}

	private String _userInfoDirectoryPath() {
		String projectDirectory = projectDir();
		return String.valueOf(projectDirectory) + File.separator + "PBUserInfo";
	}

	private String _currentUserCurrentOSPathForInfoFile() {
		return String.valueOf(_userInfoDirectoryPath()) + File.separator + _userInfoFileName();
	}

	private String _userInfoCurrentOSName() {
		String name = System.getProperty("os.name");
		if (name.equals("Rhapsody")) {
			name = "NSMACHOperatingSystem";
		}
		else if (name.equals("Windows NT")) {
			name = "NSWindowsNTOperatingSystem";
		}
		else {
			if (NSLog.debugLoggingAllowedForLevel(1))
				NSLog.err.appendln("_userInfoCurrentOSName unknown OS " + name);
			name = "SomeUnknownOperatingSystem";
		}
		return name;
	}

	private NSMutableDictionary _userInfoDict() {
		if (this._userInfoDict == null)
			_initializeUserInfoDict();
		return this._userInfoDict;
	}

	private NSMutableDictionary _userInfoDictForCurrentOS() {
		NSMutableDictionary userDict = _userInfoDict();
		return (NSMutableDictionary) userDict.objectForKey(_userInfoCurrentOSName());
	}

	public NSDictionary currentUserCurrentOSObjectForKey(Object aKey) {
		return (NSDictionary) _userInfoDictForCurrentOS().objectForKey(aKey);
	}

	static int addFileToPBBucket(String projectName, String fileName, String bucketName) {
		StringBuffer buffer = new StringBuffer(128);
		buffer.append("<AddFile>");
		buffer.append("<projectname>" + projectName + "</projectname>");
		buffer.append("<filepath>" + fileName + "</filepath>");
		buffer.append("<pbresource>" + bucketName + "</pbresource>");
		buffer.append("</AddFile>");
		String result = _sendXMLToPB(new String(buffer));
		if (result != null) {
			if (NSLog.debugLoggingAllowedForLevel(1))
				NSLog.err.appendln("Error: Could not add " + fileName + " to project " + projectName + " because " + result);
			return 1;
		}
		return 0;
	}

	public static String openFile(String filename, int line, String errorMessage) {
		StringBuffer buffer = new StringBuffer(128);
		buffer.append("<OpenFile><filename>");
		buffer.append(filename);
		buffer.append("</filename><linenumber>");
		buffer.append(line);
		buffer.append("</linenumber><message>");
		buffer.append(errorMessage);
		buffer.append("</message></OpenFile>");
		String pbString = new String(buffer);
		String result = _sendXMLToPB(pbString);
		return result;
	}

	private static String _sendXMLToPB(String command) {
		try {
			Socket pbSocket = new Socket(_PBHostname, _PBPort);
			OutputStream os = pbSocket.getOutputStream();
			os.write(command.getBytes());
			os.flush();
			pbSocket.close();
		}
		catch (Exception e) {
			if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 4L)) {
				NSLog.err.appendln("<_PBProject>: Exception occurred during _sendXMLToPB():" + e.getMessage());
				NSLog._conditionallyLogPrivateException(e);
			}
		}
		return null;
	}
}