package com.webobjects.appserver._private;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSMutableSet;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation._NSStringUtilities;
import com.webobjects.foundation._NSThreadsafeMutableDictionary;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;

public class WODeployedBundle {
  private final NSMutableDictionary _absolutePaths;
  
  private final NSMutableDictionary _pathURLs;
  
  protected final String _bundlePath;
  
  protected final boolean _isFramework;
  
  protected final String _projectName;
  
  private final _NSThreadsafeMutableDictionary _relativePaths;
  
  private boolean _resourcesHaveBeenPreloaded;
  
  private final NSMutableDictionary _URLs;
  
  protected final String _wrapperName;
  
  private final NSMutableSet _expectedLanguages;
  
  private boolean _isJar;
  
  private NSBundle _nsBundle;
  
  private static final NSMutableDictionary TheBundles = new NSMutableDictionary(NSBundle.frameworkBundles().count());
  
  private static final boolean _allowRapidTurnaround = NSPropertyListSerialization.booleanForString(NSProperties.getProperty("WOAllowRapidTurnaround"));
  
  private static final String _webServerResources = "/WebServerResources";
  
  private static final String _resources = "/Resources";
  
  private static final String _cvs = "/CVS";
  
  private static final String _versions = "/Versions";
  
  private static final String _contentsWebServerResourcesPathKey = "Contents/WebServerResources/";
  
  private static final String _webServerResourcesPathKey = "WebServerResources/";
  
  public WODeployedBundle(NSBundle nsb) {
    this(null, nsb);
  }
  
  public WODeployedBundle(String projectPath, NSBundle nsb) {
    if (nsb == null)
      throw new IllegalStateException("Cannot create WODeployedBundle without corresponding NSBundle!"); 
    this._nsBundle = nsb;
    this._isJar = this._nsBundle.isJar();
    String aProjectPath = (projectPath != null) ? projectPath : this._nsBundle.bundlePathURL().getPath();
    this._bundlePath = _initBundlePath(aProjectPath);
    if (NSBundle.mainBundle() == this._nsBundle || (!this._nsBundle.isFramework() && !this._nsBundle.name().endsWith(".woa"))) {
      this._wrapperName = String.valueOf(this._nsBundle.name()) + ".woa";
    } else if (this._nsBundle.isFramework() && !this._nsBundle.name().endsWith(".framework")) {
      this._wrapperName = String.valueOf(this._nsBundle.name()) + ".framework";
    } else {
      this._wrapperName = this._nsBundle.name();
    } 
    this._projectName = _initProjectName(this._wrapperName);
    if (NSBundle.mainBundle() == this._nsBundle) {
      this._isFramework = false;
    } else {
      this._isFramework = this._nsBundle.isFramework();
    } 
    this._absolutePaths = new NSMutableDictionary(128);
    this._pathURLs = new NSMutableDictionary(128);
    this._relativePaths = new _NSThreadsafeMutableDictionary(new NSMutableDictionary(128));
    this._URLs = new NSMutableDictionary();
    this._expectedLanguages = new NSMutableSet(16);
    this._resourcesHaveBeenPreloaded = false;
  }
  
  public static WODeployedBundle deployedBundle() {
    return bundleWithNSBundle(NSBundle.mainBundle());
  }
  
  private void _newAddToRelativePaths(File aFile, int pathIndex, int keyPathIndex) {
    String absolutePath = aFile.getAbsolutePath();
    String aFileName = NSPathUtilities._standardizedPath(absolutePath.substring(keyPathIndex));
    String aRelativePath = absolutePath.substring(pathIndex);
    Object localizedPathsTable = this._relativePaths.objectForKey(aFileName);
    if (localizedPathsTable != null) {
      if (localizedPathsTable instanceof NSMutableDictionary)
        if (((NSMutableDictionary)localizedPathsTable).objectForKey("Default") == null)
          ((NSMutableDictionary)localizedPathsTable).setObjectForKey(NSPathUtilities._standardizedPath(aRelativePath), "Default");  
    } else {
      this._relativePaths.setObjectForKey(NSPathUtilities._standardizedPath(aRelativePath), aFileName);
    } 
  }
  
