package er.extensions.routes;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation._NSUtilities;

/**
 * The application's URL handling, as one layer of the inheritance chain:
 *
 * <pre>
 * WOApplication ← ERXRoutingApplication ← ERXAjaxApplication ← ERXApplication
 * </pre>
 *
 * Inbound, every URL is canonicalized before WO parses it ({@link #createRequest}), and every request that isn't a
 * handler URL is handed to the route table through {@link RouteRequestHandler}. Outbound, generated URLs lose the
 * adaptor prefix when short URLs are on ({@link #shortURLs()}; the context's half lives in {@link ERXRoutingContext}).
 *
 * This layer sees only WebObjects, {@link ERXShortURLs} and the routes package. Nothing above it may be required
 * for URL handling to work.
 */
public abstract class ERXRoutingApplication extends WOApplication {

	private static final String SHORT_URLS_PROPERTY = "er.extensions.ERXApplication.shortURLs";

	/**
	 * Short URLs: request handler keys as top-level routes. See {@link #shortURLs()}.
	 */
	private final boolean _shortURLs;

	public ERXRoutingApplication() {
		super();

		refuseObsoleteURLRewriterProperties();
		_shortURLs = booleanProperty( SHORT_URLS_PROPERTY, true );

		// RouteAction is a very generic name for a direct action class, so we register it explicitly to prevent problems
		_NSUtilities.setClassForName( RouteAction.class, "RouteAction" );

		// Routes: createRequest() canonicalizes every URL that is not a handler URL to /route/<path>, which is how routes
		// get here. The same handler is the default request handler as well, so that a request which somehow arrives
		// uncanonicalized with an unknown handler key gets the route table's answer rather than WO's component request
		// handler. See ERXShortURLs.canonicalize and RouteRequestHandler.
		final RouteRequestHandler routeRequestHandler = new RouteRequestHandler();
		registerRequestHandler( routeRequestHandler, ERXShortURLs.ROUTE_KEY );
		setDefaultRequestHandler( routeRequestHandler );
	}

	/**
	 * Every inbound URL is turned into the canonical WO URL for it here, before the request exists: handler-key URLs
	 * get the application prefix, everything else becomes /route/<path> under the same prefix. WO then parses a
	 * well-formed URL every time, whatever shape the front end delivered (freestyle, adaptor prefix, instance number,
	 * or already marked as a route). See {@link ERXShortURLs#canonicalize}. The request itself is built by
	 * {@link #newRequest}, which subclasses override to construct a request class of their own.
	 */
	@Override
	public final WORequest createRequest( final String method, final String url, final String httpVersion, final Map<String, ? extends List<String>> headers, final NSData content, final Map<String, Object> info ) {

		// The handler keys are read per request rather than cached: handlers may be registered after construction, and the array is small.
		@SuppressWarnings("unchecked")
		final Collection<String> handlerKeys = registeredRequestHandlerKeys();

		final String canonicalURL = ERXShortURLs.canonicalize( url, adaptorPath(), name(), applicationExtension(), handlerKeys, RouteTable.defaultRouteTable()::hasRouteFor );
		return newRequest( method, canonicalURL, httpVersion, headers, content, info );
	}

	/**
	 * @return A request for the given (already canonical) URL. Override to construct a request class of your own.
	 */
	protected WORequest newRequest( final String method, final String url, final String httpVersion, final Map<String, ? extends List<String>> headers, final NSData content, final Map<String, Object> info ) {
		return super.createRequest( method, url, httpVersion, headers, content, info );
	}

	/**
	 * @return The handler registered for the request's key, the default request handler (the route handler) otherwise.
	 *
	 * Overridden to disable WOStaticResourceRequestHandler being returned for URLs ending with resource suffixes.
	 */
	@Override
	public WORequestHandler handlerForRequest( final WORequest request ) {
		final WORequestHandler requestHandler = requestHandlerForKey( request.requestHandlerKey() );
		return requestHandler != null ? requestHandler : defaultRequestHandler();
	}

	/**
	 * The location WO redirects to when it won't serve a request itself — for example when refusing new sessions and
	 * the request carries an expired session. WOApplication builds it from the request's adaptor prefix and
	 * application name (no extension), i.e. in long form, so it is shortened like every generated URL: otherwise a
	 * front end that only knows the short form would receive a redirect it can't route. The prefix removed is the
	 * request's own, for the reason given at {@link ERXShortURLs#applicationPrefix}.
	 */
	@Override
	public String _newLocationForRequest( final WORequest request ) {
		final String location = super._newLocationForRequest( request );

		if( shortURLs() && request != null ) {
			return ERXShortURLs.shorten( location, ERXShortURLs.applicationPrefix( request.adaptorPrefix(), request.applicationName(), "" ) );
		}

		return location;
	}

	/**
	 * @return The direct-connect URL — the application's own front door, so shortened with short URLs on
	 */
	@Override
	public String directConnectURL() {
		final String url = super.directConnectURL();
		return shortURLs() ? ERXShortURLs.shorten( url, applicationURLPrefix() ) : url;
	}

	/**
	 * Whether the application accepts and generates short URLs — a request handler key as the first path segment, no
	 * adaptor prefix ({@code /wa/…} for {@code /cgi-bin/WebObjects/App.woa/wa/…}). The long form keeps working either
	 * way; explicit routes take precedence over the shortcut. Property: {@code er.extensions.ERXApplication.shortURLs},
	 * default true — the clean form is the default, an application that must keep generating long URLs opts out. See
	 * {@link ERXShortURLs}.
	 *
	 * Why a property and not a front-end rewrite rule: the point is the same URLs in development and in deployment, so
	 * a page's links work whether the app is hit directly or through a proxy, with nothing to configure per app on the
	 * front end.
	 */
	public boolean shortURLs() {
		return _shortURLs;
	}

	/**
	 * @return The prefix every long-form URL of this application starts with, {@code /cgi-bin/WebObjects/App.woa} by
	 *         default: the adaptor path (which carries no trailing slash), the application name and extension
	 */
	public String applicationURLPrefix() {
		return adaptorPath() + "/" + name() + applicationExtension();
	}

	/**
	 * ERXURLRewriter (a regular expression applied to every generated URL) is gone. It rewrote in one direction only and
	 * never saw the long form it was written to match once short URLs were on. Configuration that still asks for it
	 * stops the launch, rather than being silently ignored.
	 */
	private static void refuseObsoleteURLRewriterProperties() {
		for( final String key : List.of( "er.extensions.ERXApplication.replaceApplicationPath.pattern", "er.extensions.ERXApplication.replaceApplicationPath.replace" ) ) {
			final String value = System.getProperty( key );

			if( value != null && !value.isEmpty() ) {
				throw new IllegalStateException( "The property '" + key + "' is set, but URL rewriting by pattern has been removed. Short URLs (" + SHORT_URLS_PROPERTY + ", on by default) remove the adaptor prefix from generated URLs and accept them inbound; remove the replaceApplicationPath properties. Serving an application beneath a path of its own is not supported at present." );
			}
		}
	}

	private static boolean booleanProperty( final String key, final boolean defaultValue ) {
		final String value = System.getProperty( key );
		return value == null || value.isBlank() ? defaultValue : NSPropertyListSerialization.booleanForString( value.trim() );
	}
}
