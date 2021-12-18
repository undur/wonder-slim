package er.extensions.appserver;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WORequest;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.foundation.ERXUtilities;

/**
 * All WebObjects applications have exactly one <code>ERXBrowserFactory</code> 
 * instance. Its primary role is to manage {@link ERXBrowser} objects. 
 * It provides facility to parse <code>"user-agent"</code> HTTP header and to 
 * create an appropriate browser object. It also maintains the 
 * browser pool to store shared <code>ERXBrowser</code> objects. 
 * Since <code>ERXBrowser</code> object is immutable, it can be 
 * safely shared between sessions and <code>ERXBrowserFactory</code> 
 * tries to have only one instance of <code>ERXBrowser</code> for 
 * each kind of web browsers.
 * <p>
 * The primary method called by {@link ERXSession} and {@link ERXDirectAction} 
 * is {@link #browserMatchingRequest browserMatchingRequest} 
 * which takes a {@link com.webobjects.appserver.WORequest WORequest} 
 * as the parameter and returns a shared instance of browser object. 
 * You actually wouldn't have to call this function by yourself 
 * because <code>ERXSession</code> and <code>ERXDirectAction</code> 
 * provide {@link ERXSession#browser() browser} method 
 * that returns a browser object for the current request for you.
 * <p>
 * Note that <code>ERXSession</code> and <code>ERXDirectAction</code> 
 * call <code>ERXBrowserFactory</code>'s 
 * {@link #retainBrowser retainBrowser} and {@link #releaseBrowser releaseBrowser}  
 * to put the browser object to the browser pool when it is 
 * created and to remove the browser object from the pool when 
 * it is no longer referred from sessions and direct actions. 
 * <code>ERXSession</code> and <code>ERXDirectAction</code> 
 * automatically handle this and you do not have to call these 
 * methods from your code.<br>
 * <p>
 * The current implementation of the parsers support variety of 
 * Web browsers in the market such as Internet Explorer (IE), 
 * OmniWeb, Netscape, iCab and Opera, versions between 2.0 and 7.0. <br>
 * <p>
 * To customize the parsers for <code>"user-agent"</code> HTTP header, 
 * subclass <code>ERXBrowserFactory</code> and override methods 
 * like {@link #parseBrowserName parseBrowserName}, 
 * {@link #parseVersion parseVersion}, 
 * {@link #parseMozillaVersion parseMozillaVersion} and 
 * {@link #parsePlatform parsePlatForm}. 
 * Then put the following statement into the application's 
 * constructor. 
 * <p>
 * <code>ERXBrowserFactory.{@link #setFactory 
 * setFactory(new SubClassOfERXBrowserFactory())};</code>
 * <p>
 * If you want to use your own subclass of <code>ERXBrowser</code>, 
 * put the following statement into the application's constructor.
 * <p>
 * <code>ERXBrowserFactory.factory().{@link #setBrowserClassName 
 * setBrowserClassName("NameOfTheSubClassOfERXBrowser")}</code>
 *
 * <p>
 * <pre>
 * This implementation is tested with the following browsers (or "user-agent" strings)
 * Please ask the guy (tatsuyak@mac.com) for WOUnitTest test cases. 
 * 
 * Mac OS X 
 * ----------------------------------------------------------------------------------
 * iCab 2.8.1       Mozilla/4.5 (compatible; iCab 2.8.1; Macintosh; I; PPC)
 * IE 5.21          Mozilla/4.0 (compatible; MSIE 5.21; Mac_PowerPC)
 * Netscape 7.0b1   Mozilla/5.0 (Macintosh; U; PPC Mac OS X; en-US; rv:1.0rc2) Gecko/20020512 Netscape/7.0b1
 * Netscape 6.2.3   Mozilla/5.0 (Macintosh; U; PPC Mac OS X; en-US; rv:0.9.4.1) Gecko/20020508 Netscape6/6.2.3
 * OmniWeb 5.11.1   Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_7_3; en-US) AppleWebKit/533.21.1+(KHTML, like Gecko, Safari/533.19.4) Version/5.11.1 OmniWeb/622.18.0
 * Safari 1.0b(v48) Mozilla/5.0 (Macintosh; U; PPC Mac OS X; en-us) AppleWebKit/48 (like Gecko) Safari/48
 * iPhone 1.0       Mozilla/5.0 (iPhone; U; CPU like Mac OS X; en) AppleWebKit/420+ (KHTML, like Gecko) Version/3.0 Mobile/1A543a Safari/419.3
 * iPhone 7.0		Mozilla/5.0 (iPhone; CPU iPhone OS 12_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.0 Mobile/15E148 Safari/604.1
 * 
 * Windows 2000
 * ----------------------------------------------------------------------------------
 * IE 6.0           Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.0)
 * IE 5.5           Mozilla/4.0 (compatible; MSIE 5.5; Windows NT 5.0)
 * Netscape 6.2.3   Mozilla/5.0 (Windows; U; Windows NT 5.0; en-US; rv:0.9.4.1) Gecko/20020508 Netscape6/6.2.3
 * Netscape 4.79    Mozilla/4.79 [en] (Windows NT 5.0; U)
 * Opera 6.04       Mozilla/4.0 (compatible; MSIE 5.0; Windows 2000) Opera 6.04  [en]
 * 
 * Android
 * ----------------------------------------------------------------------------------
 * Chrome 70		Mozilla/5.0 (Linux; Android 8.0.0; moto g(6)) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.80 Mobile Safari/537.36
 * 
 * </pre>
 *
 * @property er.extensions.ERXBrowserFactory.FactoryClassName
 * @property er.extensions.ERXBrowserFactory.BrowserClassName (default ERXBasicBrowser)
 */