  private void _jarAddToRelativePaths(String aFile) {
    int jarIndex = aFile.indexOf("!/");
    if (jarIndex != -1) {
      jarIndex += 2;
    } else {
      jarIndex = 0;
    } 
    String aRelativePath = aFile.substring(jarIndex);
    String aFileName = aRelativePath;
    if (aRelativePath.startsWith("WebServerResources")) {
      aFileName = aRelativePath.substring(19);
    } else if (aRelativePath.startsWith("Resources")) {
      aFileName = aRelativePath.substring(10);
    } else {
      aFileName = NSPathUtilities.lastPathComponent(aRelativePath);
    } 
    Object localizedPathsTable = this._relativePaths.objectForKey(aFileName);
    if (localizedPathsTable != null) {
      if (localizedPathsTable instanceof NSMutableDictionary)
        if (((NSMutableDictionary)localizedPathsTable).objectForKey("Default") == null)
          ((NSMutableDictionary)localizedPathsTable).setObjectForKey(aRelativePath, "Default");  
    } else {
      this._relativePaths.setObjectForKey(aRelativePath, aFileName);
    } 
  }
  
  private void _newPreloadAllResourcesInLanguageDirectory(File aDirectory, int pathIndex, int keyPathIndex) {
    String language = NSPathUtilities.lastPathComponent(NSPathUtilities.stringByDeletingPathExtension(aDirectory.getName()));
    int languagePathIndex = keyPathIndex + language.length() + 7;
    NSMutableArray resources = new NSMutableArray(64);
    _newPreloadAllResourcesInSubDirectory(aDirectory, pathIndex, keyPathIndex, resources);
    int count = resources.count();
    for (int i = 0; i < count; i++) {
      String aResourcePath = (String)resources.objectAtIndex(i);
      String aResourceKey = NSPathUtilities._standardizedPath(aResourcePath).substring(languagePathIndex);
      Object localizedPathsTable = this._relativePaths.objectForKey(aResourceKey);
      if (localizedPathsTable != null) {
        if (localizedPathsTable instanceof String) {
          Object defaultRelativePath = localizedPathsTable;
          localizedPathsTable = new NSMutableDictionary();
          ((NSMutableDictionary)localizedPathsTable).setObjectForKey(defaultRelativePath, "Default");
          this._relativePaths.setObjectForKey(localizedPathsTable, aResourceKey);
        } 
      } else {
        localizedPathsTable = new NSMutableDictionary();
        this._relativePaths.setObjectForKey(localizedPathsTable, aResourceKey);
      } 
      if (((NSMutableDictionary)localizedPathsTable).objectForKey(language) == null) {
        String relativePath = aResourcePath.substring(pathIndex);
        ((NSMutableDictionary)localizedPathsTable).setObjectForKey(relativePath, language);
      } 
    } 
  }
  
  private void _jarPreloadAllResourcesInLanguageDirectory(String aDirectory) {
    String language = NSPathUtilities.lastPathComponent(NSPathUtilities.stringByDeletingPathExtension(aDirectory));
    NSMutableArray<String> resources = new NSMutableArray(64);
    _jarPreloadAllResourcesInSubDirectory(aDirectory, resources);
    int count = resources.count();
    for (int i = 0; i < count; i++) {
      String aResourcePath = (String)resources.objectAtIndex(i);
      String aResourceKey = NSPathUtilities.lastPathComponent(aResourcePath);
      Object localizedPathsTable = this._relativePaths.objectForKey(aResourceKey);
      if (localizedPathsTable != null) {
        if (localizedPathsTable instanceof String) {
          Object defaultRelativePath = localizedPathsTable;
          localizedPathsTable = new NSMutableDictionary();
          ((NSMutableDictionary)localizedPathsTable).setObjectForKey(defaultRelativePath, "Default");
          this._relativePaths.setObjectForKey(localizedPathsTable, aResourceKey);
        } 
      } else {
        localizedPathsTable = new NSMutableDictionary();
        this._relativePaths.setObjectForKey(localizedPathsTable, aResourceKey);
      } 
      if (((NSMutableDictionary)localizedPathsTable).objectForKey(language) == null) {
        int jarIndex = aResourcePath.indexOf("!/");
        if (jarIndex != -1) {
          jarIndex += 2;
        } else {
          jarIndex = 0;
        } 
        String relativePath = aResourcePath.substring(jarIndex);
        ((NSMutableDictionary)localizedPathsTable).setObjectForKey(relativePath, language);
      } 
    } 
  }
  
