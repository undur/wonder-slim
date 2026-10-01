package er.extensions.routes;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOResponse;

import er.extensions.internal.components.dev.ERXRouteNotFoundPage;
import er.extensions.internal.components.dev.ERXWelcomePage;

/**
 * Answers a URL nothing claims in development, with a page rather than the plain 404: {@link ERXWelcomePage} for
 * {@code /}, which the application hasn't mapped, and {@link ERXRouteNotFoundPage}, with the mapped routes, for any
 * other URL. ERXApplication registers it in development (see {@link RouteTable#setNotFoundRouteHandler(RouteHandler)}).
 */
public class ERXDevelopmentNotFoundRouteHandler implements RouteHandler {

	@Override
	public WOActionResults handle( final RouteInvocation invocation ) {
		return "/".equals( invocation.url() ) ? welcomeResponse( invocation ) : notFoundResponse( invocation );
	}

	private static WOResponse welcomeResponse( final RouteInvocation invocation ) {
		return WOApplication.application().pageWithName( ERXWelcomePage.class.getName(), invocation.request().context() ).generateResponse();
	}

	private static WOResponse notFoundResponse( final RouteInvocation invocation ) {
		final ERXRouteNotFoundPage page = (ERXRouteNotFoundPage)WOApplication.application().pageWithName( ERXRouteNotFoundPage.class.getName(), invocation.request().context() );
		page.setURL( invocation.url() );
		final WOResponse response = page.generateResponse();
		response.setStatus( 404 );
		return response;
	}
}