public class ERXBrowserFactory {

    private static final Logger log = LoggerFactory.getLogger(ERXBrowserFactory.class);

    /** 
     * holds the default browser class name
     */
    private static final String _DEFAULT_BROWSER_CLASS_NAME = ERXBasicBrowser.class.getName();

    /** 
     * Caches a reference to the browser factory
     */
    private static ERXBrowserFactory _factory;

    /** 
     * Expressions that define a robot
     */
    private static final NSMutableArray<Pattern> robotExpressions = new NSMutableArray();

    /** 
     * Mapping of UAs to browsers
     */
    private static final Map _cache = new ConcurrentHashMap<>();

    /**
     * Gets the singleton browser factory object.
     * 
     * @return browser factory
     */
    public static ERXBrowserFactory factory() {
        if (_factory == null) {
            String browserFactoryClass = System.getProperty("er.extensions.ERXBrowserFactory.FactoryClassName");
            if (browserFactoryClass != null && !browserFactoryClass.equals(ERXBrowserFactory.class.getName())) {
                log.debug("Creating browser factory for class name: {}", browserFactoryClass);
                try {
                    Class browserClass = Class.forName(browserFactoryClass);
                    _factory = (ERXBrowserFactory)browserClass.newInstance();
                } catch (Exception e) {
                    log.error("Unable to create browser factory for class name '{}'", browserFactoryClass, e);
                }
            }
            if (_factory == null) {
                log.debug("Factory null creating default browser factory. {}", browserFactoryClass);
                _factory = new ERXBrowserFactory();
            }
        }
        return _factory;
    }

    /**
     * Sets the browser factory used to create browser objects.
     * 
     * @param newFactory new browser factory
     */
    public static void setFactory(ERXBrowserFactory newFactory) { _factory = newFactory; }

    /** 
     * Caches the browser class name
     */
    protected String _browserClassName;

    /**
     * Returns the name of the {@link ERXBrowser} subclass. 
     * The default value is <code>"er.extensions.appserver.ERXBasicBrowser"</code>.
     * 
     * @see	#setBrowserClassName
     * 
     * @return the name of the ERXBrowser subclass; default to <code>"er.extensions.appserver.ERXBasicBrowser"</code>
     */
    public String browserClassName() { return _browserClassName; }
    
