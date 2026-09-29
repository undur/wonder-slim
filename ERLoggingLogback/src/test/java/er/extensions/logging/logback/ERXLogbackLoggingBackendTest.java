package er.extensions.logging.logback;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import er.extensions.ERXLoggingBackend;
import er.extensions.ERXLoggingSupport;
import er.extensions.foundation.ERXConfigurationManager;

public class ERXLogbackLoggingBackendTest {

	private final ERXLogbackLoggingBackend _backend = new ERXLogbackLoggingBackend();

	private static final List<String> KEYS = List.of( "er.extensions.logging.level.test.neutral", "er.extensions.logging.level.test.both", "er.extensions.logging.level.test.legacyAndNeutral", "log4j.logger.test.legacy", "log4j.logger.test.legacyAndNeutral", "log4j.appender.A1", "logback.configurationFile", "er.extensions.logging.pattern" );

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
		System.setProperty( "er.extensions.logging.level.test.neutral", "debug" );
		_backend.configure();
		assertEquals( Level.DEBUG, logger( "test.neutral" ).getLevel() );
	}

	@Test
	public void aLegacyLevelIsUsedAndANeutralOneWinsOverIt() {
		System.setProperty( "log4j.logger.test.legacy", "WARN, A1" );
		System.setProperty( "log4j.logger.test.legacyAndNeutral", "WARN" );
		System.setProperty( "er.extensions.logging.level.test.legacyAndNeutral", "TRACE" );
		_backend.configure();
		assertEquals( Level.WARN, logger( "test.legacy" ).getLevel() );
		assertEquals( Level.TRACE, logger( "test.legacyAndNeutral" ).getLevel() );
	}

	@Test
	public void logbacksOwnConfigurationWinsWhereItNamesALoggerAndTakesOverTheOutput() throws IOException {
		System.setProperty( "er.extensions.logging.level.test.both", "DEBUG" );
		System.setProperty( "er.extensions.logging.level.test.neutral", "DEBUG" );
		System.setProperty( "logback.configurationFile", nativeConfiguration( "<logger name=\"test.both\" level=\"ERROR\" />" ).toString() );
		_backend.configure();

		assertEquals( Level.ERROR, logger( "test.both" ).getLevel() );
		assertEquals( Level.DEBUG, logger( "test.neutral" ).getLevel(), "Not named by logback's configuration, so the neutral level stands" );
		assertNull( logger( Logger.ROOT_LOGGER_NAME ).getAppender( "console" ), "logback's configuration gives the root logger output of its own" );
		assertNotNull( logger( Logger.ROOT_LOGGER_NAME ).getAppender( "native" ) );
	}

	@Test
	public void aLevelSetOnTheRunningInstanceWinsOverEverything() throws IOException {
		if( ERXConfigurationManager.current() == null ) {
			ERXConfigurationManager.compose( new String[0] );
		}

		System.setProperty( "logback.configurationFile", nativeConfiguration( "<logger name=\"test.instance\" level=\"ERROR\" />" ).toString() );
		ERXConfigurationManager.setProperty( "er.extensions.logging.level.test.instance", "TRACE" );

		try {
			_backend.configure();
			assertEquals( Level.TRACE, logger( "test.instance" ).getLevel() );
		}
		finally {
			ERXConfigurationManager.unsetProperty( "er.extensions.logging.level.test.instance" );
		}

		_backend.configure();
		assertEquals( Level.ERROR, logger( "test.instance" ).getLevel(), "Unset, logback's own configuration decides again" );
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
	public void webObjectsCallsToTheLog4jApiReachLogback() {
		System.setProperty( "er.extensions.logging.level.test.neutral", "DEBUG" );
		_backend.installCapture();
		_backend.configure();

		final org.apache.log4j.Logger log4j = org.apache.log4j.Logger.getLogger( "test.neutral" );
		assertTrue( log4j.isDebugEnabled() );
		log4j.debug( "THROUGH-LOG4J-API" );
		assertEquals( 1, _backend.capturedLines( "THROUGH-LOG4J-API", 0 ).size() );
	}
}
