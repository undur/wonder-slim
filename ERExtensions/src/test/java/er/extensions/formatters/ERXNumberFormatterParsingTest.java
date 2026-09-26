package er.extensions.formatters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import er.extensions.appserver.ERXLocale;

/**
 * Pins how text fields parse and show numbers when the application configures no locale: exactly as they always have,
 * whatever the JVM's default locale. Applications keep money in these fields; a locale they didn't ask for would turn
 * 12.50 into 1250.
 */
public class ERXNumberFormatterParsingTest {

	private Locale _originalDefault;

	@BeforeEach
	public void setIcelandicJVMDefault() {
		_originalDefault = Locale.getDefault();
		Locale.setDefault( Locale.of( "is", "IS" ) );
		ERXLocale.setApplicationLocale( null );
	}

	@AfterEach
	public void restoreJVMDefault() {
		Locale.setDefault( _originalDefault );
	}

	private static BigDecimal parse( final String pattern, final String input ) throws ParseException {
		return (BigDecimal)ERXNumberFormatter.numberFormatterForPattern( pattern ).parseObject( input );
	}

	@Test
	public void withNoLocaleConfiguredInputParsesAsItAlwaysHas() throws ParseException {
		for( final String pattern : new String[] { "0", "#,##0.00" } ) {
			assertEquals( new BigDecimal( "1000" ), parse( pattern, "1000" ), pattern );
			assertEquals( new BigDecimal( "12.50" ), parse( pattern, "12.50" ), pattern );
			assertEquals( new BigDecimal( "1.000" ), parse( pattern, "1.000" ), pattern );
			assertEquals( new BigDecimal( "1000" ), parse( pattern, "1,000" ), pattern );
			assertEquals( new BigDecimal( "1000.50" ), parse( pattern, "1,000.50" ), pattern );
			assertEquals( new BigDecimal( "-5" ), parse( pattern, "-5" ), pattern );
		}
	}

	@Test
	public void withNoLocaleConfiguredOutputIsAsItAlwaysWas() {
		assertEquals( "1000", ERXNumberFormatter.numberFormatterForPattern( "0" ).format( Integer.valueOf( 1000 ) ) );
		assertEquals( "1,234.50", ERXNumberFormatter.numberFormatterForPattern( "#,##0.00" ).format( new BigDecimal( "1234.5" ) ) );
	}

	@Test
	public void aConfiguredLocaleDecidesParsingToo() throws ParseException {
		ERXLocale.setApplicationLocale( Locale.of( "is" ) );

		try {
			assertEquals( new BigDecimal( "12.50" ), parse( "0", "12,50" ) );
			assertEquals( "1.234,50", ERXNumberFormatter.numberFormatterForPattern( "#,##0.00" ).format( new BigDecimal( "1234.5" ) ) );
		}
		finally {
			ERXLocale.setApplicationLocale( null );
		}
	}
}
