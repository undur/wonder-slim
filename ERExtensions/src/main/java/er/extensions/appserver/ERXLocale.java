package er.extensions.appserver;

import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;

import com.webobjects.appserver.WOContext;

/**
 * The locale numbers and dates are formatted and parsed in (WOString, WOTextField's numberformat and dateformat).
 *
 * The locale is always the application's decision. Resolved per request, first match wins:
 *
 * <ol>
 * <li>a locale the application gave the session ({@link ERXSession#setLocale(Locale)})</li>
 * <li>the application's locale, when set ({@link #setApplicationLocale(Locale)})</li>
 * </ol>
 *
 * When neither applies, {@link #current()} answers null and formatters are created exactly as they always were. That
 * is deliberate, and not the JVM's default locale: a locale also changes how text fields parse input (in Icelandic,
 * {@code 12.50} parses as 1250), so it must never change for an application that didn't ask for it.
 *
 * The locale a request asks for in its {@code Accept-Language} header is never used on its own: it is a hint
 * ({@link ERXRequest#requestedLocale()}) that an application may act on, for example by setting it as the session's
 * locale.
 */
public final class ERXLocale {

	/**
	 * The application's locale, null for none (formatting and parsing as they always were)
	 */
	private static volatile Locale _applicationLocale;

	private ERXLocale() {}

	/**
	 * @return The locale to format and parse numbers and dates in for the current request, null when the application
	 *         configured none (see the class documentation)
	 */
	public static Locale current() {
		final ERXSession session = session( ERXWOContext.currentContext() );

		if( session != null && session.locale() != null ) {
			return session.locale();
		}

		return applicationLocale();
	}

	/**
	 * @return The session of the current request: the awake one, or the one the request names (restored through the
	 *         context, as a route request doesn't wake its session unless something asks for it); null when there is none
	 */
	private static ERXSession session( final WOContext context ) {
		final ERXSession awake = ERXSession.session();

		if( awake != null ) {
			return awake;
		}

		if( context != null && context.hasSession() && context.session() instanceof ERXSession restored ) {
			return restored;
		}

		return null;
	}

	/**
	 * @param acceptLanguage The value of a request's {@code Accept-Language} header, may be null
	 * @param fallback The locale to answer when the header names no usable language, may be null
	 * @return The highest-weighted language the header names, as a locale; the fallback when there is none
	 */
	public static Locale fromAcceptLanguage( final String acceptLanguage, final Locale fallback ) {

		if( acceptLanguage == null || acceptLanguage.isBlank() ) {
			return fallback;
		}

		final List<LanguageRange> ranges;

		try {
			ranges = LanguageRange.parse( acceptLanguage );
		}
		catch( IllegalArgumentException e ) {
			return fallback;
		}

		// LanguageRange.parse returns the ranges ordered by weight, highest first
		for( final LanguageRange range : ranges ) {
			if( range.getWeight() > 0 && !range.getRange().contains( "*" ) ) {
				final Locale locale = Locale.forLanguageTag( range.getRange() );

				if( !locale.getLanguage().isEmpty() ) {
					return locale;
				}
			}
		}

		return fallback;
	}

	/**
	 * @return The application's locale, null when none was set
	 */
	public static Locale applicationLocale() {
		return _applicationLocale;
	}

	/**
	 * Sets the locale the application formats and parses numbers and dates in, typically once, in the application's
	 * constructor. A session's own locale still takes precedence. null reverts to no locale: formatting and parsing as
	 * they always were.
	 */
	public static void setApplicationLocale( final Locale locale ) {
		_applicationLocale = locale;
	}
}
