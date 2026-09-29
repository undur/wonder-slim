package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

public class ERXLoggingConfigurationTest {

	private static Properties properties( final String... keysAndValues ) {
		final Properties properties = new Properties();

		for( int i = 0; i < keysAndValues.length; i += 2 ) {
			properties.setProperty( keysAndValues[i], keysAndValues[i + 1] );
		}

		return properties;
	}

	@Test
	public void neutralLevelsAreReadByLoggerNameAndNormalized() {
		final Properties properties = properties(
				"er.extensions.logging.level.root", "info",
				"er.extensions.logging.level.er.extensions", " Debug ",
				"er.extensions.logging.level.a", "warning",
				"er.extensions.logging.level.b", "fatal",
				"er.extensions.logging.level.c", "loud",
				"er.extensions.logging.level.", "DEBUG",
				"unrelated", "DEBUG" );

		assertEquals( Map.of( "root", "INFO", "er.extensions", "DEBUG", "a", "WARN", "b", "ERROR" ), ERXLoggingConfiguration.levels( properties, false ) );
	}

	@Test
	public void legacyLevelsAreTheLevelPartOfLog4jConfiguration() {
		final Properties properties = properties(
				"log4j.rootCategory", "INFO, A1",
				"log4j.logger.er.extensions", "DEBUG",
				"log4j.logger.er.ajax", "WARN, A2",
				"log4j.appender.A1", "org.apache.log4j.ConsoleAppender",
				"log4j.appender.A1.layout.ConversionPattern", "%m%n" );

		assertEquals( Map.of( "root", "INFO", "er.extensions", "DEBUG", "er.ajax", "WARN" ), ERXLoggingConfiguration.legacyLevels( properties ) );
		assertEquals( List.of( "log4j.appender.A1", "log4j.appender.A1.layout.ConversionPattern" ), ERXLoggingConfiguration.legacyOutputKeys( properties ) );
	}

	@Test
	public void neutralLevelsWinOverLegacyOnesBeneathTheBackendsOwn() {
		final Properties properties = properties( "log4j.logger.x", "WARN", "er.extensions.logging.level.x", "TRACE", "log4j.logger.y", "ERROR" );
		assertEquals( Map.of( "x", "TRACE", "y", "ERROR" ), ERXLoggingConfiguration.belowNativeConfiguration( properties, true ) );
		assertEquals( Map.of( "x", "TRACE" ), ERXLoggingConfiguration.belowNativeConfiguration( properties, false ) );
	}

	@Test
	public void thePatternDefaults() {
		assertEquals( ERXLoggingConfiguration.DEFAULT_PATTERN, ERXLoggingConfiguration.pattern( properties() ) );
		assertEquals( "%m%n", ERXLoggingConfiguration.pattern( properties( "er.extensions.logging.pattern", "%m%n" ) ) );
	}
}