  protected void _newPreloadAllResourcesInSubDirectory(File aDir, int pathIndex, int keyPathIndex, NSMutableArray array) {
    String[] resources = aDir.list();
    File aFile = null;
    if (resources != null) {
      int length = resources.length;
      for (int i = 0; i < length; i++) {
        String aFileName = resources[i];
        aFile = new File(aDir, aFileName);
        if (array == null) {
          _newAddToRelativePaths(aFile, pathIndex, keyPathIndex);
        } else {
          array.addObject(aFile.getAbsolutePath());
        } 
        if (aFile.isDirectory() && 
          !aFileName.endsWith(".wo") && !aFileName.equals("CVS"))
          _newPreloadAllResourcesInSubDirectory(aFile, pathIndex, keyPathIndex, array); 
      } 
    } 
  }
  
  protected void _jarPreloadAllResourcesInSubDirectory(String aDirectory, NSMutableArray<String> array) {
    NSMutableArray<String> dirNames = new NSMutableArray();
    NSMutableArray<String> fileNames = new NSMutableArray();
    this._nsBundle._simplePathsInDirectoryInJar(aDirectory, "", dirNames, "", fileNames);
    for (Enumeration<String> fileE = fileNames.objectEnumerator(); fileE.hasMoreElements(); ) {
      String fileName = fileE.nextElement();
      if (array == null) {
        _jarAddToRelativePaths(_NSStringUtilities.concat(aDirectory, "/", fileName));
        continue;
      } 
      array.addObject(_NSStringUtilities.concat(aDirectory, "/", fileName));
    } 
    for (Enumeration<String> dirE = dirNames.objectEnumerator(); dirE.hasMoreElements(); ) {
      String dirName = dirE.nextElement();
      if (array == null) {
        _jarAddToRelativePaths(_NSStringUtilities.concat(aDirectory, "/", dirName));
      } else {
        array.addObject(_NSStringUtilities.concat(aDirectory, "/", dirName));
      } 
      if (!dirName.endsWith(".wo") && !dirName.equals("CVS"))
        _jarPreloadAllResourcesInSubDirectory(_NSStringUtilities.concat(aDirectory, "/", dirName), array); 
    } 
  }
  
  private void _newPreloadAllResourcesInTopDirectory(String aDirectory) {
    File aDir = new File(bundlePath(), aDirectory);
    String[] resources = aDir.list();
    if (resources != null) {
      File aFile = null;
      int length = resources.length;
      int keyPathIndex = 0;
      int pathIndex = 0;
      keyPathIndex = aDir.getAbsolutePath().length() + 1;
      pathIndex = aDir.getAbsolutePath().lastIndexOf(aDirectory);
      if (aDirectory.equals("."))
        pathIndex += 2; 
      for (int i = 0; i < length; i++) {
        String aFileName = resources[i];
        aFile = new File(aDir, aFileName);
        String aFilePath = aFile.getAbsolutePath();
        if (aFile.isDirectory()) {
          if (!aFilePath.endsWith("/WebServerResources") && !aFilePath.endsWith("/Resources") && !aFilePath.endsWith(".subproj") && !aFilePath.endsWith("/Versions") && !aFilePath.endsWith("/CVS"))
            if (aFilePath.endsWith(".lproj")) {
              String language = NSPathUtilities.lastPathComponent(aFilePath);
              language = language.substring(0, language.length() - ".lproj".length());
              addToExpectedLanguages(language);
              _newPreloadAllResourcesInLanguageDirectory(aFile, pathIndex, keyPathIndex);
            } else {
              if (aFilePath.endsWith(".wo") || aFilePath.endsWith(".eomodeld"))
                _newAddToRelativePaths(aFile, pathIndex, keyPathIndex); 
              if (NSPathUtilities.pathExtension(aFilePath).length() > 0)
                _newAddToRelativePaths(aFile, pathIndex, keyPathIndex); 
              _newPreloadAllResourcesInSubDirectory(aFile, pathIndex, keyPathIndex, null);
            }  
        } else {
          _newAddToRelativePaths(aFile, pathIndex, keyPathIndex);
        } 
      } 
    } 
  }
  
