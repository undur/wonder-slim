package er.extensions.resources;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXApplication;

/**
 * Request handler for serving web server resources through the application (rather than the web server "split install").
 * 
 * URLs look the same in development and production, containing frameworkName, resourceName and languages.
 * The request handler looks at the URL, finds the data and serves it.
 * This means serving of resources is entirely controlled from within the application and works identically in development and production.
 *
 * Work to do before labeling this "totally ready":
 * 
 * FIXME: Resource cache needs work (currently stores all resources in-memory indefinitely in production) // Hugi 2025-10-04
 * FIXME: Support range requests (Range / 206 Partial Content / Accept-Ranges). We currently always serve a full 200 with the whole resource, ignoring Range headers. This breaks media: a <video> element streams via range requests, and browsers tend not to cache range-incapable media — so e.g. an autoplay marketing video re-downloads in full on every refresh, and seeking is degraded. // Hugi 2026-06-04
 * TODO: Add some nice way to control client-side caching (i.e. set caching headers on the response) // Hugi 2025-10-04
 * TODO: Handle localized resources // Hugi 2025-10-04
 * TODO: ERXResourceManager's "resource versioning" is a nice idea, we could do with something like that // Hugi 2025-10-05
 * TODO: Along the lines of versioning, we should see if serving ETag headers is worth the effort // Hugi 2025-11-16
 * TODO: Look into "resource processing". E.g. for templating in resources // Hugi 2025-10-05
 */

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
	 * In-memory resource cache, of the resources found (production), keyed by the one path that names each. Stores everything! Forever! Which isn't great. FIXME: Needs work // Hugi 2025-10-04
	 */
	private final Map<String,CachedResourceResponse> _cache = new ConcurrentHashMap<>();

	/**
	 * How many paths known to name no resource are remembered, see {@link #_missingPaths}
	 */
	static final int MISSING_PATHS_LIMIT = 10_000;

	/**
	 * Paths known to name no resource (production), so a repeated request for one is answered without the lookup, which
	 * searches every bundle and takes most of a millisecond. Bounded, as any URL can name a missing resource: past the
	 * limit, the path requested least recently is forgotten.
	 */
	private final BoundedPathSet _missingPaths = new BoundedPathSet( MISSING_PATHS_LIMIT );

	public ERXAppBasedResourceRequestHandler() {
		_useCache = !ERXApplication.isDevelopmentModeSafe();
	}

	@Override
	public WOResponse handleRequest(WORequest request) {
		return responseForPath(request.requestHandlerPath());
	}

	/**
	 * @return A response for the resource at the given path, {@code <frameworkName>/<resourceName>} ({@code app} for the
	 *         application's own resources), as this handler serves it at its URLs. Also used for the application's public
	 *         resources (see {@link ERXPublicResources}).
	 */
	public WOResponse responseForPath(final String path) {

		// A path that doesn't name a framework and a resource names nothing; not cached, as it isn't a resource
		if( path == null || path.indexOf('/') < 1 || path.endsWith("/") ) {
			return notFoundResponse(path);
		}

		if( !_useCache ) {
			return uncachedResponseForPath(path);
		}

		if( _missingPaths.contains(path) ) {
			return notFoundResponse(path);
		}

		// Only a resource that was found is cached (a null from the mapping function stores nothing)
		final CachedResourceResponse cached = _cache.computeIfAbsent(path, _ -> {
			final WOResponse response = uncachedResponseForPath(path);
			return response.status() == 200 ? new CachedResourceResponse( response ) : null;
		});

		if( cached != null ) {
			return cached.streamingResponse();
		}

		_missingPaths.add(path);
		return notFoundResponse(path);
	}

	/**
	 * @return A response for the given request handler path
	 */
	private WOResponse uncachedResponseForPath(final String path) {
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
			return notFoundResponse(frameworkName + "/" + resourceName);
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
	 * @return A 404 response for the given path
	 */
	private static WOResponse notFoundResponse(final String path) {
		final WOResponse response = new WOResponse();
		response.setStatus(404);
		response.setContent("Resource '%s' not found".formatted(path) );
		return response;
	}

	/**
	 * A set of paths holding at most a given number, forgetting the one used least recently past it
	 */
	static class BoundedPathSet {

		private final Map<String,Boolean> _paths;

		BoundedPathSet( final int limit ) {
			_paths = Collections.synchronizedMap( new LinkedHashMap<>( 16, 0.75f, true ) {
				@Override
				protected boolean removeEldestEntry( final Map.Entry<String,Boolean> eldest ) {
					return size() > limit;
				}
			} );
		}

		/**
		 * @return true if the set holds the path (which counts as a use of it)
		 */
		boolean contains( final String path ) {
			return _paths.get( path ) != null;
		}

		void add( final String path ) {
			_paths.put( path, Boolean.TRUE );
		}

		int size() {
			return _paths.size();
		}
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