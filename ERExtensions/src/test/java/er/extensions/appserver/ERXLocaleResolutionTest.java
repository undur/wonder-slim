package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;

import er.extensions.routes.ERXRoutingApplication;

/**
 * The order {@link ERXLocale#current()} resolves the locale in: the request's, the session's, the application's
 */
public class ERXLocaleResolutionTest {

	private static final Locale REQUEST = Locale.GERMAN;
	private static final Locale SESSION = Locale.FRENCH;
	private static final Locale APPLICATION = Locale.of( "is" );

	static class App extends ERXRoutingApplication {

		@Override
		public WOContext createContextForRequest( final WORequest request ) {
			return new ERXWOContext( request );
		}
	}

	private static App app;

	@BeforeAll
	static void createApplication() {
		System.setProperty( "WOApplicationName", "App" );

		try {
			app = new App();
		}
		finally {
			System.clearProperty( "WOApplicationName" );
		}
	}

	@AfterEach
	void reset() {
		ERXWOContext.setCurrentContext( null );
		ERXSession.setSession( null );
		ERXLocale.setApplicationLocale( null );
	}

	/**
	 * @return A new context for a request, made the current one
	 */
	private static ERXWOContext currentContext() {
		final ERXWOContext context = (ERXWOContext)app.createContextForRequest( app.createRequest( "GET", "/wa/x", "HTTP/1.1", null, null, null ) );
		ERXWOContext.setCurrentContext( context );
		return context;
	}

	private static void awakeSessionWithLocale( final Locale locale ) {
		final ERXSession session = new ERXSession();
		session.setLocale( locale );
		ERXSession.setSession( session );
	}

	@Test
	public void noLocaleAnywhereIsNull() {
		currentContext();
		assertNull( ERXLocale.current() );
	}

	@Test
	public void theRequestsLocaleComesFirst() {
		ERXLocale.setApplicationLocale( APPLICATION );
		awakeSessionWithLocale( SESSION );
		currentContext().setLocale( REQUEST );
		assertEquals( REQUEST, ERXLocale.current() );
	}

	@Test
	public void withoutARequestLocaleTheSessionsThenTheApplications() {
		ERXLocale.setApplicationLocale( APPLICATION );
		currentContext();
		assertEquals( APPLICATION, ERXLocale.current() );

		awakeSessionWithLocale( SESSION );
		assertEquals( SESSION, ERXLocale.current() );
	}

	@Test
	public void theRequestsLocaleLivesAndDiesWithTheRequest() {
		ERXLocale.setApplicationLocale( APPLICATION );
		currentContext().setLocale( REQUEST );
		assertEquals( REQUEST, ERXLocale.current() );

		// The next request, on the same thread
		currentContext();
		assertEquals( APPLICATION, ERXLocale.current() );
	}

	@Test
	public void settingNullRevertsToTheSessionsOrTheApplications() {
		ERXLocale.setApplicationLocale( APPLICATION );
		final ERXWOContext context = currentContext();
		context.setLocale( REQUEST );
		context.setLocale( null );
		assertEquals( APPLICATION, ERXLocale.current() );
	}

	@Test
	public void aCloneOfTheContextKeepsItsLocale() {
		final ERXWOContext context = currentContext();
		context.setLocale( REQUEST );
		assertEquals( REQUEST, ((ERXWOContext)context.clone()).locale() );
	}
}
