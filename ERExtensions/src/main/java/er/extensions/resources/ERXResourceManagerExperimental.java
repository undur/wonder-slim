package er.extensions.resources;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXApplication;
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
 * TODO: Cleanup in construction of the response cache // Hugi 2025-10-04
 * TODO: Go over the old ERXResourceManager thoroughly and see if we're missing any required features // Hugi 2025-10-04
 * TODO: Look into resource mime-types in general. Should probably extend Wonder's mechanism of adding content types, allowing the user to add his own // Hugi 2025-10-04
 * TODO: We might want to look into resource post-processing. E.g. for templating in resources // Hugi 2025-10-05   
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
	private boolean isWebServerResource( String resourceName, String frameworkName, NSArray<String> languages ) {
		final String path = super.urlForResourceNamed(resourceName, frameworkName, languages, null );
		
		if( path == null ) {
			return false;
		}

		return path.contains("WebServerResources") || path.contains("webserver-resources");
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
		private final Map<String,CachedResourceResponse> _cache = new ConcurrentHashMap<>();

		public ERXWebServerResourceRequestHandler() {
			_useCache = !ERXApplication.isDevelopmentModeSafe();
		}

		@Override
		public WOResponse handleRequest(WORequest request) {
			
			final String path = request.requestHandlerPath();

			if( _useCache ) {
				return _cache.computeIfAbsent(path, bork -> new CachedResourceResponse( responseForPath(path) )).streamingResponse();
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

			// Resource isn't a webserver resource, 403
			// TODO: Actually, this should probably be a 404 (identical to the non-existent response) // Hugi 2025-10-05
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

			// FIXME: Temporarily setting one hour client-side caching (in production). This should be user controllable // Hugi 2025-10-04
			if( _useCache ) {
				response.setHeader("public, max-age=3600", "cache-control" );
			}

			return response;
		}

		/**
		 * Entry for our resource cache
		 * 
		 * TODO: A little silly caching strategy (constructing the streaming response from a non-streaming one). Also; we're not streaming in dev mode // Hugi 2025-10-04 
		 */
		private class CachedResourceResponse {
			
			private final int _status;
			private final NSDictionary _headers;
			private final byte[] _content;
			private final long _length;

			public CachedResourceResponse( final WOResponse response ) {
				_status = response.status();
				_headers = response.headers();
				_content = response.content().bytes();
				_length = _content.length;
			}

			public WOResponse streamingResponse() {
				final WOResponse response = new WOResponse();
				response.setStatus( _status );
				response.setHeaders(_headers);
				response.setContentStream(new ByteArrayInputStream( _content ), 32000, _length);
				return response;
			}
		}
	}
}