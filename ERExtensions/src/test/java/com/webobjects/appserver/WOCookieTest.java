package com.webobjects.appserver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.webobjects.foundation.NSTimestamp;

public class WOCookieTest {

	@Test
	public void timeoutAlsoGivesAnExpiresDate() {
		final String header = new WOCookie( "a", "b", "/", null, 3600, false ).headerString();
		assertTrue( header.contains( "; max-age=3600" ), header );
		assertTrue( header.contains( "; expires=" ), header );
	}

	@Test
	public void explicitExpiresIsKept() {
		final WOCookie cookie = new WOCookie( "a", "b", "/", null, new NSTimestamp( 0L ), false );
		cookie.setTimeOut( 3600 );
		final String header = cookie.headerString();
		assertTrue( header.contains( "; expires=Thu, 01-Jan-1970" ) || header.contains( "; expires=Thu, 01 Jan 1970" ), header );
	}

	@Test
	public void longTimeoutDoesNotOverflow() {
		final int sixtyDays = 60 * 24 * 3600;
		final String header = new WOCookie( "a", "b", "/", null, sixtyDays, false ).headerString();
		final int year = java.time.Year.now().getValue();
		assertFalse( header.contains( "1969" ) || header.contains( "1970" ), header );
		assertTrue( header.contains( String.valueOf( year ) ) || header.contains( String.valueOf( year + 1 ) ), header );
	}

	@Test
	public void noExpiryForSessionCookie() {
		final String header = new WOCookie( "a", "b" ).headerString();
		assertFalse( header.contains( "expires" ), header );
		assertFalse( header.contains( "max-age" ), header );
	}
}