    /**
     * Sets the name of the {@link ERXBrowser} subclass.
     * 
     * @param name the name of the ERXBrowser subclass; ignored if null
     * 
     * @see #browserClassName
     * @see #createBrowser
     */
    public void setBrowserClassName(String name) { 
        if (name != null  &&  name.length() > 0) 
            _browserClassName = name; 
    }

    /**
     * Public browser constructor.
     */
    public ERXBrowserFactory() {
        // ENHANCEME: (tk) to arrow to set the class name from property files and launch arguments. 
        setBrowserClassName(System.getProperty("er.extensions.ERXBrowserFactory.BrowserClassName", _DEFAULT_BROWSER_CLASS_NAME));
    }

    /** 
     * Gets a shared browser object for given request. 
     * Parses <code>"user-agent"</code> string in the request and gets 
     * the appropriate browser object. 
     * <p>
     * This is the primary method to call from application logics, and 
     * once you get a browser object, you are responsible to call 
     * {@link #retainBrowser retainBrowser} to keep the browser 
     * object in the browser pool. 
     * <p>
     * You are also required to call {@link #releaseBrowser releaseBrowser} 
     * to release the browser from the pool when it is no longer needed. 
     * 
     * @param request - WORequest
     * @return a shared browser object
     */
    public ERXBrowser browserMatchingRequest(WORequest request) {
        if (request == null) {
        	throw new IllegalArgumentException("Request can't be null.");
        }

        String ua = request.headerForKey("user-agent");
        return browserMatchingUserAgent(ua);
    }

    /** 
     * Returns a shared browser object for a given <code>user-agent</code>
     * string by parsing the string and retrieving the appropriate browser 
     * object, creating it if necessary. 
     * <p>
     * Use this method to retrieve a browser instance from an existing
     * user-agent string rather than a request object (e.g. you're 
     * recreating a browser instance from a past user-agent string). Once 
     * you get the browser object, you are responsible for calling {@link 
     * #retainBrowser retainBrowser} to keep it in the browser pool. 
     * <p>
     * You are also required to call {@link #releaseBrowser releaseBrowser} 
     * to release the browser from the pool when it is no longer needed. 
     * 
     * @param ua - user agent string (e.g. from request headers)
     * @return a shared browser object
     */
    public ERXBrowser browserMatchingUserAgent(String ua) {
        if (ua == null) {
            return getBrowserInstance(ERXBrowser.UNKNOWN_BROWSER, ERXBrowser.UNKNOWN_VERSION, 
            		ERXBrowser.UNKNOWN_VERSION, ERXBrowser.UNKNOWN_PLATFORM, null);
        }
        
       	ERXBrowser result = (ERXBrowser) _cache.get(ua);
       	if (result == null) {
       		String browserName 		= parseBrowserName(ua);
       		String version 			= parseVersion(ua);
       		String mozillaVersion	= parseMozillaVersion(ua);
       		String platform 		= parsePlatform(ua);
       		NSDictionary userInfo 	= new NSDictionary(
       				new Object[] {parseCPU(ua), parseGeckoVersion(ua)},
       				new Object[] {"cpu", "geckoRevision"});
       		
        	result = getBrowserInstance(browserName, version, mozillaVersion, platform, userInfo);
        	_cache.put(ua,result);
        }
        return result;
    }

    /** 
     * Gets a shared browser object from browser pool. If such browser 
     * object does not exist, this method will create one by using 
     * {@link #createBrowser createBrowser} method.
     * 
     * @param browserName string
     * @param version string
     * @param mozillaVersion string
     * @param platform string
     * @param userInfo string
     * 
     * @return a shared browser object
     */
    public synchronized ERXBrowser getBrowserInstance(String browserName, String version, String mozillaVersion, String platform, NSDictionary userInfo) {
        String key = _computeKey(browserName, version, mozillaVersion, platform, userInfo);
        ERXBrowser browser = (ERXBrowser)_browserPool().objectForKey(key);
        if (browser == null) 
            browser = createBrowser(browserName, version, mozillaVersion, platform, userInfo);
        return browser;
    }

