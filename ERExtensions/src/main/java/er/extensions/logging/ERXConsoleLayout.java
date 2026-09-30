package er.extensions.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The layout of the console logger's lines ({@link ERXConsoleLoggingBackend}): the common part of log4j's and logback's
 * pattern language, as {@code er.logging.pattern} writes it.
 *
 * <ul>
 * <li>{@code %d} / {@code %date}, optionally with a date format in braces ({@code %d{MMM dd HH:mm:ss}})</li>
 * <li>{@code %p} / {@code %le} / {@code %level}, the level</li>
 * <li>{@code %c} / {@code %lo} / {@code %logger}, the logger's name</li>
 * <li>{@code %m} / {@code %msg} / {@code %message}, the message</li>
 * <li>{@code %t} / {@code %thread}, the thread's name</li>
 * <li>{@code %n}, a line break, and {@code %%}, a percent sign</li>
 * </ul>
 *
 * Each takes a minimum width, right-aligned ({@code %5p}) or left-aligned ({@code %-5p}); a maximum ({@code %.30c}) is
 * ignored, as is anything in braces after a conversion other than the date's. A conversion not listed is written as it
 * stands, so nothing in the pattern is lost silently.
 */
public final class ERXConsoleLayout {

	/**
	 * A logging event, as the layout needs it
	 */
	public record Event( String level, String logger, String message, String thread, LocalDateTime time ) {}

	private interface Part {
		void append( StringBuilder line, Event event );
	}

	private final List<Part> _parts;

	private ERXConsoleLayout( final List<Part> parts ) {
		_parts = parts;
	}

	/**
	 * @return The layout the pattern describes
	 */
	public static ERXConsoleLayout of( final String pattern ) {
		final List<Part> parts = new ArrayList<>();
		final StringBuilder literal = new StringBuilder();
		int i = 0;

		while( i < pattern.length() ) {
			final char c = pattern.charAt( i );

			if( c != '%' || i == pattern.length() - 1 ) {
				literal.append( c );
				i++;
				continue;
			}

			if( pattern.charAt( i + 1 ) == '%' ) {
				literal.append( '%' );
				i += 2;
				continue;
			}

			// %[-][width][.max]name[{option}]
			final int start = i;
			i++;

			final boolean leftAligned = pattern.charAt( i ) == '-';

			if( leftAligned ) {
				i++;
			}

			int width = 0;

			while( i < pattern.length() && Character.isDigit( pattern.charAt( i ) ) ) {
				width = width * 10 + (pattern.charAt( i ) - '0');
				i++;
			}

			if( i < pattern.length() && pattern.charAt( i ) == '.' ) {
				i++;

				while( i < pattern.length() && Character.isDigit( pattern.charAt( i ) ) ) {
					i++;
				}
			}

			final int nameStart = i;

			while( i < pattern.length() && Character.isLetter( pattern.charAt( i ) ) ) {
				i++;
			}

			final String name = pattern.substring( nameStart, i );
			String option = null;

			if( i < pattern.length() && pattern.charAt( i ) == '{' ) {
				final int close = pattern.indexOf( '}', i );

				if( close > 0 ) {
					option = pattern.substring( i + 1, close );
					i = close + 1;
				}
			}

			final Part part = conversion( name, option );

			if( part == null ) {
				literal.append( pattern, start, i );
				continue;
			}

			if( !literal.isEmpty() ) {
				final String text = literal.toString();
				parts.add( ( line, event ) -> line.append( text ) );
				literal.setLength( 0 );
			}

			parts.add( width == 0 ? part : padded( part, width, leftAligned ) );
		}

		if( !literal.isEmpty() ) {
			final String text = literal.toString();
			parts.add( ( line, event ) -> line.append( text ) );
		}

		return new ERXConsoleLayout( List.copyOf( parts ) );
	}

	/**
	 * @return The part for a conversion, null if it isn't one the layout knows
	 */
	private static Part conversion( final String name, final String option ) {
		return switch( name ) {
			case "d", "date" -> {
				final DateTimeFormatter format = DateTimeFormatter.ofPattern( option == null || option.isBlank() ? "yyyy-MM-dd HH:mm:ss,SSS" : option );
				yield ( line, event ) -> line.append( format.format( event.time() ) );
			}
			case "p", "le", "level" -> ( line, event ) -> line.append( event.level() );
			case "c", "lo", "logger" -> ( line, event ) -> line.append( event.logger() );
			case "m", "msg", "message" -> ( line, event ) -> line.append( event.message() );
			case "t", "thread" -> ( line, event ) -> line.append( event.thread() );
			case "n" -> ( line, event ) -> line.append( System.lineSeparator() );
			default -> null;
		};
	}

	private static Part padded( final Part part, final int width, final boolean leftAligned ) {
		return ( line, event ) -> {
			final int start = line.length();
			part.append( line, event );
			final int padding = width - (line.length() - start);

			if( padding > 0 ) {
				line.insert( leftAligned ? line.length() : start, " ".repeat( padding ) );
			}
		};
	}

	/**
	 * @return The event laid out, with the throwable's stack trace after it if there is one
	 */
	public String format( final Event event, final Throwable throwable ) {
		final StringBuilder line = new StringBuilder();

		for( final Part part : _parts ) {
			part.append( line, event );
		}

		if( throwable != null ) {
			line.append( ERXStackTraces.format( throwable ) );
		}

		return line.toString();
	}
}
