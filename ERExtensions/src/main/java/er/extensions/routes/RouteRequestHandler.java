package er.extensions.routes;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WODynamicURL;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver._private.WODirectActionRequestHandler;
import com.webobjects.foundation.NSArray;

/**
 * The request handler behind {@link ERXShortURLs#ROUTE_KEY}: hands the request to the default RouteTable through
 * RouteAction.defaultAction(). Every request that is not a handler URL reaches it, because
 * {@code ERXRoutingApplication.createRequest()} canonicalizes such URLs to {@code <prefix>/route/<path>} before WO parses
 * them - whatever shape the front end delivered (freestyle, adaptor prefix, instance number, or already marked).
 * The key is internal: it never appears in a generated URL, and public URLs are the same in development and
 * deployment. The handler is the application's default request handler too, as a safety net.
 *
 * A plain WODirectActionRequestHandler would read the handler path as an action class and action name
 * ({@code /route/askrifendur/verdbreyting} would go looking for RouteAction.verdbreytingAction()), so this handler
 * reports an empty action path for every request: the default action class (RouteAction) and the default action
 * ("default") are used regardless, and RouteAction.defaultAction() takes the route path off the request
 * ({@link #routePath(WORequest)}).
 */
public class RouteRequestHandler extends WODirectActionRequestHandler {

	public RouteRequestHandler() {
		super( RouteAction.class.getName(), "default", true );
	}

	@Override
	public NSArray<String> getRequestHandlerPathForRequest( final WORequest request ) {
		return NSArray.emptyArray();
	}

	/**
	 * The path routes match against for the given request: what was asked for, independent of the URL shape the front
	 * end delivered it in, since every request has been canonicalized to {@code <prefix>/route/<path>} by the time it
	 * exists. The query string is not part of it.
	 *
	 * This is the supported way for an application to describe the current page (canonical URLs, landing-page
	 * recording and the like). Reading {@code request.uri()} for that purpose ties the application to the canonical
	 * form, which is an implementation detail.
	 */
	public static String routePath( final WORequest request ) {

		if( ERXShortURLs.ROUTE_KEY.equals( request.requestHandlerKey() ) ) {
			final String path = request._uriDecomposed().requestHandlerPath();
			return "/" + ( path == null ? "" : path );
		}

		// Not a route: a handler URL (a page served by a direct or component action describing itself), or this handler
		// answering as the default request handler for a URL that names another application. The path is the URI
		// without query string, carried application prefix and instance number: /App.woa/1/wa/default gives /wa/default.
		final String uri = request.uri();
		final int q = uri.indexOf( '?' );
		final String path = q == -1 ? uri : uri.substring( 0, q );

		final WODynamicURL url = request._uriDecomposed();

		if( url.applicationName() == null || url.applicationName().isEmpty() ) {
			return path;
		}

		final String base = ERXShortURLs.applicationPrefix( url.prefix(), url.applicationName(), "" );
		final String extension = WOApplication.application().applicationExtension();
		return ERXShortURLs.shorten( path, path.startsWith( base + extension ) ? base + extension : base );
	}
}
