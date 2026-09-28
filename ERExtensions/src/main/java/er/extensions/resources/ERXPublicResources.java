package er.extensions.resources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSBundle;

import er.extensions.appserver.ERXApplication;
import er.extensions.routes.RouteHandler;
import er.extensions.routes.RouteInvocation;
import er.extensions.routes.RouteTable;

/**
 * The application's public resources: the files in the {@value #FOLDER} folder of its web server resources
 * ({@code src/main/webserver-resources/public}), served at the root of the application's URL space by their path within
 * it. {@code public/favicon.ico} answers {@code /favicon.ico}, {@code public/.well-known/security.txt} answers
 * {@code /.well-known/security.txt}. For the files browsers, crawlers and other services ask a site's root for without
 * being told where: {@code favicon.ico}, {@code robots.txt}, {@code sitemap.xml}, {@code .well-known/…}.
 *
 * Off unless the application turns it on, see {@link ERXApplication#setServesPublicResources(boolean)}. When on, this is
 * the default route table's fallback: a URL is looked up here only once no request handler and no route has claimed it,
 * so a route always wins over a file, and a miss gets the route table's usual 404.
 *
 * Only files in the folder are served. The folder is indexed on first use, and a path that isn't in the index is a miss
 * without any lookup: finding a resource through the resource manager takes most of a millisecond when it isn't there,
 * and a miss is what every URL nobody claims costs (scanners' probes, and, where another framework answers what this
 * application doesn't, every one of its requests). In development the folder is indexed again on a miss, so a new file
 * is served without a restart.
 *
 * A file is served by the resource request handler, exactly as at its resource URL ({@code /res/app/public/…}), which
 * stays valid: web server resources are public by definition, so this exposes nothing that wasn't already.
 */
public class ERXPublicResources implements RouteHandler {

	private static final Logger logger = LoggerFactory.getLogger( ERXPublicResources.class );

	/**
	 * The folder in the application's web server resources whose files are served at the root
	 */
	public static final String FOLDER = "public";

	/**
	 * Where the application's web server resources are, relative to its bundle: in a built bundle, and in a project in
	 * development
	 */
	private static final List<String> WEBSERVER_RESOURCES_LOCATIONS = List.of( "Contents/WebServerResources", "WebServerResources", "src/main/webserver-resources" );

	/**
	 * Index the folder again on a miss (development), so files added since are served
	 */
	private final boolean _reindexOnMiss;

	/**
	 * The paths of the files in the folder, relative to it, with {@code /} as the separator. Null until first use.
	 */
	private volatile Set<String> _paths;

	public ERXPublicResources() {
		_reindexOnMiss = ERXApplication.isDevelopmentModeSafe();
	}

	@Override
	public WOActionResults handle( final RouteInvocation invocation ) {
		final String path = resourcePath( invocation.url() );

		if( path != null && isPublicResource( path ) ) {
			final ERXAppBasedResourceRequestHandler handler = (ERXAppBasedResourceRequestHandler)WOApplication.application().requestHandlerForKey( ERXAppBasedResourceRequestHandler.KEY );
			return handler.responseForPath( "app/" + FOLDER + "/" + path, invocation.request() );
		}

		return RouteTable.notFoundRouteHandler().handle( invocation );
	}

	/**
	 * @return true if the folder has a file at the given path
	 */
	private boolean isPublicResource( final String path ) {

		if( _paths == null ) {
			final Path folder = folder( true );
			_paths = index( folder );
			logger.info( "Public resources: serving {} file(s) from {}", _paths.size(), folder );
		}

		if( _paths.contains( path ) ) {
			return true;
		}

		if( _reindexOnMiss ) {
			_paths = index( folder( false ) );
			return _paths.contains( path );
		}

		return false;
	}

	/**
	 * @return The path a route URL asks for, relative to the folder, decoded; null for the root
	 */
	static String resourcePath( final String url ) {

		if( url == null ) {
			return null;
		}

		final int queryStart = url.indexOf( '?' );
		String path = queryStart == -1 ? url : url.substring( 0, queryStart );

		while( path.startsWith( "/" ) ) {
			path = path.substring( 1 );
		}

		if( path.isEmpty() ) {
			return null;
		}

		try {
			// A literal '+' is a '+' in a path, not a space
			return URLDecoder.decode( path.replace( "+", "%2B" ), StandardCharsets.UTF_8 );
		}
		catch( IllegalArgumentException e ) {
			return null; // malformed escape, can't name a file
		}
	}

	/**
	 * @return The paths of the regular files in the given folder, relative to it, with {@code /} as the separator; empty if the folder is null
	 */
	static Set<String> index( final Path folder ) {

		if( folder == null ) {
			return Set.of();
		}

		try( Stream<Path> files = Files.walk( folder ) ) {
			return files
					.filter( Files::isRegularFile )
					.map( file -> folder.relativize( file ).toString().replace( folder.getFileSystem().getSeparator(), "/" ) )
					.collect( Collectors.toUnmodifiableSet() );
		}
		catch( IOException e ) {
			throw new UncheckedIOException( e );
		}
	}

	/**
	 * @param warnIfMissing true to log a warning when there's no folder
	 * @return The {@value #FOLDER} folder in the application's web server resources, null if there is none
	 */
	private static Path folder( final boolean warnIfMissing ) {
		final Path bundle = Path.of( NSBundle.mainBundle().bundlePath() );

		for( final String location : WEBSERVER_RESOURCES_LOCATIONS ) {
			final Path folder = bundle.resolve( location ).resolve( FOLDER );

			if( Files.isDirectory( folder ) ) {
				return folder;
			}
		}

		if( warnIfMissing ) {
			logger.warn( "Public resources are on, but the application has no '{}' folder in its web server resources (looked in {} under {})", FOLDER, WEBSERVER_RESOURCES_LOCATIONS, bundle );
		}

		return null;
	}
}
