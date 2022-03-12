//
// ERXLocalizer.java
// Project armehaut
//
// Created by ak on Sun Apr 14 2002
//
package er.extensions.localization;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.DateFormatSymbols;
import java.text.Format;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSKeyValueCodingAdditions;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSNumberFormatter;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSTimestampFormatter;

import er.extensions.appserver.ERXWOContext;
import er.extensions.formatters.ERXNumberFormatter;
import er.extensions.formatters.ERXTimestampFormatter;
import er.extensions.foundation.ERXFileNotificationCenter;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXSimpleTemplateParser;
import er.extensions.foundation.ERXThreadStorage;
import er.extensions.foundation.ERXUtilities;

/**
 * Provides KVC access to localization.
 * 
 * Monitors a set of files in all loaded frameworks and returns a string given a key for a language. These types of keys
 * are acceptable in the monitored files:
 * 
 * <pre><code>
 *   &quot;this is a test&quot; = &quot;some test&quot;;
 *   &quot;unittest.key.path.as.string&quot; = &quot;some test&quot;;
 *   &quot;unittest&quot; = {
 *      &quot;key&quot; = { 
 *          &quot;path&quot; = { 
 *              &quot;as&quot; = {
 *                  &quot;dict&quot;=&quot;some test&quot;;
 *               };
 *          };
 *      };
 *   };
 * </code></pre>
 * 
 * Note that if you only call for <code>unittest</code>, you'll get a dictionary not a string. So you can localize
 * more complex objects than strings.
 * <p>
 * If you set the base class of your session to ERXSession, you can then use this code in your components:
 * 
 * <pre><code>
 *  valueForKeyPath(&quot;session.localizer.this is a test&quot;)
 *  valueForKeyPath(&quot;session.localizer.unittest.key.path.as.string&quot;)
 *  valueForKeyPath(&quot;session.localizer.unittest.key.path.as.dict&quot;)
 * </code></pre>
 * 
 * For sessionless Apps, you must use another method to get at the requested language and then call the localizer via:
 * 
 * <pre><code>
 *  ERXLocalizer l = ERXLocalizer.localizerForLanguages(languagesThisUserCanHandle) or
 *  ERXLocalizer l = ERXLocalizer.localizerForLanguage(&quot;German&quot;)
 * </code></pre>
 * 
 * These are the defaults can be set (listed with their current defaults):
 * 
 * <pre><code>
 *  er.extensions.ERXLocalizer.defaultLanguage=English
 *  er.extensions.ERXLocalizer.fileNamesToWatch=(&quot;Localizable.strings&quot;,&quot;ValidationTemplate.strings&quot;)
 *  er.extensions.ERXLocalizer.availableLanguages=(English,German)
 *  er.extensions.ERXLocalizer.frameworkSearchPath=(app,ERDirectToWeb,ERExtensions)
 * </code></pre>
 * 
 * There are also methods that pluralize using normal English pluralizing rules (y -&gt; ies, x -&gt; xes etc). You can provide
 * your own plural strings by using a dict entry:
 * 
 * <pre><code>
 *  localizerExceptions = {
 *      &quot;Table.0&quot; = &quot;Table&quot;; 
 *      &quot;Table&quot; = &quot;Tables&quot;;
 *      ...
 *  };
 * </code></pre>
 * 
 * in your Localizable.strings. <code>Table.0</code> meaning no "Table", <code>Table.1</code> one table and
 * <code>Table</code> any other number. <b>Note:</b> unlike all other keys, you need to give the translated value
 * ("Tisch" for "Table" in German) as the key, not the untranslated one. This is because this method is mainly called
 * via d2wContext.displayNameForProperty which is already localized.
 */

public class ERXLocalizer implements NSKeyValueCoding, NSKeyValueCodingAdditions {

