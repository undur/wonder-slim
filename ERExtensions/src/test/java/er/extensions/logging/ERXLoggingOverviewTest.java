package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.logging.ERXLoggingBackend.LoggerLevel;
import er.extensions.logging.ERXLoggingOverview.Row;
import er.extensions.logging.ERXLoggingOverview.Setting;

public class ERXLoggingOverviewTest {

	private static Properties properties( final String... keysAndValues ) {
		final Properties properties = new Properties();

		for( int i = 0; i < keysAndValues.length; i += 2 ) {
			properties.setProperty( keysAndValues[i], keysAndValues[i + 1] );
		}

		return properties;
	}

	/**
	 * Every key comes from the application's Properties, except those named, set on the running instance
	 */
	private static Function<String, String> sourceOf( final String... setOnInstance ) {
		return key -> List.of( setOnInstance ).contains( key ) ? ERXConfigurationManager.INSTANCE_SOURCE_NAME : "App.app";
	}

	@Test
	public void eachLoggerIsCreditedToItsHighestLayerAndListsWhatItOverrides() {
		final Properties properties = properties(
				"log4j.logger.x", "WARN, A1",
				"er.logging.level.x", "DEBUG",
				"er.logging.level.root", "INFO",
				"er.logging.level.y", "TRACE" );

		final List<Row> rows = ERXLoggingOverview.rows( properties, false, "file:/app/Resources/logback.xml", Map.of( "x", "ERROR" ), List.of( new LoggerLevel( "x", "ERROR", "ERROR" ) ), sourceOf( "er.logging.level.y" ) );

		assertEquals( List.of( "root", "x", "y" ), rows.stream().map( Row::logger ).toList() );

		final Row x = rows.get( 1 );
		assertEquals( new Setting( "logback.xml", null, "ERROR" ), x.setBy() );
		assertEquals( List.of( new Setting( "er.logging.level.x", "App.app", "DEBUG" ), new Setting( "log4j.logger.x", "App.app", "WARN" ) ), x.overrides() );
		assertFalse( x.isSetOnInstance() );

		final Row y = rows.get( 2 );
		assertTrue( y.isSetOnInstance() );
		assertEquals( "TRACE", y.level() );
	}

	@Test
	public void theInstanceLayerWinsOverTheBackendsOwnConfiguration() {
		final Properties properties = properties( "er.logging.level.x", "TRACE" );
		final List<Row> rows = ERXLoggingOverview.rows( properties, false, "file:/logback.xml", Map.of( "x", "ERROR" ), List.of(), sourceOf( "er.logging.level.x" ) );

		assertEquals( 1, rows.size() );
		assertTrue( rows.get( 0 ).isSetOnInstance() );
		assertEquals( List.of( new Setting( "logback.xml", null, "ERROR" ) ), rows.get( 0 ).overrides() );
	}

	@Test
	public void aBackendReadingLog4jConfigurationHasItsLevelsAsItsOwnOnly() {
		final Properties properties = properties( "log4j.rootLogger", "INFO, A1", "log4j.logger.x", "WARN", "er.logging.level.x", "DEBUG" );
		final List<Row> rows = ERXLoggingOverview.rows( properties, true, "log4j.* properties", ERXLoggingConfiguration.legacyLevels( properties ), List.of(), sourceOf() );

		assertEquals( new Setting( "log4j.rootLogger", "App.app", "INFO" ), rows.get( 0 ).setBy() );
		assertEquals( new Setting( "log4j.logger.x", "App.app", "WARN" ), rows.get( 1 ).setBy() );
		assertEquals( List.of( new Setting( "er.logging.level.x", "App.app", "DEBUG" ) ), rows.get( 1 ).overrides() );
	}

	@Test
	public void theBackendsConfigurationIsNamedByItsFile() {
		assertEquals( "logback.xml", ERXLoggingOverview.fileName( "file:/app/Resources/logback.xml" ) );
		assertEquals( "logback.xml", ERXLoggingOverview.fileName( "jar:file:/app/lib/app.jar!/logback.xml" ) );
		assertEquals( "logback-prod.xml", ERXLoggingOverview.fileName( "/etc/app/logback-prod.xml" ) );
	}
}
