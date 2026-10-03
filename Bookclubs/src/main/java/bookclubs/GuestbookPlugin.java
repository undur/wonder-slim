package bookclubs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import er.routing.PlainRoute;
import er.routing.RouteGroup;
import er.routing.core.Method;

/**
 * Stands in for a plugin: a framework bringing routes of its own, mapped in its own table, ranked below the
 * application's. It maps into the application's groups by name, taking their host, prefix and filters: the club's
 * pages, and the admin pages, behind the application's admin filter. The application overrides its {@code /about}
 * (logged at startup), and links to its {@code /guestbook}.
 */
public class GuestbookPlugin {

	private static final Map<String, List<String>> ENTRIES = new ConcurrentHashMap<>();

	/**
	 * The guestbook's page, for links to it: set once the application names the club group
	 */
	private PlainRoute _page;

	/**
	 * Joins the application's groups as they're named, so it works whether the plugin is set up before or after the
	 * application declares its routes, as a plugin would be
	 */
	public GuestbookPlugin( final RouteGroup routes ) {
		routes.join( "club", this::mapClubRoutes );

		// In the application's admin group, so behind its admin filter
		routes.join( "admin", admin -> admin.map( "/guestbook", ri -> BookclubRoutes.text( 200, "Moderating the guestbook of %s: %s".formatted( ri.parameter( "club" ), entries( ri.parameter( "club" ) ) ) ) ) );
	}

	public PlainRoute page() {
		return _page;
	}

	private void mapClubRoutes( final RouteGroup club ) {
		_page = club.map( "/guestbook", ri -> BookclubRoutes.text( 200, "Guestbook of %s: %s".formatted( ri.parameter( "club" ), entries( ri.parameter( "club" ) ) ) ), Method.GET );

		// Post, redirect, get, to the page's URL (no URL written by hand)
		club.map( "/guestbook", ri -> {
			final String entry = ri.request().stringFormValueForKey( "entry" );

			if( entry != null && !entry.isBlank() ) {
				entries( ri.parameter( "club" ) ).add( entry.strip() );
			}

			return BookclubRoutes.seeOther( _page.url( ri.context() ) );
		}, Method.POST );

		club.map( "/about", ri -> BookclubRoutes.text( 200, "The guestbook plugin's about page" ) );
	}

	private static List<String> entries( final String club ) {
		return ENTRIES.computeIfAbsent( club, c -> Collections.synchronizedList( new ArrayList<>() ) );
	}
}