	private static final Logger log = LoggerFactory.getLogger(ERXLocalizer.class);
	private static final Logger createdKeysLog = LoggerFactory.getLogger(ERXLocalizer.class.getName() + ".createdKeys");

	public static final String LocalizationDidResetNotification = "LocalizationDidReset";

	private static final String KEY_LOCALIZER_EXCEPTIONS = "localizerExceptions";
	private static boolean isLocalizationEnabled = true;
	private static boolean isInitialized = false;
	private static Boolean _useLocalizedFormatters;
	private static Boolean _fallbackToDefaultLanguage;
	private static Observer observer = new Observer();
	private static List<URL> monitoredFiles = new NSMutableArray<>();
	private static final char _localizerMethodIndicatorCharacter = '@';
	private static NSArray<String> fileNamesToWatch;
	private static NSArray<String> frameworkSearchPath;
	private static NSArray<String> availableLanguages;
	private static String defaultLanguage;

	private static NSMutableDictionary<String, ERXLocalizer> localizers = new NSMutableDictionary<>();

	public static class Observer {
		public void fileDidChange(NSNotification n) {
			ERXLocalizer.resetCache();
			NSNotificationCenter.defaultCenter().postNotification(LocalizationDidResetNotification, null);
		}
	}

	public static void initialize() {
		if (!isInitialized) {
			isLocalizationEnabled = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXLocalizer.isLocalizationEnabled", true);
			isInitialized = true;
		}
	}

	public static boolean isLocalizationEnabled() {
		return isLocalizationEnabled;
	}

	public static void setIsLocalizationEnabled(boolean value) {
		isLocalizationEnabled = value;
	}

	/**
	 * Returns the current localizer for the current thread. Note that the localizer for a given session is pushed onto
	 * the thread when a session awakes and is nulled out when a session sleeps. In case there is no localizer set, it tries to
	 * pull it from the current WOContext or the default language.
	 * 
	 * @return the current localizer that has been pushed into thread storage.
	 */
	public static ERXLocalizer currentLocalizer() {
		ERXLocalizer current = (ERXLocalizer) ERXThreadStorage.valueForKey("localizer");

		if (current == null) {
			if (!isInitialized) {
				initialize();
			}
			WOContext context = ERXWOContext.currentContext();
			// set the current localizer
			if (context != null && context.request() != null && context.request().browserLanguages() != null) {
				current = ERXLocalizer.localizerForLanguages(context.request().browserLanguages());
				ERXLocalizer.setCurrentLocalizer(current);
			}
			else {
				current = defaultLocalizer();
			}
		}

		return current;
	}

	/**
	 * Sets a localizer for the current thread. This is accomplished by using the object {@link ERXThreadStorage}
	 * 
	 * @param currentLocalizer to set in thread storage for the current thread.
	 */
	public static void setCurrentLocalizer(ERXLocalizer currentLocalizer) {
		ERXThreadStorage.takeValueForKey(currentLocalizer, "localizer");
	}

	/**
	 * @return localizer for the default language
	 */
	public static ERXLocalizer defaultLocalizer() {
		return localizerForLanguage(defaultLanguage());
	}

	public static ERXLocalizer englishLocalizer() {
		return localizerForLanguage("English");
	}

	public static ERXLocalizer localizerForRequest(WORequest request) {
		return localizerForLanguages(request.browserLanguages());
	}

	/**
	 * Resets the localizer cache. If WOCaching is enabled then after being reinitialize all of the localizers will be reloaded.
	 */
	private static void resetCache() {
		initialize();
		if (WOApplication.application().isCachingEnabled()) {
			Enumeration<ERXLocalizer> e = localizers.objectEnumerator();
			while (e.hasMoreElements()) {
				e.nextElement().load();
			}
		}
		else {
			localizers = new NSMutableDictionary<>();
		}
	}

