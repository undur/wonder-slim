package er.extensions.logging;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Function;

import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.logging.ERXLoggingBackend.LoggerLevel;

/**
 * Where each logger's level comes from: every logger with a level set in any of the layers {@link ERXLoggingConfiguration}
 * describes, with the setting in effect and the settings it overrides. What the control panel's Logging page shows.
 */
public final class ERXLoggingOverview {

	/**
	 * A level set for a logger in one layer.
	 *
	 * @param setting What sets it: a property key, or the backend's own configuration file
	 * @param source The configuration source the property came from ({@link ERXConfigurationManager#INSTANCE_SOURCE_NAME}
	 *        if set on the running instance), null if there's none to name
	 * @param level The level it sets
	 */
	public record Setting( String setting, String source, String level ) {

		public boolean isSetOnInstance() {
			return ERXConfigurationManager.INSTANCE_SOURCE_NAME.equals( source );
		}

		/**
		 * @return The setting, its source and level, as one line
		 */
		public String description() {
			return level + " · " + setting + (source == null ? "" : " · " + source);
		}
	}

	/**
	 * A logger with a level set in at least one layer.
	 *
	 * @param logger The logger's name ({@link ERXLoggingConfiguration#ROOT} for the root logger)
	 * @param level The level the backend has set on it
	 * @param setBy The setting in effect: the one in the highest layer
	 * @param overrides The settings in lower layers, highest first
	 */
	public record Row( String logger, String level, Setting setBy, List<Setting> overrides ) {

		/**
		 * @return true if the level in effect was set on the running instance, so it can be unset
		 */
		public boolean isSetOnInstance() {
			return setBy.isSetOnInstance() && setBy.setting().startsWith( ERXLoggingConfiguration.LEVEL_PREFIX );
		}

		public boolean hasOverrides() {
			return !overrides.isEmpty();
		}

		public String overridesDescription() {
			return String.join( "; ", overrides.stream().map( Setting::description ).toList() );
		}
	}

	private ERXLoggingOverview() {}

	/**
	 * @return The rows for the current configuration and backend
	 */
	public static List<Row> rows( final ERXLoggingBackend backend ) {
		final ERXConfigurationManager configuration = ERXConfigurationManager.current();
		final Function<String, String> sourceOf = key -> {
			final var origin = configuration == null ? null : configuration.origin( key );
			return origin == null ? null : origin.name();
		};

		return rows( System.getProperties(), backend.readsLog4jConfiguration(), backend.nativeConfiguration(), backend.nativeLevels(), backend.loggers(), sourceOf );
	}

	/**
	 * @param properties The configuration
	 * @param readsLog4jConfiguration True if the backend reads {@code log4j.*} itself, see {@link ERXLoggingBackend#readsLog4jConfiguration()}
	 * @param nativeConfiguration Where the backend's own configuration came from, null if none
	 * @param nativeLevels The levels the backend's own configuration set
	 * @param loggers The loggers the backend knows
	 * @param sourceOf The name of the source a property came from, null if none
	 *
	 * @return A row for each logger with a level set in any layer, the root logger first, then by name
	 */
	static List<Row> rows( final Properties properties, final boolean readsLog4jConfiguration, final String nativeConfiguration, final Map<String, String> nativeLevels, final List<LoggerLevel> loggers, final Function<String, String> sourceOf ) {

		// The settings for each logger, lowest layer first
		final Map<String, List<Setting>> settings = new TreeMap<>( ( a, b ) -> a.equals( b ) ? 0 : a.equals( ERXLoggingConfiguration.ROOT ) ? -1 : b.equals( ERXLoggingConfiguration.ROOT ) ? 1 : a.compareTo( b ) );

		// 1: Project Wonder style log4j levels, when the backend translates rather than reads them
		if( !readsLog4jConfiguration ) {
			ERXLoggingConfiguration.legacyLevels( properties ).forEach( ( logger, level ) -> {
				final String key = log4jKey( properties, logger );
				add( settings, logger, new Setting( key, sourceOf.apply( key ), level ) );
			} );
		}

		// 2 and 4: the neutral keys, those set on the running instance last
		final List<Setting> instanceSettings = new ArrayList<>();
		final List<String> instanceLoggers = new ArrayList<>();

		for( final String key : properties.stringPropertyNames().stream().sorted().toList() ) {
			if( key.startsWith( ERXLoggingConfiguration.LEVEL_PREFIX ) && key.length() > ERXLoggingConfiguration.LEVEL_PREFIX.length() ) {
				final String level = ERXLoggingConfiguration.level( properties.getProperty( key ) );

				if( level != null ) {
					final String logger = key.substring( ERXLoggingConfiguration.LEVEL_PREFIX.length() );
					final Setting setting = new Setting( key, sourceOf.apply( key ), level );

					if( setting.isSetOnInstance() ) {
						instanceLoggers.add( logger );
						instanceSettings.add( setting );
					}
					else {
						add( settings, logger, setting );
					}
				}
			}
		}

		// 3: the backend's own configuration
		nativeLevels.forEach( ( logger, level ) -> {
			if( readsLog4jConfiguration ) {
				final String key = log4jKey( properties, logger );
				add( settings, logger, new Setting( key, sourceOf.apply( key ), level ) );
			}
			else {
				add( settings, logger, new Setting( fileName( nativeConfiguration ), null, level ) );
			}
		} );

		for( int i = 0; i < instanceSettings.size(); i++ ) {
			add( settings, instanceLoggers.get( i ), instanceSettings.get( i ) );
		}

		final Map<String, String> levels = new TreeMap<>();

		for( final LoggerLevel logger : loggers ) {
			if( logger.level() != null ) {
				levels.put( logger.name(), logger.level() );
			}
		}

		final List<Row> rows = new ArrayList<>();

		settings.forEach( ( logger, loggerSettings ) -> {
			final Setting setBy = loggerSettings.getLast();
			final List<Setting> overrides = new ArrayList<>( loggerSettings.subList( 0, loggerSettings.size() - 1 ) ).reversed();
			rows.add( new Row( logger, levels.getOrDefault( logger, setBy.level() ), setBy, overrides ) );
		} );

		return rows;
	}

	private static void add( final Map<String, List<Setting>> settings, final String logger, final Setting setting ) {
		settings.computeIfAbsent( logger, l -> new ArrayList<>() ).add( setting );
	}

	/**
	 * @return The log4j key setting the given logger's level
	 */
	private static String log4jKey( final Properties properties, final String logger ) {
		if( ERXLoggingConfiguration.ROOT.equals( logger ) ) {
			return properties.getProperty( "log4j.rootLogger" ) != null ? "log4j.rootLogger" : "log4j.rootCategory";
		}

		return "log4j.logger." + logger;
	}

	/**
	 * @return The last part of a configuration's location ({@code logback.xml} for {@code file:/…/logback.xml})
	 */
	static String fileName( final String location ) {
		if( location == null ) {
			return "the backend's own configuration";
		}

		try {
			final String path = URI.create( location ).getPath();

			if( path != null && !path.isEmpty() ) {
				return path.substring( path.lastIndexOf( '/' ) + 1 );
			}
		}
		catch( IllegalArgumentException e ) {
			// Not a URI: use it as it is
		}

		final int slash = location.lastIndexOf( '/' );
		return slash < 0 ? location : location.substring( slash + 1 );
	}
}
