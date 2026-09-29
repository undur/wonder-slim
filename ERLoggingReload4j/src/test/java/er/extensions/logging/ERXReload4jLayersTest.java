package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import er.extensions.foundation.ERXConfigurationManager;

/**
 * The layers of the logging configuration (see ERXLoggingConfiguration), as reload4j applies them: the neutral levels,
 * then log4j.* (which wins where both name a logger), then the neutral levels set on the running instance
 */
public class ERXReload4jLayersTest {

	private static final List<String> KEYS = List.of( "er.logging.level.test.neutral", "er.logging.level.test.both", "log4j.logger.test.both" );

	@AfterEach
	public void clean() {
		KEYS.forEach( System::clearProperty );
		ERXLogger.configureLoggingWithSystemProperties();
	}

	@Test
	public void aNeutralLevelIsSet() {
		System.setProperty( "er.logging.level.test.neutral", "DEBUG" );
		ERXLogger.configureLoggingWithSystemProperties();
		assertEquals( Level.DEBUG, Logger.getLogger( "test.neutral" ).getLevel() );
	}

	@Test
	public void log4jConfigurationWinsWhereItNamesALogger() {
		System.setProperty( "er.logging.level.test.both", "DEBUG" );
		System.setProperty( "log4j.logger.test.both", "ERROR" );
		ERXLogger.configureLoggingWithSystemProperties();
		assertEquals( Level.ERROR, Logger.getLogger( "test.both" ).getLevel() );
	}

	@Test
	public void aChangedLoggingPropertyReconfiguresLoggingByItself() {
		if( ERXConfigurationManager.current() == null ) {
			ERXConfigurationManager.compose( new String[0] );
		}

		ERXLoggingSupport.configureAndFollowChanges();
		ERXConfigurationManager.setProperty( "er.logging.level.test.followed", "TRACE" );

		try {
			assertEquals( Level.TRACE, Logger.getLogger( "test.followed" ).getLevel(), "No explicit reconfiguration: the listener did it" );
		}
		finally {
			ERXConfigurationManager.unsetProperty( "er.logging.level.test.followed" );
		}

		assertEquals( null, Logger.getLogger( "test.followed" ).getLevel(), "Unset, and reconfigured again" );
	}

	@Test
	public void aLevelSetOnTheRunningInstanceWinsOverEverything() {
		if( ERXConfigurationManager.current() == null ) {
			ERXConfigurationManager.compose( new String[0] );
		}

		System.setProperty( "log4j.logger.test.both", "ERROR" );
		ERXConfigurationManager.setProperty( "er.logging.level.test.both", "TRACE" );

		try {
			ERXLogger.configureLoggingWithSystemProperties();
			assertEquals( Level.TRACE, Logger.getLogger( "test.both" ).getLevel() );
		}
		finally {
			ERXConfigurationManager.unsetProperty( "er.logging.level.test.both" );
		}
	}
}