	private void addToCreatedKeys(Object value, String key) {
		if (key != null && value != null) {
			createdKeys.takeValueForKey(value, key);
			if (key.indexOf(" ") > 0) {
				log.info("Value added: {}->{} in {}", key, value, NSPropertyListSerialization.stringFromPropertyList(ERXWOContext.componentPath(ERXWOContext.currentContext())));
			}
		}
	}

	/**
	 * @return The best localizer for a set of languages.
	 */
	public static ERXLocalizer localizerForLanguages(NSArray<String> languages) {

		if (!isLocalizationEnabled) {
			return createLocalizerForLanguage("Nonlocalized", false);
		}

		if (languages == null || languages.isEmpty()) {
			return localizerForLanguage(defaultLanguage());
		}
		
		ERXLocalizer l = null;

		Enumeration<String> e = languages.objectEnumerator();

		while (e.hasMoreElements()) {
			String language = e.nextElement();
			l = localizers.objectForKey(language);

			if (l != null) {
				return l;
			}

			if (availableLanguages().containsObject(language)) {
				return localizerForLanguage(language);
			}

			// try to do a fallback to the base language if this was regionalized
			int index = language.indexOf('_');

			if (index > 0) {
				language = language.substring(0, index);
				if (availableLanguages().containsObject(language)) {
					return localizerForLanguage(language);
				}
			}
		}

		return localizerForLanguage(languages.objectAtIndex(0));
	}

	private static NSArray<String> _languagesWithoutPluralForm = new NSArray<>(new String[] { "Japanese" });

	/**
	 * Get a localizer for a specific language. If none could be found or language
	 * is <code>null</code> a localizer for the {@link #defaultLanguage()} is returned.
	 * 
	 * @param language name of the requested language
	 */
	public static ERXLocalizer localizerForLanguage(String language) {
		if (!isLocalizationEnabled)
			return createLocalizerForLanguage("Nonlocalized", false);

		if (language == null) {
			language = defaultLanguage();
		}
		ERXLocalizer l = null;
		l = localizers.objectForKey(language);
		if (l == null) {
			if (availableLanguages().containsObject(language)) {
				if (_languagesWithoutPluralForm.containsObject(language))
					l = createLocalizerForLanguage(language, false);
				else
					l = createLocalizerForLanguage(language, true);
			}
			else {
				l = localizers.objectForKey(defaultLanguage());
				if (l == null) {
					if (_languagesWithoutPluralForm.containsObject(defaultLanguage()))
						l = createLocalizerForLanguage(defaultLanguage(), false);
					else
						l = createLocalizerForLanguage(defaultLanguage(), true);
					localizers.setObjectForKey(l, defaultLanguage());
				}
			}
			localizers.setObjectForKey(l, language);
		}
		return l;
	}

	/**
	 * Returns the default language (English) or the contents of the
	 * <code>er.extensions.ERXLocalizer.defaultLanguage</code> property.
	 * 
	 * @return default language name
	 */
	public static String defaultLanguage() {
		if (defaultLanguage == null) {
			defaultLanguage = ERXProperties.stringForKeyWithDefault("er.extensions.ERXLocalizer.defaultLanguage", "English");
		}
		return defaultLanguage;
	}

	/**
	 * Sets the default language.
	 */
	public static void setDefaultLanguage(String value) {
		defaultLanguage = value;
		resetCache();
	}

	public static NSArray<String> fileNamesToWatch() {
		if (fileNamesToWatch == null) {
			fileNamesToWatch = ERXProperties.arrayForKeyWithDefault("er.extensions.ERXLocalizer.fileNamesToWatch", new NSArray<>(new String[] { "Localizable.strings", "ValidationTemplate.strings" }));
			if (log.isDebugEnabled())
        log.debug("FileNamesToWatch: {}", fileNamesToWatch.componentsJoinedByString(" / "));
		}
		return fileNamesToWatch;
	}

	public static void setFileNamesToWatch(NSArray<String> value) {
		fileNamesToWatch = value;
		resetCache();
	}

