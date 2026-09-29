package er.extensions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.foundation.ERXConfigurationManager.Source;

/**
 * The logging configuration every backend understands, read from the properties. A backend applies it in layers, lowest
 * first (see {@link ERXLoggingBackend#configure()}):
 *
 * <ol>
 * <li>{@link #legacyLevels(Properties)}: the levels in Project Wonder style log4j configuration ({@code log4j.logger.X},
 * {@code log4j.rootLogger}, {@code log4j.rootCategory}), for a backend other than log4j's, which reads those itself</li>
 * <li>{@link #levels(Properties, boolean) levels( properties, false )}: {@code er.extensions.logging.level.<logger>} and
 * {@code er.extensions.logging.level.root}, set anywhere but on the running instance</li>
 * <li>the backend's own configuration ({@code log4j.*} for reload4j, {@code logback.xml} for logback), which can
 * express what these can't, and wins where both name a logger</li>
 * <li>{@link #levels(Properties, boolean) levels( properties, true )}: the same keys set on the running instance
 * ({@code ERXConfigurationManager.setProperty()}, the admin console), which win over everything</li>
 * </ol>
 *
 * {@link #pattern(Properties)} ({@code er.extensions.logging.pattern}) is the layout of the console output a backend
 * sets up when its own configuration sets up none.
 */
public final class ERXLoggingConfiguration {

	/**
	 * The name the root logger goes by in the level keys
	 */
	public static final String ROOT = "root";

	/**
	 * The prefix of the level keys: {@code er.extensions.logging.level.<logger>}
	 */
	public static final String LEVEL_PREFIX = ERXP.LOGGING_LEVEL_PREFIX.id();

	/**
	 * The key of the console output's layout
	 */
	public static final String PATTERN = ERXP.LOGGING_PATTERN.id();

	/**
	 * The console output's layout when {@link #PATTERN} isn't set: ERExtensions' Properties sets the same, so this only
	 * applies without it (in a unit test, say)
	 */
	public static final String DEFAULT_PATTERN = "%d{MMM dd HH:mm:ss} %-5p %c - %m%n";

	/**
	 * The levels a level key can name, as every backend names them
	 */
	private static final List<String> LEVELS = List.of( "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF" );

	private ERXLoggingConfiguration() {}

	/**
	 * @return The levels set by {@code er.extensions.logging.level.*} keys, by logger name ({@link #ROOT} for the root
	 *         logger), upper-cased: those set on the running instance, or those set anywhere else. A key naming no level
	 *         the backends know is left out.
	 */
	public static Map<String, String> levels( final Properties properties, final boolean setOnRunningInstance ) {
		final Map<String, String> levels = new TreeMap<>();

		for( final String key : properties.stringPropertyNames() ) {
			if( key.startsWith( LEVEL_PREFIX ) && key.length() > LEVEL_PREFIX.length() && isSetOnRunningInstance( key ) == setOnRunningInstance ) {
				final String level = level( properties.getProperty( key ) );

				if( level != null ) {
					levels.put( key.substring( LEVEL_PREFIX.length() ), level );
				}
			}
		}

		return levels;
	}

	/**
	 * @return The levels in Project Wonder style log4j configuration, by logger name ({@link #ROOT} for the root logger):
	 *         {@code log4j.logger.X=LEVEL[, appenders]}, and {@code log4j.rootLogger} or {@code log4j.rootCategory}
	 */
	public static Map<String, String> legacyLevels( final Properties properties ) {
		final Map<String, String> levels = new TreeMap<>();

		for( final String key : properties.stringPropertyNames() ) {
			final String logger;

			if( key.startsWith( "log4j.logger." ) ) {
				logger = key.substring( "log4j.logger.".length() );
			}
			else if( key.equals( "log4j.rootLogger" ) || key.equals( "log4j.rootCategory" ) ) {
				logger = ROOT;
			}
			else {
				continue;
			}

			// "INFO, A1": the level, then the appenders
			final String level = level( properties.getProperty( key ).split( "," )[0] );

			if( level != null ) {
				levels.put( logger, level );
			}
		}

		return levels;
	}

	/**
	 * @return The keys of Project Wonder style log4j configuration that set up output rather than levels (appenders,
	 *         layouts and the like), which a backend other than log4j's can't honour
	 */
	public static List<String> legacyOutputKeys( final Properties properties ) {
		return properties
				.stringPropertyNames()
				.stream()
				.filter( key -> key.startsWith( "log4j." ) && !key.startsWith( "log4j.logger." ) && !key.equals( "log4j.rootLogger" ) && !key.equals( "log4j.rootCategory" ) )
				.sorted()
				.toList();
	}

	/**
	 * @return The layout of the console output ({@link #PATTERN}), {@link #DEFAULT_PATTERN} if it isn't set
	 */
	public static String pattern( final Properties properties ) {
		final String pattern = properties.getProperty( PATTERN );
		return pattern == null || pattern.isBlank() ? DEFAULT_PATTERN : pattern;
	}

	/**
	 * @return The level named, upper-cased ({@code WARNING} as {@code WARN}), null if it names none
	 */
	static String level( final String value ) {

		if( value == null ) {
			return null;
		}

		final String level = value.trim().toUpperCase();

		if( level.equals( "WARNING" ) ) {
			return "WARN";
		}

		if( level.equals( "FATAL" ) ) {
			return "ERROR";
		}

		return LEVELS.contains( level ) ? level : null;
	}

	/**
	 * @return true if the configuration says the key was set on the running instance
	 */
	private static boolean isSetOnRunningInstance( final String key ) {
		final ERXConfigurationManager configuration = ERXConfigurationManager.current();

		if( configuration == null ) {
			return false;
		}

		final Source origin = configuration.origin( key );
		return origin != null && origin.isInstanceSource();
	}

	/**
	 * @return The levels that go beneath the backend's own configuration, merged, lowest first: the legacy levels (unless
	 *         the backend reads {@code log4j.*} itself) and the neutral ones not set on the running instance
	 */
	public static Map<String, String> belowNativeConfiguration( final Properties properties, final boolean includeLegacy ) {
		final Map<String, String> levels = new LinkedHashMap<>();

		if( includeLegacy ) {
			levels.putAll( legacyLevels( properties ) );
		}

		levels.putAll( levels( properties, false ) );
		return levels;
	}
}