    /** 
     * Creates a new browser object for given parameters. Override this 
     * method if you need to provide your own subclass of {@link ERXBrowser}. 
     * If you override it, your implementation should not call <code>super</code>.
     * <p>
     * Alternatively, use {@link #setBrowserClassName} and {@link #browserClassName}.
     * 
     * @see	#setBrowserClassName
     * @see	#browserClassName
     * 
     * @param browserName string
     * @param version string
     * @param mozillaVersion string
     * @param platform string
     * @param userInfo string
     * 
     * @return new browser object that is a concrete subclass of <code>ERXBrowser</code>
     */
    public synchronized ERXBrowser createBrowser(String browserName, String version, String mozillaVersion, String platform, NSDictionary userInfo) {
        ERXBrowser browser = null;
        try {
            browser = _createBrowserWithClassName(browserClassNameForBrowserNamed(browserName), browserName, version, mozillaVersion, platform, userInfo);
        } catch (Exception ex) {
            log.error("Unable to create a browser for class name: {} with exception: {}. Will use default classes."
                            + " Please ensure that the fully-qualified name for the class is specified"
                            + " if it is in a different package.", browserClassNameForBrowserNamed(browserName), ex.getMessage(), ex);
        }
        if (browser == null) {
            try {
                browser = _createBrowserWithClassName(_DEFAULT_BROWSER_CLASS_NAME, browserName, version, mozillaVersion, platform, userInfo);
            } catch (Exception ex) {
                log.error("Unable to create even a default browser for class name: {} with exception: {} "
                            + "Will instanciate a browser with regular new {}(...) statement.",
                            _DEFAULT_BROWSER_CLASS_NAME, ex.getMessage(), _DEFAULT_BROWSER_CLASS_NAME, ex);
                browser = new ERXBasicBrowser(browserName, version, mozillaVersion, platform, userInfo);
            }
        }
        return browser;
    }

    private ERXBrowser _createBrowserWithClassName(String className, String browserName, String version, 
                                            String mozillaVersion, String platform, NSDictionary userInfo) 

                                            throws  ClassNotFoundException, 
                                                    NoSuchMethodException, 
                                                    InstantiationException, 
                                                    IllegalAccessException, 
                                                    java.lang.reflect.InvocationTargetException 
    {
                            
        Class browserClass = Class.forName(className);
        Class[] paramArray = new Class[] { String.class, String.class, String.class, 
                                                                String.class, NSDictionary.class };
        java.lang.reflect.Constructor constructor = browserClass.getConstructor(paramArray);
        return (ERXBrowser)constructor.newInstance(new Object[] {
                    browserName, version, mozillaVersion, platform, userInfo } );
    }
        

    /**
     * Retains a given browser object.
     * 
     * @param browser to be retained
     */
    public synchronized void retainBrowser(ERXBrowser browser) {
        String key = _computeKey(browser);
        _browserPool().setObjectForKey(browser, key);
        _incrementReferenceCounterForKey(key);
    }

    /**
     * Decrements the retain count for a given browser object.
     * 
     * @param browser to be released
     */
    public synchronized void releaseBrowser(ERXBrowser browser) {
        String key = _computeKey(browser);
        AtomicInteger count = _decrementReferenceCounterForKey(key);
        if (count == null) {
            // Perhaps forgot to call registerBrowser() but try to remove the browser for sure
            _browserPool().removeObjectForKey(key);
        } else if (count.intValue() <= 0) {
            _browserPool().removeObjectForKey(key);
            _referenceCounters().removeObjectForKey(key);
        } 
    }

    /**
     * Adds the option to use multiple different ERXBrowser subclasses
     * depending on the name of the browser.
     * 
     * @param browserName name of the browser
     * @return ERXBrowser subclass class name
     */
    public String browserClassNameForBrowserNamed(String browserName) {
        return browserClassName();
    }

