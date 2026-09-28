package er.extensions.resources;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXApplication;

/**
 * Request handler for serving web server resources through the application (rather than the web server "split install").
 * 
 * URLs look the same in development and production, containing frameworkName and resourceName.
 * The request handler looks at the URL, finds the data and serves it.
 * This means serving of resources is entirely controlled from within the application and works identically in development and production.
 *
 * Localized resources aren't supported: resources are looked up without languages, so one that only exists in a
 * language's .lproj folder isn't found.
 *
 * Work to do before labeling this "totally ready":
 * 
 * FIXME: Resource cache needs work (currently stores all resources in-memory indefinitely in production) // Hugi 2025-10-04
 * TODO: Add some nice way to control client-side caching (i.e. set caching headers on the response) // Hugi 2025-10-04
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
		return responseForPath(request.requestHandlerPath(), request);
	}

	/**
	 * @param request The request, for a range it asks for (media players ask for parts of a file); null to serve the
	 *        whole resource
	 * @return A response for the resource at the given path, {@code <frameworkName>/<resourceName>} ({@code app} for the
	 *         application's own resources), as this handler serves it at its URLs. Also used for the application's public
	 *         resources (see {@link ERXPublicResources}).
	 */
	public WOResponse responseForPath(final String path, final WORequest request) {

		// A path that doesn't name a framework and a resource names nothing; not cached, as it isn't a resource
		if( path == null || path.indexOf('/') < 1 || path.endsWith("/") ) {
			return notFoundResponse(path);
		}

		// A stamped name (css/site.3f9c1e07ab.css, as the resource manager generates it) names the resource without the
		// stamp. If there's nothing by that name, the name may be a file's own, and is looked up as it is.
		final ERXResourceStamps.Stamped stamped = ERXResourceStamps.parse(path);
		String resourcePath = stamped != null ? stamped.unstampedPath() : path;
		String requestedStamp = stamped != null ? stamped.stamp() : null;
		CachedResourceResponse resource = find(resourcePath);

		if( resource == null && stamped != null ) {
			resourcePath = path;
			requestedStamp = null;
			resource = find(path);
		}

		if( resource == null ) {
			return notFoundResponse(path);
		}

		// The resource's stamp is its ETag: the same content always has the same one
		final String stamp = currentStamp(resourcePath);
		final String etag = stamp == null ? null : "\"" + stamp + "\"";

		// Cached for good when requested by its current stamp, as the URL changes whenever the content does. Otherwise
		// the browser may keep it, but must ask again before using it (and gets a 304 if it hasn't changed): an unstamped
		// URL, or a stamp that isn't the resource's own, as when an instance that hasn't been updated yet gets a request
		// for a newer version during a deploy.
		final boolean current = _useCache && requestedStamp != null && requestedStamp.equals(stamp);
		final String cacheControl = current ? STAMPED_CACHE_CONTROL : "no-cache";

		if( etag != null && request != null && matchesAny(request.headerForKey("if-none-match"), etag) ) {
			final WOResponse notModified = new WOResponse();
			notModified.setStatus(304);
			notModified.setHeader(etag, "etag");
			notModified.setHeader(cacheControl, "cache-control");
			return notModified;
		}

		final WOResponse response = resource.response(request, etag);

		if( response.status() != 416 ) {
			response.setHeader(cacheControl, "cache-control");

			if( etag != null ) {
				response.setHeader(etag, "etag");
			}
		}

		return response;
	}

	/**
	 * @return true if an {@code If-None-Match} header names the given entity tag, or is {@code *}. Compared weakly, as the
	 *         header's semantics require: a {@code W/} prefix doesn't matter.
	 */
	static boolean matchesAny(final String ifNoneMatch, final String etag) {

		if( ifNoneMatch == null ) {
			return false;
		}

		for( String candidate : ifNoneMatch.split(",") ) {
			candidate = candidate.trim();

			if( candidate.startsWith("W/") ) {
				candidate = candidate.substring(2);
			}

			if( candidate.equals("*") || candidate.equals(etag) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @return The stamp of the content of the resource at the given (unstamped) path, the one its generated URLs carry
	 */
	private static String currentStamp(final String path) {
		final int firstSlashIndex = path.indexOf('/');
		final ERXAppBasedResourceManager resourceManager = (ERXAppBasedResourceManager) WOApplication.application().resourceManager();
		return resourceManager.stamp(path.substring(firstSlashIndex + 1), path.substring(0, firstSlashIndex));
	}

	/**
	 * The cache lifetime of a resource requested by a URL with its current stamp: a year, and never revalidated, as the
	 * URL changes whenever the content does
	 */
	private static final String STAMPED_CACHE_CONTROL = "public, max-age=31536000, immutable";

	/**
	 * @return The resource at the given path, cached in production; null if there's none
	 */
	private CachedResourceResponse find(final String path) {

		if( !_useCache ) {
			final WOResponse response = uncachedResponseForPath(path);
			return response.status() == 200 ? new CachedResourceResponse( response ) : null;
		}

		if( _missingPaths.contains(path) ) {
			return null;
		}

		// Only a resource that was found is cached (a null from the mapping function stores nothing)
		final CachedResourceResponse cached = _cache.computeIfAbsent(path, _ -> {
			final WOResponse response = uncachedResponseForPath(path);
			return response.status() == 200 ? new CachedResourceResponse( response ) : null;
		});

		if( cached == null ) {
			_missingPaths.add(path);
			return null;
		}

		return cached;
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
		response.setHeader("bytes", "accept-ranges");

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

		/**
		 * @param etag The resource's entity tag, for {@code If-Range}; may be null
		 * @return A response to the given request for the resource: the whole of it, or the range the request asks for
		 *         ({@code Range: bytes=…}, one range) as 206 Partial Content, or 416 when that range lies past its end. A
		 *         request with {@code If-Range} (the range only if the resource hasn't changed) gets the range only if it
		 *         names the resource's current entity tag, and the whole resource otherwise (a date included, as we don't
		 *         keep modification dates).
		 */
		public WOResponse response( final WORequest request, final String etag ) {
			final String ifRange = request == null ? null : request.headerForKey( "if-range" );
			final boolean rangeApplies = ifRange == null || ( etag != null && ifRange.trim().equals( etag ) );
			final String rangeHeader = request == null || !rangeApplies ? null : request.headerForKey( "range" );
			final long[] range = range( rangeHeader, _length );

			final WOResponse response = new WOResponse();
			response.setHeaders( _headers );

			if( range == null ) {
				response.setStatus( _status );
				response.setContentStream( new ByteArrayInputStream( _content ), 32000, _length );
				return response;
			}

			// Not to be kept by any cache, which could otherwise answer a request for the whole resource with it
			if( range.length == 0 ) {
				response.setStatus( 416 );
				response.setHeader( "no-store", "cache-control" );
				response.setHeader( "bytes */" + _length, "content-range" );
				response.setHeader( "0", "content-length" );
				return response;
			}

			final long start = range[0];
			final long length = range[1] - start + 1;
			response.setStatus( 206 );
			response.setHeader( "bytes " + start + "-" + range[1] + "/" + _length, "content-range" );
			response.setHeader( String.valueOf( length ), "content-length" );
			response.setContentStream( new ByteArrayInputStream( _content, (int)start, (int)length ), 32000, length );
			return response;
		}
	}

	/**
	 * A single byte range, {@code bytes=start-end}, {@code bytes=start-} or {@code bytes=-suffixLength}
	 */
	private static final Pattern BYTE_RANGE = Pattern.compile( "bytes=(\\d*)-(\\d*)" );

	/**
	 * @param header A {@code Range} header, may be null
	 * @param length The length of the resource
	 * @return The first and last byte (inclusive) of the range the header asks for; an empty array if the range lies
	 *         past the resource's end (unsatisfiable); null to serve the whole resource: no header, one we don't
	 *         understand, or several ranges (which the whole resource answers, as the specification allows)
	 */
	static long[] range( final String header, final long length ) {

		if( header == null ) {
			return null;
		}

		final Matcher matcher = BYTE_RANGE.matcher( header.trim() );

		if( !matcher.matches() || (matcher.group(1).isEmpty() && matcher.group(2).isEmpty()) ) {
			return null;
		}

		try {
			// A suffix: the last N bytes
			if( matcher.group(1).isEmpty() ) {
				final long suffixLength = Long.parseLong( matcher.group(2) );
				return suffixLength == 0 || length == 0 ? new long[0] : new long[] { Math.max( 0, length - suffixLength ), length - 1 };
			}

			final long start = Long.parseLong( matcher.group(1) );

			if( start >= length ) {
				return new long[0];
			}

			if( matcher.group(2).isEmpty() ) {
				return new long[] { start, length - 1 };
			}

			final long end = Long.parseLong( matcher.group(2) );

			// A last byte before the first is an invalid range, which is ignored
			return end < start ? null : new long[] { start, Math.min( end, length - 1 ) };
		}
		catch( NumberFormatException e ) {
			return null; // a number too large to be a position in the resource
		}
	}
}
