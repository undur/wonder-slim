package com.webobjects.appserver._private;

import com.webobjects._ideservices._IDEProject;
import com.webobjects._ideservices._IDEProjectPBX;
import com.webobjects._ideservices._WOProject;
import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;
import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.util.Enumeration;

public class WOProjectBundle extends WODeployedBundle {
  private volatile _WOProject _woProject;
  
  private final String _projectPath;
  
  private final int _projectPathLength;
  
  private final WODeployedBundle _associatedDeployedBundle;
  
  private static final NSMutableDictionary TheProjectBundles = new NSMutableDictionary(NSBundle.frameworkBundles().count());
  
  private static volatile boolean _refreshProjectBundlesOnCacheMiss = NSPropertyListSerialization.booleanForString(NSProperties.getProperty("WOMissingResourceSearchEnabled"));
  
  public WOProjectBundle(String aProjectPath, WODeployedBundle aDeployedBundle) {
    super(aProjectPath, aDeployedBundle.nsBundle());
    String projectPath = this._woProject.ideProject().ideProjectPath();
    if (projectPath.endsWith("PB.project")) {
      projectPath = NSPathUtilities.stringByDeletingLastPathComponent(projectPath);
    } else if (projectPath.endsWith("project.pbxproj")) {
      projectPath = NSPathUtilities.stringByDeletingLastPathComponent(projectPath);
      if (projectPath.endsWith(".pbproj"))
        projectPath = NSPathUtilities.stringByDeletingLastPathComponent(projectPath); 
      if (projectPath.endsWith(".xcodeproj"))
        projectPath = NSPathUtilities.stringByDeletingLastPathComponent(projectPath); 
      if (projectPath.endsWith(".xcode"))
        projectPath = NSPathUtilities.stringByDeletingLastPathComponent(projectPath); 
    } 
    this._projectPath = projectPath;
    this._projectPathLength = projectPath.length();
    if (this._projectName == null && 
      NSLog._debugLoggingAllowedForLevelAndGroups(3, 36L))
      NSLog.debug.appendln("<" + getClass().getName() + ">: Warning - Unable to locate PROJECTNAME in '" + aDeployedBundle.bundlePath() + "'"); 
    this._associatedDeployedBundle = aDeployedBundle;
  }
  
  public String toString() {
    return "<" + getClass().getName() + ": projectName='" + this._projectName + "'; bundlePath='" + this._bundlePath + "'; projectPath='" + this._projectPath + "'>";
  }
  
  public _WOProject _woProject() {
    return this._woProject;
  }
  
  protected String _initBundlePath(String aPath) {
    String path = null;
    _WOProject project = _WOProject.projectAtPath(aPath);
    if (project != null) {
      path = project.bundlePath();
      this._woProject = project;
    } 
    if (path == null)
      path = NSPathUtilities.stringByNormalizingExistingPath(aPath); 
    return path;
  }
  
  protected String _initProjectName(String aProjectName) {
    if (_woProject() != null)
      return _woProject().ideProject().projectName(); 
    return null;
  }
  
  public String projectName() {
    return this._projectName;
  }
  
  public String projectPath() {
    return this._projectPath;
  }
  
  public WOProjectBundle projectBundle() {
    return this;
  }
  
  public URL pathURLForResourceNamed(String aResourceName, String aLanguageString, boolean refreshProjectOnCacheMiss) {
    String absolutePath = _absolutePathForResource(aResourceName, aLanguageString, refreshProjectOnCacheMiss);
    return NSPathUtilities._URLWithPath(absolutePath);
  }
  
  public URL pathURLForResourceNamed(String aResourceName, String aLanguageString) {
    String absolutePath = _absolutePathForResource(aResourceName, aLanguageString);
    return NSPathUtilities._URLWithPath(absolutePath);
  }
  
  public URL pathURLForResourceNamed(String aResourceName, NSArray aLanguagesList) {
    String absolutePath = _absolutePathForResource(aResourceName, aLanguagesList);
    return NSPathUtilities._URLWithPath(absolutePath);
  }
  
  public String _absolutePathForResource(String aResourceName, NSArray aLanguagesList) {
    String anAbsolutePath = null;
    if (aLanguagesList != null && aLanguagesList.count() > 0) {
      int count = aLanguagesList.count();
      boolean refreshWOProjectOnCacheMiss = _refreshProjectBundlesOnCacheMiss;
      for (int i = 0; i < count && anAbsolutePath == null; i++) {
        String language = (String)aLanguagesList.objectAtIndex(i);
        anAbsolutePath = _absolutePathForResource(aResourceName, language, refreshWOProjectOnCacheMiss);
        refreshWOProjectOnCacheMiss = false;
      } 
    } else {
      anAbsolutePath = this._woProject.pathForResourceNamed(aResourceName, _refreshProjectBundlesOnCacheMiss);
      if (anAbsolutePath == null)
        anAbsolutePath = this._associatedDeployedBundle._absolutePathForResource(aResourceName, aLanguagesList); 
    } 
    return anAbsolutePath;
  }
  