	public static NSArray<String> availableLanguages() {
		if (availableLanguages == null) {
			availableLanguages = ERXProperties.arrayForKeyWithDefault("er.extensions.ERXLocalizer.availableLanguages", new NSArray(new String[] { "English", "German", "Japanese" }));
			if (log.isDebugEnabled())
        log.debug("AvailableLanguages: {}", availableLanguages.componentsJoinedByString(" / "));
		}
		return availableLanguages;
	}

	public static void setAvailableLanguages(NSArray<String> value) {
		availableLanguages = value;
		resetCache();
	}

	public static NSArray<String> frameworkSearchPath() {
		if (frameworkSearchPath == null) {
		  frameworkSearchPath = ERXProperties.arrayForKey("er.extensions.ERXLocalizer.frameworkSearchPath");
		  if(frameworkSearchPath == null) {
		    NSMutableArray<String> defaultValue = new NSMutableArray<>();
		    for (Enumeration<NSBundle> e = NSBundle.frameworkBundles().objectEnumerator(); e.hasMoreElements();) {
		      NSBundle bundle = e.nextElement();
		      String name = bundle.name();
		  
		      // Check the Properties and Add it Automatically
		      String propertyName = "er.extensions." + name + ".hasLocalization";
		      boolean hasLocalization = ERXProperties.booleanForKeyWithDefault(propertyName, true);
		  
          if(name.equals("ERCoreBusinessLogic") || name.equals("ERDirectToWeb") || name.equals("ERExtensions")){ //|| name.startsWith("Java")
            // do nothing yet, because will add later
          } else if(hasLocalization) { 
            defaultValue.addObject(name);
          }
				}
				if(NSBundle.bundleForName("ERCoreBusinessLogic") != null) 
					defaultValue.addObject("ERCoreBusinessLogic");
				if(NSBundle.bundleForName("ERDirectToWeb") != null) 
					defaultValue.addObject("ERDirectToWeb");
				if(NSBundle.bundleForName("ERExtensions") != null) 
					defaultValue.addObject("ERExtensions");
				defaultValue.insertObjectAtIndex("app", 0);
				frameworkSearchPath = defaultValue;
			}
			if (log.isDebugEnabled())
				log.debug("FrameworkSearchPath: {}", frameworkSearchPath.componentsJoinedByString(" / "));
		}
		return frameworkSearchPath;
	}

	public static void setFrameworkSearchPath(NSArray<String> value) {
		frameworkSearchPath = value;
		resetCache();
	}

	/**
	 * Creates a localizer for a given language and with an indication if the language supports plural forms. To provide
	 * your own subclass of an ERXLocalizer you can set the system property
	 * <code>er.extensions.ERXLocalizer.pluralFormClassName</code> or
	 * <code>er.extensions.ERXLocalizer.nonPluralFormClassName</code>.
	 * 
	 * @param language name to construct the localizer for
	 * @param pluralForm denotes if the language supports the plural form
	 * @return a localizer for the given language
	 */
	protected static ERXLocalizer createLocalizerForLanguage(String language, boolean pluralForm) {
		ERXLocalizer localizer = null;
		String className = null;
		if (pluralForm) {
			className = ERXProperties.stringForKeyWithDefault("er.extensions.ERXLocalizer.pluralFormClassName", ERXLocalizer.class.getName());
		}
		else {
			throw new RuntimeException( "Sorry, I've deleted ERXNonPluralFormLocalizer" ); // FIXME: Hugi 2021-12-18
//			className = ERXProperties.stringForKeyWithDefault("er.extensions.ERXLocalizer.nonPluralFormClassName", ERXNonPluralFormLocalizer.class.getName());
		}
		try {
			Class localizerClass = Class.forName(className);
			Constructor constructor = localizerClass.getConstructor(new Class[] { String.class });
			localizer = (ERXLocalizer) constructor.newInstance(new Object[] { language });
		}
		catch (Exception e) {
			log.error("Unable to create localizer for language '{}' class name: {} exception: {}, will use default classes.", language, className, e.getMessage(), e);
		}
		if (localizer == null) {
			if (pluralForm) {
				localizer = new ERXLocalizer(language);
			}
			else {
				throw new RuntimeException( "Sorry, I've deleted ERXNonPluralFormLocalizer" ); // FIXME: Hugi 2021-12-18
//				localizer = new ERXNonPluralFormLocalizer(language);
			}
		}
		return localizer;
	}

