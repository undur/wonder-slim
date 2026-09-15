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

		// The application prefix the request actually carried, so the routes can match the path
		// beneath it. Read off the request's own parsed URL, not the context's: the context's
		// URL base is normalised for URL generation (ERXWOContext._setRequest) and no longer says
		// what arrived. A freestyle URL parses to no application name and carries no prefix.
		//
		// NOTE for the day routing is split out as a library of its own: this action used to fix
		// up the context's URL base itself (setPrefix(adaptorPath) + a missing application name),
		// so a freestyle request generated correct URLs. That fixup now lives in
		// ERXWOContext._setRequest, for every context, and is deliberately NOT duplicated here.
		// Standing alone, RouteAction would have to do it again - after capturing carriedPrefix,
		// and on the context's URL, never the request's - and ERXShortURLs.applicationPrefix(),
		// which carriedApplicationPrefix() below relies on, goes along or gets inlined.
		final String carriedPrefix = carriedApplicationPrefix( request()._uriDecomposed(), request().uri(), WOApplication.application().applicationExtension() );

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