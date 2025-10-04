package er.extensions.appserver;

import java.util.Objects;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;

/**
 * Experimental nicer URL generation for WO webserver resources.
 * 
 * FIXME: Currently serves all resources, Webserver or not. So don't use this if you don't want to share your EOModels and Property files with the world. // Hugi 2025-10-04  
 * FIXME: Currently does not handle localized resources // Hugi 2025-10-04
 * FIXME: We should be using streaming resources (requires us to figure how to get the content-length for a resource first) // Hugi 2025-10-04
 */

public class ERXResourceManagerExperimental extends ERXResourceManagerBase {

	/**
	 * Experimental URL generation. In this scheme, urls will only contain frameworkName, resourceName and language. It's then the job of the request handler to resolve the location of the actual data to serve
	 * 
	 * URL format: MyApp.woa/_sr_/[framework]/resourceName?languages=[lang1,lang2,lang3]
	 */
	@Override
	public String urlForResourceNamed(String resourceName, String frameworkName, NSArray<String> languages, WORequest request) {

		if( frameworkName == null ) {
			frameworkName = "app";
		}
		
		return context( request ).urlWithRequestHandlerKey(ERXWebServerResourceRequestHandler.KEY, frameworkName + "/" + resourceName, null);
	}

	/**
	 * Horrid way to desperately attempt to get a request
	 *  
	 * FIXME: This sucks. Ideally we'd be able to generate that URL without WOContext's assistance // Hugi 2025-10-04
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

		public static final String KEY = "res";

		@Override
		public WOResponse handleRequest(WORequest request) {
			
			// Decode the URL. String before first slash is the framework, everything after that is the resource name
			final String path = request.requestHandlerPath();
			final int firstSlashIndex = path.indexOf('/');
			final String frameworkName = path.substring( 0, firstSlashIndex );
			final String resourceName = path.substring(firstSlashIndex+1, path.length());	

			final WOResourceManager resourceManager = WOApplication.application().resourceManager();

			final byte[] bytes = resourceManager.bytesForResourceNamed(resourceName, frameworkName, null);
			
			// Resource not found, 404
			if( bytes == null ) {
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
			return response;
		}
	}
}