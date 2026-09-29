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
import er.extensions.control.ERXControlPages;
import er.extensions.control.ERXControlPages.Page;
import er.extensions.foundation.ERXProperties;
import er.extensions.routes.RouteInvocation;
import er.extensions.routes.RouteTable;

/**
 * The framework's admin UI: one gate, one set of routes, and the pages registered with {@link ERXControlPages}.
 *
 * Everything lives beneath {@link #PATH}. A request is let in when the application runs in development mode, or when
 * its session has logged in with the admin password ({@link #PASSWORD_PROPERTY}). Anything else gets the login page.
 * A logged-in session times out after {@link #SESSION_TIMEOUT_SECONDS} without activity.
 *
 * The routes are registered by ERControl's plugin ({@code ERXControl}) once the application is constructed, after
 * the application's own, so a route an application maps itself always wins over ours.
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
	 * How long an admin session lives without activity: ten minutes. Set on the session when it logs in, replacing the
	 * application's timeout for that session; the login page notes it.
	 */
	public static final int SESSION_TIMEOUT_SECONDS = 10 * 60;

	/**
	 * The framework's own pages, in the order they're listed
	 */
	private static final List<Page> PAGES = List.of(
			page( "", "Overview", null, ERXAdminOverviewPage.class ),
			page( "statistics", "Statistics", "What the statistics store has counted since the instance started.", ERXAdminStatisticsPage.class ),
			page( "events", "Events", "WOEvent instrumentation: turn recording on for the event classes of interest, exercise the application, then read what was recorded.", ERXAdminEventsPage.class ),
			page( "exceptions", "Exceptions", "The exceptions this instance has handled since it started, newest last.", ERXAdminExceptionsPage.class ),
			page( "caches", "Sessions and caches", "Active sessions, what their page caches hold, and how the pressure valve is doing.", ERXAdminCachesPage.class ),
			page( "threads", "Threads", "A thread dump of this JVM, taken when the page rendered.", ERXAdminThreadsPage.class ),
			page( "log", "Log", "The tail of what this instance has logged, from an in-memory ring buffer.", ERXAdminLogPage.class ),
			page( "configuration", "Configuration", "The plugins in the order they run, the sources of properties in the order they're applied, and every property with the source it came from.", ERXAdminConfigurationPage.class ),
			page( "bundles", "Bundles", "The application's main bundle and the frameworks loaded alongside it.", ERXAdminBundlesPage.class ) );

	private static Page page( final String name, final String title, final String description, final Class<? extends WOComponent> component ) {
		return new Page( ERXControlPages.FRAMEWORK_CATEGORY, name, title, description, component );
	}

	private ERXAdmin() {}

	/**
	 * Registers the framework's own pages with {@link ERXControlPages}
	 */
	public static void registerPages() {
		PAGES.forEach( ERXControlPages::register );
	}

	/**
	 * @return The path of a page
	 */
	public static String path( final Page page ) {
		return page.name().isEmpty() ? PATH : PATH + "/" + page.name();
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

		final String name = url.equals( PATH ) ? "" : url.substring( PATH.length() + 1 );
		final Page page = ERXControlPages.page( name );

		final ERXAdminPage adminPage = page( ERXAdminPage.class, context );
		adminPage.controlPage = page != null ? page : ERXControlPages.page( "" );
		return adminPage;
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

		// A session holding privileged access is worth keeping short: the application's own timeout is sized for its users
		session.setTimeOut( SESSION_TIMEOUT_SECONDS );

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
	 * The property the admin password is read from: the same one ERXMonitorServer uses, so the deployment tools hand an
	 * instance one secret that opens both. Unset means the control panel is closed outside development mode.
	 */
	public static final String PASSWORD_PROPERTY = "WOMonitorServicePassword";

	/**
	 * @return The admin password, null if none is set
	 */
	static String adminPassword() {
		return ERXProperties.stringForKey( PASSWORD_PROPERTY );
	}

	/**
	 * @return The URL for an admin path, as a link on a page rendered in the given context
	 */
	public static String url( final WOContext context, final String path ) {
		final ERXApplication application = ERXApplication.erxApplication();
		return application.shortURLs() ? path : application.applicationURLPrefix() + path;
	}
}
