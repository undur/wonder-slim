package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.junit.jupiter.api.Test;

public class ERXLocaleTest {

	private static final Locale FALLBACK = Locale.of( "is" );

	private static Locale negotiate( final String header ) {
		return ERXLocale.fromAcceptLanguage( header, FALLBACK );
	}

	@Test
	public void theHighestWeightedLanguageWins() {
		assertEquals( Locale.forLanguageTag( "de-CH" ), negotiate( "de-CH, de;q=0.9, en;q=0.8" ) );
		assertEquals( Locale.ENGLISH, negotiate( "de;q=0.5, en;q=0.9" ) );
		assertEquals( Locale.forLanguageTag( "en-US" ), negotiate( "en-US,en;q=0.9" ) );
	}

	@Test
	public void noUsableLanguageGivesTheFallback() {
		assertEquals( FALLBACK, negotiate( null ) );
		assertEquals( FALLBACK, negotiate( "" ) );
		assertEquals( FALLBACK, negotiate( "*" ) );
		assertEquals( FALLBACK, negotiate( "en;q=0" ) );
		assertEquals( FALLBACK, negotiate( ";;;garbage===" ) );
	}

	@Test
	public void aWildcardIsSkippedNotChosen() {
		assertEquals( Locale.FRENCH, negotiate( "*, fr;q=0.5" ) );
	}
}
