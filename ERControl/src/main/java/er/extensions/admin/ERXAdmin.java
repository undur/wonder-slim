package er.extensions.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOSession;

import er.extensions.appserver.ERXApplication;
import er.extensions.routes.RouteInvocation;
import er.extensions.routes.RouteTable;

/**
 * The framework's admin UI: one gate, one set of routes, one list of sections.
 *
 * Everything lives beneath {@link #PATH}. A request is let in when the application runs in development mode, or when
 * its session has logged in with the admin password. Anything else gets the login page.
 *
 * The routes are registered by the ERControl framework principal once the application object exists, after the
 * application's own, so a route an application maps itself always wins over ours.
 */
public final class ERXAdmin {

	private static final Logger logger = LoggerFactory.getLogger( ERXAdmin.class );

	/**
	 * The path the admin UI lives beneath. Under a namespace of the framework's own rather than at /admin, which
	 * applications want for themselves.
	 */
	public static final String PATH = "/wonder/admin";

	private static final String AUTHORIZED_KEY = "er.extensions.admin.ERXAdmin.authorized";

	/**
	 * A section of the admin UI: a path beneath {@link #PATH}, its title in the navigation and the page that renders it
	 */
	public record Section( String name, String title, Class<? extends WOComponent> pageClass ) {

		public String path() {
			return name.isEmpty() ? PATH : PATH + "/" + name;
		}
	}

	private static final List<Section> SECTIONS = List.of(
			new Section( "", "Overview", ERXAdminOverviewPage.class ),
			new Section( "statistics", "Statistics", ERXAdminStatisticsPage.class ),
			new Section( "events", "Events", ERXAdminEventsPage.class ),
			new Section( "exceptions", "Exceptions", ERXAdminExceptionsPage.class ),
			new Section( "caches", "Sessions and caches", ERXAdminCachesPage.class ),
			new Section( "threads", "Threads", ERXAdminThreadsPage.class ),
			new Section( "log", "Log", ERXAdminLogPage.class ),
			new Section( "properties", "Properties", ERXAdminPropertiesPage.class ),
			new Section( "bundles", "Bundles", ERXAdminBundlesPage.class ) );

	private ERXAdmin() {}

	public static List<Section> sections() {
		return SECTIONS;
	}

	/**
	 * Maps the admin routes, unless the application has claimed {@link #PATH} for itself.
	 */
	public static void registerRoutes() {
		final RouteTable routes = RouteTable.defaultRouteTable();

		if( routes.hasRouteFor( PATH ) ) {
			logger.warn( "The application maps a route of its own at {}, so the framework's admin UI is not available", PATH );
			return;
		}

		routes.map( PATH, ERXAdmin::handle );
		routes.map( PATH + "/*", ERXAdmin::handle );
	}

	/**
	 * @return The page for the section the URL names (the login page if the request isn't authorized, the overview for a section we don't have)
	 */
	private static WOActionResults handle( final RouteInvocation invocation ) {
		final WOContext context = invocation.context();

		if( !isAuthorized( context ) ) {
			final ERXAdminLoginPage loginPage = page( ERXAdminLoginPage.class, context );
			loginPage.destination = invocation.url();
			return loginPage;
		}

		final String url = invocation.url().endsWith( "/" ) ? invocation.url().substring( 0, invocation.url().length() - 1 ) : invocation.url();

		if( url.equals( PATH + "/logout" ) ) {
			logOut( context );
			return page( ERXAdminLoginPage.class, context );
		}

		for( final Section section : SECTIONS ) {
			if( section.path().equals( url ) ) {
				return page( section.pageClass(), context );
			}
		}

		return page( ERXAdminOverviewPage.class, context );
	}

	@SuppressWarnings("unchecked")
	private static <E extends WOComponent> E page( final Class<E> pageClass, final WOContext context ) {
		return (E)WOApplication.application().pageWithName( pageClass.getName(), context );
	}

	/**
	 * @return true if the request may see the admin UI: always in development mode, otherwise once its session has logged in
	 */
	public static boolean isAuthorized( final WOContext context ) {

		if( ERXApplication.isDevelopmentModeSafe() ) {
			return true;
		}

		return context.hasSession() && Boolean.TRUE.equals( context.session().objectForKey( AUTHORIZED_KEY ) );
	}

	/**
	 * Logs the context's session in if the password is the admin password.
	 *
	 * @return true if the password was accepted
	 */
	static boolean logIn( final WOContext context, final String password ) {
		final String adminPassword = adminPassword();

		if( password == null || password.isEmpty() || adminPassword == null || adminPassword.isEmpty() ) {
			return false;
		}

		if( !MessageDigest.isEqual( password.getBytes( StandardCharsets.UTF_8 ), adminPassword.getBytes( StandardCharsets.UTF_8 ) ) ) {
			logger.warn( "Refused an admin login from {}", context.request()._remoteAddress() );
			return false;
		}

		final WOSession session = context.session();
		session.setObjectForKey( Boolean.TRUE, AUTHORIZED_KEY );

		return true;
	}

	static void logOut( final WOContext context ) {
		if( context.hasSession() ) {
			context.session().removeObjectForKey( AUTHORIZED_KEY );
		}
	}

	/**
	 * @return true if an admin password is configured, i.e. if logging in is possible at all outside development mode
	 */
	static boolean hasPassword() {
		final String password = adminPassword();
		return password != null && !password.isEmpty();
	}

	/**
	 * @return The admin password. Hardcoded while the control panel is developed on its branch; becomes a property before it lands.
	 */
	static String adminPassword() {
		return "smu";
	}

	/**
	 * @return The URL for an admin path, as a link on a page rendered in the given context
	 */
	public static String url( final WOContext context, final String path ) {
		final ERXApplication application = ERXApplication.erxApplication();
		return application.shortURLs() ? path : application.applicationURLPrefix() + path;
	}
}
