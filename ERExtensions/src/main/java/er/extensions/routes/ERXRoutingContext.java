package er.extensions.routes;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WODynamicURL;
import com.webobjects.appserver.WORequest;

/**
 * The context's half of the application's URL handling (see {@link ERXRoutingApplication}), as one layer of the
 * inheritance chain:
 *
 * <pre>
 * WOContext ← ERXRoutingContext ← ERXAjaxContext ← ERXWOContext
 * </pre>
 */
public class ERXRoutingContext extends WOContext {

	public ERXRoutingContext( final WORequest request ) {
		super( request );
	}

	/**
	 * WOContext copies the request's adaptor prefix and application name into
	 * the URL it composes generated URLs from. Two kinds of request leave that
	 * base broken: a root request ({@code /}, or a front end's rewrite of it
	 * to {@code …/wa/default} without the application name) parses to an
	 * empty application name, and a freestyle route URL ({@code /about})
	 * parses with its whole path as the prefix and no name. Left alone, every
	 * URL such a context generates reads {@code /cgi-bin/WebObjects/.woa/wo/…}
	 * or {@code /about/.woa/wo/…} — which WO happens to accept, since it
	 * ignores the application name when dispatching, but which no front end
	 * routes by application and which short URLs can't recognise as the
	 * application's own.
	 *
	 * So the base is normalised here, once, for every context: a prefix that
	 * doesn't contain the adaptor token is replaced by the application's
	 * adaptor path, and a missing application name is filled in. A real
	 * prefix the request carried ({@code /Apps/WebObjects}) is kept, so URLs
	 * keep mirroring the request.
	 */
	@Override
	public void _setRequest( final WORequest request ) {
		super._setRequest( request );

		if( request != null ) {
			final WOApplication application = WOApplication.application();
			final WODynamicURL url = _url();
			final String prefix = url.prefix();

			if( prefix == null || !prefix.toLowerCase().contains( application.adaptorName().toLowerCase() ) ) {
				url.setPrefix( application.adaptorPath() );
			}

			if( url.applicationName() == null || url.applicationName().isEmpty() ) {
				url.setApplicationName( application.name() );
			}
		}
	}

	/**
	 * Every URL WOContext generates — component actions, direct actions, resources, routes — is assembled by this method,
	 * so shortening here covers them all: an exact removal of the prefix the URL was built from. Redirect locations take
	 * the same path in {@link ERXRoutingApplication#_newLocationForRequest(WORequest)}.
	 *
	 * The prefix removed is the one WOContext just composed the URL from — this context's parsed request URL — not the
	 * application's adaptorPath(): behind a front end that rewrites into {@code /Apps/WebObjects/App.woa/…} the two
	 * differ, and only the former is in the URL. The URL base is normalised in {@link #_setRequest(WORequest)}, so it
	 * always names the application; a context without a request falls back to the application's own prefix.
	 */
	@Override
	public String _urlWithRequestHandlerKey( final String requestHandlerKey, final String requestHandlerPath, final String queryString, final boolean isSecure, final int somePort ) {
		final String url = super._urlWithRequestHandlerKey( requestHandlerKey, requestHandlerPath, queryString, isSecure, somePort );
		final ERXRoutingApplication application = (ERXRoutingApplication)WOApplication.application();
		return application.shortURLs() ? ERXShortURLs.shorten( url, generatedApplicationPrefix( application ) ) : url;
	}

	/**
	 * @return {@code <adaptor prefix>/<App>.woa} as this context composes its URLs, see {@link #_urlWithRequestHandlerKey}
	 */
	private String generatedApplicationPrefix( final ERXRoutingApplication application ) {
		final WODynamicURL url = _url();

		if( url == null || url.applicationName() == null || url.applicationName().isEmpty() ) {
			return application.applicationURLPrefix();
		}

		return ERXShortURLs.applicationPrefix( url.prefix(), url.applicationName(), application.applicationExtension() );
	}
}