	public static void setLocalizerForLanguage(ERXLocalizer l, String language) {
		localizers.setObjectForKey(l, language);
	}

	protected NSMutableDictionary<String, Object> cache;
	private NSMutableDictionary<String, Object> createdKeys;
	private String NOT_FOUND = "**NOT_FOUND**";
	protected Map<String, Format> _dateFormatters = new Hashtable<>();
	protected Map<String, Format> _numberFormatters = new Hashtable<>();
	protected String language;
	protected Locale locale;
	
	public ERXLocalizer(String aLanguage) {
		language = aLanguage;
		cache = new NSMutableDictionary<>();
		createdKeys = new NSMutableDictionary<>();

		// We first check to see if we have a locale register for the language name
		String shortLanguage = ERXProperties.stringForKey("er.extensions.ERXLocalizer." + aLanguage + ".locale");

		// Let's go fishing
		if (shortLanguage == null) {
			NSDictionary<String, Object> dict = ERXUtilities.dictionaryFromPropertyList("Languages", "JavaWebObjects");
			if (dict != null) {
				NSArray<String> keys = dict.allKeysForObject(aLanguage);
				if (keys.count() > 0) {
					shortLanguage = keys.objectAtIndex(0);
					if (keys.count() > 1) {
						log.info("Found multiple entries for language '{}' in Language.plist file! Found keys: {}", aLanguage, keys);
					}
				}
			}
			else {
				log.info("No Languages.plist found in JavaWebObjects bundle.");
			}
		}
		if (shortLanguage != null) {
			locale = new Locale(shortLanguage);
		}
		else {
			log.info("Locale for {} not found! Using default locale: {}", aLanguage, Locale.getDefault());
			locale = Locale.getDefault();
		}
		load();
	}

	public NSDictionary<String, Object> cache() {
		return cache;
	}

	public void load() {
		cache.removeAllObjects();
		createdKeys.removeAllObjects();

		if (log.isDebugEnabled())
		  log.debug("Loading templates for language: {} for files: {} with search path: {}", language, fileNamesToWatch().componentsJoinedByString(" / "), frameworkSearchPath().componentsJoinedByString(" / "));

		NSArray<String> languages = new NSArray<>(language);
		Enumeration<String> fn = fileNamesToWatch().objectEnumerator();
		while (fn.hasMoreElements()) {
			String fileName = fn.nextElement();
			Enumeration<String> fr = frameworkSearchPath().reverseObjectEnumerator();
			while (fr.hasMoreElements()) {
				String framework = fr.nextElement();

				URL path = WOApplication.application().resourceManager().pathURLForResourceNamed(fileName, framework, languages);
				if (path != null) {
					try {
						framework = "app".equals(framework) ? null : framework;
						if(log.isDebugEnabled())
						  log.debug("Loading: {} - {} - {} {}", fileName, (framework == null ? "app" : framework), languages.componentsJoinedByString(" / "), path);
						
						NSDictionary<String, Object> dict = (NSDictionary<String, Object>) readPropertyListFromFileInFramework(fileName, framework, languages);
						

						// HACK: ak we have could have a collision between the search path for validation strings and
						// the normal localized strings.
						// FIXME: Disabled all of this when deleting ERXValidationFactory. Remove. // Hugi 2021-12-18
//						if (fileName.indexOf(ERXValidationFactory.VALIDATION_TEMPLATE_PREFIX) == 0) {
//							NSMutableDictionary<String, Object> newDict = new NSMutableDictionary<>();
//							for (Enumeration<String> keys = dict.keyEnumerator(); keys.hasMoreElements();) {
//								String key = keys.nextElement();
//								newDict.setObjectForKey(dict.objectForKey(key), ERXValidationFactory.VALIDATION_TEMPLATE_PREFIX + key);
//							}
//							dict = newDict;
//						}

						
						
						addEntriesToCache(dict);
						if (!WOApplication.application().isCachingEnabled()) {
							synchronized (monitoredFiles) {
								if (!monitoredFiles.contains(path)) {
									ERXFileNotificationCenter.defaultCenter().addObserver(observer, ERXUtilities.notificationSelector("fileDidChange"), path.getFile());
									monitoredFiles.add(path);
								}
							}
						}
					}
					catch (Exception ex) {
            log.warn("Exception loading: {} - {} - {}.", fileName, (framework == null ? "app" : framework), languages.componentsJoinedByString(" / "), ex);
					}
				}
				else {
				  if(log.isDebugEnabled())
				    log.debug("Unable to create path for resource named: {} framework: {} languages: {}", fileName, (framework == null ? "app" : framework), languages.componentsJoinedByString(" / "));
				}
			}
		}
	}

