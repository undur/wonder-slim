package er.extensions.logging;

import java.util.Enumeration;
import java.util.List;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * FIXME: Temporary bridge until we work out a nicer method of initializing logging
 */

public class ERXTemporaryLoggingBridge {

	public static void configureLoggingWithSystemProperties() {
		ERXLogger.configureLoggingWithSystemProperties();
	}

	/**
	 * A console appender at INFO on the root logger, so early logging (WO's own initialization,
	 * framework principals, the application constructor) is neither dropped nor greeted with log4j's
	 * "No appenders could be found". configureLogging() resets and replaces it with the configured
	 * appenders once the Properties cascade is loaded.
	 *
	 * Unconditional: log4j may already have auto-configured itself from a log4j.properties some jar
	 * on the classpath happens to ship, which would give the first lines of the log a third format.
	 * configureLogging() discards such a configuration anyway, so it is discarded here too - the
	 * Properties cascade is the only logging configuration this stack honours.
	 */
	public static void configureDefaultLogging() {
		LogManager.resetConfiguration();
		Logger.getRootLogger().addAppender( new ConsoleAppender( new ERXPatternLayout( "%d{MMM dd HH:mm:ss} %-5p %c - %m%n" ), "System.out" ) );
		Logger.getRootLogger().setLevel( Level.INFO );
	}

	/**
	 * ak: telling Log4J to re-init the Console appenders so we get logging into WOOutputPath again
	 */
	public static void reInitConsoleAppenders() {
		for( Enumeration<Appender> e = Logger.getRootLogger().getAllAppenders(); e.hasMoreElements(); ) {
			final Appender appender = e.nextElement();

			if( appender instanceof ConsoleAppender app ) {
				app.activateOptions();
			}
		}
	}

	/**
	 * Attaches the in-memory ring-buffer appender so recent log output can be read
	 * back (development convenience). Idempotent. Delegated here so callers in
	 * ERExtensions can reach it through the same reflective bridge they already use,
	 * without a compile dependency on this logging backend.
	 *
	 * @return true if log capture is active after this call
	 */
	public static boolean installLogCapture() {
		return ERXRingBufferAppender.install();
	}

	/**
	 * @return true if the ring-buffer log capture appender is attached.
	 */
	public static boolean isLogCaptureInstalled() {
		return ERXRingBufferAppender.isInstalled();
	}

	/**
	 * @return a snapshot of recently captured log lines (oldest first), filtered by
	 *         {@code contains} and limited to the last {@code tail} lines. See
	 *         {@link ERXRingBufferAppender#snapshot(String, int)}.
	 */
	public static List<String> logSnapshot( final String contains, final int tail ) {
		return ERXRingBufferAppender.snapshot( contains, tail );
	}
}