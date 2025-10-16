package er.extensions.resources;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.foundation.NSArray;

import er.extensions.appserver.ERXWOContext;

/**
 * ResourceManager/RequestHandler implementations. For serving web server resources through the application (rather than the web server "split install")
 * 
 * URLs look the same in development and production, containing frameworkName, resourceName and languages.
 * The request handler looks at the URL, finds the data and serves it.
 * This means serving of resources is entirely controlled from within the application and works identically in development and production.
 *
 * Work to do before labeling this "totally ready":
 * 
 * FIXME: Resource cache needs work (currently stores all resources in-memory indefinitely in production) // Hugi 2025-10-04
 * TODO: Add some nice way to control client-side caching (i.e. set caching headers on the response) // Hugi 2025-10-04
 * TODO: Look into better ways to check if a resource is an actual WebServerResource (and not a private/internal/application resource) // Hugi 2025-10-04  
 * TODO: Handle localized resources // Hugi 2025-10-04
 * TODO: ERXResourceManager's "resource versioning" is nice, we need that // Hugi 2025-10-05
 * TODO: Go over the old ERXResourceManager thoroughly and see if we're missing any required features // Hugi 2025-10-04
 * TODO: Look into "resource processing". E.g. for templating in resources // Hugi 2025-10-05   
 */

public class ERXResourceManagerAppBased extends ERXResourceManagerBase {

	/**
	 * Generates a URL for the given resource. Format: .../App.woa/res/[framework]/[resourceName]?languages=[lang1,lang2,lang3]
	 */
	@Override
	public String urlForResourceNamed(String resourceName, String frameworkName, NSArray<String> languages, WORequest request) {

		if( frameworkName == null ) {
			frameworkName = "app";
		}
		
		return context( request ).urlWithRequestHandlerKey(ERXWebServerResourceRequestHandler.KEY, frameworkName + "/" + resourceName, null);
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
			throw new NullPointerException( "Couldn't find a WOContext to use" );
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