  private void _jarPreloadAllResourcesInTopDirectory(String aDirectory) {
    NSMutableArray<String> dirNames = new NSMutableArray();
    NSMutableArray<String> fileNames = new NSMutableArray();
    this._nsBundle._simplePathsInDirectoryInJar(aDirectory, "", dirNames, "", fileNames);
    String dirPrefix = aDirectory.equals(".") ? "" : aDirectory.concat("/");
    for (Enumeration<String> fileE = fileNames.objectEnumerator(); fileE.hasMoreElements(); ) {
      String fileName = fileE.nextElement();
      _jarAddToRelativePaths(dirPrefix.concat(fileName));
    } 
    for (Enumeration<String> dirE = dirNames.objectEnumerator(); dirE.hasMoreElements(); ) {
      String dirName = dirE.nextElement();
      String aFilePath = _NSStringUtilities.concat(aDirectory, "/", dirName);
      if (!aFilePath.endsWith("/WebServerResources") && !aFilePath.endsWith("/Resources") && !aFilePath.endsWith(".subproj") && !aFilePath.endsWith("/Versions") && !aFilePath.endsWith("/CVS") && 
        !aFilePath.endsWith("META-INF")) {
        if (aFilePath.endsWith(".lproj")) {
          String language = dirName;
          language = language.substring(0, language.length() - ".lproj".length());
          addToExpectedLanguages(language);
          _jarPreloadAllResourcesInLanguageDirectory(dirPrefix.concat(dirName));
          continue;
        } 
        if (aFilePath.endsWith(".eomodeld") || aFilePath.endsWith(".wo"))
          _jarAddToRelativePaths(dirPrefix.concat(dirName)); 
        if (NSPathUtilities.pathExtension(dirName).length() > 0)
          _jarAddToRelativePaths(dirPrefix.concat(dirName)); 
        _jarPreloadAllResourcesInSubDirectory(dirPrefix.concat(dirName), null);
      } 
    } 
  }
  
  private void addToExpectedLanguages(String aLanguage) {
    this._expectedLanguages.addObject(aLanguage);
  }
  
  private NSArray expectedLanguages() {
    return this._expectedLanguages.allObjects();
  }
  
  private boolean isLocalized() {
    return (this._expectedLanguages.count() > 0);
  }
  
  private synchronized void _preloadAllResourcesIfNecessary() {
    if (!this._resourcesHaveBeenPreloaded) {
      WOProjectBundle projectBundle = projectBundle();
      if (!_allowRapidTurnaround || projectBundle == null || projectBundle._woProject() == null || projectBundle._woProject().ideProject() == null || projectBundle._woProject().ideProject().shouldPreloadResources())
        if (!loadResourceIndex())
          if (this._isJar) {
            _jarPreloadAllResourcesInTopDirectory("WebServerResources");
            _jarPreloadAllResourcesInTopDirectory("Resources");
            _jarPreloadAllResourcesInTopDirectory(".");
          } else {
            _newPreloadAllResourcesInTopDirectory(_NSStringUtilities.concat("Contents", File.separator, "WebServerResources"));
            _newPreloadAllResourcesInTopDirectory(_NSStringUtilities.concat("Contents", File.separator, "Resources"));
            _newPreloadAllResourcesInTopDirectory("Contents");
            _newPreloadAllResourcesInTopDirectory("WebServerResources");
            _newPreloadAllResourcesInTopDirectory("Resources");
            _newPreloadAllResourcesInTopDirectory(".");
          }   
      if (isLocalized())
        WOApplication.application()._addToExpectedLanguages(expectedLanguages()); 
      this._resourcesHaveBeenPreloaded = true;
      if (NSLog.debugLoggingAllowedForLevelAndGroups(3, 32L))
        NSLog.debug.appendln(String.valueOf(this._wrapperName) + " " + this._relativePaths.delegate()); 
    } 
  }
  