  public String _absolutePathForResource(String aResourceName, String aLanguageString) {
    return _absolutePathForResource(aResourceName, aLanguageString, true);
  }
  
  public String _absolutePathForResource(String aResourceName, String aLanguageString, boolean refreshWOProjectOnCacheMiss) {
    String anAbsolutePath = null;
    _WOProject woProject = this._woProject;
    anAbsolutePath = woProject.pathForResourceNamed(aResourceName, aLanguageString, refreshWOProjectOnCacheMiss);
    if (anAbsolutePath == null)
      anAbsolutePath = this._associatedDeployedBundle._absolutePathForResource(aResourceName, aLanguageString); 
    return anAbsolutePath;
  }
  
  public String relativePathForResource(String aResourceName, String aLanguageString) {
    String absolutePath = _absolutePathForResource(aResourceName, aLanguageString);
    String relativePath = null;
    if (absolutePath == null || absolutePath.length() < this._projectPathLength || !absolutePath.startsWith(this._projectPath)) {
      relativePath = absolutePath;
    } else {
      relativePath = absolutePath.substring(this._projectPathLength);
    } 
    return relativePath;
  }
  
  public String relativePathForResource(String aResourceName, NSArray aLanguagesList) {
    String absolutePath = _absolutePathForResource(aResourceName, aLanguagesList);
    String relativePath = null;
    if (absolutePath == null || absolutePath.length() < this._projectPathLength || !absolutePath.startsWith(this._projectPath)) {
      relativePath = absolutePath;
    } else {
      relativePath = absolutePath.substring(this._projectPathLength);
    } 
    return relativePath;
  }
  
  protected static boolean _isProjectBundlePath(String aProjectDirectoryPath) {
    _IDEProject ideProject = _WOProject.ideProjectAtPath(aProjectDirectoryPath);
    return (ideProject != null);
  }
  
  static class PBWOProjectAndDirFilter implements FilenameFilter {
    public boolean accept(File aDir, String aName) {
      return !(!aName.equals("PB.project") && !(new File(String.valueOf(aDir.getPath()) + aName)).isDirectory());
    }
  }
  
  private static PBWOProjectAndDirFilter theProjectsInDirectoryFilter = new PBWOProjectAndDirFilter();
  
  public static synchronized WODeployedBundle bundleWithPath(String aPath) {
    WODeployedBundle aBundle = WODeployedBundle.bundleWithPath(aPath);
    if (aBundle == null) {
      WOProjectBundle pBundle = (WOProjectBundle)TheProjectBundles.objectForKey(aPath);
      if (pBundle == null) {
        _IDEProject ideProject = _WOProject.ideProjectAtPath(aPath);
        if (ideProject != null) {
          String name = ideProject.projectName();
          NSBundle bundle = NSBundle.bundleForName(name);
          if (bundle == null)
            bundle = NSBundle._appBundleForName(name); 
          if (bundle != null) {
            aBundle = WODeployedBundle.bundleWithNSBundle(bundle);
            if (aBundle != null) {
              aBundle = new WOProjectBundle(aPath, aBundle);
              TheProjectBundles.setObjectForKey(aBundle, aPath);
            } 
          } 
        } 
      } else {
        aBundle = pBundle;
      } 
    } 
    return aBundle;
  }
  
