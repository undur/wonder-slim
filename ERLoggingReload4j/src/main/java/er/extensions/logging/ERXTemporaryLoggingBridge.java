package er.extensions.logging;

import java.util.Enumeration;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;

/**
 * This class exists only as a temporary bridge while we work out receiving instructions from ERExtensions in a nicer manner
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