  private String resourceIndexPath() {
    return NSPathUtilities.stringByAppendingPathComponent(nsBundle().resourcePath(), "WODeployedBundle-index.plist");
  }
  
  public void writeResourceIndex() {
    _preloadAllResourcesIfNecessary();
    _NSThreadsafeMutableDictionary tempRelativePaths = this._relativePaths;
    tempRelativePaths.acquireReadLock();
    try {
      NSMutableDictionary dictToSave = new NSMutableDictionary(2);
      dictToSave.setObjectForKey(tempRelativePaths.delegate(), "relativePaths");
      if (isLocalized())
        dictToSave.setObjectForKey(expectedLanguages(), "expectedLanguages"); 
      String plist = NSPropertyListSerialization.stringFromPropertyList(dictToSave);
      NSData data = new NSData(plist, "UTF-8");
      String outputPath = resourceIndexPath();
      OutputStream dataOutputStream = new BufferedOutputStream(new FileOutputStream(outputPath));
      try {
        data.writeToStream(dataOutputStream);
      } finally {
        dataOutputStream.close();
      } 
      NSLog.debug.appendln("writeResourceIndex() wrote to path: " + outputPath);
    } catch (IOException e) {
      NSLog.err.appendln("writeResourceIndex() error writing index file for " + this._wrapperName + " to path: " + resourceIndexPath() + ": " + e);
    } finally {
      tempRelativePaths.releaseReadLock();
    } 
  }
  
  private boolean loadResourceIndex() {
    boolean shouldReadIndex = Boolean.valueOf(System.getProperty("WODeployedBundleShouldReadIndexFile", "true")).booleanValue();
    if (!shouldReadIndex)
      return false; 
    try {
      File file = new File(resourceIndexPath());
      if (file.exists()) {
        NSData data;
        FileInputStream fis = new FileInputStream(file);
        try {
          data = new NSData(fis, -1);
        } finally {
          fis.close();
        } 
        if (data != null && data.length() > 0) {
          NSDictionary indexDict = (NSDictionary)NSPropertyListSerialization.propertyListFromData(data, "UTF-8");
          if (indexDict != null) {
            NSDictionary relativePaths = (NSDictionary)indexDict.objectForKey("relativePaths");
            _NSThreadsafeMutableDictionary tempRelativePaths = this._relativePaths;
            tempRelativePaths.acquireReadLock();
            try {
              tempRelativePaths.delegate().addEntriesFromDictionary(relativePaths);
            } finally {
              tempRelativePaths.releaseReadLock();
            } 
            NSArray expectedLanguages = (NSArray)indexDict.objectForKey("expectedLanguages");
            if (expectedLanguages != null)
              this._expectedLanguages.addObjectsFromArray(expectedLanguages); 
            return true;
          } 
        } 
      } 
    } catch (IOException e) {
      NSLog.err.appendln("loadResourceIndex(): " + e);
    } 
    return false;
  }
  
