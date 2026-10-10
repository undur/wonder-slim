package er.extensions.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

public class ERXShortURLsTest {

	private static final String PREFIX = "/cgi-bin/WebObjects/App.woa";
	private static final String ADAPTOR = "/cgi-bin/WebObjects";
	private static final Set<String> KEYS = Set.of( "wa", "wo", "res", "ajax" );

	private static String canonical( final String url ) {
		return ERXShortURLs.canonicalize( url, ADAPTOR, "App", ".woa", KEYS );
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
		assertEquals( PREFIX + "/_erxroute_/about?u=/wa/y", canonical( "/about?u=/wa/y" ) );
		assertEquals( PREFIX + "/_erxroute_/?x=1", canonical( "/?x=1" ) );
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
		assertEquals( PREFIX + "/_erxroute_/", canonical( "/" ) );
		assertEquals( PREFIX + "/_erxroute_/about", canonical( "/about" ) );
		assertEquals( PREFIX + "/_erxroute_/a/b/c", canonical( "/a/b/c" ) );
		assertEquals( PREFIX + "/_erxroute_/2/", canonical( "/2/" ) );           // trailing slash kept
		assertEquals( PREFIX + "/_erxroute_/1234", canonical( "/1234" ) );       // a freestyle number is a path
		assertEquals( PREFIX + "/_erxroute_/wax/y", canonical( "/wax/y" ) );     // not a key, merely starts like one
		assertEquals( PREFIX + "/_erxroute_/WA/y", canonical( "/WA/y" ) );       // keys are case-sensitive, like WO's
	}

	@Test
	public void routesUnderAnAdaptorPrefixKeepThePrefixTheyCarried() {
		assertEquals( PREFIX + "/_erxroute_/", canonical( PREFIX ) );
		assertEquals( PREFIX + "/_erxroute_/", canonical( PREFIX + "/" ) );
		assertEquals( PREFIX + "/_erxroute_/a/b", canonical( PREFIX + "/a/b" ) );
		assertEquals( PREFIX + "/1/_erxroute_/", canonical( PREFIX + "/1" ) );
		assertEquals( PREFIX + "/1/_erxroute_/a/b", canonical( PREFIX + "/1/a/b" ) );
		assertEquals( PREFIX + "/-3/_erxroute_/a/b?x=1", canonical( PREFIX + "/-3/a/b?x=1" ) );
		assertEquals( ADAPTOR + "/App/1/_erxroute_/a", canonical( ADAPTOR + "/App/1/a" ) );
		assertEquals( PREFIX + "/1234/_erxroute_/", canonical( PREFIX + "/1234" ) ); // WO's grammar: a number after .woa is the instance
	}

	@Test
	public void theCarriedAdaptorPathNeedNotBeTheApplicationsOwn() {
		final String foreign = "/Apps/WebObjects/App.woa";
		assertEquals( foreign + "/_erxroute_/", canonical( foreign ) );
		assertEquals( foreign + "/1/_erxroute_/a/b", canonical( foreign + "/1/a/b" ) );
		assertEquals( foreign + "/1/_erxroute_/a/b", canonical( foreign + "/1/_erxroute_/a/b" ) );
		assertEquals( foreign + "/1/res/app/x.css", canonical( foreign + "/1/_erxroute_/res/app/x.css" ) );
		assertEquals( foreign + "/wa/x?y=1", canonical( foreign + "/wa/x?y=1" ) );
		assertEquals( "/Apps/WebObjects/Other.woa/a/b", canonical( "/Apps/WebObjects/Other.woa/a/b" ) );
	}

	@Test
	public void aRequestAlreadyMarkedAsARouteIsCanonical() {
		assertEquals( PREFIX + "/_erxroute_/a/b", canonical( PREFIX + "/_erxroute_/a/b" ) );
		assertEquals( PREFIX + "/1/_erxroute_/1234", canonical( PREFIX + "/1/_erxroute_/1234" ) );
		assertEquals( PREFIX + "/1/_erxroute_/2/", canonical( PREFIX + "/1/_erxroute_/2/" ) );
		assertEquals( PREFIX + "/_erxroute_/", canonical( PREFIX + "/_erxroute_" ) );
		assertEquals( PREFIX + "/_erxroute_/", canonical( "/_erxroute_/" ) );
		assertEquals( PREFIX + "/_erxroute_/_erxroute_s/x", canonical( "/_erxroute_s/x" ) );
		assertEquals( PREFIX + "/_erxroute_/route/x", canonical( "/route/x" ) );            // the former key is an ordinary path (#197)
		assertEquals( PREFIX + "/_erxroute_/route/x", canonical( PREFIX + "/route/x" ) );   // also forwarded by a front end configured for it // not the marker, merely starts like it
	}

	@Test
	public void aMarkedHandlerKeyURLGoesToItsHandler() {
		assertEquals( PREFIX + "/1/res/app/x.css", canonical( PREFIX + "/1/_erxroute_/res/app/x.css" ) );
		assertEquals( PREFIX + "/wa/page?name=Main", canonical( PREFIX + "/_erxroute_/wa/page?name=Main" ) );
	}

