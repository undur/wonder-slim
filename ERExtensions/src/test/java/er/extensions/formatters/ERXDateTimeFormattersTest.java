package er.extensions.formatters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.webobjects.foundation.NSTimeZone;
import com.webobjects.foundation.NSTimestamp;
import com.webobjects.foundation.NSTimestampFormatter;

public class ERXDateTimeFormattersTest {

	private static final LocalDateTime DATE_TIME = LocalDateTime.of( 2026, 9, 7, 14, 5, 9, 123_000_000 );

	private static String format( final Object value, final String pattern ) {
		return ERXDateTimeFormatters.format( value, pattern );
	}

	@Test
	public void translatesEveryConversion() {
		assertEquals( "yyyy'-'MM'-'dd", ERXDateTimeFormatters.toDateTimeFormatterPattern( "%Y-%m-%d" ) );
		assertEquals( "yy", ERXDateTimeFormatters.toDateTimeFormatterPattern( "%y" ) );
		assertEquals( "d' 'DDD", ERXDateTimeFormatters.toDateTimeFormatterPattern( "%e %j" ) );
		assertEquals( "HH':'mm':'ss'.'SSS", ERXDateTimeFormatters.toDateTimeFormatterPattern( "%H:%M:%S.%F" ) );
		assertEquals( "hh' 'a", ERXDateTimeFormatters.toDateTimeFormatterPattern( "%I %p" ) );
		assertEquals( "EEE' 'EEEE' 'MMM' 'MMMM' 'EEE", ERXDateTimeFormatters.toDateTimeFormatterPattern( "%a %A %b %B %w" ) );
		assertEquals( "xx' 'VV", ERXDateTimeFormatters.toDateTimeFormatterPattern( "%z %Z" ) );
	}

	@Test
	public void quotesLiterals() {
		assertEquals( "'100%'", ERXDateTimeFormatters.toDateTimeFormatterPattern( "100%%" ) );
		assertEquals( "'Week of 'dd", ERXDateTimeFormatters.toDateTimeFormatterPattern( "Week of %d" ) );
		assertEquals( "HH' o''''clock'", ERXDateTimeFormatters.toDateTimeFormatterPattern( "%H o''clock" ) );
		assertEquals( "Week of 07", format( DATE_TIME, "Week of %d" ) );
	}

	@Test
	public void rejectsConversionsWithoutEquivalent() {
		for( final String pattern : new String[] { "%Q", "%d%" } ) {
			assertThrows( IllegalArgumentException.class, () -> ERXDateTimeFormatters.toDateTimeFormatterPattern( pattern ), pattern );
		}
	}

	@Test
	public void tellsTheSyntaxesApart() {
		assertTrue( ERXDateTimeFormatters.isStrftimePattern( "%d.%m.%Y" ) );
		assertTrue( ERXDateTimeFormatters.isStrftimePattern( "%H o''clock" ) );
		assertFalse( ERXDateTimeFormatters.isStrftimePattern( "dd.MM.yyyy" ) );

		// A % inside quoted text is literal text in a native pattern, as NSTimestampFormatter treats it
		assertFalse( ERXDateTimeFormatters.isStrftimePattern( "dd.MM 'at 100%'" ) );
		assertEquals( "07.09 at 100%", format( DATE_TIME, "dd.MM 'at 100%'" ) );

		// So an unmatched quote before a % quotes the rest, making the pattern native, and here invalid
		assertFalse( ERXDateTimeFormatters.isStrftimePattern( "It's %H" ) );
		assertThrows( IllegalArgumentException.class, () -> format( DATE_TIME, "It's %H" ) );

		assertEquals( format( DATE_TIME, "%d.%m.%Y %H:%M" ), format( DATE_TIME, "dd.MM.yyyy HH:mm" ) );
	}

	/**
	 * The promise that lets templates keep their patterns: a strftime pattern renders a java.time value exactly as
	 * NSTimestampFormatter renders the same moment as an NSTimestamp, in the same zone and the JVM's locale.
	 *
	 * Checked in zones whose rules haven't changed in years: NSTimestampFormatter's time zone data can be older than
	 * the JDK's, so in a zone with recent rule changes (Egypt's daylight saving time since 2023, for one) it renders
	 * the wrong hour, where java.time is right.
	 */
	@Test
	@SuppressWarnings("deprecation")
	public void matchesNSTimestampFormatterForEveryConversion() {
		final String[] patterns = { "%Y", "%y", "%m", "%d", "%e", "%j", "%H", "%I", "%M", "%S", "%F", "%p", "%a", "%A", "%w", "%b", "%B", "%z", "%Z", "%x", "%X", "%c", "%d.%m.%Y %H:%M:%S", "100%%", "%H o''clock" };

		for( final String zoneID : new String[] { "Atlantic/Reykjavik", "America/New_York", "Asia/Kolkata", "Asia/Tokyo" } ) {
			final ZoneId zone = ZoneId.of( zoneID );
			final ZonedDateTime[] moments = {
					DATE_TIME.atZone( zone ),
					ZonedDateTime.of( 2026, 1, 1, 0, 30, 5, 7_000_000, zone ),
					ZonedDateTime.of( 2026, 7, 4, 12, 0, 0, 0, zone ),
					ZonedDateTime.of( 2025, 12, 31, 23, 59, 59, 999_000_000, zone ) };

			for( final ZonedDateTime moment : moments ) {
				final NSTimestamp timestamp = new NSTimestamp( moment.toInstant().toEpochMilli() );

				for( final String pattern : patterns ) {
					final NSTimestampFormatter nsFormatter = new NSTimestampFormatter( pattern );
					nsFormatter.setDefaultFormatTimeZone( NSTimeZone.timeZoneWithName( zoneID, true ) );
					assertEquals( nsFormatter.format( timestamp ), format( moment, pattern ), pattern + " at " + moment );
				}
			}
		}
	}

	@Test
	public void formatsJavaTimeValues() {
		assertEquals( "07.09.2026", format( LocalDate.of( 2026, 9, 7 ), "%d.%m.%Y" ) );
		assertEquals( "2026-09-07 14:05:09.123", format( DATE_TIME, "%Y-%m-%d %H:%M:%S.%F" ) );
		assertEquals( "14:05", format( LocalTime.of( 14, 5 ), "%H:%M" ) );
		assertEquals( "14:05 Atlantic/Reykjavik", format( DATE_TIME.atZone( ZoneId.of( "Atlantic/Reykjavik" ) ), "%H:%M %Z" ) );
		assertEquals( "14:05 +0200", format( OffsetDateTime.of( DATE_TIME, ZoneOffset.ofHours( 2 ) ), "%H:%M %z" ) );
	}

	@Test
	public void formatsInstantsInTheDefaultZone() {
		final Instant instant = DATE_TIME.atZone( ZoneId.systemDefault() ).toInstant();
		assertEquals( "2026-09-07 14:05", format( instant, "yyyy-MM-dd HH:mm" ) );
	}

	@Test
	public void monthAndDayNamesFollowTheLocale() {
		// ERXLocale sets no locale in these tests, so names follow the default locale
		final Locale locale = Locale.getDefault( Locale.Category.FORMAT );
		assertEquals( DateTimeFormatter.ofPattern( "EEEE d. MMMM", locale ).format( DATE_TIME ), format( DATE_TIME, "%A %e. %B" ) );
	}

	@Test
	public void aPatternThatDoesNotFitTheValueThrows() {
		final IllegalArgumentException e = assertThrows( IllegalArgumentException.class, () -> format( LocalDate.of( 2026, 9, 7 ), "%H:%M" ) );
		assertTrue( e.getMessage().startsWith( "Date format '%H:%M' can't format a LocalDate" ), e.getMessage() );
		assertThrows( IllegalArgumentException.class, () -> format( DATE_TIME, "%Y %Z" ) );
		assertThrows( IllegalArgumentException.class, () -> format( new NSTimestamp(), "%Y" ) );
		assertThrows( IllegalArgumentException.class, () -> format( "2026-09-07", "yyyy" ) );
	}

	@Test
	public void formattersAreShared() {
		assertSame( ERXDateTimeFormatters.formatterForPattern( "%d.%m.%Y" ), ERXDateTimeFormatters.formatterForPattern( "%d.%m.%Y" ) );
	}

	@Test
	public void zonedValuesKeepTheirZone() {
		final ZonedDateTime tokyo = DATE_TIME.atZone( ZoneId.of( "Asia/Tokyo" ) );
		assertEquals( "14:05", format( tokyo, "HH:mm" ) );
	}
}