  public String relativePathForResource(String aResourceName, NSArray aLanguagesList) {
    Object aRelativePath = null;
    if (aResourceName != null) {
      _preloadAllResourcesIfNecessary();
      aRelativePath = this._relativePaths.objectForKey(aResourceName);
      if (aRelativePath != null) {
        if (aRelativePath instanceof NSDictionary) {
          NSMutableDictionary localizedPathTable = (NSMutableDictionary)aRelativePath;
          if (aLanguagesList != null) {
            int count = aLanguagesList.count();
            for (int i = 0; i < count; i++) {
              aRelativePath = localizedPathTable.objectForKey(aLanguagesList.objectAtIndex(i));
              if (aRelativePath != null)
                return (String)aRelativePath; 
            } 
          } 
          aRelativePath = localizedPathTable.objectForKey("Default");
        } 
      } else {
        String standardizedPath = NSPathUtilities._standardizedPath(aResourceName);
        if (!standardizedPath.equals(aResourceName)) {
          aRelativePath = relativePathForResource(standardizedPath, aLanguagesList);
          if (aRelativePath != null && ((String)aRelativePath).length() > 0)
            this._relativePaths.setObjectForKey(this._relativePaths.objectForKey(standardizedPath), aResourceName); 
        } 
      } 
    } 
    return (String)aRelativePath;
  }
  
  public String relativePathForResource(String aResourceName, String aLanguageString) {
    _preloadAllResourcesIfNecessary();
    Object aRelativePath = this._relativePaths.objectForKey(aResourceName);
    if (aRelativePath instanceof NSMutableDictionary) {
      NSMutableDictionary localizedPathTable = (NSMutableDictionary)aRelativePath;
      if (aLanguageString != null) {
        aRelativePath = localizedPathTable.objectForKey(aLanguageString);
      } else {
        aRelativePath = localizedPathTable.objectForKey("Default");
      } 
    } 
    return (String)aRelativePath;
  }
  
  private String _cachedAbsolutePath(String aRelativePath) {
    String aPath = null;
    if (aRelativePath != null) {
      aPath = (String)this._absolutePaths.objectForKey(aRelativePath);
      if (aPath == null) {
        if (this._isJar) {
          aPath = this._nsBundle._bundleURLPrefix().concat(aRelativePath);
        } else {
          aPath = NSPathUtilities.stringByAppendingPathComponent(bundlePath(), aRelativePath);
          aPath = NSPathUtilities.stringByNormalizingExistingPath(aPath);
        } 
        this._absolutePaths.setObjectForKey(aPath, aRelativePath);
      } 
    } 
    return aPath;
  }
  
  private URL _cachedPathURL(String aRelativePath) {
    URL anURL = null;
    if (aRelativePath != null) {
      anURL = (URL)this._pathURLs.objectForKey(aRelativePath);
      if (anURL == null) {
        anURL = this._nsBundle._urlForRelativePath(aRelativePath);
        if (anURL != null)
          this._pathURLs.setObjectForKey(anURL, aRelativePath); 
      } 
    } 
    return anURL;
  }
  
  private String _absolutePathForRelativePath(String aRelativePath) {
    String anAbsolutePath = null;
    synchronized (this._absolutePaths) {
      anAbsolutePath = _cachedAbsolutePath(aRelativePath);
    } 
    return anAbsolutePath;
  }
  
  private URL _pathURLForRelativePath(String aRelativePath) {
    URL anURL = null;
    synchronized (this._pathURLs) {
      anURL = _cachedPathURL(aRelativePath);
    } 
    return anURL;
  }
  
  public String _absolutePathForResource(String aResourceName, NSArray aLanguagesList) {
    String aRelativePath = relativePathForResource(aResourceName, aLanguagesList);
    return _absolutePathForRelativePath(aRelativePath);
  }
  
  public String _absolutePathForResource(String aResourceName, String aLanguage, boolean refresh) {
    return _absolutePathForResource(aResourceName, aLanguage);
  }
  
  public String _absolutePathForResource(String aResourceName, String aLanguage) {
    String aRelativePath = relativePathForResource(aResourceName, aLanguage);
    return _absolutePathForRelativePath(aRelativePath);
  }
  