	/**
	 * FIXME: Eliminate this encoding guesswork horror
	 */
	private static Object readPropertyListFromFileInFramework(String fileName, String aFrameWorkName, NSArray<String> languageList) {
		Object plist = null;

		try {
			plist = ERXUtilities.readPropertyListFromFileInFramework(fileName, aFrameWorkName, languageList, System.getProperty("file.encoding"));
		}
		catch (IllegalArgumentException e) {
			try {
				// BUGFIX: we didnt use an encoding before, so java tried to
				// guess the encoding. Now some Localizable.strings plists
				// are encoded in MacRoman whereas others are UTF-16.
				plist = ERXUtilities.readPropertyListFromFileInFramework(fileName, aFrameWorkName, languageList, "utf-16");
			}
			catch (IllegalArgumentException e1) {
				// OK, whatever it is, try to parse it!
				plist = ERXUtilities.readPropertyListFromFileInFramework(fileName, aFrameWorkName, languageList, "utf-8");
			}
		}

		return plist;
	}

	protected void addEntriesToCache(NSDictionary<String, Object> dict) {
		try {
			// try-catch to prevent potential CCE when the value for the key localizerExcepions is not an NSDictionary
			NSDictionary<String, Object> currentLEs = (NSDictionary<String, Object>) cache.valueForKey(KEY_LOCALIZER_EXCEPTIONS);
			NSDictionary<String, Object> newLEs = (NSDictionary<String, Object>) dict.valueForKey(KEY_LOCALIZER_EXCEPTIONS);
			if (currentLEs != null && newLEs != null) {
				log.debug("Merging localizerExceptions {} with {}", currentLEs, newLEs);
				NSMutableDictionary<String, Object> combinedLEs = currentLEs.mutableClone();
				combinedLEs.addEntriesFromDictionary(newLEs);
				NSMutableDictionary<String, Object> replacementDict = dict.mutableClone();
				replacementDict.takeValueForKey(combinedLEs, KEY_LOCALIZER_EXCEPTIONS);
				dict = replacementDict;
				log.debug("Result of merge: {}", combinedLEs);
			}
		}
		catch (RuntimeException e) {
			log.error("Error while adding enties to cache.", e);
		}

		cache.addEntriesFromDictionary(dict);
	}

	/**
	 * Cover method that calls <code>localizedStringForKey</code>.
	 * 
	 * @param key to resolve a localized variant of
	 * @return localized string for the given key
	 */
	public Object valueForKey(String key) {
		return valueForKeyPath(key);
	}