  protected static NSMutableArray _projectsInDirectory(String aDirectoryPath) {
    String aProjectDirectoryName = null;
    File aDir = new File(aDirectoryPath);
    String[] aProjectDirectoryNameArray = aDir.list(theProjectsInDirectoryFilter);
    NSMutableArray aProjectBundleArray = new NSMutableArray(64);
    NSMutableDictionary aDuplicationDictionary = new NSMutableDictionary(64);
    if (aProjectDirectoryNameArray != null) {
      int len = aProjectDirectoryNameArray.length;
      for (int i = 0; i < len; i++) {
        aProjectDirectoryName = aProjectDirectoryNameArray[i];
        String aProjectDirectoryPath = NSPathUtilities.stringByAppendingPathComponent(aDirectoryPath, aProjectDirectoryName);
        if (_isProjectBundlePath(aProjectDirectoryPath)) {
          WOProjectBundle aProjectBundle = (WOProjectBundle)bundleWithPath(aProjectDirectoryPath);
          if (aProjectBundle != null) {
            String aProjectName = aProjectBundle.projectName();
            if (aProjectName != null) {
              boolean isFramework = aProjectBundle.isFramework();
              String aProjectNameWithExtension = String.valueOf(aProjectName) + (isFramework ? "framework" : "woa");
              WOProjectBundle anExistingProjectBundle = (WOProjectBundle)aDuplicationDictionary.objectForKey(aProjectNameWithExtension);
              if (anExistingProjectBundle != null) {
                boolean alreadyFoundFramework = anExistingProjectBundle.isFramework();
                if (isFramework && alreadyFoundFramework) {
                  String aProjectBundlePath = aProjectBundle.bundlePath();
                  String aProjectBundleDirectoryName = NSPathUtilities.lastPathComponent(aProjectBundlePath);
                  if (aProjectBundleDirectoryName.startsWith(aProjectName)) {
                    aProjectBundleArray.removeObject(anExistingProjectBundle);
                    aProjectBundleArray.addObject(aProjectBundle);
                    aDuplicationDictionary.setObjectForKey(aProjectBundle, aProjectNameWithExtension);
                    if (WOApplication._isDebuggingEnabled())
                      NSLog.debug.appendln("** Warning: More than one framework project with the name '" + aProjectName + "'.  Choosing " + aProjectBundlePath + " over " + 
                          anExistingProjectBundle.bundlePath() + " ."); 
                  } else if (WOApplication._isDebuggingEnabled()) {
                    NSLog.debug.appendln("** Warning: More than one framework project with the name '" + aProjectName + "'.  Using " + anExistingProjectBundle.bundlePath() + 
                        " rather than " + aProjectBundlePath + " .");
                  } 
                } else if (!isFramework && !alreadyFoundFramework) {
                  String anExecutablePath = System.getProperty("user.dir");
                  String aProjectBundlePath = aProjectBundle.bundlePath();
                  if (anExecutablePath.startsWith(aProjectBundlePath)) {
                    aProjectBundleArray.removeObject(anExistingProjectBundle);
                    aProjectBundleArray.addObject(aProjectBundle);
                    aDuplicationDictionary.setObjectForKey(aProjectBundle, aProjectNameWithExtension);
                    if (WOApplication._isDebuggingEnabled())
                      NSLog.debug.appendln("** Warning: More than one application project with the name '" + aProjectName + "'.  Choosing " + aProjectBundlePath + " over " + 
                          anExistingProjectBundle.bundlePath() + " ."); 
                  } else if (WOApplication._isDebuggingEnabled()) {
                    NSLog.debug.appendln("** Warning: More than one application project with the name '" + aProjectName + "'.  Choosing " + 
                        anExistingProjectBundle.bundlePath() + " over " + aProjectBundlePath + " .");
                  } 
                } 
              } else {
                aProjectBundleArray.addObject(aProjectBundle);
                aDuplicationDictionary.setObjectForKey(aProjectBundle, aProjectNameWithExtension);
              } 
            } else if (WOApplication._isDebuggingEnabled()) {
              NSLog.debug.appendln("<WOProjectBundle> Warning - Project bundle has no name. " + aProjectBundle);
            } 
          } 
        } 
      } 
    } 
    return aProjectBundleArray;
  }
  
  protected static NSMutableArray TheProjectsArrayArray = null;
  