  public InputStream inputStreamForResourceNamed(String aResourceName, NSArray aLanguagesList) {
    InputStream is = null;
    URL url = pathURLForResourceNamed(aResourceName, aLanguagesList);
    if (url != null)
      try {
        is = url.openStream();
      } catch (IOException iOException) {} 
    return is;
  }
  
  public InputStream inputStreamForResourceNamed(String aResourceName, String aLanguage) {
    InputStream is = null;
    URL url = pathURLForResourceNamed(aResourceName, aLanguage);
    if (url != null)
      try {
        is = url.openStream();
      } catch (IOException iOException) {} 
    return is;
  }
  
  public URL pathURLForResourceNamed(String aResourceName, String aLanguageString, boolean refreshProjectOnCacheMiss) {
    return pathURLForResourceNamed(aResourceName, aLanguageString);
  }
  
  public URL pathURLForResourceNamed(String aResourceName, String aLanguageString) {
    String aRelativePath = relativePathForResource(aResourceName, aLanguageString);
    return _pathURLForRelativePath(aRelativePath);
  }
  
  public URL pathURLForResourceNamed(String aResourceName, NSArray aLanguagesList) {
    String aRelativePath = relativePathForResource(aResourceName, aLanguagesList);
    return _pathURLForRelativePath(aRelativePath);
  }
  
  private String _cachedURL(String aRelativePath) {
    String aURL = null;
    if (aRelativePath != null) {
      aURL = (String)this._URLs.objectForKey(aRelativePath);
      if (aURL == null) {
        String aBaseURL = null;
        if (isFramework()) {
          aBaseURL = WOApplication.application().frameworksBaseURL();
        } else {
          aBaseURL = WOApplication.application().applicationBaseURL();
        } 
        String aWrapperName = wrapperName();
        if (aBaseURL != null && aWrapperName != null) {
          aURL = _NSStringUtilities.concat(aBaseURL, File.separator, aWrapperName, File.separator, aRelativePath);
          aURL = NSPathUtilities._standardizedPath(aURL);
          this._URLs.setObjectForKey(aURL, aRelativePath);
        } 
      } 
    } 
    return aURL;
  }
  
  public String urlForResource(String aResourceName, NSArray aLanguagesList) {
    String aRelativePath = relativePathForResource(aResourceName, aLanguagesList);
    String aURL = null;
    synchronized (this._URLs) {
      aURL = _cachedURL(aRelativePath);
    } 
    return aURL;
  }
  
  public String bundlePath() {
    return this._bundlePath;
  }
  
  protected String _initBundlePath(String aPath) {
    if (!this._isJar)
      return NSPathUtilities.stringByNormalizingExistingPath(aPath); 
    return aPath;
  }
  
  protected URL _initBundleURL(URL anURL) {
    return anURL;
  }
  
  protected String _initProjectName(String aProjectName) {
    return NSPathUtilities.stringByDeletingPathExtension(aProjectName);
  }
  
  public String projectName() {
    return this._projectName;
  }
  
  public String wrapperName() {
    return this._wrapperName;
  }
  
  public boolean isFramework() {
    return this._isFramework;
  }
  
  public boolean isAggregate() {
    return false;
  }
  
  public boolean isJar() {
    return this._isJar;
  }
  
  public NSBundle nsBundle() {
    return this._nsBundle;
  }
  
  public WOProjectBundle projectBundle() {
    WOProjectBundle aProjectBundle = null;
    if (_allowRapidTurnaround) {
      String aProjectName = projectName();
      boolean isFramework = isFramework();
      aProjectBundle = WOProjectBundle.projectBundleForProject(aProjectName, isFramework);
    } 
    return aProjectBundle;
  }
  
  public static synchronized WODeployedBundle bundleWithPath(String aPath) {
    NSBundle bundle = NSBundle.bundleWithPath(aPath);
    if (bundle == null)
      return null; 
    return bundleWithNSBundle(bundle);
  }
  
