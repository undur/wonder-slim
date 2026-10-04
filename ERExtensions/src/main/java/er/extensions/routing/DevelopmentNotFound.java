package er.extensions.routing;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOResponse;

import er.extensions.internal.components.dev.ERXRouteNotFoundPage;
import er.extensions.internal.components.dev.ERXWelcomePage;

/**
 * What answers a URL nothing else answered, in development: a welcome page at {@code /} for an application that hasn't
 * mapped it, saying what's running and how to map a page, and otherwise a 404 page with the URL, the routes, and why
 * those that matched passed it on. Deployed, the not found answer is a plain 404.
 */
final class DevelopmentNotFound {

	static final RouteHandler HANDLER = invocation -> "/".equals( invocation.url() ) ? welcome( invocation ) : notFound( invocation );

	private DevelopmentNotFound() {}

	private static WOResponse welcome( final RouteInvocation invocation ) {
		return WOApplication.application().pageWithName( ERXWelcomePage.class.getName(), invocation.context() ).generateResponse();
	}

	private static WOResponse notFound( final RouteInvocation invocation ) {
		final ERXRouteNotFoundPage page = (ERXRouteNotFoundPage)WOApplication.application().pageWithName( ERXRouteNotFoundPage.class.getName(), invocation.context() );
		page.setURL( invocation.url() );
		final WOResponse response = page.generateResponse();
		response.setStatus( 404 );
		return response;
	}
}
