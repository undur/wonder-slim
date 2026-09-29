package er.extensions.logging.logback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import er.extensions.logging.ERXLoggingBackend;
import er.extensions.logging.ERXLoggingSupport;
import er.extensions.foundation.ERXConfigurationManager;

public class ERXLogbackLoggingBackendTest {

	private final ERXLogbackLoggingBackend _backend = new ERXLogbackLoggingBackend();

	private static final List<String> KEYS = List.of( "er.logging.level.test.neutral", "er.logging.level.test.both", "er.logging.level.test.legacyAndNeutral", "log4j.logger.test.legacy", "log4j.logger.test.legacyAndNeutral", "log4j.appender.A1", "logback.configurationFile", "er.logging.pattern" );

	@AfterEach
	public void clean() {
		KEYS.forEach( System::clearProperty );
		_backend.configure();
	}

	private static Logger logger( final String name ) {
		return ((LoggerContext)LoggerFactory.getILoggerFactory()).getLogger( name );
	}

	private static Path nativeConfiguration( final String loggers ) throws IOException {
		final Path file = Files.createTempFile( "logback", ".xml" );
		file.toFile().deleteOnExit();
		Files.writeString( file, """
				<configuration>
					<appender name="native" class="ch.qos.logback.core.ConsoleAppender">
						<encoder><pattern>NATIVE %msg%n</pattern></encoder>
					</appender>
					<root level="INFO"><appender-ref ref="native" /></root>
					LOGGERS
				</configuration>
				""".replace( "LOGGERS", loggers ) );
		return file;
	}

	@Test
	public void theBackendIsFoundThroughServiceLoader() {
		final ERXLoggingBackend backend = ERXLoggingSupport.backend();
		assertNotNull( backend );
		assertSame( ERXLogbackLoggingBackend.class, backend.getClass() );
	}

	@Test
	public void aNeutralLevelIsSet() {
		System.setProperty( "er.logging.level.test.neutral", "debug" );
		_backend.configure();
		assertEquals( Level.DEBUG, logger( "test.neutral" ).getLevel() );
	}

	@Test
	public void aLegacyLevelIsUsedAndANeutralOneWinsOverIt() {
		System.setProperty( "log4j.logger.test.legacy", "WARN, A1" );
		System.setProperty( "log4j.logger.test.legacyAndNeutral", "WARN" );
		System.setProperty( "er.logging.level.test.legacyAndNeutral", "TRACE" );
		_backend.configure();
		assertEquals( Level.WARN, logger( "test.legacy" ).getLevel() );
		assertEquals( Level.TRACE, logger( "test.legacyAndNeutral" ).getLevel() );
	}

	@Test
	public void logbacksOwnConfigurationWinsWhereItNamesALoggerAndTakesOverTheOutput() throws IOException {
		System.setProperty( "er.logging.level.test.both", "DEBUG" );
		System.setProperty( "er.logging.level.test.neutral", "DEBUG" );
		System.setProperty( "logback.configurationFile", nativeConfiguration( "<logger name=\"test.both\" level=\"ERROR\" />" ).toString() );
		_backend.configure();

		assertEquals( Level.ERROR, logger( "test.both" ).getLevel() );
		assertEquals( Level.DEBUG, logger( "test.neutral" ).getLevel(), "Not named by logback's configuration, so the neutral level stands" );
		assertNull( logger( Logger.ROOT_LOGGER_NAME ).getAppender( "console" ), "logback's configuration gives the root logger output of its own" );
		assertNotNull( logger( Logger.ROOT_LOGGER_NAME ).getAppender( "native" ) );
		assertEquals( "ERROR", _backend.nativeLevels().get( "test.both" ), "The level logback's configuration changed is credited to it" );
		assertFalse( _backend.nativeLevels().containsKey( "test.neutral" ) );
		assertTrue( _backend.nativeConfiguration().endsWith( ".xml" ) );
		assertTrue( _backend.loggers().stream().anyMatch( l -> l.name().equals( "test.both" ) && "ERROR".equals( l.level() ) ) );
		assertEquals( "root", _backend.loggers().get( 0 ).name() );
	}

	@Test
	public void aLevelSetOnTheRunningInstanceWinsOverEverything() throws IOException {
		if( ERXConfigurationManager.current() == null ) {
			ERXConfigurationManager.compose( new String[0] );
		}

		System.setProperty( "logback.configurationFile", nativeConfiguration( "<logger name=\"test.instance\" level=\"ERROR\" />" ).toString() );
		ERXConfigurationManager.setProperty( "er.logging.level.test.instance", "TRACE" );

		try {
			_backend.configure();
			assertEquals( Level.TRACE, logger( "test.instance" ).getLevel() );
		}
		finally {
			ERXConfigurationManager.unsetProperty( "er.logging.level.test.instance" );
		}

		_backend.configure();
		assertEquals( Level.ERROR, logger( "test.instance" ).getLevel(), "Unset, logback's own configuration decides again" );
	}

	@Test
	public void parenthesesInTheLayoutAreLiteral() {
		assertEquals( "%d \\(%F:%L\\) %-5p %c - %m%n", ERXLogbackLoggingBackend.logbackPattern( "%d (%F:%L) %-5p %c - %m%n" ) );

		System.setProperty( "er.logging.pattern", "[(%F:%L) %-5p %m]%n" );
		_backend.installCapture();
		_backend.configure();

		final ch.qos.logback.classic.PatternLayout layout = new ch.qos.logback.classic.PatternLayout();
		layout.setContext( (LoggerContext)LoggerFactory.getILoggerFactory() );
		layout.setPattern( ERXLogbackLoggingBackend.logbackPattern( "[(%F:%L) %-5p %m]" ) );
		layout.start();

		final ch.qos.logback.classic.spi.LoggingEvent event = new ch.qos.logback.classic.spi.LoggingEvent( "x", logger( "test.layout" ), Level.INFO, "the message", null, null );
		final String line = layout.doLayout( event );
		assertTrue( line.startsWith( "[(" ) && line.endsWith( "INFO  the message]" ), line );
	}

	@Test
	public void withoutItsOwnConfigurationTheConsoleOutputStays() {
		_backend.configure();
		assertNotNull( logger( Logger.ROOT_LOGGER_NAME ).getAppender( "console" ) );
	}

	@Test
	public void captureSurvivesAReconfiguration() {
		_backend.installCapture();
		_backend.configure();
		LoggerFactory.getLogger( "test.capture" ).info( "CAPTURED-AFTER-RECONFIGURE" );
		assertEquals( 1, _backend.capturedLines( "CAPTURED-AFTER-RECONFIGURE", 0 ).size() );
	}

	@Test
	/**
	 * An application calling the log4j API itself adds log4j-over-slf4j, as the test does
	 */
	public void anApplicationsLog4jCallsReachLogbackThroughLog4jOverSlf4j() {
		System.setProperty( "er.logging.level.test.neutral", "DEBUG" );
		_backend.installCapture();
		_backend.configure();

		final org.apache.log4j.Logger log4j = org.apache.log4j.Logger.getLogger( "test.neutral" );
		assertTrue( log4j.isDebugEnabled() );
		log4j.debug( "THROUGH-LOG4J-API" );
		assertEquals( 1, _backend.capturedLines( "THROUGH-LOG4J-API", 0 ).size() );
	}
}