  public static synchronized WODeployedBundle bundleWithNSBundle(NSBundle nsBundle) {
    Object aBundle = TheBundles.objectForKey(nsBundle);
    if (aBundle == null) {
      WODeployedBundle deployedBundle = new WODeployedBundle(nsBundle);
      if (_allowRapidTurnaround) {
        String bundlePath = nsBundle.bundlePathURL().getPath();
        try {
          if (WOProjectBundle._isProjectBundlePath(bundlePath)) {
            aBundle = new WOProjectBundle(bundlePath, deployedBundle);
          } else {
            aBundle = deployedBundle;
          } 
        } catch (Exception e) {
          if (NSLog.debugLoggingAllowedForLevel(1)) {
            NSLog.debug.appendln("<WOProjectBundle>: Warning - Unable to find project at path " + nsBundle.bundlePathURL().getPath() + " - Ignoring project.");
            NSLog.debug.appendln(e);
          } 
          aBundle = deployedBundle;
        } 
      } else {
        aBundle = deployedBundle;
      } 
      TheBundles.setObjectForKey(aBundle, nsBundle);
    } 
    return (WODeployedBundle)aBundle;
  }
  
  private boolean _isWebServerResource(String resourceName) {
    _preloadAllResourcesIfNecessary();
    Object localizedPathsTable = this._relativePaths.objectForKey(resourceName);
    if (localizedPathsTable != null)
      if (localizedPathsTable instanceof String) {
        if (_isAnyWebServerResource((String)localizedPathsTable))
          return true; 
      } else {
        NSArray localizedPaths = ((NSDictionary)localizedPathsTable).allValues();
        int count = localizedPaths.count();
        for (int i = 0; i < count; i++) {
          if (_isAnyWebServerResource((String)localizedPaths.objectAtIndex(i)))
            return true; 
        } 
      }  
    return false;
  }
  
  private boolean _isAnyWebServerResource(String pathName) {
    return !(!pathName.startsWith("WebServerResources/") && !pathName.startsWith("Contents/WebServerResources/"));
  }
  
  public NSArray<String> _allResourceNamesWithExtension(String extension, boolean webServerResourcesOnly) {
    if (extension == null)
      throw new IllegalArgumentException("Extension needs to be specified in _allResourceNamesWithExtension"); 
    _preloadAllResourcesIfNecessary();
    NSMutableArray<String> resourceNames = new NSMutableArray(16);
    NSArray<String> allResourceNames = this._relativePaths.allKeys();
    int numberOfResourceNames = allResourceNames.count();
    for (int i = 0; i < numberOfResourceNames; i++) {
      String resourceName = (String)allResourceNames.objectAtIndex(i);
      String resourceNameExtension = NSPathUtilities.pathExtension(resourceName);
      if (resourceNameExtension != null && extension.equals(resourceNameExtension) && (
        !webServerResourcesOnly || _isWebServerResource(resourceName)))
        resourceNames.addObject(resourceName); 
    } 
    return (NSArray<String>)resourceNames;
  }
  
  public static synchronized WODeployedBundle deployedBundleForFrameworkNamed(String aFrameworkName) {
    WODeployedBundle aBundle = null;
    NSArray bundleArray = TheBundles.allValues();
    int baCount = TheBundles.count();
    NSBundle nsBundle = NSBundle.bundleForName(aFrameworkName);
    if (nsBundle == null)
      nsBundle = NSBundle.bundleWithPath(aFrameworkName); 
    if (nsBundle != null)
      for (int i = 0; i < baCount; i++) {
        WODeployedBundle aFrameworkBundle = (WODeployedBundle)bundleArray.objectAtIndex(i);
        if (nsBundle.equals(aFrameworkBundle.nsBundle())) {
          aBundle = aFrameworkBundle;
          WODeployedBundle dBundle = aBundle.projectBundle();
          if (dBundle != null)
            aBundle = dBundle; 
          break;
        } 
      }  
    return aBundle;
  }
  
  public String toString() {
    return "<" + getClass().getName() + ": bundlePath='" + this._bundlePath + "'>";
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERWebObjects/1.0/ERWebObjects-1.0.jar!/com/webobjects/appserver/_private/WODeployedBundle.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */