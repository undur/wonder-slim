package er.extensions.formatters;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import er.extensions.appserver.ERXLocale;

/**
 * Formats java.time values with date format patterns, as ERXWOString's {@code dateformat} binding does.
 *
 * A pattern is one of two syntaxes, told apart by whether it contains {@code %}:
 *
 * <ul>
 * <li>An NSTimestampFormatter (strftime-style) pattern, such as {@code %d.%m.%Y}, which is translated to the equivalent
 * {@link DateTimeFormatter} pattern and renders exactly as NSTimestampFormatter would. {@code %} isn't a DateTimeFormatter
 * pattern letter, so the two can't be confused; a {@code %} inside quoted text is literal, as NSTimestampFormatter treats it.</li>
 * <li>Any other pattern is a DateTimeFormatter pattern, such as {@code dd.MM.yyyy}.</li>
 * </ul>
 *
 * DateTimeFormatters are immutable and thread-safe, so the formatter for a pattern and locale is created once and
 * shared.
 *
 * Framework-independent by design, meant to move to ng-core eventually to serve ng's elements as well.
 */

public class ERXDateTimeFormatters {

	/**
	 * Formatters by pattern and locale
	 */
	private static final Map<Key, DateTimeFormatter> _formatters = new ConcurrentHashMap<>();

	private record Key( String pattern, Locale locale ) {}

	/**
	 * @return A shared formatter for the given pattern (either syntax) in the current locale ({@link ERXLocale#current()}, or the default locale if none is set)
	 * @throws IllegalArgumentException if the pattern is invalid
	 */
	public static DateTimeFormatter formatterForPattern( final String pattern ) {
		final Locale current = ERXLocale.current();
		final Locale locale = current != null ? current : Locale.getDefault( Locale.Category.FORMAT );

		return _formatters.computeIfAbsent( new Key( pattern, locale ), key -> {
			final String dateTimeFormatterPattern = isStrftimePattern( key.pattern() ) ? toDateTimeFormatterPattern( key.pattern(), key.locale() ) : key.pattern();
			return DateTimeFormatter.ofPattern( dateTimeFormatterPattern, key.locale() );
		} );
	}

	/**
	 * @return true if the pattern is an NSTimestampFormatter (strftime-style) pattern rather than a DateTimeFormatter
	 *         pattern: it has a {@code %} outside quoted text. As with NSTimestampFormatter, a {@code %} inside quotes
	 *         ({@code 'at 100%'}) is literal text in a native pattern, and a doubled quote ({@code ''}) is a literal quote.
	 */
	public static boolean isStrftimePattern( final String pattern ) {
		boolean insideQuote = false;

		for( int i = 0; i < pattern.length(); i++ ) {
			final char c = pattern.charAt( i );

			if( c == '\'' ) {
				if( i + 1 < pattern.length() && pattern.charAt( i + 1 ) == '\'' ) {
					i++;
				}
				else {
					insideQuote = !insideQuote;
				}
			}
			else if( c == '%' && !insideQuote ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @return The value formatted with the pattern (either syntax). An {@link Instant}, which has no calendar fields, is formatted in the default time zone.
	 * @throws IllegalArgumentException if the pattern is invalid, the value isn't a java.time value, or the pattern needs fields the value doesn't have (a time for a LocalDate, a zone for a LocalDateTime)
	 */
	public static String format( final Object value, final String pattern ) {
		return format( value, formatterForPattern( pattern ), pattern, null );
	}

	/**
	 * @return The value formatted with the formatter. An {@link Instant}, which has no calendar fields, is formatted in the default time zone.
	 * @throws IllegalArgumentException if the value isn't a java.time value, or the formatter needs fields the value doesn't have
	 */
	public static String format( final Object value, final DateTimeFormatter formatter ) {
		return format( value, formatter, null, formatter );
	}

	/**
	 * @param pattern The pattern the formatter was created from, or null if it was passed in. Only used to describe the formatter in an error message, which is built only when one is thrown.
	 * @param passedFormatter The formatter, if it was passed in, likewise.
	 */
	private static String format( final Object value, final DateTimeFormatter formatter, final String pattern, final DateTimeFormatter passedFormatter ) {

		if( !(value instanceof TemporalAccessor temporal) ) {
			throw new IllegalArgumentException( "%s can only format java.time values, not a %s (%s)".formatted( describe( pattern, passedFormatter ), value.getClass().getName(), value ) );
		}

		try {
			return formatter.format( temporal instanceof Instant instant ? instant.atZone( ZoneId.systemDefault() ) : temporal );
		}
		catch( DateTimeException e ) {
			throw new IllegalArgumentException( "%s can't format a %s (%s): %s".formatted( describe( pattern, passedFormatter ), temporal.getClass().getSimpleName(), temporal, e.getMessage() ), e );
		}
	}

	/**
	 * @return A description of the formatter for an error message: the pattern as written, if there is one
	 */
	private static String describe( final String pattern, final DateTimeFormatter passedFormatter ) {
		return pattern != null ? "Date format '%s'".formatted( pattern ) : "DateTimeFormatter %s".formatted( passedFormatter );
	}

	/**
	 * @return The DateTimeFormatter pattern equivalent to an NSTimestampFormatter (strftime-style) pattern, in the default locale. See {@link #toDateTimeFormatterPattern(String, Locale)}.
	 */
	public static String toDateTimeFormatterPattern( final String strftimePattern ) {
		return toDateTimeFormatterPattern( strftimePattern, Locale.getDefault( Locale.Category.FORMAT ) );
	}

	/**
	 * @return The DateTimeFormatter pattern equivalent to an NSTimestampFormatter (strftime-style) pattern, covering all of
	 *         its conversions. Literal text is quoted. {@code %x}, {@code %X} and {@code %c} are the locale's short date,
	 *         time, and date-and-time patterns, as NSTimestampFormatter takes them. {@code %w} is the abbreviated weekday,
	 *         which is what NSTimestampFormatter renders for it.
	 * @throws IllegalArgumentException for an unknown conversion or a trailing {@code %}, where NSTimestampFormatter would silently render {@code %}
	 */
	public static String toDateTimeFormatterPattern( final String strftimePattern, final Locale locale ) {
		final StringBuilder result = new StringBuilder();
		final StringBuilder literal = new StringBuilder();

		for( int i = 0; i < strftimePattern.length(); i++ ) {
			final char c = strftimePattern.charAt( i );

			if( c != '%' ) {
				literal.append( c );
				continue;
			}

			if( i + 1 == strftimePattern.length() ) {
				throw new IllegalArgumentException( "Date format '%s' ends with a lone '%%'".formatted( strftimePattern ) );
			}

			final char conversion = strftimePattern.charAt( ++i );

			if( conversion == '%' ) {
				literal.append( '%' );
				continue;
			}

			final String letters = switch( conversion ) {
				case 'Y' -> "yyyy";
				case 'y' -> "yy";
				case 'm' -> "MM";
				case 'd' -> "dd";
				case 'e' -> "d";
				case 'j' -> "DDD";
				case 'H' -> "HH";
				case 'I' -> "hh";
				case 'M' -> "mm";
				case 'S' -> "ss";
				case 'F' -> "SSS";
				case 'p' -> "a";
				case 'a', 'w' -> "EEE";
				case 'A' -> "EEEE";
				case 'b' -> "MMM";
				case 'B' -> "MMMM";
				case 'z' -> "xx";
				case 'Z' -> "VV";
				case 'x' -> shortDatePattern( locale );
				case 'X' -> shortTimePattern( locale );
				case 'c' -> shortDatePattern( locale ) + " " + shortTimePattern( locale );
				default -> throw new IllegalArgumentException( "Date format '%s': unknown conversion %%%s".formatted( strftimePattern, conversion ) );
			};

			appendQuoted( result, literal );
			result.append( letters );
		}

		appendQuoted( result, literal );
		return result.toString();
	}

	/**
	 * @return The locale's short date pattern, as java.text.DateFormat provides it (its pattern letters mean the same to DateTimeFormatter)
	 */
	private static String shortDatePattern( final Locale locale ) {
		return ((SimpleDateFormat)DateFormat.getDateInstance( DateFormat.SHORT, locale )).toPattern();
	}

	/**
	 * @return The locale's short time pattern, as java.text.DateFormat provides it
	 */
	private static String shortTimePattern( final Locale locale ) {
		return ((SimpleDateFormat)DateFormat.getTimeInstance( DateFormat.SHORT, locale )).toPattern();
	}

	/**
	 * Appends literal text to a DateTimeFormatter pattern, quoted (with any quote character doubled), and clears it
	 */
	private static void appendQuoted( final StringBuilder pattern, final StringBuilder literal ) {
		if( !literal.isEmpty() ) {
			pattern.append( '\'' ).append( literal.toString().replace( "'", "''" ) ).append( '\'' );
			literal.setLength( 0 );
		}
	}
}
