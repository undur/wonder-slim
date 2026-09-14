package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WODirectAction;
import com.webobjects.appserver.WODynamicURL;
import com.webobjects.appserver.WORequest;

import er.extensions.appserver.ERXShortURLs;

/**
 * A Direct Action that passes requests on to the default RouteTable for handling.
 *
 * The "real" URL to handle is obtained from either the query parameter [url]
 * or the [redirect_url] header passed in by Apache's 404 handler.
 */

public class RouteAction extends WODirectAction {

	public RouteAction( WORequest r ) {
		super( r );
	}

	/**
	 * @return The result of invoking the route mathing the provided URL.
	 */
	public WOActionResults handlerAction() {
		return RouteTable.defaultRouteTable().handle( request(), true );
	}
	
	@Override
	public WOActionResults defaultAction() {

		final WOApplication application = WOApplication.application();
		final WODynamicURL url = context()._url();

		// The application prefix the request actually carried, so the routes can match the
		// path beneath it — captured before the fixup below overwrites it. A freestyle URL
		// parses to no application name (its whole path lands in the prefix) and carries none.
		final String carriedPrefix = carriedApplicationPrefix( url, request().uri(), application.applicationExtension() );

		// A freestyle request URL won't have an adaptor prefix or an application name, so we have to set it explicitly ourselves to ensure proper dynamic URL generation
		url.setPrefix(application.adaptorPath());

		if( url.applicationName() == null || url.applicationName().isEmpty() ) {
			url.setApplicationName(application.name());
		}

		return RouteTable.defaultRouteTable().handle( request(), RouteTable.routePath( request().uri(), carriedPrefix ) );
	}

	/**
	 * @return {@code <adaptor prefix>/App[.woa]} as the request wrote it (the extension is optional in WO URLs), null when the request named no application
	 */
	static String carriedApplicationPrefix( final WODynamicURL url, final String uri, final String applicationExtension ) {

		if( url.applicationName() == null || url.applicationName().isEmpty() ) {
			return null;
		}

		final String base = ERXShortURLs.applicationPrefix( url.prefix(), url.applicationName(), "" );
		return uri.startsWith( base + applicationExtension ) ? base + applicationExtension : base;
	}
}