package er.extensions.resources;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.foundation.NSArray;

import er.extensions.appserver.ERXWOContext;

/**
 * ResourceManager implementation for serving web server resources through the application (rather than the web server "split install")
 * 
 * URLs look the same in development and production, containing frameworkName, resourceName and languages.
 * The request handler looks at the URL, finds the data and serves it.
 * This means serving of resources is entirely controlled from within the application and works identically in development and production.
 */

public class ERXAppBasedResourceManager extends ERXResourceManagerBase {

	/**
	 * Generates a URL for the given resource. Format: .../App.woa/res/[framework]/[resourceName]?languages=[lang1,lang2,lang3]
	 * 
	 * FIXME: Handle localized resources // Hugi 2025-10-04
	 */
	@Override
	public String urlForResourceNamed(String resourceName, String frameworkName, NSArray<String> languages, WORequest request) {

		if( frameworkName == null ) {
			frameworkName = "app";
		}
		
		return context( request ).urlWithRequestHandlerKey(ERXAppBasedResourceRequestHandler.KEY, frameworkName + "/" + resourceName, null);
	}

	/**
	 * Every way to get a context
	 */
	private static WOContext context( final WORequest request ) {
		WOContext context = null; 
		
		if( request != null ) {
			context = request.context();
		}
		
		if( context == null ) {
			context = ERXWOContext.currentContext(); 
		}
		
		if( context == null ) {
			throw new IllegalStateException( "Attempted to generate a resource URL outside of a WOContext" );
		}
		
		return context;
	}

	/**
	 * @return true if the given resource exists and is a webserver (public) resource
	 */
	public boolean isWebServerResource( String resourceName, String frameworkName ) {
		final String path = super.urlForResourceNamed(resourceName, frameworkName, null, null );
		
		if( path == null ) {
			return false;
		}

		return path.contains("WebServerResources") || path.contains("webserver-resources");
	}
}