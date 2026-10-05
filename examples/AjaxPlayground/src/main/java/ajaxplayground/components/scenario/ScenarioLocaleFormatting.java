package ajaxplayground.components.scenario;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.TimeZone;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSTimestamp;

import ajaxplayground.components.PlaygroundPage;
import er.extensions.appserver.ERXLocale;
import er.extensions.appserver.ERXRequest;
import er.extensions.appserver.ERXSession;

/**
 * Locale-aware formatting: WOString's numberformat and dateformat in the formatting locale (ERXLocale). The locale is the
 * application's choice: the session's when set, the application's (ERXLocale.setApplicationLocale) otherwise; with neither,
 * formatters behave (and parse) exactly as they always did. The browser's Accept-Language is only a hint the page may choose to act on.
 */
public class ScenarioLocaleFormatting extends PlaygroundPage {

	public final BigDecimal number = new BigDecimal( "1234567.891" );

	@SuppressWarnings("deprecation")
	public final NSTimestamp date = new NSTimestamp( 2026, 3, 5, 10, 0, 0, TimeZone.getTimeZone( "UTC" ) );

	public ScenarioLocaleFormatting( WOContext context ) {
		super( context );
	}

	public String formattingLocale() {
		final Locale locale = ERXLocale.current();
		return locale == null ? "(none)" : locale.toLanguageTag();
	}

	public WOActionResults setSessionGermanAction() {
		((ERXSession)session()).setLocale( Locale.GERMANY );
		return null;
	}

	public String requestedLocale() {
		final Locale locale = ((ERXRequest)context().request()).requestedLocale();
		return locale == null ? "(none)" : locale.toLanguageTag();
	}

	/**
	 * The application opting in to the browser's hint
	 */
	public WOActionResults useBrowserLocaleAction() {
		((ERXSession)session()).setLocale( ((ERXRequest)context().request()).requestedLocale() );
		return null;
	}

	public WOActionResults setApplicationIcelandicAction() {
		ERXLocale.setApplicationLocale( Locale.of( "is" ) );
		return null;
	}

	public WOActionResults clearApplicationLocaleAction() {
		ERXLocale.setApplicationLocale( null );
		return null;
	}

	public WOActionResults clearSessionLocaleAction() {
		((ERXSession)session()).setLocale( null );
		return null;
	}
}
