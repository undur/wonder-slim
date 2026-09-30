package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * The layout writes dates in the JVM's default locale, which the tests set, so month names are English wherever they run
 */
public class ERXConsoleLoggingBackendTest {

	private static final List<String> KEYS = List.of( "er.logging.level.test.console", "er.logging.level.test.console.quiet", "log4j.logger.test.legacy", "er.logging.pattern" );

	private static final ERXConsoleLoggingBackend backend = ERXConsoleLoggingBackend.INSTANCE;

	private Locale _defaultLocale;

	@BeforeEach
	public void useEnglish() {
		_defaultLocale = Locale.getDefault();
		Locale.setDefault( Locale.ENGLISH );
	}

	@AfterEach
	public void clean() {
		KEYS.forEach( System::clearProperty );
		backend.configure();
		Locale.setDefault( _defaultLocale );
	}

	private static Logger logger( final String name ) {
		return ERXConsoleServiceProvider.FACTORY.getLogger( name );
	}

	@Test
	public void aLoggerLogsAtTheLevelOfTheNearestLoggerAboveIt() {
		System.setProperty( "er.logging.level.test.console", "DEBUG" );
		System.setProperty( "er.logging.level.test.console.quiet", "ERROR" );
		backend.configure();

		assertTrue( logger( "test.console.some.Class" ).isDebugEnabled() );
		assertFalse( logger( "test.console.some.Class" ).isTraceEnabled() );
		assertFalse( logger( "test.console.quiet.Class" ).isWarnEnabled() );
		assertTrue( logger( "test.console.quiet.Class" ).isErrorEnabled() );
		assertFalse( logger( "test.elsewhere" ).isDebugEnabled(), "The root logger's INFO" );
	}

	@Test
	public void aLoggerFollowsAReconfiguration() {
		final Logger logger = logger( "test.console.follows" );
		assertFalse( logger.isDebugEnabled() );

		System.setProperty( "er.logging.level.test.console", "DEBUG" );
		backend.configure();
		assertTrue( logger.isDebugEnabled() );
	}

	@Test
	public void legacyLog4jLevelsAreUsed() {
		System.setProperty( "log4j.logger.test.legacy", "WARN, A1" );
		backend.configure();
		assertFalse( logger( "test.legacy.Class" ).isInfoEnabled() );
		assertTrue( logger( "test.legacy.Class" ).isWarnEnabled() );
	}

	@Test
	public void linesAreLaidOutByThePatternAndCaptured() {
		System.setProperty( "er.logging.pattern", "[%-5p] %c: %m%n" );
		backend.configure();
		backend.installCapture();

		logger( "test.console.layout" ).info( "Hello {}", "there" );
		assertEquals( List.of( "[INFO ] test.console.layout: Hello there" ), backend.capturedLines( "test.console.layout", 0 ) );

		logger( "test.console.layout" ).warn( "Failed", new IllegalStateException( "EXPECTED-IN-TEST" ) );
		assertTrue( backend.capturedLines( "EXPECTED-IN-TEST", 0 ).getFirst().startsWith( "[WARN ] test.console.layout: Failed" ) );
	}

	@Test
	public void theLayoutKnowsTheCommonConversions() {
		final ERXConsoleLayout.Event event = new ERXConsoleLayout.Event( "INFO", "a.b.C", "message", "main", LocalDateTime.of( 2026, 9, 29, 14, 5, 7 ) );

		assertEquals( "Sep 29 14:05:07 INFO  a.b.C - message", ERXConsoleLayout.of( "%d{MMM dd HH:mm:ss} %-5p %c - %m" ).format( event, null ) );
		assertEquals( " INFO|main|100%|message", ERXConsoleLayout.of( "%5level|%thread|100%%|%msg" ).format( event, null ) );
		assertEquals( "(a.b.C) %X{user} message", ERXConsoleLayout.of( "(%logger) %X{user} %m" ).format( event, null ), "Parentheses stay, an unknown conversion is written as it stands" );
	}

	@Test
	public void theBackendListsItsLoggers() {
		System.setProperty( "er.logging.level.test.console", "DEBUG" );
		backend.configure();
		logger( "test.console.listed" );

		assertEquals( "root", backend.loggers().getFirst().name() );
		assertTrue( backend.loggers().stream().anyMatch( l -> l.name().equals( "test.console.listed" ) && l.level() == null && "DEBUG".equals( l.effectiveLevel() ) ) );
	}
}