    public String parseBrowserName(String userAgent) {
        String browserString = _browserString(userAgent);
        String browser  = ERXBrowser.UNKNOWN_BROWSER;
        if (isRobot(browserString)) 						browser  = ERXBrowser.ROBOT;
        else if (browserString.indexOf("Edge") > -1) 		browser  = ERXBrowser.EDGE;
        else if (browserString.indexOf("Chrome") > -1) 		browser  = ERXBrowser.CHROME;
        else if (browserString.indexOf("MSIE") > -1 || browserString.indexOf("Trident") > -1)	browser  = ERXBrowser.IE;
        else if (browserString.indexOf("Safari") > -1) 		browser  = ERXBrowser.SAFARI;
        else if (browserString.indexOf("Firefox") > -1) 	browser  = ERXBrowser.FIREFOX;
        else if (browserString.indexOf("OmniWeb") > -1)		browser  = ERXBrowser.OMNIWEB;
        else if (browserString.indexOf("iCab") > -1)		browser  = ERXBrowser.ICAB;
        else if (browserString.indexOf("Opera") > -1)		browser  = ERXBrowser.OPERA;
        else if (browserString.indexOf("Netscape") > -1)	browser  = ERXBrowser.NETSCAPE;
        else if (browserString.startsWith("Mozilla") && (browserString.indexOf("compatible") == -1))	browser  = ERXBrowser.MOZILLA;

        // This condition should always come last because *all* browsers have 
        // the word Mozilla at the beginning of their user-agent string. 
        else if (browserString.indexOf("Mozilla") > -1)		browser  = ERXBrowser.NETSCAPE;

        return browser;
    }

    private boolean isRobot(String userAgent) {
    	synchronized (robotExpressions) {
			if(robotExpressions.count()==0) {
				String strings = ERXUtilities.stringFromResource("robots", "txt", NSBundle.bundleForName("ERExtensions"));
				for (String item : NSArray.componentsSeparatedByString(strings, "\n")) {
					if(item.trim().length() > 0 && item.charAt(0) != '#') {
						robotExpressions.addObject(Pattern.compile(item));
					}
				}
			}
			userAgent = userAgent.toLowerCase();
			for (Pattern pattern : robotExpressions) {
				if (pattern.matcher(userAgent).find()) {
					log.debug("{} matches {}", pattern, userAgent);
					return true;
				}
			}
		}
		
    	return false;
    }
    
    public String parseGeckoVersion(String userAgent) {
    	if (userAgent.indexOf("Gecko") >= 0) {
    		final String revString = "; rv:";
    		int startPos = userAgent.indexOf(revString) + revString.length();
            if (startPos > revString.length()) {
            	
            	int endPos = userAgent.indexOf(")", startPos);
            	
            	if (endPos > startPos) {
            		return userAgent.substring(startPos, endPos);
            	} 	
            }
        }
        return ERXBrowser.NO_GECKO;
    }

    public String parseVersion(String userAgent) {
        String versionString = _versionString(userAgent);
        int startpos;
        String version = ERXBrowser.UNKNOWN_VERSION;
        
        // Remove "Netscape6" from string such as "Netscape6/6.2.3", 
        // otherwise this method will produce wrong result "6/6.2.3" as the version
        final String netscape6 = "Netscape6";
        startpos = versionString.indexOf(netscape6);
        if (startpos > -1) 
            versionString = versionString.substring(startpos + netscape6.length());
        
        // Find first numeric in the string such as "MSIE 5.21; Mac_PowerPC)"
        startpos = indexOfNumericInString(versionString);

        if (startpos > -1) {
            StringTokenizer st = new StringTokenizer(versionString.substring(startpos), " ;"); 
            if (st.hasMoreTokens()) 
                version = st.nextToken();  // Will return "5.21" portion of "5.21; Mac_PowerPC)"
        }
		// Test if we got a real number
		try {
	        String normalizedVersion = ERXBrowser.removeExtraDotsFromVersionString(version);
			Double.parseDouble(normalizedVersion);
		}
		catch (NumberFormatException e) {
			version = ERXBrowser.UNKNOWN_VERSION;
		}
        return version;
    }
    
 	/**
 	 * Locate the the first numeric character in the given string.
 	 * 
 	 * @param str string to scan
 	 * @return position in string or -1 if no numeric found
 	 */
 	private static int indexOfNumericInString(String str) {
 		return indexOfNumericInString(str, 0);
 	}

 	/**
 	 * Locate the the first numeric character after <code>fromIndex</code> in the given string.
 	 * 
 	 * @param str string to scan
 	 * @param fromIndex index position from where to start
 	 * @return position in string or -1 if no numeric found
 	 */
 	private static int indexOfNumericInString(String str, int fromIndex) {
 		if (str == null)
 			throw new IllegalArgumentException("String cannot be null.");

 		int pos = -1;
 		for (int i = fromIndex; i < str.length(); i++) {
 			char c = str.charAt(i);
 			if ('0' <= c && c <= '9') {
 				pos = i;
 				break;
 			}
 		}
 		return pos;
 	}

    public String parseMozillaVersion(String userAgent) {
        final String mozilla = "Mozilla/";
        String mozillaVersion = ERXBrowser.UNKNOWN_VERSION;
        int startpos = userAgent.indexOf(mozilla);
        if (startpos > -1) {
            StringTokenizer st = new StringTokenizer(userAgent.substring(startpos + mozilla.length()), " ;"); 
            if (st.hasMoreTokens()) 
                mozillaVersion = st.nextToken();  // Will return "5.21" portion of "5.21; Mac_PowerPC)"
        }
        return mozillaVersion;
    }

    public String parsePlatform(String userAgent) {
        String platform = ERXBrowser.UNKNOWN_PLATFORM;
        if      (userAgent.indexOf("Win") > -1) 	platform = ERXBrowser.WINDOWS;
        else if ((userAgent.indexOf("iPhone") > -1) || (userAgent.indexOf("iPod") > -1)) 	platform = ERXBrowser.IPHONE;
        else if (userAgent.indexOf("iPad") > -1) platform = ERXBrowser.IPAD;
        else if (userAgent.indexOf("Mac") > -1) 	platform = ERXBrowser.MACOS;
        else if (userAgent.indexOf("Android") > -1) platform = ERXBrowser.ANDROID;
        else if (userAgent.indexOf("Linux") > -1) 	platform = ERXBrowser.LINUX;
        return platform;
    }

    public String parseCPU(String userAgent) {
        String cpu = ERXBrowser.UNKNOWN_CPU;
        if      (userAgent.indexOf("PowerPC") > -1) 	cpu = ERXBrowser.POWER_PC;
        else if (userAgent.indexOf("PPC") > -1) 	cpu = ERXBrowser.POWER_PC;
        return cpu;
    }

    private String _browserString(String userAgent) {
        int startpos;

        // Get substring "Chrome/0.X.Y.Z Safari/525.13"
        // from          "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) "
        //               "AppleWebKit/525.13 (KHTML, wie z. B. Gecko) Chrome/0.X.Y.Z Safari/525.13"
        final String chrome = "Chrome";
        startpos = userAgent.indexOf(chrome);
        if (startpos > -1)
            return userAgent.substring(startpos);
        
        // Get substring "OmniWeb/622.18.0"
        // from          "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_7_3; en-US)"
        //               "AppleWebKit/533.21.1+(KHTML, like Gecko, Safari/533.19.4) "
        //               "Version/5.11.1 OmniWeb/622.18.0"
        final String omniWeb = "OmniWeb";
        startpos = userAgent.indexOf(omniWeb);
        if (startpos > -1)
          return userAgent.substring(startpos);
        
        // Get substring "Safari/48"
        // from          "Safari 1.0b(v48) Mozilla/5.0 (Macintosh; U; PPC Mac OS X; en-us) "
        //               "AppleWebKit/48 (like Gecko) Safari/48"
        final String safari = "Safari";
        startpos = userAgent.indexOf(safari);
        if (startpos > -1)
        	return userAgent.substring(startpos);

        // Get substring "Opera 6.04  [en]" 
        // from          "Mozilla/4.0 (compatible; MSIE 5.0; Windows 2000) Opera 6.04  [en]"
        final String opera = "Opera";
        startpos = userAgent.indexOf(opera);
        if (startpos > -1) 
        	return userAgent.substring(startpos);

        // Get substring "MSIE 5.21; Mac_PowerPC)"
        // from          "Mozilla/4.0 (compatible; MSIE 5.21; Mac_PowerPC)" 
        final String compatible = "compatible;";
        startpos = userAgent.indexOf(compatible);
        if (startpos > -1) 
        	return userAgent.substring(startpos + compatible.length());
            
        // Get substring "Netscape6/6.2.3" 
        // from          "Mozilla/5.0 (Macintosh; U; PPC Mac OS X; en-US; rv:0.9.4.1) 
        //                Gecko/20020508 Netscape6/6.2.3"
        final String netscape = "Netscape";
        startpos = userAgent.indexOf(netscape);
        if (startpos > -1) 
        	return userAgent.substring(startpos);
            
        return userAgent;
    }

    private NSMutableDictionary _browserPool;
    private NSMutableDictionary _browserPool() { 
        if (_browserPool == null) 
            _browserPool = new NSMutableDictionary();
        return _browserPool;
    }

    private NSMutableDictionary _referenceCounters;
    private NSMutableDictionary _referenceCounters() {
        if (_referenceCounters == null)
            _referenceCounters = new NSMutableDictionary();
        return _referenceCounters;
    }

    private AtomicInteger _incrementReferenceCounterForKey(String key) {
        AtomicInteger count = (AtomicInteger)_referenceCounters().objectForKey(key);
        if (count != null) {
        	count.incrementAndGet();
        }
        else {
            count = new AtomicInteger(1);
            _referenceCounters().setObjectForKey(count, key);
        }
        log.debug("_incrementReferenceCounterForKey() - count = {}, key = {}", count, key);
        return count;
    }

    private AtomicInteger _decrementReferenceCounterForKey(String key) {
    	AtomicInteger count = (AtomicInteger)_referenceCounters().objectForKey(key);
    	
        if (count != null) {
        	count.decrementAndGet();
        }
        
        log.debug("_decrementReferenceCounterForKey() - count = {}, key = {}", count, key);
        return count;
    }

    private String _computeKey(ERXBrowser browser) {
        return browser.browserName() + "." + browser.version() + "." + browser.mozillaVersion() + "." + browser.platform() + "." + browser.userInfo();
    }

    private String _computeKey(String browserName, String version, String mozillaVersion, String platform, NSDictionary userInfo) {
        return browserName + "." + version + "." + mozillaVersion + "." + platform + "." + userInfo;
    }
    
    private String _versionString(String userAgent) {
    	String versionString;

    	int startpos = userAgent.indexOf("Version/");
    	if (startpos > -1)  {
    		versionString = userAgent.substring(startpos);
    	} else {
    		startpos = userAgent.indexOf("Firefox/");
        	if (startpos > -1)  {
        		versionString = userAgent.substring(startpos);
        	} else {
        		startpos = userAgent.indexOf("rv:");
            	if (startpos > -1)  {
            		versionString = userAgent.substring(startpos);
            		int endpos = versionString.indexOf(')');
            		if (endpos > -1) {
            			versionString = versionString.substring(0, endpos);
            		}
            	} else {
            		startpos = userAgent.indexOf("Edge/");
                	if (startpos > -1)  {
                		versionString = userAgent.substring(startpos);
                	} else {
                		versionString = _browserString(userAgent);
                	}
            	}
        	}
    	}

    	return versionString;
    }
}