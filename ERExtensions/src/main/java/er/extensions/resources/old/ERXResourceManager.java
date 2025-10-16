package er.extensions.resources.old;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects._ideservices._WOProject;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.appserver._private.WODeployedBundle;
import com.webobjects.appserver._private.WOProjectBundle;
import com.webobjects.appserver._private.WOURLEncoder;
import com.webobjects.appserver._private.WOURLValuedElementData;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation._NSStringUtilities;
import com.webobjects.foundation._NSThreadsafeMutableDictionary;

import er.extensions.appserver.ERXApplication;
import er.extensions.appserver.ERXRequest;
import er.extensions.foundation.ERXProperties;
import er.extensions.resources.ERXResourceManagerBase;

/**
 * Replacement of the WOResourceManager which adds:
 * <ul>
 * <li> dealing with nested web server resources when not deploying
 * <li> resource versioning (for better caching control)
 * </ul>
 * 
 * @author ak
 * @author mschrag
 */

public class ERXResourceManager extends ERXResourceManagerBase {

	private static final Logger log = LoggerFactory.getLogger(ERXResourceManager.class);

	private final WODeployedBundle TheAppProjectBundle;
	private final _NSThreadsafeMutableDictionary<String, WOURLValuedElementData> _urlValuedElementsData;
	private final _NSThreadsafeMutableDictionary _myFrameworkProjectBundles = new _NSThreadsafeMutableDictionary(new NSMutableDictionary(128));

	public ERXResourceManager() {
		TheAppProjectBundle = _initAppBundle();
		_initFrameworkProjectBundles();

		try {
			Field field = WOResourceManager.class.getDeclaredField("_urlValuedElementsData");
			field.setAccessible(true);
			// AK: yeah, hack, I know...
			_urlValuedElementsData = (_NSThreadsafeMutableDictionary) field.get(this);
		}
		catch (SecurityException | NoSuchFieldException | IllegalArgumentException | IllegalAccessException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}

    private void _initFrameworkProjectBundles() {
        NSBundle aBundle = null;

        NSArray aFrameworkBundleList = NSBundle.frameworkBundles();

        for(Enumeration aBundleEnumerator = aFrameworkBundleList.objectEnumerator(); aBundleEnumerator.hasMoreElements(); _erxCachedBundleForFrameworkNamed(aBundle.name())) {
        	aBundle = (NSBundle)aBundleEnumerator.nextElement();
        }
    }

	private static WODeployedBundle _initAppBundle() {
		Object obj = null;

		try {
			WODeployedBundle wodeployedbundle = WODeployedBundle.deployedBundle();
			obj = wodeployedbundle.projectBundle();

			if (obj != null) {
				log.warn("Application project found: Will locate resources in '{}' rather than '{}'.", ((WOProjectBundle) obj).projectPath(), wodeployedbundle.bundlePath());
			}
			else {
				obj = wodeployedbundle;
			}
		}
		catch (Exception exception) {
			log.error("<WOResourceManager> Unable to initialize AppProjectBundle for reason:", exception);
			throw NSForwardException._runtimeExceptionForThrowable(exception);
		}

		return (WODeployedBundle) obj;
	}

	
    private static WODeployedBundle _locateBundleForFrameworkNamed(String aFrameworkName) {
        WODeployedBundle aBundle = null;
        aBundle = ERXDeployedBundle.deployedBundleForFrameworkNamed(aFrameworkName);

        if(aBundle == null) {
            NSBundle nsBundle = NSBundle.bundleForName(aFrameworkName);

            if(nsBundle != null) {
            	aBundle = _bundleWithNSBundle(nsBundle);
            }
        }

        return aBundle;
    }

    private static WODeployedBundle _bundleWithNSBundle(NSBundle nsBundle) {
        WODeployedBundle aBundle = null;
        WODeployedBundle aDeployedBundle = ERXDeployedBundle.bundleWithNSBundle(nsBundle);
        WODeployedBundle aProjectBundle = aDeployedBundle.projectBundle();

        if(aProjectBundle != null) {
            if(WOApplication._isDebuggingEnabled()) {
            	NSLog.debug.appendln((new StringBuilder()).append("Framework project found: Will locate resources in '").append(aProjectBundle.bundlePath()).append("' rather than '").append(aDeployedBundle.bundlePath()).append("' .").toString());
            }

            aBundle = aProjectBundle;
        }
        else {
            aBundle = aDeployedBundle;
        }

        return aBundle;
    }

    @Override
    public NSArray _frameworkProjectBundles() {
        return _myFrameworkProjectBundles.immutableClone().allValues();
    }

    private WODeployedBundle _erxCachedBundleForFrameworkNamed(String aFrameworkName) {
        WODeployedBundle aBundle = null;
        if(aFrameworkName != null) {
            aBundle = (WODeployedBundle)_myFrameworkProjectBundles.objectForKey(aFrameworkName);

            if(aBundle == null) {
                aBundle = _locateBundleForFrameworkNamed(aFrameworkName);

                if(aBundle != null) {
                	_myFrameworkProjectBundles.setObjectForKey(aBundle, aFrameworkName);
                }
            }
        }

        if(aBundle == null) {
        	aBundle = TheAppProjectBundle;
        }

        return aBundle;
    }
	
	private String _cachedURLForResource(String name, String bundleName, NSArray languages, WORequest request) {
		String result = null;

		if (bundleName != null) {
			WODeployedBundle wodeployedbundle = _erxCachedBundleForFrameworkNamed(bundleName);

			if (wodeployedbundle != null) {
				result = wodeployedbundle.urlForResource(name, languages);
			}

			if (result == null) {
				result = "/ERROR/NOT_FOUND/framework=" + bundleName + "/filename=" + (name == null ? "*null*" : name);
			}
		}
		else {
			result = TheAppProjectBundle.urlForResource(name, languages);

			if (result == null) {
				String appName = WOApplication.application().name();
				result = "/ERROR/NOT_FOUND/app=" + appName + "/filename=" + (name == null ? "*null*" : name);
			}
		}

		String resourceUrlPrefix = null;

		if (ERXRequest.isRequestSecure(request)) {
			resourceUrlPrefix = ERXProperties.stringForKey("er.extensions.ERXResourceManager.secureResourceUrlPrefix");
		}
		else {
			resourceUrlPrefix = ERXProperties.stringForKey("er.extensions.ERXResourceManager.resourceUrlPrefix");
		}

		if (resourceUrlPrefix != null && resourceUrlPrefix.length() > 0) {
			result = resourceUrlPrefix + result;
		}

		return result;
	}

	@Override
	public String urlForResourceNamed(String name, String bundleName, NSArray<String> languages, WORequest request) {
		String completeURL = null;
		if (request == null || request.isUsingWebServer() && !WOApplication.application()._rapidTurnaroundActiveForAnyProject()) {
			completeURL = _cachedURLForResource(name, bundleName, languages, request);
		}
		else {
			URL url = pathURLForResourceNamed(name, bundleName, languages);
			String fileURL = null;

			if (url == null) {
				fileURL = "ERROR_NOT_FOUND_framework_" + (bundleName == null ? "*null*" : bundleName) + "_filename_" + (name == null ? "*null*" : name);
			}
			else {
				fileURL = url.toString();
				cacheDataIfNotInCache(fileURL);
			}

			String encoded = WOURLEncoder.encode(fileURL);
			String key = WOApplication.application().resourceRequestHandlerKey();

			if (WOApplication.application()._rapidTurnaroundActiveForAnyProject() && WOApplication.application().isDirectConnectEnabled()) {
				key = "_wr_";
			}

			WOContext context = (WOContext) request.valueForKey("context");
			String wodata = _NSStringUtilities.concat("wodata", "=", encoded);

			if (context != null) {
				// If the resource is inside a jar-file, replace 'jar:file://' with 'jar-data' (more acceptable as a URL-element).
				// Then append the resource's path to the URL's path (instead of adding it as a query parameter)
				if( fileURL.startsWith("jar:file://") ) {
					completeURL = context.urlWithRequestHandlerKey(key, fileURL.replace("jar:file://", "jar-data"), null);
				}
				else {
					completeURL = context.urlWithRequestHandlerKey(key, null, wodata);
				}
			}
			else {
				StringBuilder sb = new StringBuilder(request.applicationURLPrefix());
				sb.append('/');
				sb.append(key);
				sb.append('?');
				sb.append(wodata);
				completeURL = sb.toString();
			}

			// AK: TODO get rid of regex
			int offset = completeURL.indexOf("?wodata=file%3A");

			if (offset >= 0) {
				completeURL = completeURL.replaceFirst("\\?wodata=file%3A", "/wodata=");
				if (completeURL.indexOf("/wodata=") > 0) {
					completeURL = completeURL.replaceAll("%2F", "/");
					// SWK: On Windows we have /C%3A/ changed to /C:
					completeURL = completeURL.replaceAll("%3A", ":");
				}
			}
		}

//		completeURL = _versionManager.versionedUrlForResourceNamed(completeURL, name, bundleName, languages, request);
		completeURL = _postprocessURL(completeURL, bundleName);
		return completeURL;
	}
	
	protected String _postprocessURL(String url, String bundleName) {

		if (WOApplication.application() instanceof ERXApplication app) {
			final WODeployedBundle bundle = _cachedBundleForFrameworkNamed(bundleName);
			return app._rewriteResourceURL(url, bundle);
		}

		return url;
	}

	private WOURLValuedElementData cachedDataForKey(String key) {
		WOURLValuedElementData data = _urlValuedElementsData.objectForKey(key);

		if (data == null && key != null && key.startsWith("file:") && ERXApplication.isDevelopmentModeSafe()) {
			data = cacheDataIfNotInCache(key);
		}

		return data;
	}

	protected WOURLValuedElementData cacheDataIfNotInCache(String key) {
		WOURLValuedElementData data = _urlValuedElementsData.objectForKey(key);

		if (data == null) {
			String contentType = contentTypeForResourceNamed(key);
			data = new WOURLValuedElementData(null, contentType, key);
			_urlValuedElementsData.setObjectForKey(data, key);
		}

		return data;
	}

	@Override
	public WOURLValuedElementData _cachedDataForKey(String key) {
		WOURLValuedElementData wourlvaluedelementdata = null;

		if (key != null) {
			wourlvaluedelementdata = cachedDataForKey(key);
		}

		return wourlvaluedelementdata;
	}
	
	/**
	 * Replacement of the WODeployedBundle which adds:
	 * <ul>
	 * <li> Determines whether the Bundle is embedded within the Main Bundle 
	 * <li> If so, mark the state and the name of embedding main bundle
	 * <li> Resource URLs for embedded Frameworks are automatically adapted to refer to the embedded Framework, 
	 *      rather than to the Frameworks base URL
	 * </ul>
	 * 
	 * There are two styles of webserver resource packages, with ANT (old style) builds, embedded frameworks are embedded this way
	 *    MyApp.woa/Frameworks/EmbeddedFramework.framework/...
	 * 
	 * while with Maven builds (and some newer ANT builds), frameworks are embedded in the WebServerResources package the same way as in the Application package:
	 *    MyApp.woa/Contents/Frameworks/EmbeddedFramework.framework/...
	 * 
	 * ERXDeployedBundle introduces automatic url generation for embedded frameworks. The new property WOEmbeddedFrameworksPath 
	 * helps adjusting to the deployment schemes on embedding:
	 * 
	 * in the cited cases above, the property should be
	 *   WOEmbeddedFrameworksPath=Frameworks (default)
	 *   or
	 *   WOEmbeddedFrameworksPath=Contents/Frameworks
	 * 
	 * However if WOFrameworksBaseURL is custom defined, you get the behaviour as before, there is nointervention in url generation.
	 * In the case of a mixed deployment (some frameworks globally installed, some embedded), the property WOOverrideEmbeddedFrameworksPath
	 * lets activate automatic url generation for embedded frameworks, while globally installad frameworks do get their path from WOFrameworksBaseURL.
	 * 
	 * @author mstoll
	 */
	private static class ERXDeployedBundle extends WODeployedBundle {

	    private final NSMutableDictionary _myURLs;
	    private static final NSMutableDictionary TheBundles = new NSMutableDictionary(NSBundle.frameworkBundles().count());
	    private static final boolean _allowRapidTurnaround = NSPropertyListSerialization.booleanForString(NSProperties.getProperty("WOAllowRapidTurnaround"));
	    private boolean isEmbeddedFramework = false;
	    private String embeddingWrapperName = null;
	    private static final String defaultFrameworkBaseURL = "/WebObjects/Frameworks";
	    
	    /**
	     * Initializer, determines by comparing bundle paths whether bundle is embedded. 
	     * 
	     * @param nsb the given NSBundle
	     */
	    public ERXDeployedBundle(NSBundle nsb)
	    {
	    	super(nsb);

	    	_myURLs = new NSMutableDictionary();
			embeddingWrapperName = NSBundle.mainBundle().name() + ".woa";

	    	if(bundlePath().startsWith(NSBundle.mainBundle().bundlePath()))
	    	{
	    		isEmbeddedFramework = true;
	    	}
	    }

	    @Override
	    public String urlForResource(String resourceName, NSArray languagesList) {

	        String aRelativePath = relativePathForResource(resourceName, languagesList);
	        String aURL = null;
	        synchronized(_myURLs)
	        {
	            aURL = _cachedURL(aRelativePath);
	        }
	        return aURL;
	    }
	    
	    /**
	     *  Get cached URL for relative path within current bundle. If URL not yet in cache,
	     *  create one, taking into account whether the current Bundle is embedded within main Bundle
		 * 
	     * @param aRelativePath a relative path to a resource within current Bundle
	     * @return The URL path to the resource
	     */
	    private String _cachedURL(String aRelativePath)
	    {
	        String aURL = null;
	        if(aRelativePath != null)
	        {
	            aURL = (String)_myURLs.objectForKey(aRelativePath);
	            if(aURL == null)
	            {
	                String aBaseURL = null;
	                if(isFramework())
	                {
	                	// WOFrameworksBaseURL is never null but rather by default "/WebObjects/Frameworks"
	                	boolean enableAutomaticEmbeddedFrameworkPath = defaultFrameworkBaseURL.equals(WOApplication.application().frameworksBaseURL()) ||
	                			ERXProperties.booleanForKeyWithDefault("WOOverrideEmbeddedFrameworksPath", false);
	                	if(isEmbeddedFramework && enableAutomaticEmbeddedFrameworkPath)
	                	{
	            			String embeddedFrameworkPath = ERXProperties.stringForKeyWithDefault("WOEmbeddedFrameworksPath", "Frameworks");
	                        aBaseURL = WOApplication.application().applicationBaseURL() + "/" + embeddingWrapperName + "/" + embeddedFrameworkPath;
	                	}
	                	else
	                		aBaseURL = WOApplication.application().frameworksBaseURL();
	                }
	                else
	                    aBaseURL = WOApplication.application().applicationBaseURL();
	                String aWrapperName = wrapperName();
	                if(aBaseURL != null && aWrapperName != null)
	                {
	                    aURL = _NSStringUtilities.concat(aBaseURL, File.separator, aWrapperName, File.separator, aRelativePath);
	                    aURL = NSPathUtilities._standardizedPath(aURL);
	                    _myURLs.setObjectForKey(aURL, aRelativePath);
	                }
	            }
	        }
	        return aURL;
	    }

	    public static synchronized WODeployedBundle bundleWithNSBundle(NSBundle nsBundle)
	    {
	        Object aBundle = TheBundles.objectForKey(nsBundle);
	        if(aBundle == null)
	        {
	            WODeployedBundle deployedBundle = new ERXDeployedBundle(nsBundle);
	            if(_allowRapidTurnaround)
	            {
	                String bundlePath = nsBundle.bundlePathURL().getPath();
	                try
	                {
	                	if(_WOProject.ideProjectAtPath(bundlePath) != null)
	                        aBundle = new WOProjectBundle(bundlePath, deployedBundle);
	                    else
	                        aBundle = deployedBundle;
	                }
	                catch(Exception e)
	                {
	                    if(NSLog.debugLoggingAllowedForLevel(1))
	                    {
	                        NSLog.debug.appendln((new StringBuilder()).append("<WOProjectBundle>: Warning - Unable to find project at path ").append(nsBundle.bundlePathURL().getPath()).append(" - Ignoring project.").toString());
	                        NSLog.debug.appendln(e);
	                    }
	                    aBundle = deployedBundle;
	                }
	            } else
	            {
	                aBundle = deployedBundle;
	            }
	            TheBundles.setObjectForKey(aBundle, nsBundle);
	        }
	        return (WODeployedBundle)aBundle;
	    }
	    
	    public static synchronized WODeployedBundle deployedBundleForFrameworkNamed(String aFrameworkName)
	    {
	        WODeployedBundle aBundle = null;
	        NSArray bundleArray = TheBundles.allValues();
	        int baCount = TheBundles.count();
	        NSBundle nsBundle = NSBundle.bundleForName(aFrameworkName);
	        if(nsBundle == null)
	            nsBundle = NSBundle.bundleWithPath(aFrameworkName);
	        if(nsBundle != null)
	        {
	            int i = 0;
	            do
	            {
	                if(i >= baCount)
	                    break;
	                WODeployedBundle aFrameworkBundle = (WODeployedBundle)bundleArray.objectAtIndex(i);
	                if(nsBundle.equals(aFrameworkBundle.nsBundle()))
	                {
	                    aBundle = aFrameworkBundle;
	                    WODeployedBundle dBundle = aBundle.projectBundle();
	                    if(dBundle != null)
	                        aBundle = dBundle;
	                    break;
	                }
	                i++;
	            } while(true);
	        }
	        return aBundle;
	    }

		public boolean isEmbedded() {
			return isEmbeddedFramework;
		}
	}
}