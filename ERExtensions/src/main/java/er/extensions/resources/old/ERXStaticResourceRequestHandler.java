package er.extensions.resources.old;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WODynamicURL;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODeployedBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSNotificationCenter;

import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXUtilities;

/**
 * Simple static resource request handler. Allows for better debugging 
 * and you can set the document root via the system property <code>WODocumentRoot</code>.
 * @author ak
 */
public class ERXStaticResourceRequestHandler extends WORequestHandler {
	private static Logger log = LoggerFactory.getLogger(ERXStaticResourceRequestHandler.class);
	
	private static WOApplication application = WOApplication.application();

	private String _documentRoot;
	
	private boolean _useRequestHandlerPath;
	
	public ERXStaticResourceRequestHandler() {
		_documentRoot = null;
	}

	/**
	 * Creates a static resource handler for the given framework, which gives you
	 * nicer relative URLs to work with.  For instance, you could register a request
	 * handler "aj" that maps to the "Ajax" framework, which would make URLs of the 
	 * form "/aj/wonder.js" map onto Ajax's WebServerResources/wonder.js folder.
	 * 
	 * @param frameworkName the name of the framework to map to (or null/"app" for the application) 
	 */
	public ERXStaticResourceRequestHandler(String frameworkName) {
		if ("app".equals(frameworkName)) {
			frameworkName = null;
		}
		WODeployedBundle bundle = WOApplication.application().resourceManager()._cachedBundleForFrameworkNamed(frameworkName);
		File bundleFile = new File(bundle.bundlePath());
		if (bundle.isFramework()) {
			bundleFile = new File(bundleFile, "WebServerResources");
		}
		else {
			bundleFile = new File(new File(bundleFile, "Contents"), "WebServerResources");
		}
		_documentRoot = bundleFile.getAbsolutePath();
		_useRequestHandlerPath = true;
	}

	protected WOResponse _generateResponseForInputStream(InputStream is, long length, String type) {
		WOResponse response = application.createResponseInContext(null);
		if (is != null) {
			if (length != 0) {
				response.setContentStream(is, 50*1024, length);
			}
		} else {
			response.setStatus(404);
		}
		if (type != null) {
			response.setHeader(type, "content-type");
		}
		if(length != 0) {
			response.setHeader("" + length, "content-length");
		}
		return response;
	}

	private String documentRoot() {
		if (_documentRoot == null) {
			_documentRoot = ERXProperties.stringForKey("WODocumentRoot");
			if(_documentRoot == null) {
				NSDictionary dict = ERXUtilities.readDictionaryPlistFromBundleResource("WebServerConfig", "JavaWebObjects");
				_documentRoot = (String) dict.objectForKey("DocumentRoot");
			}
		}
		return _documentRoot;
	}

	@Override
	public WOResponse handleRequest(WORequest request) {
		WOResponse response = null;
		InputStream is = null;
		long length = 0;
		String contentType = null;
		String uri = request.uri();
		if (uri.charAt(0) == '/') {
			WOResourceManager rm = application.resourceManager();
			String documentRoot = documentRoot();
			File file = null;
			StringBuilder sb = new StringBuilder(documentRoot.length() + uri.length());
			String wodataKey = request.stringFormValueForKey("wodata");

			// If no ?wodata query parameter is present, check if the path starts with 'jar-data' (which signifies a path inside a jar file).
			if( wodataKey == null ) {
				if( request.requestHandlerPath().startsWith("jar-data" ) ) {
					wodataKey = request.requestHandlerPath().replace("jar-data", "jar:file://" );
				}
			}

			if(uri.startsWith("/cgi-bin") && wodataKey != null) {
				uri = wodataKey;
				if(uri.startsWith("file:")) {
					// remove file:/
					uri = uri.substring(5);
				} else {
					
				}
			} else {
				int index = uri.indexOf("/wodata=");

				if(index >= 0) {
					uri = uri.substring(index+"/wodata=".length());
				} else {
					sb.append(documentRoot);
				}
			}
			
			if (_useRequestHandlerPath) {
					try {
						WODynamicURL dynamicURL = new WODynamicURL(uri);
						String requestHandlerPath = dynamicURL.requestHandlerPath();
						if (requestHandlerPath == null || requestHandlerPath.length() == 0) {
							sb.append(uri);
						} else {
							sb.append('/');
							sb.append(requestHandlerPath);
						}
					}
					catch (Exception e) {
						throw new RuntimeException("Failed to parse URL '" + uri + "'.", e);
					}
			}
			else {
				sb.append(uri);
			}
			
			String path = sb.toString();
			try {
				path = path.replaceAll("\\?.*", "");
				if (request.userInfo() != null && !request.userInfo().containsKey("HttpServletRequest")) {
					/* PATH_INFO is already decoded by the servlet container */
					path = path.replace('+', ' ');
					path = URLDecoder.decode(path, StandardCharsets.UTF_8);
				}

				file = new File(path);

				if(path.startsWith("jar:"))
				{
					URLConnection uc = new URL(path).openConnection();
					if(uc instanceof JarURLConnection)
					{
						length = (int)uc.getContentLengthLong();
					} else
					{
						length = -1;						
					}
					is = uc.getInputStream();
					
				} else
				{
					length = (int) file.length();
					is = new FileInputStream(file);
				}
				
				contentType = rm.contentTypeForResourceNamed(path);
				log.debug("Reading file '{}' for uri: {}", file, uri);
			} catch (IOException ex) {
				if (!uri.toLowerCase().endsWith("/favicon.ico")) {
					log.info("Unable to get contents of file '{}' for uri: {}", file, uri);
				}
			}
		} else {
			log.error("Can't fetch relative path: {}", uri);
		}
		response = _generateResponseForInputStream(is, length, contentType);
		NSNotificationCenter.defaultCenter().postNotification(WORequestHandler.DidHandleRequestNotification, response);
		response._finalizeInContext(null);
		return response;
	}
}