	protected void setCacheValueForKey(Object value, String key) {
		if (key != null && value != null) {
			cache.setObjectForKey(value, key);
		}
	}

	public Object valueForKeyPath(String key) {
		Object result = localizedValueForKey(key);
		if (result == null) {
			int indexOfDot = key.indexOf(".");
			if (indexOfDot > 0) {
				String firstComponent = key.substring(0, indexOfDot);
				String otherComponents = key.substring(indexOfDot + 1, key.length());
				result = cache.objectForKey(firstComponent);
				log.debug("Trying {} . {}", firstComponent, otherComponents);
				if (result != null) {
					try {
						result = NSKeyValueCodingAdditions.Utility.valueForKeyPath(result, otherComponents);
						if (result != null) {
							setCacheValueForKey(result, key);
						}
						else {
							setCacheValueForKey(NOT_FOUND, key);
						}
					}
					catch (NSKeyValueCoding.UnknownKeyException e) {
						if (log.isDebugEnabled()) {
							log.debug(e.getMessage());
						}
						setCacheValueForKey(NOT_FOUND, key);
					}
				}
			}
		}
		return result;
	}

	public void takeValueForKey(Object value, String key) {
		setCacheValueForKey(value, key);
		addToCreatedKeys(value, key);
	}

	public void takeValueForKeyPath(Object value, String key) {
		setCacheValueForKey(value, key);
		addToCreatedKeys(value, key);
	}

	public String language() {
		return language;
	}

	public NSDictionary<String, Object> createdKeys() {
		return createdKeys;
	}

	public void dumpCreatedKeys() {
	  if(log.isInfoEnabled())
	    log.info(NSPropertyListSerialization.stringFromPropertyList(createdKeys()));
	}

	public Object localizedValueForKeyWithDefault(String key) {
		if (key == null) {
			log.warn("Attempt to insert null key!");
			return null;
		}
		Object result = localizedValueForKey(key);
		if (result == null || NOT_FOUND.equals(result)) {
			createdKeysLog.debug("Default key inserted: '{}'/{}", key, language);
			setCacheValueForKey(key, key);
			addToCreatedKeys(key, key);
			result = key;
		}
		return result;
	}

	/**
	 * Returns the localized value for a key. An {@literal @} keypath such as 
	 * <code>session.localizer.{@literal @}locale.getLanguage</code> indicates that
	 * the methods on ERXLocalizer itself should be called instead of
	 * searching the strings file for a '{@literal @}locale.getLanguage' key.
	 * 
	 * @param key the keyPath string
	 * @return a localized string value or the object value of the @ keyPath
	 */
	public Object localizedValueForKey(String key) {
		if(!ERXUtilities.stringIsNullOrEmpty(key) && _localizerMethodIndicatorCharacter == key.charAt(0)) {
			int dotIndex = key.indexOf(NSKeyValueCodingAdditions.KeyPathSeparator);
			String methodKey = (dotIndex>0)?key.substring(1, dotIndex):key.substring(1, key.length());
      
      // KI : This can make bad invoke Errors in D2W Apps, when Rules are like '@count'
      // If the key is one of operatorNames then don't invoke it.
      if(!NSArray.operatorNames().contains(methodKey)) {
			try {
				Method m = ERXLocalizer.class.getMethod(methodKey);
				return m.invoke(this, (Object[])null);
			} catch(NoSuchMethodException nsme) {
				throw NSForwardException._runtimeExceptionForThrowable(nsme);
			} catch(IllegalAccessException iae) {
				throw NSForwardException._runtimeExceptionForThrowable(iae);
			} catch(InvocationTargetException ite) {
				throw NSForwardException._runtimeExceptionForThrowable(ite);
			}
		}
    }
		Object result = cache.objectForKey(key);
		if (key == null || result == NOT_FOUND)
			return null;
		if (result != null)
			return result;

		log.debug("Key not found: '{}'/{}", key, language);
		if (fallbackToDefaultLanguage() && !defaultLanguage().equals(language)) {
			Object valueInDefaultLanguage = defaultLocalizer().localizedValueForKey(key);
			setCacheValueForKey(valueInDefaultLanguage == null ? NOT_FOUND : valueInDefaultLanguage, key);
			return valueInDefaultLanguage;
		}
		setCacheValueForKey(NOT_FOUND, key);
		return null;
	}

