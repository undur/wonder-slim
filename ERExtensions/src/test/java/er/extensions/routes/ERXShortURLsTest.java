package er.extensions.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

public class ERXShortURLsTest {

	private static final String PREFIX = "/cgi-bin/WebObjects/App.woa";
	private static final String ADAPTOR = "/cgi-bin/WebObjects";
	private static final Set<String> KEYS = Set.of( "wa", "wo", "res", "ajax" );
	private static final Predicate<String> NO_ROUTES = path -> false;

	private static String canonical( final String url ) {
		return ERXShortURLs.canonicalize( url, ADAPTOR, "App", ".woa", KEYS, NO_ROUTES );
	}

	@Test
	public void freestyleHandlerKeyURLsGetTheApplicationPrefix() {
		assertEquals( PREFIX + "/wa/AppAction/search", canonical( "/wa/AppAction/search" ) );
		assertEquals( PREFIX + "/res/app/x.css", canonical( "/res/app/x.css" ) );
		assertEquals( PREFIX + "/wa", canonical( "/wa" ) );
		assertEquals( PREFIX + "/wa/", canonical( "/wa/" ) );
	}

	@Test
	public void theQueryStringIsNeverTouched() {
		assertEquals( PREFIX + "/wa/AppAction/search?s=S%C3%A6la&x=1", canonical( "/wa/AppAction/search?s=S%C3%A6la&x=1" ) );
		assertEquals( PREFIX + "/wa/x?u=/wo/y", canonical( "/wa/x?u=/wo/y" ) );
		assertEquals( PREFIX + "/route/about?u=/wa/y", canonical( "/about?u=/wa/y" ) );
		assertEquals( PREFIX + "/route/?x=1", canonical( "/?x=1" ) );
	}

	@Test
	public void longHandlerURLsKeepTheirShape() {
		assertEquals( PREFIX + "/wa/x", canonical( PREFIX + "/wa/x" ) );
		assertEquals( PREFIX + "/2/wa/x", canonical( PREFIX + "/2/wa/x" ) );
		assertEquals( PREFIX + "/-1/wo/abc/0.1", canonical( PREFIX + "/-1/wo/abc/0.1" ) );
		assertEquals( ADAPTOR + "/App/wa/x", canonical( ADAPTOR + "/App/wa/x" ) ); // the extension is optional in WO URLs
	}

	@Test
	public void anotherApplicationsURLsPassThrough() {
		assertEquals( "/cgi-bin/WebObjects/Other.woa/wa/x", canonical( "/cgi-bin/WebObjects/Other.woa/wa/x" ) );
		assertEquals( "/cgi-bin/WebObjects/Other.woa/a/b", canonical( "/cgi-bin/WebObjects/Other.woa/a/b" ) );
		assertEquals( "/cgi-bin/WebObjects/Application.woa/a", canonical( "/cgi-bin/WebObjects/Application.woa/a" ) ); // merely starts like ours
	}

	@Test
	public void everythingElseIsARoute() {
		assertEquals( PREFIX + "/route/", canonical( "/" ) );
		assertEquals( PREFIX + "/route/about", canonical( "/about" ) );
		assertEquals( PREFIX + "/route/a/b/c", canonical( "/a/b/c" ) );
		assertEquals( PREFIX + "/route/2/", canonical( "/2/" ) );           // trailing slash kept
		assertEquals( PREFIX + "/route/1234", canonical( "/1234" ) );       // a freestyle number is a path
		assertEquals( PREFIX + "/route/wax/y", canonical( "/wax/y" ) );     // not a key, merely starts like one
		assertEquals( PREFIX + "/route/WA/y", canonical( "/WA/y" ) );       // keys are case-sensitive, like WO's
	}

	@Test
	public void routesUnderAnAdaptorPrefixKeepThePrefixTheyCarried() {
		assertEquals( PREFIX + "/route/", canonical( PREFIX ) );
		assertEquals( PREFIX + "/route/", canonical( PREFIX + "/" ) );
		assertEquals( PREFIX + "/route/a/b", canonical( PREFIX + "/a/b" ) );
		assertEquals( PREFIX + "/1/route/", canonical( PREFIX + "/1" ) );
		assertEquals( PREFIX + "/1/route/a/b", canonical( PREFIX + "/1/a/b" ) );
		assertEquals( PREFIX + "/-3/route/a/b?x=1", canonical( PREFIX + "/-3/a/b?x=1" ) );
		assertEquals( ADAPTOR + "/App/1/route/a", canonical( ADAPTOR + "/App/1/a" ) );
		assertEquals( PREFIX + "/1234/route/", canonical( PREFIX + "/1234" ) ); // WO's grammar: a number after .woa is the instance
	}

	@Test
	public void theCarriedAdaptorPathNeedNotBeTheApplicationsOwn() {
		final String foreign = "/Apps/WebObjects/App.woa";
		assertEquals( foreign + "/route/", canonical( foreign ) );
		assertEquals( foreign + "/1/route/a/b", canonical( foreign + "/1/a/b" ) );
		assertEquals( foreign + "/1/route/a/b", canonical( foreign + "/1/route/a/b" ) );
		assertEquals( foreign + "/1/res/app/x.css", canonical( foreign + "/1/route/res/app/x.css" ) );
		assertEquals( foreign + "/wa/x?y=1", canonical( foreign + "/wa/x?y=1" ) );
		assertEquals( "/Apps/WebObjects/Other.woa/a/b", canonical( "/Apps/WebObjects/Other.woa/a/b" ) );
	}

	@Test
	public void aRequestAlreadyMarkedAsARouteIsCanonical() {
		assertEquals( PREFIX + "/route/a/b", canonical( PREFIX + "/route/a/b" ) );
		assertEquals( PREFIX + "/1/route/1234", canonical( PREFIX + "/1/route/1234" ) );
		assertEquals( PREFIX + "/1/route/2/", canonical( PREFIX + "/1/route/2/" ) );
		assertEquals( PREFIX + "/route/", canonical( PREFIX + "/route" ) );
		assertEquals( PREFIX + "/route/", canonical( "/route/" ) );
		assertEquals( PREFIX + "/route/routes/x", canonical( "/routes/x" ) ); // not the marker, merely starts like it
	}

	@Test
	public void aMarkedHandlerKeyURLGoesToItsHandler() {
		assertEquals( PREFIX + "/1/res/app/x.css", canonical( PREFIX + "/1/route/res/app/x.css" ) );
		assertEquals( PREFIX + "/wa/page?name=Main", canonical( PREFIX + "/route/wa/page?name=Main" ) );
	}

	@Test
	public void whatIsNotAnAbsolutePathIsLeftAlone() {
		assertEquals( "", canonical( "" ) );
		assertEquals( null, canonical( null ) );
		assertEquals( "http://h/wa/x", canonical( "http://h/wa/x" ) );
	}

	@Test
	public void anExplicitRouteWins() {
		final Predicate<String> waIsARoute = path -> path.startsWith( "/wa/" );
		assertEquals( PREFIX + "/route/wa/page", ERXShortURLs.canonicalize( "/wa/page", ADAPTOR, "App", ".woa", KEYS, waIsARoute ) );
		assertEquals( PREFIX + "/res/app/x.css", ERXShortURLs.canonicalize( "/res/app/x.css", ADAPTOR, "App", ".woa", KEYS, waIsARoute ) );
	}

	@Test
	public void shortenStripsThePrefixAndAnInstanceNumber() {
		assertEquals( "/wa/AppAction/search?s=1", ERXShortURLs.shorten( PREFIX + "/wa/AppAction/search?s=1", PREFIX ) );
		assertEquals( "/wa/x", ERXShortURLs.shorten( PREFIX + "/2/wa/x", PREFIX ) );
		assertEquals( "/wa/x", ERXShortURLs.shorten( PREFIX + "/-1/wa/x", PREFIX ) );
		assertEquals( "/wo/1/0.1.3", ERXShortURLs.shorten( PREFIX + "/wo/1/0.1.3", PREFIX ) );
		assertEquals( "https://h:443/wa/x", ERXShortURLs.shorten( "https://h:443" + PREFIX + "/wa/x", PREFIX ) );
	}

	@Test
	public void shortenOfThePrefixAloneIsTheRoot() {
		assertEquals( "/", ERXShortURLs.shorten( PREFIX, PREFIX ) );
		assertEquals( "/", ERXShortURLs.shorten( PREFIX + "/", PREFIX ) );
		assertEquals( "/?x=1", ERXShortURLs.shorten( PREFIX + "?x=1", PREFIX ) );
		assertEquals( "https://h/", ERXShortURLs.shorten( "https://h" + PREFIX, PREFIX ) );
		assertEquals( "https://h/?x=1", ERXShortURLs.shorten( "https://h" + PREFIX + "?x=1", PREFIX ) );
	}

	@Test
	public void shortenLeavesForeignAndAlreadyShortURLsAlone() {
		assertEquals( "/wa/x", ERXShortURLs.shorten( "/wa/x", PREFIX ) );
		assertEquals( "/cgi-bin/WebObjects/Other.woa/wa/x", ERXShortURLs.shorten( "/cgi-bin/WebObjects/Other.woa/wa/x", PREFIX ) );
		assertEquals( PREFIX + "x/wa", ERXShortURLs.shorten( PREFIX + "x/wa", PREFIX ) ); // App.woax is not App.woa
		assertEquals( null, ERXShortURLs.shorten( null, PREFIX ) );
	}

	@Test
	public void applicationPrefixFollowsTheRequestNotTheConfiguration() {
		assertEquals( "/Apps/WebObjects/App.woa", ERXShortURLs.applicationPrefix( "/Apps/WebObjects", "App", ".woa" ) );
		assertEquals( "/cgi-bin/WebObjects/App", ERXShortURLs.applicationPrefix( "/cgi-bin/WebObjects", "App", "" ) );
		assertEquals( "/App.woa", ERXShortURLs.applicationPrefix( "", "App", ".woa" ) );
		assertEquals( "/App", ERXShortURLs.applicationPrefix( null, "App", null ) );

		// A front end rewrote / into /Apps/WebObjects/App.woa/wa/default: the app's URLs carry that prefix
		final String prefix = ERXShortURLs.applicationPrefix( "/Apps/WebObjects", "App", ".woa" );
		assertEquals( "/wo/0.1", ERXShortURLs.shorten( "/Apps/WebObjects/App.woa/wo/0.1", prefix ) );
		assertEquals( "/res/app/x.css", ERXShortURLs.shorten( "/Apps/WebObjects/App.woa/3/res/app/x.css", prefix ) );
		assertEquals( "/", ERXShortURLs.shorten( "/Apps/WebObjects/App", ERXShortURLs.applicationPrefix( "/Apps/WebObjects", "App", "" ) ) );
	}
}
