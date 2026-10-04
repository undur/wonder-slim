package bookclubs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import er.extensions.routing.PlainRoute;
import er.extensions.routing.Route;
import er.extensions.routing.RouteGroup;
import er.routing.options.Method;



/**
 * Stands in for a plugin: a framework bringing routes of its own, mapped in its own table, ranked below the
 * application's. It maps into the application's groups by name, taking their prefix, conditions and filters: the club's
 * pages, and the admin pages, behind the application's admin filter. The application overrides its {@code /about}
 * (logged at startup), and links to its {@code /guestbook}.
 */
public class GuestbookPlugin {

	private static final Map<String, List<String>> ENTRIES = new ConcurrentHashMap<>();

	/**
	 * The guestbook's page, for links to it: given its pattern once the application names the club group
	 */
	public static final PlainRoute page = Route.plain();

	/**
	 * Declares the plugin's routes in a table of its own, ranked below the application's. It joins the application's
	 * groups as they're named, so it works whether it's declared before or after the application's routes, as a plugin's
	 * would be.
	 */
	public static void declare( final RouteGroup routes ) {
		routes.join( "club", GuestbookPlugin::mapClubRoutes );

		// In the application's admin group, so behind its admin filter
		routes.join( "admin", admin -> admin.map( "/guestbook", ri -> BookclubRoutes.text( 200, "Moderating the guestbook of %s: %s".formatted( ri.parameter( "club" ), entries( ri.parameter( "club" ) ) ) ) ) );
	}

	private static void mapClubRoutes( final RouteGroup club ) {
		club.map( "/guestbook", page, ri -> BookclubRoutes.text( 200, "Guestbook of %s: %s".formatted( ri.parameter( "club" ), entries( ri.parameter( "club" ) ) ) ), Method.GET );

		// Post, redirect, get, to the page's URL (no URL written by hand)
		club.map( "/guestbook", ri -> {
			final String entry = ri.request().stringFormValueForKey( "entry" );

			if( entry != null && !entry.isBlank() ) {
				entries( ri.parameter( "club" ) ).add( entry.strip() );
			}

			return page.redirect( Map.of( "club", ri.parameter( "club" ) ), ri.context() );
		}, Method.POST );

		club.map( "/about", ri -> BookclubRoutes.text( 200, "The guestbook plugin's about page" ) );
	}

	private static List<String> entries( final String club ) {
		return ENTRIES.computeIfAbsent( club, c -> Collections.synchronizedList( new ArrayList<>() ) );
	}
}