	public String localizedStringForKeyWithDefault(String key) {
		return (String) localizedValueForKeyWithDefault(key);
	}

	public String localizedStringForKey(String key) {
		return (String) localizedValueForKey(key);
	}

	public String localizedTemplateStringForKeyWithObject(String key, Object o1) {
		return localizedTemplateStringForKeyWithObjectOtherObject(key, o1, null);
	}

	public String localizedTemplateStringForKeyWithObjectOtherObject(String key, Object o1, Object o2) {
		if (key != null) {
			String template = localizedStringForKeyWithDefault(key);
			if (template != null)
				return ERXSimpleTemplateParser.sharedInstance().parseTemplateWithObject(template, null, o1, o2);
		}
		return key;
	}

	@Override
	public String toString() {
		return "<" + getClass().getName() + " " + language + ">";
	}

	/**
	 * Returns a localized date formatter for the given key.
	 * 
	 * @return the formatter object
	 */
	public Format localizedDateFormatForKey(String formatString) {
		formatString = formatString == null ? ERXTimestampFormatter.DEFAULT_PATTERN : formatString;
		formatString = localizedStringForKeyWithDefault(formatString);
		Format result = _dateFormatters.get(formatString);
		if (result == null) {
			Locale current = locale();
			NSTimestampFormatter formatter = new NSTimestampFormatter(formatString, new DateFormatSymbols(current));
			result = formatter;
			_dateFormatters.put(formatString, result);
		}
		return result;
	}

	/**
	 * Returns a localized number formatter for the given key. Also, can localize units to, just define in your
	 * Localizable.strings a suitable key, with the appropriate pattern.
	 * 
	 * @return the formatter object
	 */
	public Format localizedNumberFormatForKey(String formatString) {
		formatString = formatString == null ? "#,##0.00;-(#,##0.00)" : formatString;
		formatString = localizedStringForKeyWithDefault(formatString);
		Format result = _numberFormatters.get(formatString);
		if (result == null) {
			Locale current = locale();
			NSNumberFormatter formatter = new ERXNumberFormatter();
			formatter.setLocale(current);
			formatter.setLocalizesPattern(true);
			formatter.setPattern(formatString);
			result = formatter;
			_numberFormatters.put(formatString, result);
		}
		return result;
	}

	public void setLocalizedNumberFormatForKey(Format formatter, String pattern) {
		_numberFormatters.put(pattern, formatter);
	}

	public Locale locale() {
		return locale;
	}

	public void setLocale(Locale value) {
		locale = value;
	}

	public void setLocalizedDateFormatForKey(NSTimestampFormatter formatter, String pattern) {
		_dateFormatters.put(pattern, formatter);
	}

	public static boolean useLocalizedFormatters() {
		if (_useLocalizedFormatters == null) {
			_useLocalizedFormatters = ERXProperties.booleanForKey("er.extensions.ERXLocalizer.useLocalizedFormatters") ? Boolean.TRUE : Boolean.FALSE;
		}
		return _useLocalizedFormatters.booleanValue();
	}

	public static boolean fallbackToDefaultLanguage() {
		if (_fallbackToDefaultLanguage == null) {
			_fallbackToDefaultLanguage = ERXProperties.booleanForKey("er.extensions.ERXLocalizer.fallbackToDefaultLanguage") ? Boolean.TRUE : Boolean.FALSE;
		}
		return _fallbackToDefaultLanguage.booleanValue();
	}
}