package ajaxplayground;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOResponse;

import er.extensions.experimental.routing.ERXRouter;
import er.extensions.experimental.routing.RouteGroup;
import er.extensions.experimental.routing.RoutedInvocation;
import er.extensions.experimental.routing.core.Host;
import er.extensions.experimental.routing.core.Method;
import er.extensions.experimental.routing.core.TrailingSlash;
import er.extensions.routes.RouteHandler;
import er.extensions.routes.RouteTable;

/**
 * EXPERIMENTAL (route-links branch). Routes on the new router, under /router/, each answering with what it received.
 */
public class RouterDemo {

	private RouterDemo() {}

	public static void register( final RouteTable routeTable ) {
		final ERXRouter router = new ERXRouter();
		final RouteGroup routes = router.table( "application" );

		// Parameters, and methods: GET (and HEAD) and PUT at the same path, anything else is 405
		routes.map( "/router/items/{id}", ri -> text( ri, "item" ), Method.GET );
		routes.map( "/router/items/{id}", ri -> text( ri, "update item" ), Method.PUT );
		routes.map( "/router/hooks", ri -> text( ri, "hook" ), Method.POST );

		// A literal before a parameter, whatever the mapping order
		routes.map( "/router/items/new", ri -> text( ri, "new item" ) );

		// A host pattern before no host: try it with "Host: acme.localhost"
		routes.map( "/router/whoami", ri -> text( ri, "no tenant" ) );
		routes.map( "/router/whoami", ri -> text( ri, "tenant " + ri.parameter( "tenant" ) ), Host.of( "{tenant}.localhost" ) );

		// A group, wrapped in a filter asking for ?key=secret, including routes mapped before the filter was added
		routes.group( "/router/admin", admin -> {
			admin.map( "/stats", ri -> text( ri, "stats" ) );
			admin.wrap( ( invocation, next ) -> "secret".equals( invocation.request().stringFormValueForKey( "key" ) ) ? next.handle( invocation ) : status( 403, "forbidden" ) );
			admin.map( "/users/{id}", ri -> text( ri, "user" ) );
		} );

		// Trailing slashes: redirected to the declared form
		routes.map( "/router/docs/", TrailingSlash.REDIRECT, ri -> text( ri, "docs" ) );

		// A wildcard, and declining: the page route declines "secret", which passes to the wildcard
		routes.map( "/router/pages/*", ri -> text( ri, "pages wildcard" ) );
		routes.map( "/router/pages/{name}", ri -> "secret".equals( ri.parameter( "name" ) ) ? RouteHandler.DECLINED : text( ri, "page" ) );

		// The endpoints the /typed page links to
		Endpoints.declare( routes );

		router.mapInto( routeTable );
	}

	private static WOActionResults text( final RoutedInvocation invocation, final String what ) {
		return status( 200, what + " " + invocation.request().method() + " " + invocation.parameters() );
	}

	private static WOResponse status( final int status, final String content ) {
		final WOResponse response = new WOResponse();
		response.setStatus( status );
		response.setHeader( "text/plain; charset=utf-8", "content-type" );
		response.setContent( content );
		return response;
	}
}
