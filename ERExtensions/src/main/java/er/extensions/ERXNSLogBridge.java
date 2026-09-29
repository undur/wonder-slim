package er.extensions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSProperties;

/**
 * Sends WebObjects' own logging (NSLog) to slf4j's {@code NSLog} logger, and from there to whichever logging backend is
 * in use: {@code NSLog.out} at INFO, {@code NSLog.err} at WARN, {@code NSLog.debug} at DEBUG. Installed first thing in
 * {@code ERXApplication.main()}, see {@link #install()}.
 *
 * A subclass of {@code NSLog.PrintStreamLogger} because WebObjects' constructor casts NSLog's loggers to that class to
 * point them at the {@code WOOutputPath} file; any other class would fail the launch wherever {@code WOOutputPath} is
 * set. The stream it's given is not used.
 *
 * {@code NSLog.debug}'s output is let through only from the Informational level up, and that level is capped at
 * {@code er.extensions.NSLog.debugLevel} (default: Critical): WebObjects resets it from its own properties while it
 * constructs the application, and writes a fair amount of unguarded debug output at startup (every WebObjects
 * property, its URLs, "Waiting for requests...") that the startup report already covers. With
 * {@code er.extensions.ERXNSLogLog4jBridge.ignoreNSLogSettings}, all of it goes to slf4j and its configuration decides.
 */
public class ERXNSLogBridge extends NSLog.PrintStreamLogger {

	private static final Logger log = LoggerFactory.getLogger( "NSLog" );

	private enum Kind {
		OUT, ERR, DEBUG
	}

	private final Kind _kind;

	/**
	 * The highest NSLog debug level let through, see the class comment
	 */
	private final int _debugLevelCap;

	private ERXNSLogBridge( final Kind kind, final int debugLevelCap ) {
		_kind = kind;
		_debugLevelCap = debugLevelCap;
	}

	/**
	 * Makes NSLog's out, err and debug loggers bridges to slf4j, NSLog's debug level capped at
	 * {@code er.extensions.NSLog.debugLevel}. Read as a JVM option, since this runs before the configuration is composed.
	 */
	public static void install() {
		final int debugLevelCap = Integer.getInteger( ERXP.NSLOG_DEBUG_LEVEL.id(), NSLog.DebugLevelCritical );
		NSLog.setOut( new ERXNSLogBridge( Kind.OUT, debugLevelCap ) );
		NSLog.setErr( new ERXNSLogBridge( Kind.ERR, debugLevelCap ) );
		NSLog.setDebug( new ERXNSLogBridge( Kind.DEBUG, debugLevelCap ) );
		NSLog.debug.setAllowedDebugLevel( debugLevelCap );
	}

	/**
	 * @return true if NSLog's own settings are ignored, and slf4j's configuration alone decides what's logged
	 */
	private static boolean ignoreNSLogSettings() {
		return NSProperties.booleanForKeyWithDefault( ERXP.NSLOG_IGNORE_SETTINGS.id(), false );
	}

	@Override
	public void setAllowedDebugLevel( final int debugLevel ) {
		super.setAllowedDebugLevel( Math.min( debugLevel, _debugLevelCap ) );
	}

	@Override
	public void appendln( final Object object ) {
		final String message = object == null ? "" : object.toString();

		// WebObjects writes blank lines for spacing; as log events they're just an empty "NSLog -" line
		if( message.isBlank() ) {
			return;
		}

		switch( _kind ) {
			case OUT -> {
				if( isEnabled() ) {
					log.info( message );
				}
			}
			case ERR -> log.warn( message );
			case DEBUG -> {
				if( ignoreNSLogSettings() || isEnabled() && allowedDebugLevel() >= NSLog.DebugLevelInformational ) {
					log.debug( message );
				}
			}
		}
	}

	@Override
	public void appendln() {}

	@Override
	public void flush() {}
}
