package er.extensions.resources;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXApplication;

public class ERXAppBasedResourceRequestHandler extends WORequestHandler {

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

	public ERXAppBasedResourceRequestHandler() {
		_useCache = !ERXApplication.isDevelopmentModeSafe();
	}

	@Override
	public WOResponse handleRequest(WORequest request) {
		
		final String path = request.requestHandlerPath();

		if( _useCache ) {
			return _cache.computeIfAbsent(path, __ -> new CachedResourceResponse( responseForPath(path) )).streamingResponse();
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
		final ERXAppBasedResourceManager resourceManager = (ERXAppBasedResourceManager) WOApplication.application().resourceManager();

		final byte[] bytes = resourceManager.bytesForResourceNamed(resourceName, frameworkName, null);
		
		// Resource not found or isn't a webserver resource -> 404
		if( bytes == null || !resourceManager.isWebServerResource( resourceName, frameworkName ) ) {
			final WOResponse response = new WOResponse();
			response.setStatus(404);
			response.setContent("Resource '[%s]/[%s]' not found".formatted(frameworkName, resourceName) );
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
	private static class CachedResourceResponse {
		
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