	@Test
	public void whatIsNotAnAbsolutePathIsLeftAlone() {
		assertEquals( "", canonical( "" ) );
		assertEquals( null, canonical( null ) );
		assertEquals( "http://h/wa/x", canonical( "http://h/wa/x" ) );
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

	// ---- A base path (#51) ----

	private static String canonicalBeneath( final String url ) {
		return ERXShortURLs.canonicalize( url, "/App", ADAPTOR, "App", ".woa", KEYS );
	}

	@Test
	public void theBasePathIsRemovedFromARequestsURL() {
		assertEquals( PREFIX + "/wo/123.4.5.6", canonicalBeneath( "/App/wo/123.4.5.6" ) );
		assertEquals( PREFIX + "/_erxroute_/about?x=1", canonicalBeneath( "/App/about?x=1" ) );
		assertEquals( PREFIX + "/_erxroute_/", canonicalBeneath( "/App/" ) );
		assertEquals( PREFIX + "/_erxroute_/", canonicalBeneath( "/App" ) );

		// A path merely starting with the same letters isn't beneath it
		assertEquals( PREFIX + "/_erxroute_/Apple", canonicalBeneath( "/Apple" ) );
	}

	@Test
	public void theBasePathIsPrependedToGeneratedURLs() {
		assertEquals( "/App/wo/123.4.5.6", ERXShortURLs.shorten( PREFIX + "/wo/123.4.5.6", PREFIX, "/App" ) );
		assertEquals( "https://example.com/App/wa/x", ERXShortURLs.shorten( "https://example.com" + PREFIX + "/wa/x", PREFIX, "/App" ) );
		assertEquals( "/App/", ERXShortURLs.shorten( PREFIX, PREFIX, "/App" ) );

		// A URL that isn't the application's is left alone
		assertEquals( "https://elsewhere.example/x", ERXShortURLs.shorten( "https://elsewhere.example/x", PREFIX, "/App" ) );
	}

	@Test
	public void aRoutesShortURLLosesTheRouteKeyBeneathTheBasePath() {
		assertEquals( "/App/search/bork", ERXShortURLs.withoutRouteKey( "/App/_erxroute_/search/bork", "/App" ) );
		assertEquals( "/search/bork", ERXShortURLs.withoutRouteKey( "/_erxroute_/search/bork", "" ) );
		assertEquals( "https://example.com/App/?q=1", ERXShortURLs.withoutRouteKey( "https://example.com/App/_erxroute_?q=1", "/App" ) );
	}

	@Test
	public void aBasePathIsAPath() {
		assertEquals( "", ERXRoutingApplication.basePath( null, true ) );
		assertEquals( "", ERXRoutingApplication.basePath( "/", true ) );
		assertEquals( "/App", ERXRoutingApplication.basePath( " /App ", true ) );
		org.junit.jupiter.api.Assertions.assertThrows( IllegalStateException.class, () -> ERXRoutingApplication.basePath( "App", true ) );
		org.junit.jupiter.api.Assertions.assertThrows( IllegalStateException.class, () -> ERXRoutingApplication.basePath( "/App/", true ) );
		org.junit.jupiter.api.Assertions.assertThrows( IllegalStateException.class, () -> ERXRoutingApplication.basePath( "/App", false ) );
	}

	/**
	 * The prefix is removed by a plain scan; this is the regular expression it replaced, as the oracle
	 */
	private static String withoutPrefixByRegex( final String url, final String prefix ) {
		return java.util.regex.Pattern.compile( java.util.regex.Pattern.quote( prefix ) + "(?:/-?\\d+)?(?=/|\\?|$)" ).matcher( url ).replaceFirst( "" );
	}

	@Test
	public void removingThePrefixMatchesTheRegularExpressionItReplaced() {
		final java.util.List<String> prefixes = java.util.List.of( "/cgi-bin/WebObjects/App.woa", "/Apps/WebObjects/App.woa", "/App.woa", "/App" );
		final java.util.List<String> befores = java.util.List.of( "", "https://example.com", "http://h:1300", "/x" );
		final java.util.List<String> instances = java.util.List.of( "", "/2", "/-1", "/12", "/2a", "/-", "/", "/0" );
		final java.util.List<String> afters = java.util.List.of( "", "/", "/wa/default", "?a=1", "/wo/1.2?x=/App.woa/3", "x", "/App.woa", ".json", "/_erxroute_/books/7" );
		int compared = 0;

		for( final String prefix : prefixes ) {
			for( final String before : befores ) {
				for( final String instance : instances ) {
					for( final String after : afters ) {
						for( final String url : java.util.List.of( before + prefix + instance + after, before + prefix + "x" + prefix + instance + after, before + after ) ) {
							final String expected = withoutPrefixByRegex( url, prefix );
							final String actual = ERXShortURLs.withoutPrefix( url, prefix );
							assertEquals( expected, actual, () -> "'%s' without '%s'".formatted( url, prefix ) );

							// Unchanged is the same object, which shortening relies on
							assertEquals( expected == url, actual == url, () -> "identity of '%s' without '%s'".formatted( url, prefix ) );
							compared++;
						}
					}
				}
			}
		}

		assertTrue( compared > 1000, "compared " + compared );
	}

}
