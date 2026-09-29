package er.extensions.logging;

import java.util.Enumeration;
import java.util.List;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * The reload4j logging backend, listed in META-INF/services/er.extensions.logging.ERXLoggingBackend
 */
public class ERXReload4jLoggingBackend implements ERXLoggingBackend {

	@Override
	public void configure() {
		ERXLogger.configureLoggingWithSystemProperties();
	}

	/**
	 * A console appender at INFO on the root logger, so early logging (WO's own initialization, the plugins, the
	 * application constructor) is neither dropped nor greeted with log4j's "No appenders could be found". configure()
	 * resets and replaces it with the configured appenders.
	 *
	 * Unconditional: log4j may already have auto-configured itself from a log4j.properties some jar on the classpath
	 * happens to ship, which would give the first lines of the log a third format. configure() discards such a
	 * configuration anyway, so it is discarded here too - the application's configuration is the only logging
	 * configuration this stack honours.
	 */
	@Override
	public void configureDefault() {
		LogManager.resetConfiguration();
		Logger.getRootLogger().addAppender( new ConsoleAppender( new ERXPatternLayout( "%d{MMM dd HH:mm:ss} %-5p %c - %m%n" ), "System.out" ) );
		Logger.getRootLogger().setLevel( Level.INFO );
	}

	/**
	 * ak: telling Log4J to re-init the Console appenders so we get logging into WOOutputPath again
	 */
	@Override
	public void reattachConsole() {
		for( Enumeration<Appender> e = Logger.getRootLogger().getAllAppenders(); e.hasMoreElements(); ) {
			final Appender appender = e.nextElement();

			if( appender instanceof ConsoleAppender app ) {
				app.activateOptions();
			}
		}
	}

	/**
	 * Attaches the in-memory ring-buffer appender, see {@link ERXRingBufferAppender#install()}
	 */
	@Override
	public boolean installCapture() {
		return ERXRingBufferAppender.install();
	}

	/**
	 * See {@link ERXRingBufferAppender#snapshot(String, int)}
	 */
	@Override
	public List<String> capturedLines( final String contains, final int tail ) {
		return ERXRingBufferAppender.snapshot( contains, tail );
	}
}
