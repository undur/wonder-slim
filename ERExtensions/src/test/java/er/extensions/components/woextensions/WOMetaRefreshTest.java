package er.extensions.components.woextensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation._NSUtilities;

import er.extensions.appserver.ERXWOContext;
import er.extensions.appserver.ajax.ERXAjaxApplication;

public class WOMetaRefreshTest {

	static class App extends ERXAjaxApplication {

		@Override
		public WOContext createContextForRequest( final WORequest request ) {
			return new ERXWOContext( request );
		}
	}

	/**
	 * A page whose template is a WOMetaRefresh with the given bindings
	 */
	public static class Page extends WOComponent {

		static String bindings;

		public Page( final WOContext context ) {
			super( context );
		}

		@Override
		public WOElement template() {
			return templateWithHTMLString( "<div><webobject name=\"Refresh\" /></div>", "Refresh: WOMetaRefresh { %s }".formatted( bindings ), NSArray.emptyArray() );
		}

		public WOActionResults refreshed() {
			final WOResponse response = new WOResponse();
			response.setContent( "refreshed" );
			return response;
		}

		public WOActionResults nothing() {
			return null;
		}

		public String nullPageName() {
			return null;
		}
	}

	private static App app;

	@BeforeAll
	static void createApplication() {
		// As the framework's bundle does
		_NSUtilities.setClassForName( WOMetaRefresh.class, "WOMetaRefresh" );
		System.setProperty( "WOApplicationName", "App" );

		try {
			app = new App();
		}
		finally {
			System.clearProperty( "WOApplicationName" );
		}
	}

	private static WOContext context() {
		return app.createContextForRequest( app.createRequest( "GET", "/cgi-bin/WebObjects/App.woa", "HTTP/1.1", null, null, null ) );
	}

	private static String render( final String bindings ) {
		Page.bindings = bindings;
		return new Page( context() ).generateResponse().contentString();
	}

	/**
	 * @return The result of following the refresh the page renders: the page itself, or a response's content
	 */
	private static Object refresh( final String bindings ) {
		final Matcher matcher = Pattern.compile( "url=[^\"]*/[0-9]+\\.([0-9.]+)\"" ).matcher( render( bindings ) );
		assertTrue( matcher.find() );

		final WOContext context = context();
		final Page page = new Page( context );
		page._setIsPage( true );
		context._setPageElement( page );
		context._setPageComponent( page );
		context._setCurrentComponent( page );
		context._setSenderID( matcher.group( 1 ) );
		context.deleteAllElementIDComponents();

		final WOActionResults result = page.invokeAction( context.request(), context );
		return result == page ? "the page" : ((WOResponse)result).contentString();
	}

	/**
	 * Asserts that rendering fails with the message (wrapped by the template parser)
	 */
	private static void assertRefused( final String message, final Executable render ) {
		Throwable cause = assertThrows( Exception.class, render );

		while( cause.getCause() != null ) {
			cause = cause.getCause();
		}

		assertInstanceOf( WODynamicElementCreationException.class, cause );
		assertTrue( cause.getMessage().contains( message ), cause.getMessage() );
	}

	@Test
	public void rendersTheTag() {
		final String content = render( "seconds = 5; action = refreshed;" );
		assertTrue( content.matches( "<div><meta http-equiv=\"refresh\" content=\"5;url=[^\"]+\" /></div>" ), content );
	}

	@Test
	public void followsTheAction() {
		assertEquals( "refreshed", refresh( "seconds = 0; action = refreshed;" ) );
	}

	@Test
	public void aNullResultRedisplaysThePage() {
		assertEquals( "the page", refresh( "seconds = 0; action = nothing;" ) );
	}

	@Test
	public void refusesANullPageName() {
		final IllegalStateException e = assertThrows( IllegalStateException.class, () -> refresh( "seconds = 0; pageName = nullPageName;" ) );
		assertTrue( e.getMessage().contains( "'pageName' is null" ), e.getMessage() );
	}

	@Test
	public void refusesMissingBindings() {
		assertRefused( "'seconds' is a required binding", () -> render( "action = refreshed;" ) );
		assertRefused( "either 'pageName' or 'action'", () -> render( "seconds = 5;" ) );
	}
}
