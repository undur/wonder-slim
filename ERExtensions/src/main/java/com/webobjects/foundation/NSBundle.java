package com.webobjects.foundation;

import com.webobjects.foundation.development.NSBundleFactory;
import com.webobjects.foundation.development.NSLegacyBundle;
import com.webobjects.foundation.development.NSStandardProjectBundle;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public abstract class NSBundle implements NSVersion {
  public static final String NS_GLOBAL_PROPERTIES_PATH = "NSGlobalPropertiesPath";
  
  private static final String LEGACY_GLOBAL_PROPERTIES_PATH = "WebObjectsPropertiesReplacement";
  
  public static final String CFBUNDLESHORTVERSIONSTRINGKEY = "CFBundleShortVersionString";
  
  public static final String MANIFESTIMPLEMENTATIONVERSIONKEY = "Implementation-Version";
  
  public static final String BundleDidLoadNotification = "NSBundleDidLoadNotification";
  
  public static final String AllBundlesDidLoadNotification = "NSBundleAllDidLoadNotification";
  
  public static final String LoadedClassesNotification = "NSLoadedClassesNotification";
  
  private static final String userDirPath;
  
  public static final String LPROJSUFFIX = ".lproj";
  
  private static final String NONLOCALIZED_LOCALE = "Nonlocalized.lproj";
  
  private static final String NONLOCALIZED_LOCALE_PREFIX = "Nonlocalized.lproj" + File.separator;
  
  private static final String RESOURCES = "Resources";
  
  private static final NSMutableArray<NSBundle> AllBundles = new NSMutableArray(64);
  
  private static final NSMutableArray<NSBundle> AllBundlesReally = new NSMutableArray(64);
  
  private static final NSMutableArray<NSBundle> AllFrameworks = new NSMutableArray(64);
  
  private static final NSMutableDictionary<String, NSBundle> BundlesClassesTable = new NSMutableDictionary(2048);
  
  private static NSArray<String> ClassPath;
  
  private static final NSMutableDictionary<String, NSBundle> BundlesNamesTable = new NSMutableDictionary(16);
  
  private static final NSMutableDictionary<String, NSBundle> AppBundlesNamesTable = new NSMutableDictionary(1);
  
  private static NSBundle MainBundle;
  
  private static boolean PrincipalClassLookupAllowed;
  
  private static final _NSThreadsafeMutableDictionary ResourceDirectoryFilters = new _NSThreadsafeMutableDictionary(
      new NSMutableDictionary());
  
  private static final _NSThreadsafeMutableDictionary ResourceFilters = new _NSThreadsafeMutableDictionary(new NSMutableDictionary());
  
  protected static final DirectoryFilter TheDirectoryFilter = new DirectoryFilter();
  
  protected static final FilesFilter TheFilesFilter = new FilesFilter();
  
  public static final String InfoPlistFilename = "Info.plist";
  
  private static String ResourcesInfoPlist = "Resources/Info.plist";
  
  private static String JarResourcesInfoPlist = "!/" + ResourcesInfoPlist;
  
  private static String ResourcesProperties = "Resources/Properties";
  
  private static boolean safeInvokeDeprecatedJarBundleAPI = false;
  
  private Class principalClass;
  
  private static final String jarEndsWithString = ".jar".concat(JarResourcesInfoPlist);
  
  protected Integer _version;
  
  protected Integer _revision;
  
  protected Integer _fix;
  
  protected Integer _build;
  
  protected String _releaseString;
  
  protected static final Integer _zeroInteger = new Integer(0);
  
  private static final String __exctractStringFromURL(URL anURL) {
    String url2Path = null;
    try {
      String urlPath = anURL.getPath();
      if (urlPath.endsWith(jarEndsWithString)) {
        url2Path = urlPath.substring(0, urlPath.length() - JarResourcesInfoPlist.length());
        URL url2 = new URL(url2Path);
        url2Path = url2.getPath();
      } 
    } catch (Exception exception) {}
    return url2Path;
  }
  
  static {
    NSBundleFactory.registerBundleFactory((NSBundleFactory)new NSLegacyBundle.Factory());
    if (NSValueUtilities.booleanValue(System.getProperty("NSProjectBundleEnabled")))
      NSBundleFactory.registerBundleFactory((NSBundleFactory)new NSStandardProjectBundle.Factory()); 
    NSArray<String> bundleFactories = NSValueUtilities.arrayValue(System.getProperty("NSBundleFactories"));
    if (bundleFactories != null)
      for (String bundleFactory : bundleFactories) {
        try {
          NSBundleFactory.registerBundleFactory(Class.forName(bundleFactory).<NSBundleFactory>asSubclass(NSBundleFactory.class).newInstance());
        } catch (Throwable t) {
          throw new IllegalArgumentException("Failed to create the bundle factory: " + bundleFactory + ".", t);
        } 
      }  
    try {
      safeInvokeDeprecatedJarBundleAPI = NSPropertyListSerialization.booleanForString(NSProperties.getProperty("com.webobjects.safeInvokeDeprecatedJarBundleAPI"));
      String woUserDir = NSProperties.getProperty("webobjects.user.dir");
      if (woUserDir == null)
        woUserDir = System.getProperty("user.dir"); 
      userDirPath = (new File(woUserDir)).getCanonicalPath();
      NSMutableArray<String> urlArray = new NSMutableArray();
      Enumeration<URL> e;
      for (e = NSBundle.class.getClassLoader().getResources(ResourcesInfoPlist); e.hasMoreElements(); ) {
        String urlPath = __exctractStringFromURL(e.nextElement());
        if (urlPath != null)
          urlArray.addObject(urlPath); 
      } 
      if (urlArray.count() == 0)
        for (e = ClassLoader.getSystemResources(ResourcesInfoPlist); e.hasMoreElements(); ) {
          String urlPath = __exctractStringFromURL(e.nextElement());
          if (urlPath != null)
            urlArray.addObject(urlPath); 
        }  
      LoadBundlesFromJars((NSArray)urlArray);
      NSMutableArray nSMutableArray = NSArray._mutableComponentsSeparatedByString(String.valueOf(System.getProperty("java.class.path")) + File.pathSeparator + NSProperties.getProperty("com.webobjects.classpath"), File.pathSeparator);
      NSMutableArray<String> cleanedUpClassPath = new NSMutableArray();
      for (Iterator<String> iterator = nSMutableArray.iterator(); iterator.hasNext(); ) {
        String fixedComponent = NSPathUtilities.stringByNormalizingExistingPath(iterator.next());
        if (fixedComponent != null && fixedComponent.length() > 0)
          cleanedUpClassPath.add(fixedComponent); 
      } 
      for (int i = cleanedUpClassPath.count() - 1; i >= 0; i--) {
        String component = (String)cleanedUpClassPath.objectAtIndex(i);
        if (cleanedUpClassPath.indexOfObject(component) != i)
          cleanedUpClassPath.removeObjectAtIndex(i); 
      } 
      ClassPath = (NSArray<String>)cleanedUpClassPath;
      LoadBundlesFromClassPath(ClassPath);
      InitMainBundle();
      LoadUserAndBundleProperties();
      InitPrincipalClasses();
      NSNotificationCenter.defaultCenter().postNotification("NSBundleAllDidLoadNotification", null, null);
      _NSUtilities._setResourceSearcher(new _NSUtilities._ResourceSearcher() {
            public Class _searchForClassWithName(String className) {
              return NSBundle.searchAllBundlesForClassWithName(className);
            }
            
            public URL _searchPathURLForResourceWithName(Class resourceClass, String resourceName, String extension) {
              URL url = null;
              NSBundle bundle = NSBundle.bundleForClass(resourceClass);
              if (bundle != null && resourceName != null) {
                String fileName = null;
                if (extension == null || extension.length() == 0) {
                  fileName = resourceName;
                } else if (extension.startsWith(".") || resourceName.endsWith(".")) {
                  fileName = resourceName.concat(extension);
                } else {
                  fileName = _NSStringUtilities.concat(resourceName, ".", extension);
                } 
                bundle.pathURLForResourcePath(bundle.resourcePathForLocalizedResourceNamed(fileName, ""));
              } 
              return url;
            }
          });
    } catch (IOException e) {
      throw NSForwardException._runtimeExceptionForThrowable(e);
    } 
  }
  
  @Deprecated
  public static synchronized NSArray allBundles() {
    return AllBundles.immutableClone();
  }
  
  @Deprecated
  public static NSArray<NSBundle> allFrameworks() {
    return frameworkBundles();
  }
  
  public static synchronized NSBundle bundleForClass(Class aClass) {
    NSBundle bundle = null;
    if (aClass != null)
      bundle = (NSBundle)BundlesClassesTable.objectForKey(aClass.getName()); 
    return bundle;
  }
  
  @Deprecated
  public static NSBundle bundleWithPath(String aPath) {
    return _bundleWithPathShouldCreateIsJar(aPath, false, false);
  }
  
  public static NSBundle _bundleWithPathShouldCreateIsJar(String aPath, boolean shouldCreateBundle, boolean newIsJar) {
    return NSBundleFactory.bundleForPathWithRegistry(aPath, shouldCreateBundle, newIsJar);
  }
  
  public static synchronized NSBundle bundleForName(String aName) {
    NSBundle bundle = null;
    if (aName != null) {
      String fixedName;
      if (aName.endsWith(".framework")) {
        fixedName = NSPathUtilities.stringByDeletingPathExtension(aName);
      } else {
        fixedName = aName;
      } 
      bundle = (NSBundle)BundlesNamesTable.objectForKey(fixedName);
    } 
    return bundle;
  }
  
  public static synchronized NSBundle _appBundleForName(String aName) {
    NSBundle bundle = null;
    if (aName != null) {
      String fixedName;
      if (aName.endsWith(".woa")) {
        fixedName = NSPathUtilities.stringByDeletingPathExtension(aName);
      } else {
        fixedName = aName;
      } 
      bundle = (NSBundle)AppBundlesNamesTable.objectForKey(fixedName);
    } 
    return bundle;
  }
  
  public static synchronized NSBundle _bundleOrAppForName(String name) {
    NSBundle bundle = bundleForName(name);
    if (bundle == null)
      bundle = _appBundleForName(name); 
    return bundle;
  }
  
  public static synchronized NSArray<NSBundle> frameworkBundles() {
    return AllFrameworks.immutableClone();
  }
  
  public static void _setMainBundle(NSBundle bundle) {
    MainBundle = bundle;
  }
  
  public static NSBundle mainBundle() {
    return MainBundle;
  }
  
  private static void transferPropertiesFromSourceToDest(Properties sourceProps, Properties destProps) {
    if (sourceProps != null)
      destProps.putAll(sourceProps); 
  }
  
  public static Properties _userAndBundleProperties() {
    Properties nextProps = null;
    Properties bundleProps = new Properties();
    Properties userProps = null;
    Properties oldSysProps = NSProperties._getProperties();
    String userhome = System.getProperty("user.home");
    NSArray<NSBundle> allBundles = allFrameworks();
    Enumeration<NSBundle> bundleEn = allBundles.objectEnumerator();
    while (bundleEn.hasMoreElements()) {
      NSBundle nextBundle = bundleEn.nextElement();
      nextProps = nextBundle.properties();
      transferPropertiesFromSourceToDest(nextProps, bundleProps);
    } 
    if (mainBundle() != null)
      nextProps = mainBundle().properties(); 
    transferPropertiesFromSourceToDest(nextProps, bundleProps);
    nextProps = bundleProps;
    boolean validateProperties = shouldValidateProperties();
    boolean globalPropertiesPathOverride = false;
    NSMutableArray filesToCheck = new NSMutableArray();
    String wopropsfile = System.getProperty("NSGlobalPropertiesPath");
    if (wopropsfile == null)
      wopropsfile = System.getProperty("WebObjectsPropertiesReplacement"); 
    if (wopropsfile != null && wopropsfile.length() > 0) {
      if (userhome != null && userhome.length() > 0 && !(new File(wopropsfile)).isAbsolute())
        filesToCheck.addObject(new File(userhome, wopropsfile)); 
      filesToCheck.addObject(new File(wopropsfile));
      globalPropertiesPathOverride = true;
    } else if (userhome != null && userhome.length() > 0) {
      filesToCheck.addObject(new File(userhome, "WebObjects.properties"));
    } 
    File propsFile = null;
    for (int i = 0; i < filesToCheck.count(); i++) {
      boolean loadGlobalProperties;
      propsFile = (File)filesToCheck.objectAtIndex(i);
      if (validateProperties && globalPropertiesPathOverride) {
        loadGlobalProperties = true;
      } else {
        loadGlobalProperties = (propsFile.exists() && propsFile.isFile() && propsFile.canRead());
      } 
      if (loadGlobalProperties) {
        try {
          userProps = new NSProperties.NestedProperties(null);
          if (bundleProps != null)
            userProps.putAll(bundleProps); 
          ((NSProperties.NestedProperties)userProps).load(propsFile, NSProperties._shouldRequireSymlinkedGlobalAndIncludeProperties());
        } catch (Exception e) {
          if (validateProperties)
            throw new RuntimeException("Failed to load '" + propsFile + "'.", e); 
          userProps = null;
          NSLog.err.appendln(e);
        } 
        if (i == 2)
          NSLog.err.appendln("Could not read specified NSGlobalPropertiesPath file (" + wopropsfile + ").  Falling back to " + propsFile); 
        if (userProps != null)
          break; 
      } 
    } 
    if (userProps != null) {
      nextProps = userProps;
    } else {
      if (globalPropertiesPathOverride && validateProperties)
        throw new RuntimeException("There was no global properties file '" + wopropsfile + "'."); 
      NSLog.err.appendln("Couldn't load properties file: " + propsFile + " at path: " + userhome);
    } 
    Properties sysProps = new Properties();
    sysProps.putAll(nextProps);
    transferPropertiesFromSourceToDest(oldSysProps, sysProps);
    return sysProps;
  }
  
  private static void LoadUserAndBundleProperties() {
    NSProperties._setProperties(_userAndBundleProperties());
  }
  
  private static void InitMainBundle() {
    try {
      String mainBundleName = NSProperties._mainBundleName();
      if (mainBundleName != null)
        MainBundle = bundleForName(mainBundleName); 
    } catch (Exception exception) {}
    if (MainBundle == null)
      MainBundle = NSBundleFactory.bundleForPathWithRegistry(userDirPath, true, false); 
    if (MainBundle == null)
      MainBundle = bundleForName("JavaFoundation"); 
    if (MainBundle != null && (
      MainBundle.infoDictionary() == null || !_NSUtilities.safeEquals(MainBundle.name(), MainBundle.infoDictionary().objectForKey("NSExecutable"))))
      throw new IllegalStateException("There was no name defined for the bundle '" + MainBundle + "'"); 
  }
  
  @Deprecated
  public static void _setPrincipalClassWarningSuppressed(boolean flag) {}
  
  private static void InitPrincipalClasses() {
    int count = AllBundlesReally.count();
    for (int i = 0; i < count; i++) {
      ((NSBundle)AllBundlesReally.objectAtIndex(i))._bundlesDidLoad();
      ((NSBundle)AllBundlesReally.objectAtIndex(i)).initPrincipalClass();
    } 
    PrincipalClassLookupAllowed = true;
  }
  
  private static void LoadBundlesFromJars(NSArray array) {
    Enumeration<E> en = array.objectEnumerator();
    while (en.hasMoreElements())
      NSBundleFactory.bundleForPathWithRegistry(en.nextElement().toString(), true, true); 
  }
  
  private static void LoadBundlesFromClassPath(NSArray<String> array) {
    for (Enumeration<String> en = array.objectEnumerator(); en.hasMoreElements(); ) {
      String nextPathComponent = en.nextElement();
      NSBundleFactory.bundleForPathWithRegistry(nextPathComponent, true, false);
    } 
  }
  
  public static String _DefaultLocalePrefix() {
    String defaultLang = "English";
    String defaultLocaleLang = Locale.getDefault().getLanguage();
    if (defaultLocaleLang.equals("de")) {
      defaultLang = "German";
    } else if (defaultLocaleLang.equals("es")) {
      defaultLang = "Spanish";
    } else if (defaultLocaleLang.equals("fr")) {
      defaultLang = "French";
    } else if (defaultLocaleLang.equals("ja")) {
      defaultLang = "Japanese";
    } 
    return defaultLang.concat(".lproj");
  }
  
  public static synchronized NSBundle _lookupBundleWithPath(String aPath) {
    NSBundle bundle = null;
    if (aPath == null)
      return null; 
    Enumeration<NSBundle> en = AllBundlesReally.objectEnumerator();
    while (en.hasMoreElements() && bundle == null) {
      NSBundle nextBundle = en.nextElement();
      if (nextBundle.bundlePath().equals(aPath))
        bundle = nextBundle; 
    } 
    return bundle;
  }
  
  public static String _normalizeExistingBundlePath(String aPath) {
    String standardizedPath = null;
    if (aPath != null) {
      File fileAtPath = new File(aPath);
      if (fileAtPath.exists())
        standardizedPath = NSPathUtilities.stringByNormalizingExistingPath(aPath); 
    } 
    return standardizedPath;
  }
  
  public static String _cleanNormalizedBundlePath(String standardizedPath) {
    String bundlePath = standardizedPath;
    if (bundlePath != null) {
      String allDirectoriesInPath = null;
      int i = -1;
      File fileAtPath = new File(bundlePath);
      if (!fileAtPath.isDirectory()) {
        allDirectoriesInPath = _NSStringUtilities.stringByDeletingLastComponent(bundlePath, File.separatorChar);
      } else {
        allDirectoriesInPath = bundlePath;
      } 
      if (allDirectoriesInPath != null) {
        i = allDirectoriesInPath.lastIndexOf(NSLegacyBundle.RJSUFFIX);
        if (i == -1 || i == 0)
          i = allDirectoriesInPath.lastIndexOf(NSLegacyBundle.RSUFFIX); 
        if (i == -1 || i == 0) {
          bundlePath = allDirectoriesInPath;
        } else {
          bundlePath = allDirectoriesInPath.substring(0, i);
          if (NSPathUtilities.lastPathComponent(NSPathUtilities.stringByDeletingLastPathComponent(bundlePath)).equals("Versions"))
            bundlePath = NSPathUtilities.stringByDeletingLastPathComponent(NSPathUtilities.stringByDeletingLastPathComponent(bundlePath)); 
        } 
      } 
    } 
    return bundlePath;
  }
  
  public static NSArray<NSBundle> _allBundlesReally() {
    return (NSArray<NSBundle>)AllBundlesReally;
  }
  
  public static synchronized void addBundle(NSBundle bundle) {
    if (AllBundlesReally.containsObject(bundle))
      return; 
    boolean bundleAdded = true;
    if (bundle.isFramework()) {
      NSBundle possibleBundle = bundleForName(bundle.name());
      if (possibleBundle == null) {
        BundlesNamesTable.setObjectForKey(bundle, bundle.name());
        AllFrameworks.addObject(bundle);
      } else {
        bundleAdded = false;
        System.err.println("<" + NSBundle.class.getName() + "> warning: There is already a unique instance for Bundle named '" + bundle.name() + "' at the path + '" + possibleBundle.bundlePath() + "'. Skipping the version located at '" + bundle.bundlePath() + "'.");
      } 
    } else {
      AppBundlesNamesTable.setObjectForKey(bundle, bundle.name());
      MainBundle = bundle;
      AllBundles.addObject(bundle);
    } 
    if (bundleAdded) {
      AllBundlesReally.addObject(bundle);
      bundle.initVersion();
      if (PrincipalClassLookupAllowed) {
        bundle._bundlesDidLoad();
        bundle.initPrincipalClass();
      } 
      NSNotificationCenter.defaultCenter().postNotification("NSBundleDidLoadNotification", bundle, new NSDictionary(bundle.bundleClassNames(), "NSLoadedClassesNotification"));
    } 
  }
  
  public static String _userDirPath() {
    return userDirPath;
  }
  
  public static NSArray<String> _classPath() {
    return ClassPath;
  }
  
  public static synchronized void _registerClassNameForBundle(String className, NSBundle bundle) {
    NSBundle existingBundle = (NSBundle)BundlesClassesTable.objectForKey(className);
    if (existingBundle == null)
      BundlesClassesTable.setObjectForKey(bundle, className); 
  }
  
  protected static ResourceDirectoryFilter ResourceDirectoryFilterForExtension(String anExtension) {
    ResourceDirectoryFilter rdf = null;
    if (anExtension == null)
      throw new IllegalArgumentException("Illegal resource search: cannot search using a null extension"); 
    String correctedExtension = anExtension.startsWith(".") ? anExtension.substring(1) : anExtension;
    rdf = (ResourceDirectoryFilter)ResourceDirectoryFilters.objectForKey(correctedExtension);
    if (rdf == null) {
      rdf = new ResourceDirectoryFilter(correctedExtension);
      ResourceDirectoryFilters.setObjectForKey(rdf, correctedExtension);
    } 
    return rdf;
  }
  
  protected static ResourceFilter ResourceFilterForExtension(String anExtension) {
    ResourceFilter rf = null;
    if (anExtension == null)
      throw new IllegalArgumentException("Illegal resource search: cannot search using a null extension"); 
    String correctedExtension = anExtension.startsWith(".") ? anExtension.substring(1) : anExtension;
    rf = (ResourceFilter)ResourceFilters.objectForKey(correctedExtension);
    if (rf == null) {
      rf = new ResourceFilter(correctedExtension);
      ResourceFilters.setObjectForKey(rf, correctedExtension);
    } 
    return rf;
  }
  
  static Class searchAllBundlesForClassWithName(String className) {
    if (NSLog._debugLoggingAllowedForLevelAndGroups(2, 32L)) {
      NSLog.debug.appendln("NSBundle.searchAllBundlesForClassWithName(\"" + className + "\") was invoked.\n\t**This affects performance very badly.**");
      if (NSLog.debug.allowedDebugLevel() > 2)
        NSLog.debug.appendln(new RuntimeException("NSBundle.searchAllBundlesForClassWithName was invoked.")); 
    } 
    Class result = null;
    result = searchForClassInBundles(className, allBundles(), true);
    if (result == null)
      result = searchForClassInBundles(className, allFrameworks(), true); 
    return result;
  }
  
  private static Class searchForClassInBundles(String className, NSArray bundles, boolean registerPackageOnHit) {
    int count = bundles.count();
    for (int i = 0; i < count; i++) {
      NSArray<String> packages = ((NSBundle)bundles.objectAtIndex(i)).bundleClassPackageNames();
      Class result = _NSUtilities._searchForClassInPackages(className, packages, registerPackageOnHit, false);
      if (result != null)
        return result; 
    } 
    return null;
  }
  
  public byte[] bytesForResourcePath(String aResourcePath) {
    InputStream is = inputStreamForResourcePath(aResourcePath);
    byte[] b = (byte[])null;
    if (is == null) {
      b = new byte[0];
    } else {
      try {
        b = _NSStringUtilities.bytesFromInputStream(is);
      } catch (Exception e) {
        throw NSForwardException._runtimeExceptionForThrowable(e);
      } 
    } 
    return b;
  }
  
  public NSDictionary<String, Object> _infoDictionary() {
    return infoDictionary();
  }
  
  public URL pathURLForResourcePath(String aResourcePath) {
    return _pathURLForResourcePath(aResourcePath, true);
  }
  
  public InputStream inputStreamForResourcePath(String aResourcePath) {
    InputStream is = null;
    URL url = _pathURLForResourcePath(aResourcePath, false);
    if (url != null)
      try {
        is = url.openStream();
      } catch (IOException ioe) {
        throw NSForwardException._runtimeExceptionForThrowable(ioe);
      }  
    return is;
  }
  
  public boolean _directoryExistsInJar(String path) {
    if (path == null)
      return false; 
    if (path.length() == 0)
      return true; 
    if (isJar()) {
      String aPath = path;
      if (!aPath.endsWith("/"))
        aPath = aPath.concat("/"); 
      if (File.separatorChar != '/')
        aPath = aPath.replace(File.separatorChar, '/'); 
      return (_jarFile().getEntry(aPath) != null);
    } 
    return false;
  }
  
  protected void initVersion() {
    NSDictionary<String, Object> infoPlist = _infoDictionary();
    String aBuildString = null;
    String aVersion = null;
    if (infoPlist != null) {
      aVersion = (String)infoPlist.valueForKey("CFBundleShortVersionString");
      aBuildString = (String)infoPlist.valueForKey("Implementation-Version");
    } 
    parseVersionString(aVersion, aBuildString);
  }
  
  protected void parseVersionString(String aVersion, String aBuildString) {
    Integer version = new Integer(1);
    Integer revision = _zeroInteger;
    Integer fix = _zeroInteger;
    Integer build = _zeroInteger;
    String releaseString = "";
    try {
      build = (aBuildString != null && aBuildString.length() > 0) ? Integer.valueOf(aBuildString) : _zeroInteger;
    } catch (Exception e) {
      if (NSLog.debugLoggingAllowedForLevelAndGroups(1, 32L))
        NSLog.debug.appendln("The bundle " + name() + " has malformed build number: " + aBuildString); 
    } 
    if (aVersion != null && aVersion.trim().length() > 0) {
      try {
        StringBuilder numeric = new StringBuilder(32);
        StringBuilder adjective = new StringBuilder(32);
        boolean found = false;
        byte b;
        int i;
        char[] arrayOfChar;
        for (i = (arrayOfChar = aVersion.trim().toCharArray()).length, b = 0; b < i; ) {
          char c = arrayOfChar[b];
          if (!found && (Character.isDigit(c) || c == '.')) {
            numeric.append(c);
          } else {
            found = true;
            adjective.append(c);
          } 
          b++;
        } 
        StringTokenizer versionTokenizer = new StringTokenizer(numeric.toString(), ".", false);
        if (versionTokenizer.hasMoreTokens())
          version = new Integer(versionTokenizer.nextToken()); 
        if (versionTokenizer.hasMoreTokens())
          revision = new Integer(versionTokenizer.nextToken()); 
        if (versionTokenizer.hasMoreTokens())
          fix = new Integer(versionTokenizer.nextToken()); 
        releaseString = adjective.toString();
      } catch (Exception exception) {
        if (NSLog.debugLoggingAllowedForLevelAndGroups(1, 32L))
          NSLog.debug.appendln("Exception " + exception); 
      } 
    } else {
      NSLog.err.appendln("The bundle " + name() + " has malformed version number: " + aVersion);
    } 
    this._version = version;
    this._revision = revision;
    this._fix = fix;
    this._build = build;
    this._releaseString = releaseString;
  }
  
  public Integer version() {
    return (this._version != null) ? this._version : new Integer(1);
  }
  
  public Integer revision() {
    return (this._revision != null) ? this._revision : new Integer(0);
  }
  
  public Integer fix() {
    return (this._fix != null) ? this._fix : new Integer(0);
  }
  
  public Integer build() {
    return (this._build != null) ? this._build : new Integer(0);
  }
  
  public int compareTo(NSVersion object) throws ClassCastException {
    return NSVersion.DefaultImplementation.compareTo(this, object);
  }
  
  public String versionString() {
    return NSVersion.DefaultImplementation.toString(this);
  }
  
  public String releaseString() {
    return (this._releaseString != null) ? this._releaseString : "";
  }
  
  @Deprecated
  public String pathForResource(String aName, String anExtension) {
    return pathForResource(aName, anExtension, null);
  }
  
  public Class principalClass() {
    return this.principalClass;
  }
  
  public URL _urlForRelativePath(String path) {
    URL retVal = null;
    if (path != null && path.length() > 0)
      if (isJar()) {
        String aPath = path;
        if (aPath.startsWith("/"))
          aPath = aPath.substring(1, aPath.length()); 
        ZipEntry ze = _jarFile().getEntry(aPath);
        if (ze == null && !aPath.endsWith("/")) {
          aPath = aPath.concat("/");
          ze = _jarFile().getEntry(aPath);
        } 
        if (ze != null)
          try {
            retVal = new URL(_bundleURLPrefix().concat(aPath));
          } catch (MalformedURLException malformedURLException) {} 
      } else {
        File f = new File(_NSStringUtilities.concat(bundlePath(), File.separator, path));
        if (f.exists())
          try {
            retVal = f.toURL();
          } catch (MalformedURLException malformedURLException) {} 
      }  
    return retVal;
  }
  
  protected String resourcePathForLocalizedResourceNamed(String aName, String aSubDirPath, List<String> resourceBuckets) {
    String path = null;
    if (aName != null) {
      String FileSeparator = isJar() ? "/" : File.separator;
      Iterator<String> en = resourceBuckets.iterator();
      String localePrefix = _DefaultLocalePrefix().concat(FileSeparator);
      String[] pathFragments = new String[2];
      if (aSubDirPath == null || aSubDirPath.length() == 0) {
        pathFragments[0] = localePrefix;
        pathFragments[1] = "";
      } else {
        pathFragments[0] = _NSStringUtilities.concat(localePrefix, aSubDirPath, FileSeparator);
        pathFragments[1] = aSubDirPath.concat(FileSeparator);
      } 
      while (en.hasNext() && path == null) {
        String pathPrefix, nextDir = en.next();
        if (nextDir.equals("")) {
          pathPrefix = bundlePath().concat(FileSeparator);
        } else {
          pathPrefix = _NSStringUtilities.concat(bundlePath(), FileSeparator, nextDir, FileSeparator);
        } 
        for (int i = 0; i < pathFragments.length && path == null; i++) {
          if (isJar()) {
            String possiblePath = _NSStringUtilities.concat(FileSeparator, pathFragments[i], aName);
            String comparisonPath = nextDir.concat(possiblePath);
            ZipEntry ze = _jarFile().getEntry(comparisonPath);
            if (ze != null) {
              path = pathFragments[i].concat(aName);
              if (!pathFragments[i].startsWith(_DefaultLocalePrefix()))
                path = "Nonlocalized.lproj".concat(possiblePath); 
            } 
          } else {
            String possiblePath;
            if (pathFragments[i].equals("")) {
              possiblePath = pathPrefix.concat(aName);
            } else {
              possiblePath = _NSStringUtilities.concat(pathPrefix, pathFragments[i], aName);
            } 
            File possibleResource = new File(possiblePath);
            File possibleResourcePrefix = new File(pathPrefix);
            if (possibleResource.exists() && possibleResourcePrefix.isDirectory())
              try {
                path = possibleResource.getCanonicalPath();
                String absolutePathPrefix = possibleResourcePrefix.getCanonicalPath();
                if (!absolutePathPrefix.endsWith(File.separator))
                  absolutePathPrefix = String.valueOf(absolutePathPrefix) + File.separator; 
                if (path.startsWith(absolutePathPrefix)) {
                  path = path.substring(absolutePathPrefix.length());
                  if (!pathFragments[i].startsWith(_DefaultLocalePrefix()))
                    path = NONLOCALIZED_LOCALE_PREFIX.concat(path); 
                } else {
                  throw new IllegalArgumentException("<" + NSLegacyBundle.class.getName() + "> May not pass relative paths that reference resources outside of the bundle! (" + aName + "," + aSubDirPath + ")");
                } 
              } catch (IOException e) {
                throw NSForwardException._runtimeExceptionForThrowable(e);
              }  
          } 
        } 
      } 
    } 
    return path;
  }
  
  protected boolean _prefixPathWithNonLocalizedPrefix(String aPath, String resourcePath) {
    return aPath.equals(resourcePath);
  }
  
  public String toString() {
    int count = 0;
    if (bundleClassNames() != null)
      count = bundleClassNames().count(); 
    return "<" + getClass().getName() + " name:'" + name() + "' bundlePath:'" + bundlePath() + "' packages:'" + bundleClassPackageNames() + "' " + count + " classes >";
  }
  
  public Class _classWithName(String className) {
    Class objectClass = null;
    if (className == null)
      throw new IllegalArgumentException("Class name cannot be null."); 
    objectClass = _NSUtilities._classWithPartialName(className, false);
    if (objectClass != null)
      return objectClass; 
    NSArray<String> thePackages = bundleClassPackageNames();
    objectClass = _NSUtilities._searchForClassInPackages(className, thePackages, true, false);
    return (objectClass != null) ? objectClass : _NSUtilities._classWithPartialName(className, true);
  }
  
  protected NSArray<String> resourcePathsForResourcesInDirectory(String aPath, String resourcePath, FilenameFilter aFilter, boolean prependNonlocalizedLProj) {
    File subdir = new File(aPath);
    String[] fileNames = subdir.list(aFilter);
    if (fileNames == null)
      return NSArray.emptyArray(); 
    String prefix = _prefixPathWithNonLocalizedPrefix(aPath, resourcePath) ? "" : aPath.substring(resourcePath.concat(File.separator).length());
    NSMutableArray<String> list = new NSMutableArray();
    for (int i = 0; i < fileNames.length; i++) {
      if (prefix.length() == 0) {
        list.addObject(NONLOCALIZED_LOCALE_PREFIX.concat(fileNames[i]));
      } else if (prependNonlocalizedLProj) {
        list.addObject(_NSStringUtilities.concat(NONLOCALIZED_LOCALE_PREFIX, prefix, File.separator, fileNames[i]));
      } else {
        list.addObject(_NSStringUtilities.concat(prefix, File.separator, fileNames[i]));
      } 
    } 
    String[] dirNames = subdir.list(TheDirectoryFilter);
    for (int j = 0; j < dirNames.length; j++) {
      boolean prepend = (prefix.length() == 0) ? (!dirNames[j].endsWith(".lproj")) : prependNonlocalizedLProj;
      list.addObjectsFromArray(resourcePathsForResourcesInDirectory(_NSStringUtilities.concat(aPath, File.separator, dirNames[j]), resourcePath, aFilter, prepend));
    } 
    if (list.count() == 0)
      return NSArray.emptyArray(); 
    return (NSArray<String>)list;
  }
  
  protected NSArray<String> resourcePathsForDirectoriesInDirectory(String aPath, String resourcePath, FilenameFilter aFilter, boolean prependNonlocalizedLProj) {
    String[] dirNames = (new File(aPath)).list(aFilter);
    if (dirNames == null)
      return NSArray.emptyArray(); 
    NSMutableArray<String> list = new NSMutableArray();
    String prefix = _prefixPathWithNonLocalizedPrefix(aPath, resourcePath) ? "" : aPath.substring(resourcePath.concat(File.separator).length());
    int i;
    for (i = 0; i < dirNames.length; i++) {
      if (prefix.length() == 0) {
        if (dirNames[i].endsWith(".lproj")) {
          list.addObject(dirNames[i]);
        } else {
          list.addObject(NONLOCALIZED_LOCALE_PREFIX.concat(dirNames[i]));
        } 
      } else if (prependNonlocalizedLProj) {
        list.addObject(_NSStringUtilities.concat(NONLOCALIZED_LOCALE_PREFIX, prefix, File.separator, dirNames[i]));
      } else {
        list.addObject(_NSStringUtilities.concat(prefix, File.separator, dirNames[i]));
      } 
    } 
    for (i = 0; i < dirNames.length; i++) {
      if (prefix.length() == 0) {
        boolean endWithLPROJ = dirNames[i].endsWith(".lproj");
        list.addObjectsFromArray(resourcePathsForDirectoriesInDirectory(_NSStringUtilities.concat(aPath, File.separator, dirNames[i]), resourcePath, aFilter, !endWithLPROJ));
      } else {
        list.addObjectsFromArray(resourcePathsForDirectoriesInDirectory(_NSStringUtilities.concat(aPath, File.separator, dirNames[i]), resourcePath, aFilter, prependNonlocalizedLProj));
      } 
    } 
    if (list.count() == 0)
      return NSArray.emptyArray(); 
    return (NSArray<String>)list;
  }
  
  public void initPrincipalClass() {
    String principalClassName = null;
    this.principalClass = null;
    if (infoDictionary() != null) {
      principalClassName = (String)infoDictionary().objectForKey("NSPrincipalClass");
      if (principalClassName != null && !principalClassName.equals("") && !"true".equals(System.getProperty("NSSkipPrincipalClasses"))) {
        this.principalClass = _NSUtilities.classWithName(principalClassName);
        if (this.principalClass == null && _NSUtilities._principalClassLoadingWarningsNeeded) {
          NSLog.err.appendln("Principal class '" + principalClassName + "' not found in bundle " + name());
          if (NSLog.debugLoggingAllowedForLevelAndGroups(1, 32L))
            NSLog.debug.appendln(new ClassNotFoundException(principalClassName)); 
        } 
      } 
    } 
  }
  
  protected static class DirectoryFilter implements FilenameFilter {
    public boolean accept(File dir, String aName) {
      boolean result = false;
      if (aName != null && !aName.equals(".svn")) {
        File namedFile = new File(dir, aName);
        result = namedFile.isDirectory();
      } 
      return result;
    }
  }
  
  protected static class FilesFilter implements FilenameFilter {
    public boolean accept(File dir, String aName) {
      boolean result = false;
      if (aName != null && !aName.equals(".svn")) {
        File namedFile = new File(dir, aName);
        result = namedFile.isFile();
      } 
      return result;
    }
  }
  
  protected static class OldResourceFilter implements FilenameFilter {
    private String extension;
    
    public OldResourceFilter(String anExtension) {
      this.extension = "." + anExtension;
    }
    
    public boolean accept(File dir, String aName) {
      boolean result = false;
      if (aName != null && 
        aName.endsWith(this.extension))
        result = true; 
      return result;
    }
  }
  
  protected static class ResourceDirectoryFilter implements FilenameFilter {
    private String extension;
    
    public ResourceDirectoryFilter(String anExtension) {
      this.extension = "." + anExtension;
    }
    
    public boolean accept(File dir, String aName) {
      boolean result = false;
      if (aName != null && !aName.equals(".svn")) {
        File d = new File(dir + File.separator + aName);
        if (d.isDirectory() && 
          aName.endsWith(this.extension))
          result = true; 
      } 
      return result;
    }
  }
  
  protected static class ResourceFilter implements FilenameFilter {
    private String extension;
    
    public ResourceFilter(String anExtension) {
      this.extension = "." + anExtension;
    }
    
    public boolean accept(File dir, String aName) {
      boolean result = false;
      if (aName != null) {
        File f = new File(dir, aName);
        if (f.isFile() && 
          aName.endsWith(this.extension))
          result = true; 
      } 
      return result;
    }
  }
  
  protected static class SpecificResourceFilter implements FilenameFilter {
    private String name;
    
    public SpecificResourceFilter(String aName) {
      this.name = String.valueOf(aName) + ".";
    }
    
    public boolean accept(File dir, String aName) {
      boolean result = false;
      if (aName != null && 
        aName.startsWith(this.name))
        result = true; 
      return result;
    }
  }
  
  protected static boolean shouldValidateProperties() {
    return Boolean.valueOf(System.getProperty("NSValidateProperties", "true")).booleanValue();
  }
  
  protected static boolean _bundleUrlExists(URL url) {
    boolean urlPathExists = true;
    try {
      String protocol = url.getProtocol();
      if ("file".equals(protocol)) {
        File file = new File(url.getFile());
        urlPathExists = file.exists();
      } else if ("jar".equals(protocol)) {
        JarURLConnection urlConn = (JarURLConnection)url.openConnection();
        URL jarFileURL = urlConn.getJarFileURL();
        if (_bundleUrlExists(jarFileURL)) {
          if ("file".equals(jarFileURL.getProtocol())) {
            JarFile jarFile = new JarFile(jarFileURL.getPath());
            try {
              JarEntry jarEntry = (JarEntry)jarFile.getEntry(urlConn.getEntryName());
              if (jarEntry == null)
                urlPathExists = false; 
            } finally {
              jarFile.close();
            } 
          } else {
            throw new IllegalArgumentException("Unable to handle Jar File URL protocol: " + url);
          } 
        } else {
          urlPathExists = false;
        } 
      } else {
        throw new IllegalArgumentException("Unable to handle URL protocol: " + url);
      } 
    } catch (IOException e) {
      urlPathExists = true;
    } 
    return urlPathExists;
  }
  
  public abstract NSArray<String> bundleClassPackageNames();
  
  @Deprecated
  public abstract String bundlePath();
  
  public abstract URL bundlePathURL();
  
  public abstract String _bundleURLPrefix();
  
  public abstract NSArray<String> bundleClassNames();
  
  @Deprecated
  public abstract NSDictionary<String, Object> infoDictionary();
  
  public abstract URL _pathURLForResourcePath(String paramString, boolean paramBoolean);
  
  public abstract boolean isFramework();
  
  public abstract boolean _isCFBundle();
  
  public abstract boolean isJar();
  
  public abstract JarFile _jarFile();
  
  public abstract NSDictionary _jarFileLayout();
  
  @Deprecated
  public abstract boolean load();
  
  public abstract String name();
  
  @Deprecated
  public abstract String pathForResource(String paramString1, String paramString2, String paramString3);
  
  @Deprecated
  public abstract NSArray pathsForResources(String paramString1, String paramString2);
  
  public abstract Properties properties();
  
  @Deprecated
  public abstract String resourcePath();
  
  public abstract String resourcePathForLocalizedResourceNamed(String paramString1, String paramString2);
  
  public abstract NSArray<String> resourcePathsForDirectories(String paramString1, String paramString2);
  
  public abstract NSArray<String> resourcePathsForLocalizedResources(String paramString1, String paramString2);
  
  public abstract NSArray<String> resourcePathsForResources(String paramString1, String paramString2);
  
  public abstract void _simplePathsInDirectoryInJar(String paramString1, String paramString2, NSMutableArray<String> paramNSMutableArray1, String paramString3, NSMutableArray<String> paramNSMutableArray2);
  
  public abstract void _bundlesDidLoad();
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERFoundation/1.0/ERFoundation-1.0.jar!/com/webobjects/foundation/NSBundle.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */