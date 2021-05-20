package com.webobjects.foundation;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

public class NSProperties implements NSKeyValueCoding, NSKeyValueCodingAdditions {
  public static final String PropertiesDidChange = "PropertiesDidChange";
  
  public static final String PropertiesKey = "properties";
  
  private static final String _def = "-D";
  
  private static final char _dqc = '"';
  
  private static final String _dqs = "\"";
  
  private static final String _eqs = "=";
  
  private static final String _false = "false";
  
  private static final String _hyphen = "-";
  
  private static final String _no = "NO";
  
  private static final char _sqc = '\'';
  
  private static final String _sqs = "'";
  
  private static final String _true = "true";
  
  private static final String _yes = "YES";
  
  private static String _undefinedMarker = "-undefined-";
  
  private static NSProperties _sharedInstance;
  
  private Map<String, Object> _cache;
  
  private NSSizeLimitedLinkedHashMap<String, String> _appSpecificPropertyNames;
  
  private String _appName;
  
  private InitialContext _jndiContext;
  
  private Properties _jndiProperties;
  
  static {
    NSProperties properties;
  }
  
  static {
    String propertiesClassName = System.getProperty("NSProperties.className");
    if (propertiesClassName == null) {
      properties = new NSProperties();
    } else {
      try {
        properties = Class.forName(propertiesClassName).<NSProperties>asSubclass(NSProperties.class).newInstance();
      } catch (Throwable t) {
        throw new RuntimeException("Failed to create an NSProperties with the class named '" + propertiesClassName + "'.", t);
      } 
    } 
    if (NSValueUtilities.booleanValueWithDefault(System.getProperty("NSProperties.cacheEnabled"), false))
      properties.setCachingEnabled(true); 
    setSharedInstance(properties);
  }
  
  protected NSProperties() {
    initializeProperties();
  }
  
  protected void initializeProperties() {
    InitialContext ctx;
    Properties jndiProperties;
    try {
      ctx = new InitialContext();
      NamingEnumeration<NameClassPair> list = ctx.list("java:comp/env/wo");
      jndiProperties = new Properties();
      while (list.hasMore()) {
        NameClassPair pair = list.next();
        String name = pair.isRelative() ? ("java:comp/env/wo/" + pair.getName()) : pair.getName();
        Object obj = ctx.lookup(name);
        jndiProperties.setProperty(pair.getName(), obj.toString());
      } 
    } catch (NamingException e) {
      ctx = null;
      jndiProperties = null;
    } 
    this._jndiProperties = jndiProperties;
    this._jndiContext = ctx;
    this._appSpecificPropertyNames = new NSSizeLimitedLinkedHashMap<String, String>(128, false, null);
  }
  
  public static NSProperties sharedInstance() {
    return _sharedInstance;
  }
  
  public static void setSharedInstance(NSProperties sharedInstance) {
    if (_sharedInstance != null)
      _sharedInstance.unregisterForNotifications(); 
    _sharedInstance = sharedInstance;
    if (_sharedInstance != null)
      _sharedInstance.registerForNotifications(); 
  }
  
  public Object _removePropertyForKey(String key) {
    if (this._jndiContext == null)
      return System.getProperties().remove(key); 
    return this._jndiProperties.remove(key);
  }
  
  public Object _setPropertyForKey(String value, String key) {
    if (this._jndiContext == null)
      return System.setProperty(key, value); 
    return this._jndiProperties.setProperty(key, value);
  }
  
  public void _replaceProperties(Properties props) {
    if (this._jndiContext == null) {
      System.setProperties(props);
    } else {
      this._jndiProperties = props;
    } 
  }
  
  public Properties _properties() {
    Properties ret;
    if (this._jndiContext == null) {
      ret = System.getProperties();
    } else {
      ret = this._jndiProperties;
    } 
    return ret;
  }
  
  public String _propertyForKeyWithDefault(String key, String defaultValue) {
    String value = null;
    if (this._jndiContext != null)
      value = this._jndiProperties.getProperty(key); 
    if (value == null) {
      value = System.getProperty(key);
      if (value == null)
        value = defaultValue; 
    } 
    return value;
  }
  
  public Object removePropertyForKey(String key) {
    String actualKey = _applicationSpecificKey(key);
    if (this._cache != null)
      this._cache.remove(actualKey); 
    synchronized (this._appSpecificPropertyNames) {
      if (!this._appSpecificPropertyNames.isEmpty())
        this._appSpecificPropertyNames.clear(); 
    } 
    return _removePropertyForKey(actualKey);
  }
  
  public Object setPropertyForKey(String value, String key) {
    String actualKey = _applicationSpecificKey(key);
    if (this._cache != null)
      this._cache.remove(actualKey); 
    synchronized (this._appSpecificPropertyNames) {
      if (!this._appSpecificPropertyNames.isEmpty())
        this._appSpecificPropertyNames.clear(); 
    } 
    return _setPropertyForKey(value, actualKey);
  }
  
  public void replaceProperties(Properties props) {
    _replaceProperties(props);
    if (this._cache != null)
      this._cache.clear(); 
  }
  
  public Properties properties() {
    return _properties();
  }
  
  public String propertyForKeyWithDefault(String key, String defaultValue) {
    String actualKey = _applicationSpecificKey(key);
    return _propertyForKeyWithDefault(actualKey, defaultValue);
  }
  
  public void setCachingEnabled(boolean cachingEnabled) {
    if (cachingEnabled) {
      if (this._cache == null)
        this._cache = Collections.synchronizedMap(new HashMap<String, Object>()); 
    } else {
      this._cache = null;
    } 
  }
  
  protected void registerForNotifications() {
    NSNotificationCenter.defaultCenter().addObserver(this, new NSSelector("propertiesChanged", new Class[] { NSNotification.class }), "PropertiesDidChange", null);
  }
  
  protected void unregisterForNotifications() {
    NSNotificationCenter.defaultCenter().removeObserver(this, "PropertiesDidChange", null);
  }
  
  public void propertiesChanged(NSNotification notification) {
    NSDictionary userInfo = notification.userInfo();
    if (userInfo == null) {
      clearCache();
    } else {
      NSArray<String> propertyKeys = (NSArray<String>)userInfo.objectForKey("properties");
      if (propertyKeys == null) {
        clearCache();
      } else {
        for (String key : propertyKeys)
          clearCacheForKey(key); 
      } 
    } 
  }
  
  public void clearCache() {
    synchronized (this._appSpecificPropertyNames) {
      this._appSpecificPropertyNames.clear();
    } 
    if (this._cache != null)
      this._cache.clear(); 
  }
  
  public void clearCacheForKey(String key) {
    if (this._cache != null) {
      String actualKey = _applicationSpecificKey(key);
      this._cache.remove(actualKey);
    } 
  }
  
  public void setCachedObjectForKey(Object value, String key) {
    if (this._cache != null) {
      String actualKey = _applicationSpecificKey(key);
      this._cache.put(actualKey, (value == null) ? _undefinedMarker : value);
    } 
  }
  
  public Object cachedObjectForKey(String key) {
    String actualKey = _applicationSpecificKey(key);
    Object value = null;
    if (this._cache != null)
      value = this._cache.get(actualKey); 
    if (value == null)
      value = _propertyForKeyWithDefault(actualKey, null); 
    return value;
  }
  
  public void _setAppName(String appName) {
    this._appName = appName;
    synchronized (this._appSpecificPropertyNames) {
      this._appSpecificPropertyNames.clear();
    } 
  }
  
  protected String _applicationSpecificKey(String key) {
    synchronized (this._appSpecificPropertyNames) {
      String appSpecificPropertyName = this._appSpecificPropertyNames.get(key);
      if (appSpecificPropertyName == null) {
        if (this._appName != null) {
          appSpecificPropertyName = String.valueOf(key) + "." + this._appName;
        } else {
          appSpecificPropertyName = key;
        } 
        String value = _propertyForKeyWithDefault(appSpecificPropertyName, null);
        if (value == null)
          appSpecificPropertyName = key; 
        this._appSpecificPropertyNames.put(key, appSpecificPropertyName);
      } 
      return appSpecificPropertyName;
    } 
  }
  
  public void takeValueForKey(Object value, String key) {
    if (value == null) {
      removePropertyForKey(key);
    } else {
      setPropertyForKey((String)value, key);
    } 
  }
  
  public Object valueForKey(String key) {
    return propertyForKeyWithDefault(key, null);
  }
  
  public void takeValueForKeyPath(Object value, String keyPath) {
    takeValueForKey(value, keyPath);
  }
  
  public Object valueForKeyPath(String keyPath) {
    return valueForKey(keyPath);
  }
  
  public static void _setProperties(Properties props) {
    sharedInstance().replaceProperties(props);
  }
  
  public static Object _setProperty(String name, String value) {
    return sharedInstance().setPropertyForKey(value, name);
  }
  
  public static Properties _getProperties() {
    return sharedInstance().properties();
  }
  
  public static String getProperty(String name) {
    return getProperty(name, null);
  }
  
  public static String getProperty(String name, String defaultValue) {
    return sharedInstance().propertyForKeyWithDefault(name, defaultValue);
  }
  
  public static NSArray arrayForKey(String aKey) {
    return arrayForKeyWithDefault(aKey, null);
  }
  
  public static NSArray arrayForKeyWithDefault(String aKey, NSArray defaultValue) {
    NSArray value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof NSArray) {
      value = (NSArray)cachedValue;
    } else {
      value = NSValueUtilities.arrayValueWithDefault(cachedValue, null);
      properties.setCachedObjectForKey(value, aKey);
      if (value == null)
        value = defaultValue; 
    } 
    return value;
  }
  
  public static NSSet setForKey(String aKey) {
    return setForKeyWithDefault(aKey, null);
  }
  
  public static NSSet setForKeyWithDefault(String aKey, NSSet defaultValue) {
    NSSet value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof NSSet) {
      value = (NSSet)cachedValue;
    } else {
      value = NSValueUtilities.setValueWithDefault(cachedValue, null);
      properties.setCachedObjectForKey(value, aKey);
      if (value == null)
        value = defaultValue; 
    } 
    return value;
  }
  
  public static boolean booleanForKey(String aKey) {
    return booleanForKeyWithDefault(aKey, false);
  }
  
  public static boolean booleanForKeyWithDefault(String aKey, boolean defaultValue) {
    boolean value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof Boolean) {
      value = ((Boolean)cachedValue).booleanValue();
    } else {
      Boolean objValue = NSValueUtilities.BooleanValueWithDefault(cachedValue, null);
      properties.setCachedObjectForKey(objValue, aKey);
      if (objValue == null) {
        value = defaultValue;
      } else {
        value = objValue.booleanValue();
      } 
    } 
    return value;
  }
  
  public static NSData dataForKey(String aKey) {
    return dataForKeyWithDefault(aKey, null);
  }
  
  public static NSData dataForKeyWithDefault(String aKey, NSData defaultValue) {
    NSData value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof NSData) {
      value = (NSData)cachedValue;
    } else {
      value = NSValueUtilities.dataValueWithDefault(cachedValue, null);
      properties.setCachedObjectForKey(value, aKey);
      if (value == null)
        value = defaultValue; 
    } 
    return value;
  }
  
  public static NSDictionary dictionaryForKey(String aKey) {
    return dictionaryForKeyWithDefault(aKey, null);
  }
  
  public static NSDictionary dictionaryForKeyWithDefault(String aKey, NSDictionary defaultValue) {
    NSDictionary value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof NSDictionary) {
      value = (NSDictionary)cachedValue;
    } else {
      value = NSValueUtilities.dictionaryValueWithDefault(cachedValue, null);
      properties.setCachedObjectForKey(value, aKey);
      if (value == null)
        value = defaultValue; 
    } 
    return value;
  }
  
  public static double doubleForKey(String aKey) {
    return doubleForKeyWithDefault(aKey, 0.0D);
  }
  
  public static double doubleForKeyWithDefault(String aKey, double defaultValue) {
    double value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof Double) {
      value = ((Double)cachedValue).doubleValue();
    } else {
      Double objValue = NSValueUtilities.DoubleValueWithDefault(cachedValue, null);
      properties.setCachedObjectForKey(objValue, aKey);
      if (objValue == null) {
        value = defaultValue;
      } else {
        value = objValue.doubleValue();
      } 
    } 
    return value;
  }
  
  public static float floatForKey(String aKey) {
    return floatForKeyWithDefault(aKey, 0.0F);
  }
  
  public static float floatForKeyWithDefault(String aKey, float defaultValue) {
    float value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof Float) {
      value = ((Float)cachedValue).floatValue();
    } else {
      Float objValue = NSValueUtilities.FloatValueWithDefault(cachedValue, null);
      properties.setCachedObjectForKey(objValue, aKey);
      if (objValue == null) {
        value = defaultValue;
      } else {
        value = objValue.floatValue();
      } 
    } 
    return value;
  }
  
  public static int integerForKey(String aKey) {
    return intForKey(aKey);
  }
  
  public static int integerForKeyWithDefault(String aKey, int defaultValue) {
    return intForKeyWithDefault(aKey, defaultValue);
  }
  
  public static int intForKey(String aKey) {
    return intForKeyWithDefault(aKey, 0);
  }
  
  public static int intForKeyWithDefault(String aKey, int defaultValue) {
    int value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof Integer) {
      value = ((Integer)cachedValue).intValue();
    } else {
      Integer objValue = NSValueUtilities.IntegerValueWithDefault(cachedValue, null);
      properties.setCachedObjectForKey(objValue, aKey);
      if (objValue == null) {
        value = defaultValue;
      } else {
        value = objValue.intValue();
      } 
    } 
    return value;
  }
  
  public static <T> Class<T> classForKey(String aKey) {
    return classForKeyWithDefault(aKey, null);
  }
  
  public static <T> Class<T> classForKeyWithDefault(String aKey, Class<T> defaultValue) {
    Class<T> value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof Class) {
      value = (Class<T>)cachedValue;
    } else if (cachedValue == null) {
      value = defaultValue;
      properties.setCachedObjectForKey(null, aKey);
    } else if (cachedValue instanceof String) {
      String strValue = (String)cachedValue;
      if (strValue.length() > 0) {
        value = _NSUtilities.classWithName((String)cachedValue);
        if (value == null)
          throw new IllegalArgumentException("Failed to load the class named '" + cachedValue + "'."); 
        properties.setCachedObjectForKey(value, aKey);
      } else {
        value = defaultValue;
        properties.setCachedObjectForKey(null, aKey);
      } 
    } else {
      throw new IllegalArgumentException("Failed to parse a class name from the value '" + cachedValue + "'.");
    } 
    return value;
  }
  
  public static long longForKey(String aKey) {
    return longForKeyWithDefault(aKey, 0L);
  }
  
  public static long longForKeyWithDefault(String aKey, long defaultValue) {
    long value;
    NSProperties properties = sharedInstance();
    Object cachedValue = properties.cachedObjectForKey(aKey);
    if (cachedValue == _undefinedMarker) {
      value = defaultValue;
    } else if (cachedValue instanceof Long) {
      value = ((Long)cachedValue).longValue();
    } else {
      Long objValue = NSValueUtilities.LongValueWithDefault(cachedValue, null);
      properties.setCachedObjectForKey(objValue, aKey);
      if (objValue == null) {
        value = defaultValue;
      } else {
        value = objValue.longValue();
      } 
    } 
    return value;
  }
  
  public static void setPropertiesFromArgv(String[] argv) {
    try {
      Class.forName("com.webobjects.foundation.NSBundle");
    } catch (ClassNotFoundException classNotFoundException) {}
    if (argv != null)
      insertCommandLineArguments(argv); 
  }
  
  public static String stringForKey(String aKey) {
    return sharedInstance().propertyForKeyWithDefault(aKey, null);
  }
  
  public static String stringForKeyWithDefault(String aKey, String defaultValue) {
    return sharedInstance().propertyForKeyWithDefault(aKey, defaultValue);
  }
  
  public static NSDictionary valuesFromArgv(String[] argv) {
    int argCount = argv.length;
    NSMutableDictionary values = new NSMutableDictionary(argCount);
    for (int i = 0; i < argCount; i++) {
      String argument = argv[i];
      int argumentLength = argument.length();
      boolean argIsQuoted = !((!argument.startsWith("\"") || !argument.endsWith("\"")) && (!argument.startsWith("'") || !argument.endsWith("'")));
      boolean hasBeenParsed = false;
      if (argIsQuoted ? (argument.indexOf("-D") == 1) : argument.startsWith("-D")) {
        int indexOfEqualSign;
        if ((indexOfEqualSign = argument.indexOf("=")) != -1 && (
          argIsQuoted ? (indexOfEqualSign < argumentLength - 2 && indexOfEqualSign > 3) : (indexOfEqualSign < argumentLength && indexOfEqualSign > 2))) {
          String argumentName, argumentValue;
          if (argIsQuoted) {
            argumentName = argument.substring(3, indexOfEqualSign);
            argumentValue = argument.substring(indexOfEqualSign + 1, argumentLength - 1);
          } else {
            boolean valIsQuoted = (indexOfEqualSign < argumentLength - 2 && ((
              argument.charAt(indexOfEqualSign + 1) == '"' && argument.endsWith("\"")) || (argument.charAt(indexOfEqualSign + 1) == '\'' && argument.endsWith("'"))));
            argumentName = argument.substring(2, indexOfEqualSign);
            argumentValue = valIsQuoted ? argument.substring(indexOfEqualSign + 2, argumentLength - 1) : argument.substring(indexOfEqualSign + 1, argumentLength);
          } 
          values.setObjectForKey(argumentValue, argumentName);
          hasBeenParsed = true;
        } 
      } 
      if (!hasBeenParsed && argument.startsWith("-") && argument.length() > 1) {
        String argumentName = argument.substring(1);
        if (i + 1 < argCount) {
          String argumentValue = argv[i + 1];
          i++;
          if (argumentValue.equalsIgnoreCase("YES") || argumentValue.equalsIgnoreCase("true")) {
            argumentValue = "true";
          } else if (argumentValue.equalsIgnoreCase("NO") || argumentValue.equalsIgnoreCase("false")) {
            argumentValue = "false";
          } 
          values.setObjectForKey(argumentValue, argumentName);
        } 
      } 
    } 
    return (NSDictionary)values;
  }
  
  private static void insertCommandLineArguments(String[] argv) {
    NSDictionary values = valuesFromArgv(argv);
    NSArray keys = values.allKeys();
    int count = keys.count();
    for (int i = 0; i < count; i++) {
      Object key = keys.objectAtIndex(i);
      _setProperty((String)key, (String)values.objectForKey(key));
    } 
  }
  
  private static String _mainBundleName = null;
  
  public static void _setMainBundleName(String name) {
    _mainBundleName = name;
  }
  
  public static String _mainBundleName() {
    return _mainBundleName;
  }
  
  public static class NestedProperties extends Properties {
    public static final String IncludePropsSoFarKey = ".includePropsSoFar";
    
    public static final String IncludePropsKey = ".includeProps";
    
    private Stack<File> _files = new Stack<File>();
    
    public NestedProperties() {}
    
    public NestedProperties(Properties defaults) {
      super(defaults);
    }
    
    public synchronized Object put(Object key, Object value) {
      if (".includeProps".equals(key)) {
        String propsPath, propsFileName = (String)value;
        File propsFile = new File(propsFileName);
        if (!propsFile.isAbsolute()) {
          File cwd = null;
          if (this._files.size() > 0) {
            cwd = this._files.peek();
          } else {
            cwd = new File(System.getProperty("user.home"));
          } 
          propsFile = new File(cwd, propsFileName);
        } 
        try {
          propsPath = propsFile.getCanonicalPath();
        } catch (IOException e) {
          throw new RuntimeException("Failed to canonicalize the property file '" + propsFile + "'.", e);
        } 
        String existingIncludeProps = getProperty(".includePropsSoFar");
        if (existingIncludeProps == null)
          existingIncludeProps = ""; 
        if (existingIncludeProps.indexOf(propsPath) > -1) {
          NSLog.err.appendln("NSProperties.NestedProperties.load(): Possible recursive includeProps detected. '" + propsPath + "' was included in more than one of the following files: " + existingIncludeProps);
          NSLog.err.appendln("NSProperties.NestedProperties.load() cannot proceed - QUITTING!");
          System.exit(1);
        } 
        if (existingIncludeProps.length() > 0)
          existingIncludeProps = String.valueOf(existingIncludeProps) + ", "; 
        existingIncludeProps = String.valueOf(existingIncludeProps) + propsPath;
        super.put(".includePropsSoFar", existingIncludeProps);
        try {
          load(propsFile, NSProperties._shouldRequireSymlinkedGlobalAndIncludeProperties());
        } catch (IOException e) {
          throw new RuntimeException("Failed to load the property file '" + value + "'.", e);
        } 
        return null;
      } 
      return super.put(key, value);
    }
    
    public synchronized void load(File propsFile, boolean requireSymlink) throws IOException {
      File canonicalPropsFile;
      NSLog.out.appendln("NSProperties.NestedProperties.load(): " + propsFile);
      if (requireSymlink) {
        canonicalPropsFile = _NSFileUtilities.resolveLink(propsFile.getPath(), propsFile.getName());
      } else {
        canonicalPropsFile = propsFile.getCanonicalFile();
      } 
      this._files.push(canonicalPropsFile.getParentFile());
      try {
        BufferedInputStream is = new BufferedInputStream(new FileInputStream(canonicalPropsFile));
        try {
          load(is);
        } finally {
          is.close();
        } 
      } finally {
        this._files.pop();
      } 
    }
  }
  
  public static boolean _shouldRequireSymlinkedGlobalAndIncludeProperties() {
    return Boolean.valueOf(System.getProperty("NSRequireSymlinkedGlobalAndIncludeProperties", "false")).booleanValue();
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERFoundation/1.0/ERFoundation-1.0.jar!/com/webobjects/foundation/NSProperties.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */