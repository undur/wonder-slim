package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

public class ERXShortURLsTest {

	private static final String PREFIX = "/cgi-bin/WebObjects/App.woa";
	private static final String ADAPTOR = "/cgi-bin/WebObjects";
	private static final Set<String> KEYS = Set.of( "wa", "wo", "res", "ajax" );
	private static final Predicate<String> NO_ROUTES = path -> false;

	private static String expand( final String url ) {
		return ERXShortURLs.expand( url, PREFIX, ADAPTOR, KEYS, NO_ROUTES );
	}

	@Test
	public void aHandlerKeyAsFirstSegmentGetsThePrefix() {
		assertEquals( PREFIX + "/wa/AppAction/search", expand( "/wa/AppAction/search" ) );
		assertEquals( PREFIX + "/res/app/x.css", expand( "/res/app/x.css" ) );
		assertEquals( PREFIX + "/wa", expand( "/wa" ) );
		assertEquals( PREFIX + "/wa/", expand( "/wa/" ) );
	}

	@Test
	public void theQueryStringRidesAlong() {
		assertEquals( PREFIX + "/wa/AppAction/search?s=S%C3%A6la&x=1", expand( "/wa/AppAction/search?s=S%C3%A6la&x=1" ) );
		// A '?' inside the query never confuses the segment check
		assertEquals( PREFIX + "/wa/x?u=/wo/y", expand( "/wa/x?u=/wo/y" ) );
	}

	@Test
	public void longFormAndAnythingInAdaptorSpaceIsUntouched() {
		assertEquals( PREFIX + "/wa/x", expand( PREFIX + "/wa/x" ) );
		assertEquals( "/cgi-bin/WebObjects/Other.woa/wa/x", expand( "/cgi-bin/WebObjects/Other.woa/wa/x" ) );
		assertEquals( PREFIX + "/2/wa/x", expand( PREFIX + "/2/wa/x" ) );
	}

	@Test
	public void unknownSegmentsRootAndLookalikesAreUntouched() {
		assertEquals( "/", expand( "/" ) );
		assertEquals( "/about", expand( "/about" ) );
		assertEquals( "/wax/y", expand( "/wax/y" ) );   // not a key, merely starts like one
		assertEquals( "/WA/y", expand( "/WA/y" ) );     // keys are case-sensitive, like WO's
		assertEquals( "", expand( "" ) );
		assertEquals( null, expand( null ) );
		assertEquals( "http://h/wa/x", expand( "http://h/wa/x" ) ); // only absolute paths
	}

	@Test
	public void anExplicitRouteWins() {
		final Predicate<String> waIsARoute = path -> path.startsWith( "/wa/" );
		assertEquals( "/wa/page", ERXShortURLs.expand( "/wa/page", PREFIX, ADAPTOR, KEYS, waIsARoute ) );
		assertEquals( PREFIX + "/res/app/x.css", ERXShortURLs.expand( "/res/app/x.css", PREFIX, ADAPTOR, KEYS, waIsARoute ) );
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
