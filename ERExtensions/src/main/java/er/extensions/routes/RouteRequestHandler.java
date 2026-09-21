package er.extensions.routes;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WODynamicURL;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODirectActionRequestHandler;
import com.webobjects.foundation.NSArray;

import er.extensions.appserver.ERXShortURLs;

/**
 * Hands requests to the default RouteTable, through RouteAction.defaultAction(). Installed twice:
 *
 * <ul>
 * <li>as the application's default request handler, so a request whose handler key is not a registered
 * one (or that carries no key) is routed. This serves freestyle URLs from direct connect and front ends
 * that forward paths as-is.</li>
 * <li>under the key {@link #KEY}, so a front end can mark a request as a route explicitly:
 * {@code /Apps/WebObjects/App.woa/route/<path>}. A WebObjects adaptor reads a numeric segment right after
 * {@code .woa} as an instance number and consumes it before the application sees the request, so behind
 * mod_WebObjects a route such as {@code /1234} forwarded as-is is taken for an instance and lost - and the
 * adaptor drops a trailing slash after the instance number too. Behind the key the URL is an ordinary WO
 * handler URL the adaptor passes through untouched. A marked request is a freestyle request the adaptor
 * wrapped, and is handled as exactly that: unwrapped to {@code /<path>} and dispatched again, so handler-key
 * URLs ({@code /route/res/...}) reach their handler and everything else is a route, by the same rules as in
 * direct connect. The front end forwards every path the same way and needs no list of handler keys. The key
 * never appears in a generated URL.</li>
 * </ul>
 *
 * A plain WODirectActionRequestHandler would read the URL beneath the key as an action class and action name
 * ({@code /App.woa/askrifendur/verdbreyting} parses to key "askrifendur" and path "verdbreyting", and the handler
 * would go looking for RouteAction.verdbreytingAction()), so this handler reports an empty action path for every
 * request: the default action class (RouteAction) and the default action ("default") are used regardless of what
 * the URL says, and RouteAction.defaultAction() reads the full path off the request itself
 * ({@link #routePath(WORequest)}).
 */
public class RouteRequestHandler extends WODirectActionRequestHandler {

	/**
	 * The request handler key that marks a request as a route. Hardcoded on purpose; make it configurable when a need shows up.
	 */
	public static final String KEY = "route";

	public RouteRequestHandler() {
		super( RouteAction.class.getName(), "default", true );
	}

	@Override
	public NSArray<String> getRequestHandlerPathForRequest( final WORequest request ) {
		return NSArray.emptyArray();
	}

	@Override
	public WOResponse handleRequest( final WORequest request ) {

		if( KEY.equals( request.requestHandlerKey() ) ) {
			final WORequest unwrapped = unwrapped( request );
			return WOApplication.application().handlerForRequest( unwrapped ).handleRequest( unwrapped );
		}

		return super.handleRequest( request );
	}

	/**
	 * @return The marked request as the freestyle request it wraps
	 */
	private static WORequest unwrapped( final WORequest request ) {
		return WOApplication.application().createRequest( request.method(), unwrappedURL( request._uriDecomposed() ), request.httpVersion(), request.headers(), request.content(), request.userInfo() );
	}

	/**
	 * @return The freestyle URL a marked URL wraps: {@code /App.woa/1/route/a/b?x=1} gives {@code /a/b?x=1}, {@code /App.woa/route} gives {@code /}
	 */
	static String unwrappedURL( final WODynamicURL url ) {
		final String path = url.requestHandlerPath() == null ? "" : url.requestHandlerPath();
		final String query = url.queryString() == null || url.queryString().isEmpty() ? "" : "?" + url.queryString();
		return "/" + path + query;
	}

	/**
	 * The path routes match against for the given request - what was asked for, independent of the URL shape
	 * the front end delivered it in: {@code /a/b}, {@code /Apps/WebObjects/App.woa/a/b} and
	 * {@code /Apps/WebObjects/App.woa/1/a/b} all answer {@code /a/b}. The query string is dropped. (A request
	 * marked with {@link #KEY} is unwrapped before it gets here.)
	 *
	 * This is the supported way for an application to describe the current page (canonical URLs,
	 * landing-page recording and the like). Reading {@code request.uri()} for that purpose ties the
	 * application to whatever URL shape the front end happens to forward.
	 */
	public static String routePath( final WORequest request ) {
		// Read off the request's own parsed URL, not the context's: the context's URL base is normalised
		// for URL generation (ERXWOContext._setRequest) and no longer says what arrived.
		//
		// NOTE for the day routing is split out as a library of its own: the URL base fixup a freestyle
		// request needs to generate correct URLs (setPrefix(adaptorPath) + a missing application name)
		// lives in ERXWOContext._setRequest, for every context, and is deliberately NOT duplicated here.
		// Standing alone, this handler would have to do it - on the context's URL, never the request's -
		// and ERXShortURLs.applicationPrefix() goes along or gets inlined.
		return RouteTable.routePath( request.uri(), carriedApplicationPrefix( request._uriDecomposed(), request.uri(), WOApplication.application().applicationExtension() ) );
	}

	/**
	 * @return {@code <adaptor prefix>/App[.woa]} as the request wrote it (the extension is optional in WO URLs), null when the request named no application
	 */
	private static String carriedApplicationPrefix( final WODynamicURL url, final String uri, final String applicationExtension ) {

		if( url.applicationName() == null || url.applicationName().isEmpty() ) {
			return null;
		}

		final String base = ERXShortURLs.applicationPrefix( url.prefix(), url.applicationName(), "" );
		return uri.startsWith( base + applicationExtension ) ? base + applicationExtension : base;
	}
}
