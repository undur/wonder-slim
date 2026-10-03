package bookclubs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import er.routing.RouteGroup;
import er.routing.core.Method;

/**
 * Stands in for a plugin: a framework bringing routes of its own, mapped in its own table, ranked below the
 * application's. It maps into the application's groups by name, taking their host, prefix and filters: the club's
 * pages, and the admin pages, behind the application's admin filter. The application overrides its {@code /about}
 * (logged at startup), and its {@code /guestbook} stays.
 */
public class GuestbookPlugin {

	private static final Map<String, List<String>> ENTRIES = new ConcurrentHashMap<>();

	private GuestbookPlugin() {}

	public static void register( final RouteGroup routes ) {
		final RouteGroup club = routes.join( "club" );

		club.map( "/guestbook", ri -> BookclubRoutes.text( 200, "Guestbook of %s: %s".formatted( ri.parameter( "club" ), entries( ri.parameter( "club" ) ) ) ), Method.GET );

		club.map( "/guestbook", ri -> {
			final String entry = ri.request().stringFormValueForKey( "entry" );

			if( entry != null && !entry.isBlank() ) {
				entries( ri.parameter( "club" ) ).add( entry.strip() );
			}

			return BookclubRoutes.seeOther( "/guestbook" );
		}, Method.POST );

		club.map( "/about", ri -> BookclubRoutes.text( 200, "The guestbook plugin's about page" ) );

		// In the application's admin group, so behind its admin filter
		routes.join( "admin" ).map( "/guestbook", ri -> BookclubRoutes.text( 200, "Moderating the guestbook of %s: %s".formatted( ri.parameter( "club" ), entries( ri.parameter( "club" ) ) ) ) );
	}

	private static List<String> entries( final String club ) {
		return ENTRIES.computeIfAbsent( club, c -> java.util.Collections.synchronizedList( new ArrayList<>() ) );
	}
}
