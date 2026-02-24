package er.extensions.logging;

import java.util.Enumeration;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;

/**
 * FIXME: Temporary bridge until we work out a nicer method of initializing logging
 */

public class ERXTemporaryLoggingBridge {

	public static void configureLoggingWithSystemProperties() {
		ERXLogger.configureLoggingWithSystemProperties();
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
}