  static synchronized NSMutableArray _WOAllProjects() {
    if (TheProjectsArrayArray != null)
      return TheProjectsArrayArray; 
    NSArray aProjectSearchPathArray = WOProperties.projectSearchPath();
    NSMutableArray aProjectsArrayArray = new NSMutableArray(aProjectSearchPathArray.count());
    String mainBundlePath = NSBundle.mainBundle().bundlePathURL().getPath();
    if (NSLog._debugLoggingAllowedForLevelAndGroups(3, 36L))
      NSLog.debug.appendln("WOProjectBundle._WOAllProjects() -- main bundle path is: " + mainBundlePath); 
    for (NSBundle bundle : NSBundle._allBundlesReally()) {
      if (bundle instanceof com.webobjects.foundation.development.NSProjectBundle) {
        WODeployedBundle projectBundle = bundleWithPath(bundle.bundlePath());
        if (projectBundle instanceof WOProjectBundle)
          aProjectsArrayArray.addObject(new NSMutableArray(projectBundle)); 
      } 
    } 
    _IDEProjectPBX mainProject = _IDEProjectPBX.pbxProjectAtPath(mainBundlePath);
    if (mainProject == null) {
      if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
        NSLog.debug.appendln("WOProjectBundle._WOAllProjects() -- no open Xcode project found for main bundle.  Skipping initialization of Xcode WOProjectBundles."); 
    } else {
      NSArray frameworkPaths = mainProject.frameworkBundlePaths();
      if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L)) {
        NSLog.debug.appendln("WOProjectBundle._WOAllProjects() -- main bundle's project path is: " + mainProject.ideProjectPath());
        NSLog.debug.appendln("WOProjectBundle._WOAllProjects() -- main bundle's framework paths are " + frameworkPaths);
      } 
      int fpCount = (frameworkPaths != null) ? frameworkPaths.count() : 0;
      if (fpCount > 0) {
        NSMutableArray pbxFrameworksProjectsArray = new NSMutableArray(fpCount + 1);
        WOProjectBundle mainProjectBundle = (WOProjectBundle)bundleWithPath(mainBundlePath);
        if (mainProjectBundle == null) {
          if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
            NSLog.debug.appendln("WOProjectBundle._WOAllProjects() -- failed to create a WOProjectBundle for the Xcode project for the main project bundle at: " + mainBundlePath + 
                " ... Perhaps the project is not open in Xcode anymore."); 
        } else {
          pbxFrameworksProjectsArray.addObject(mainProjectBundle);
        } 
        for (int i = 0; i < fpCount; i++) {
          String frameworkPath = (String)frameworkPaths.objectAtIndex(i);
          if (_isProjectBundlePath(frameworkPath)) {
            WOProjectBundle aProjectBundle = (WOProjectBundle)bundleWithPath(frameworkPath);
            if (aProjectBundle == null) {
              if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
                NSLog.debug.appendln("WOProjectBundle._WOAllProjects() -- failed to create a WOProjectBundle for the Xcode project for the framework bundle at: " + frameworkPath + 
                    " ... Perhaps the project is not open in Xcode."); 
            } else {
              pbxFrameworksProjectsArray.addObject(aProjectBundle);
              if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L))
                NSLog.debug.appendln("WOProjectBundle._WOAllProjects() -- found an open Xcode framework project at " + aProjectBundle.bundlePath()); 
            } 
          } else if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 36L)) {
            NSLog.debug.appendln("WOProjectBundle._WOAllProjects() -- failed to find a Xcode project for the framework bundle at: " + frameworkPath + 
                " ... Perhaps the project is not a Xcode project.  If it is such a project, perhaps it's not open in Xcode or has not been built in development mode.");
          } 
        } 
        if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 36L))
          NSLog.debug.appendln("*** The application has found the following opened, development-mode Xcode projects: " + pbxFrameworksProjectsArray); 
        aProjectsArrayArray.addObject(pbxFrameworksProjectsArray);
      } 
    } 
    String aProjectDir = null;
    Enumeration<String> aProjectDirectoryEnumerator = aProjectSearchPathArray.objectEnumerator();
    while (aProjectDirectoryEnumerator.hasMoreElements()) {
      aProjectDir = aProjectDirectoryEnumerator.nextElement();
      NSMutableArray aProjectsArray = _projectsInDirectory(aProjectDir);
      if (aProjectsArray != null && aProjectsArray.count() > 0) {
        if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 36L))
          NSLog.debug.appendln("*** The application has found the following opened, development-mode ProjectBuilderWO projects: " + aProjectsArray); 
        aProjectsArrayArray.addObject(aProjectsArray);
      } 
    } 
    TheProjectsArrayArray = aProjectsArrayArray;
    return TheProjectsArrayArray;
  }
  
  private static WOProjectBundle _locateBundleInArrayForProjectNamedIsFramework(NSMutableArray aProjectBundleArray, String aProjectName, boolean shouldBeFramework) {
    WOProjectBundle aProjectBundle = null;
    int count = aProjectBundleArray.count();
    for (int i = 0; i < count; i++) {
      aProjectBundle = (WOProjectBundle)aProjectBundleArray.objectAtIndex(i);
      if (aProjectBundle.projectName().equals(aProjectName) && shouldBeFramework == aProjectBundle.isFramework())
        break; 
      aProjectBundle = null;
    } 
    return aProjectBundle;
  }
  
  public static WOProjectBundle projectBundleForProject(String aProjectName, boolean shouldBeFramework) {
    WOProjectBundle aProjectBundle = null;
    NSMutableArray aProjectBundleArrayArray = _WOAllProjects();
    if (aProjectBundleArrayArray != null) {
      NSMutableArray aProjectBundleArray = null;
      int count = aProjectBundleArrayArray.count();
      for (int i = 0; aProjectBundle == null && i < count; i++) {
        aProjectBundleArray = (NSMutableArray)aProjectBundleArrayArray.objectAtIndex(i);
        aProjectBundle = _locateBundleInArrayForProjectNamedIsFramework(aProjectBundleArray, aProjectName, shouldBeFramework);
      } 
    } 
    return aProjectBundle;
  }
  
  public static boolean refreshProjectBundlesOnCacheMiss() {
    return _refreshProjectBundlesOnCacheMiss;
  }
  
  public static void setRefreshProjectBundlesOnCacheMiss(boolean refresh) {
    _refreshProjectBundlesOnCacheMiss = refresh;
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERWebObjects/1.0/ERWebObjects-1.0.jar!/com/webobjects/appserver/_private/WOProjectBundle.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */