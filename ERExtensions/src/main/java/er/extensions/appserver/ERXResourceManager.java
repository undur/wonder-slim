package er.extensions.appserver;

import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation._NSStringUtilities;
import com.webobjects.foundation._NSThreadsafeMutableDictionary;

import er.extensions.foundation.ERXMutableURL;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXUtilities;

/**
 * Replacement of the WOResourceManager which adds:
 * <ul>
 * <li> dealing with nested web server resources when not deploying
 * <li> resource versioning (for better caching control)
 * </ul>
 * 
 * @property er.extensions.ERXResourceManager.versionManager the class name of the version manager to use (or "default", or "properties")
 * @author ak
 * @author mschrag
 */
public class ERXResourceManager extends ERXResourceManagerBase {
	private static Logger log = LoggerFactory.getLogger(ERXResourceManager.class);
	private WODeployedBundle TheAppProjectBundle;
	private _NSThreadsafeMutableDictionary<String, WOURLValuedElementData> _urlValuedElementsData;
	private IVersionManager _versionManager;	
	private final _NSThreadsafeMutableDictionary _myFrameworkProjectBundles = new _NSThreadsafeMutableDictionary(new NSMutableDictionary(128));
	private static final Map<String, String> _mimeTypes = _additionalMimeTypes();

	protected ERXResourceManager() {
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

		String versionManagerClassName = ERXProperties.stringForKeyWithDefault("er.extensions.ERXResourceManager.versionManager", "default");

		if ("default".equals(versionManagerClassName)) {
			_versionManager = new DefaultVersionManager();
		}
		else if ("properties".equals(versionManagerClassName)) {
			_versionManager = new PropertiesVersionManager();
		}
		else {
			try {
				_versionManager = Class.forName(versionManagerClassName).asSubclass(IVersionManager.class).newInstance();
			}
			catch (java.lang.InstantiationException e) {
				throw new RuntimeException("Unable to create the specified version manager '" + versionManagerClassName + ".", e);
			}
			catch (java.lang.IllegalAccessException e) {
				throw new RuntimeException("Unable to create the specified version manager '" + versionManagerClassName + ".", e);
			}
			catch (ClassNotFoundException e) {
				throw NSForwardException._runtimeExceptionForThrowable(e);
			}
		}
	}

	/**
	 * Sets the version manager to use for this resource manager.
	 * 
	 * @param versionManager the version manager to use for this resource manager
	 */
	public void setVersionManager(IVersionManager versionManager) {
		_versionManager = versionManager;
	}
	
	/**
	 * @return the current version manager for this resource manager.
	 */
	public IVersionManager versionManager() {
		return _versionManager;
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

    public NSArray _frameworkProjectBundles() {
        return _myFrameworkProjectBundles.immutableClone().allValues();
    }

    public WODeployedBundle _erxCachedBundleForFrameworkNamed(String aFrameworkName) {
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

		completeURL = _versionManager.versionedUrlForResourceNamed(completeURL, name, bundleName, languages, request);
		completeURL = _postprocessURL(completeURL, bundleName);
		return completeURL;
	}
	
	protected String _postprocessURL(String url, String bundleName) {

		if (WOApplication.application() instanceof ERXApplication) {
			WODeployedBundle bundle = _cachedBundleForFrameworkNamed(bundleName);
			return ERXApplication.erxApplication()._rewriteResourceURL(url, bundle);
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
	 * Overrides the original implementation appending the additionalMimeTypes to the content types dictionary.
	 *
	 * @return a dictionary containing the original mime types supported along with the additional mime types contributed by this class.
	 */
	@Override
	public NSDictionary _contentTypesDictionary() {
		final NSMutableDictionary d = new NSMutableDictionary<>();
		d.putAll(super._contentTypesDictionary());
		d.putAll(_mimeTypes);
		return d;
	}

	/**
	 * Returns whether or not complete resource URLs should be generated.
	 * 
	 * @param context the context
	 * @return whether or not complete resource URLs should be generated
	 */
	public static boolean _shouldGenerateCompleteResourceURL(WOContext context) {
		return context instanceof ERXWOContext && ((ERXWOContext)context)._generatingCompleteResourceURLs() && !ERXApplication.erxApplication().rewriteDirectConnectURL();
	}
	
 	/**
	 * Returns a fully qualified URL for the given partial resource URL (i.e. turns /whatever into http://server/whatever).
	 *  
	 * @param url the partial resource URL
	 * @param secure whether or not to generate a secure URL
	 * @param context the current context
	 * @return the complete URL
	 */
	public static String _completeURLForResource(String url, Boolean secure, WOContext context) {
		String completeUrl;
		boolean requestIsSecure = ERXRequest.isRequestSecure(context.request());
		boolean resourceIsSecure = (secure == null) ? requestIsSecure : secure.booleanValue();

		if ((resourceIsSecure && ERXProperties.stringForKey("er.extensions.ERXResourceManager.secureResourceUrlPrefix") == null) || (!resourceIsSecure && ERXProperties.stringForKey("er.extensions.ERXResourceManager.resourceUrlPrefix") == null)) {
			StringBuffer sb = new StringBuffer();
			String serverPortStr = context.request()._serverPort();
			int serverPort = (serverPortStr == null) ? 0 : Integer.parseInt(serverPortStr);
			context.request()._completeURLPrefix(sb, resourceIsSecure, serverPort);
			sb.append(url);
			completeUrl = sb.toString();
		}
		else {
			completeUrl = url;
		}

		return completeUrl;
	}
	
	/**
	 * IVersionManager provides an interface for adding version numbers to
	 * WebServerResources. This allows you to turn on "infinite" expiration
	 * dates in mod_expires, and instead control reloading by changing the
	 * resource's URL. As an example, you might append a version number as a
	 * query string on the URL (whatever.gif?1).
	 * 
	 * @author mschrag
	 */
	public static interface IVersionManager {

		/**
		 * Returns the variant of the given resource URL adjusted to include version information.
		 * 
		 * @param resourceUrl the original resource URL
		 * @param name the name of the resource being loaded
		 * @param bundleName the name of the bundle that contains the resource
		 * @param languages the languages requested
		 * @param request the request
		 * @return a versioned variant of the resourceUrl
		 */
		public String versionedUrlForResourceNamed(String resourceUrl, String name, String bundleName, NSArray<String> languages, WORequest request);
	}

	/**
	 * DefaultVersionManager just returns the resourceUrl unmodified.
	 * 
	 * @author mschrag
	 */
	public static class DefaultVersionManager implements IVersionManager {

		/**
		 * @return resourceUrl
		 */
		public String versionedUrlForResourceNamed(String resourceUrl, String name, String bundleName, NSArray<String> languages, WORequest request) {
			return resourceUrl;
		}
	}

	/**
     * Implementation of the IVersionManager interface which provides the
     * ability to control resource version numbers with Properties settings,
     * and appends the query parameter "?xxx" to WebServerResource URLs.
	 *
	 * @property er.extensions.ERXResourceManager.versionManager.default the
	 *           default version to use when an explicit version is not
	 *           specified, defaults to app startup time. Ideally you should set
	 *           this explicitly when you deploy, or multiple instance
	 *           deployments will end up with different version numbers for the
	 *           same resource.
	 * @property er.extensions.ERXResourceManager.versionManager.[bundleName].[resourceName]
	 *           the version to send for the specified resource. If not set
	 *           explicitly, the app default version will be used instead.
	 *          
	 * @author mschrag
	 */
	public static class PropertiesVersionManager implements IVersionManager {

		private static Logger log = LoggerFactory.getLogger(ERXResourceManager.class);
		private String _defaultVersion;

		public PropertiesVersionManager() {
			String key = "er.extensions.ERXResourceManager.versionManager.default";
			_defaultVersion = ERXProperties.stringForKey(key);

			if (_defaultVersion == null) {
				_defaultVersion = String.valueOf(System.currentTimeMillis());
			}
		}

		public String versionedUrlForResourceNamed(String resourceUrl, String name, String bundleName, NSArray<String> languages, WORequest request) {

			if (bundleName == null) {
				bundleName = "app";
			}

			String key = "er.extensions.ERXResourceManager.versionManager." + bundleName + "." + name;
			String version = ERXProperties.stringForKey(key);
			if (version == null) {
				version = _defaultVersion;
			}
			else if ("none".equals(version) || version.length() == 0) {
				version = null;
			}
			if (version != null) {
				try {
					ERXMutableURL url = new ERXMutableURL(resourceUrl);
					url.addQueryParameter("", version);
					resourceUrl = url.toExternalForm();
				}
				catch (MalformedURLException e) {
					log.error("Failed to construct URL from '{}'.", resourceUrl, e);
				}
			}
			return resourceUrl;
		}
	}
	
	/**
	 * Overridden to supply additional mime types that are not present in the JavaWebObjects framework.
	 * 
	 * @param aResourcePath file path of the resource, or just file name of the resource, as only the extension is required
	 * @return HTTP content type for the named resource specified by <code>aResourcePath</code>
	 */
	@Override
	public String contentTypeForResourceNamed(String aResourcePath) {
		String aPathExtension = NSPathUtilities.pathExtension(aResourcePath);

		if(aPathExtension != null && aPathExtension.length() != 0) {
			String mime = _mimeTypes.get(aPathExtension.toLowerCase());

			if(mime != null) {
				return mime;
			}
		}

		return super.contentTypeForResourceNamed(aResourcePath);
	}
	
	private static Map<String, String> _additionalMimeTypes() {
		return (Map<String, String>)ERXUtilities.readPropertyListFromFileInFramework("AdditionalMimeTypes.plist", "ERExtensions", null, "UTF-8");
	}
}