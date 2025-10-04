package er.extensions.appserver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;

/**
 * Experimental nicer URL generation for WO webserver resources.
 * URL only contain frameworkName, resourceName and languages. It's then the job of the request handler to resolve the location of the actual data to serve
 * 
 * FIXME: Currently uses a very basic check to see if the resource is an actual WebServerResource (and not a private/internal/application resource) // Hugi 2025-10-04  
 * FIXME: Currently no handling of localized resources // Hugi 2025-10-04
 * FIXME: Should be constructing streaming responses // Hugi 2025-10-04
 * FIXME: Resource cache needs work (currently stores all resources in-memory indefinitely in production) // Hugi 2025-10-04
 * FIXME: Missing a nice way to control client-side resource caching (i.e. set caching headers) // Hugi 2025-10-04
 * FIXME: We need to decide which features of the old ERXResourceManager need implementation here as well (URL rewriting etc.) // Hugi 2025-10-04  
 */

public class ERXResourceManagerExperimental extends ERXResourceManagerBase {

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
	 * @return true if the given resource is a webserver (public) resource
	 * 
	 * FIXME: Experimental implementation, wouldn't deem this reliable // Hugi 2025-10-04
	 */
	public boolean isWebServerResource( String resourceName, String frameworkName, NSArray<String> languages ) {
		final String path = super.urlForResourceNamed(resourceName, frameworkName, languages, null );
		
		// FIXME: Determining whether a non-existent resource is a webserver resources or not is almost a philosophical question // Hugi 2025-10-04
		if( path == null ) {
			return false;
		}

		return path.contains("WebServerResources") || path.contains("webserver-resources");
	}

	/**
	 * Horrid way to desperately attempt to get a context
	 *  
	 * FIXME: This sucks. Ideally we'd be able to generate URLs without WOContext's help // Hugi 2025-10-04
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

	public static class ERXWebServerResourceRequestHandler extends WORequestHandler {

		/**
		 * Default request handler key
		 */
		public static final String KEY = "res";

		/**
		 * Indicates if we want to enable in-memory caching of resources
		 */
		private final boolean _useCache;
		
		/**
		 * In-memory resource cache. Stores everything! Forever! Which isn't great. FIXME: Needs work // Hugi 2025-10-04
		 */
		private final Map<String,CachedResource> _cache = new ConcurrentHashMap<>();

		public ERXWebServerResourceRequestHandler() {
			_useCache = !ERXApplication.isDevelopmentModeSafe();
		}

		@Override
		public WOResponse handleRequest(WORequest request) {
			
			final String path = request.requestHandlerPath();

			if( _useCache ) {
				return _cache.computeIfAbsent(path, bork -> new CachedResource( responseForPath(path) )).response();
			}

			return responseForPath(path);
		}

		/**
		 * @return A response for the given request handler path
		 */
		private WOResponse responseForPath(final String path) {
			final int firstSlashIndex = path.indexOf('/');
			final String frameworkName = path.substring( 0, firstSlashIndex );
			final String resourceName = path.substring(firstSlashIndex+1, path.length());
			return responseForResource(frameworkName, resourceName);
		}

		/**
		 * @return A response for the given resource
		 */
		private WOResponse responseForResource(final String frameworkName, final String resourceName) {
			final ERXResourceManagerExperimental resourceManager = (ERXResourceManagerExperimental) WOApplication.application().resourceManager();

			final byte[] bytes = resourceManager.bytesForResourceNamed(resourceName, frameworkName, null);
			
			// Resource not found, 404
			if( bytes == null ) {
				final WOResponse response = new WOResponse();
				response.setStatus(404);
				response.setContent("Resource '[%s]/[%s]' not found".formatted(frameworkName, resourceName) );
				return response;
			}

			if( !resourceManager.isWebServerResource( resourceName, frameworkName, NSArray.emptyArray() ) ) {
				final WOResponse response = new WOResponse();
				response.setStatus(403);
				response.setContent("Resource '[%s]/[%s]' forbidden".formatted(frameworkName, resourceName) );
				return response;
			}

			// Resource found, return that thing
			final String contentType = resourceManager.contentTypeForResourceNamed(resourceName);
			final String contentLength = String.valueOf( bytes.length );

			final WOResponse response = new WOResponse();
			response.setContent(bytes);
			response.setHeader(contentLength, "content-length");
			response.setHeader(contentType, "content-type");

			// FIXME: Temporary client-side caching. This should be user controllable // Hugi 2025-10-04
			if( _useCache ) {
				response.setHeader("public, max-age=3600", "cache-control" );
			}

			return response;
		}

		/**
		 * Entry for our resource cache
		 */
		private record CachedResource( WOResponse response ) {}
	}
}