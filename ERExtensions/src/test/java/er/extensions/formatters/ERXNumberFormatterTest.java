package er.extensions.formatters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class ERXNumberFormatterTest {

	private static String format( final String pattern, final Object value ) {
		return new ERXNumberFormatter( pattern ).format( value );
	}

	@Test
	public void divisionIsRoundedOnlyByThePattern() {
		assertEquals( "1.46 KB", format( "(/1024=)0.00 KB", 1500 ) );
		assertEquals( "1.46 KB", format( "(/1024=)0.00 KB", 1500.0 ) );
		assertEquals( "1.47 KB", format( "(/1024=)0.00 KB", 1500.5 ) );
		assertEquals( "1.47 KB", format( "(/1024=)0.00 KB", new BigDecimal( "1500.5" ) ) );
		assertEquals( "1.47 KB", format( "(/1024=)0.00 KB", new BigDecimal( "1500.50" ) ) );
		assertEquals( "0.67", format( "(/3=)0.00", new BigDecimal( "2" ) ) );
		assertEquals( "0.67", format( "(/3=)0.00", new BigDecimal( "2.0" ) ) );
	}

	@Test
	public void divisionShowsAsManyDigitsAsThePattern() {
		assertEquals( "1.234567", format( "(/1000000=)0.000000", 1234567 ) );
	}

	@Test
	public void divisionWithExplicitScale() {
		assertEquals( "1.50", format( "(/1024;1=)0.00", 1500 ) );
	}

	@Test
	public void multiplication() {
		assertEquals( "0.99", format( "(*60;4=)0.00", new BigDecimal( "0.0165" ) ) );
		assertEquals( "90.00", format( "(*60=)0.00", 1.5 ) );
	}

	@Test
	public void withoutFactor() {
		assertEquals( "1,500.50", format( "#,##0.00", new BigDecimal( "1500.5" ) ) );
		assertEquals( "1500", format( "0", 1500 ) );
	}
}
