package er.extensions.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.foundation.NSArray;

import er.extensions.appserver.ERXApplication;
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
	 * Stamp resource URLs with their content (production), see {@link #urlForResourceNamed}
	 */
	private final boolean _stampsURLs = !ERXApplication.isDevelopmentModeSafe();

	/**
	 * The content stamp of each resource a URL has been generated for, by {@code <frameworkName>/<resourceName>}, empty for
	 * one that isn't a web server resource. Computed on first use; resources don't change while a deployed application runs.
	 */
	private final Map<String,String> _stamps = new ConcurrentHashMap<>();

	/**
	 * Generates a URL for the given resource. Format: .../App.woa/res/[framework]/[resourceName]
	 *
	 * In production, the resource name carries a stamp of the resource's content ({@code css/site.3f9c1e07ab.css}, see
	 * {@link ERXResourceStamps}), so the URL changes whenever the content does and the resource request handler can let
	 * browsers cache it for good. Not in development, where files change while the application runs.
	 *
	 * Localized resources aren't supported: {@code languages} is ignored and resources are resolved without languages, so a
	 * resource that only exists in a language's {@code .lproj} folder isn't found.
	 */
	@Override
	public String urlForResourceNamed(String resourceName, String frameworkName, NSArray<String> languages, WORequest request) {

		if( frameworkName == null ) {
			frameworkName = "app";
		}

		String path = frameworkName + "/" + resourceName;

		if( _stampsURLs ) {
			final String stamp = stamp( resourceName, frameworkName );

			if( stamp != null ) {
				path = frameworkName + "/" + ERXResourceStamps.stampedPath( resourceName, stamp );
			}
		}

		return context( request ).urlWithRequestHandlerKey(ERXAppBasedResourceRequestHandler.KEY, path, null);
	}

	/**
	 * @return The content stamp of the given resource, null if it isn't a web server resource. Computed on first use and
	 *         remembered, for the URLs generated for it and for the stamps requested for it alike.
	 */
	String stamp( final String resourceName, final String frameworkName ) {
		final String stamp = _stamps.computeIfAbsent( frameworkName + "/" + resourceName, _ -> {

			// Checked first, so a resource that isn't one is never read
			if( !isWebServerResource( resourceName, frameworkName ) ) {
				return "";
			}

			try( InputStream content = inputStreamForResourceNamed( resourceName, frameworkName, null ) ) {
				return content == null ? "" : ERXResourceStamps.stamp( content );
			}
			catch( IOException e ) {
				throw new UncheckedIOException( e );
			}
		} );

		return stamp.isEmpty() ? null : stamp;
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
	 * Where a bundle keeps its web server resources, relative to the bundle: in a built application, in a framework (a
	 * jar or a directory), and in a project in development. A resource is a web server resource when its path within its
	 * bundle starts with one of these.
	 */
	private static final List<String> WEBSERVER_RESOURCES_FOLDERS = List.of( "Contents/WebServerResources/", "WebServerResources/", "src/main/webserver-resources/" );

	/**
	 * A bundle segment in a resource URL: {@code App.woa/} or {@code Framework.framework/}
	 */
	private static final Pattern BUNDLE_SEGMENT = Pattern.compile( "[^/]+\\.(woa|framework)/" );

	/**
	 * @return true if the given resource exists and is a webserver (public) resource
	 *
	 * This decides what the resource request handler may serve: it finds any resource in a bundle, the application's
	 * {@code Properties} included, so only one in a web server resources folder must pass. Decided from the resource's URL
	 * as WebObjects composes it ({@code /WebObjects/App.woa/Contents/WebServerResources/…},
	 * {@code /WebObjects/Frameworks/F.framework/WebServerResources/…}, or {@code /WebObjects/App.woa/src/main/webserver-resources/…}
	 * in development), see {@link #isInWebServerResourcesFolder(String)}.
	 */
	public boolean isWebServerResource( String resourceName, String frameworkName ) {
		final String url = super.urlForResourceNamed(resourceName, frameworkName, null, null );
		return url != null && isInWebServerResourcesFolder( url );
	}

	/**
	 * @return true if the resource URL's path within its bundle lies in the bundle's web server resources folder: it starts
	 *         with one of {@link #WEBSERVER_RESOURCES_FOLDERS}, and has no {@code .} or {@code ..} segment that could lead
	 *         back out of it. Only that position counts, so a folder or file of that name elsewhere (a
	 *         {@code Resources/WebServerResources/} folder, say) doesn't make a resource public.
	 */
	static boolean isInWebServerResourcesFolder( final String url ) {
		final Matcher bundleSegment = BUNDLE_SEGMENT.matcher( url );

		if( !bundleSegment.find() ) {
			return false;
		}

		final String pathInBundle = url.substring( bundleSegment.end() );

		for( final String segment : pathInBundle.split( "/" ) ) {
			if( segment.equals( "." ) || segment.equals( ".." ) ) {
				return false;
			}
		}

		return WEBSERVER_RESOURCES_FOLDERS.stream().anyMatch( pathInBundle::startsWith );
	}
}