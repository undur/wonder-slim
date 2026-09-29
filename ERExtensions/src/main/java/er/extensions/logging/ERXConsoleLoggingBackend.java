package er.extensions.logging;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Logging to the console without a logging module: when neither ERLoggingLogback nor ERLoggingReload4j (nor any other
 * {@link ERXLoggingBackend}) is on the classpath, slf4j logs through this, rather than discarding everything.
 *
 * Levels come from the same layers as with logback: Project Wonder style {@code log4j.logger.*} levels, then
 * {@code er.logging.level.*}, then those set on the running instance. Lines are laid out by
 * {@code er.logging.pattern} (see {@link ERXConsoleLayout}) and written to {@code System.out} as it is at the time, so
 * they follow WebObjects' redirect to the {@code WOOutputPath} file. For files, appenders and the rest, add a logging
 * module.
 *
 * Not listed in {@code META-INF/services}, so slf4j never finds it on its own and never has two providers to choose
 * between: {@link #activateIfNoBackend()} names it as slf4j's provider when there's no other backend, from
 * {@code ERXApplication}'s static initializer.
 *
 * A program that doesn't start through {@code ERXApplication} (a stand-alone tool, a batch job, unit tests) names it
 * itself, most reliably as a JVM option, since slf4j chooses its provider when the first logger is created, often in a
 * static field before {@code main()} runs:
 *
 * <pre>
 * -Dslf4j.provider=er.extensions.logging.ERXConsoleServiceProvider
 * </pre>
 *
 * It then configures itself from the system properties, so {@code -Der.logging.level.<logger>=DEBUG} and
 * {@code er.logging.pattern} work there too. A program that creates no logger before its first line can call
 * {@link #activateIfNoBackend()} instead.
 */
public final class ERXConsoleLoggingBackend implements ERXLoggingBackend {

	/**
	 * The one instance, shared with the slf4j provider
	 */
	static final ERXConsoleLoggingBackend INSTANCE = new ERXConsoleLoggingBackend();

	/**
	 * slf4j's property naming its provider, and the one setting how much slf4j says about itself
	 */
	private static final String PROVIDER_PROPERTY = "slf4j.provider";
	private static final String VERBOSITY_PROPERTY = "slf4j.internal.verbosity";

	/**
	 * A level that logs nothing
	 */
	static final int OFF = Integer.MAX_VALUE;

	private static final Map<String, Integer> LEVEL_VALUES = Map.of( "TRACE", 0, "DEBUG", 10, "INFO", 20, "WARN", 30, "ERROR", 40, "OFF", OFF );

	/**
	 * Most recent lines kept by capture, so memory stays flat
	 */
	private static final int CAPTURE_LINES = 2000;

	/**
	 * The levels set, by logger name ({@link ERXLoggingConfiguration#ROOT} for the root logger)
	 */
	private volatile Map<String, Integer> _levels = Map.of( ERXLoggingConfiguration.ROOT, 20 );

	/**
	 * Increased on every configuration, so loggers know to look their level up again
	 */
	private volatile int _generation;

	private volatile ERXConsoleLayout _layout = ERXConsoleLayout.of( ERXLoggingConfiguration.DEFAULT_PATTERN );

	/**
	 * Recent lines, null until capture is installed
	 */
	private volatile Deque<String> _captured;

	private ERXConsoleLoggingBackend() {}

	/**
	 * Names this as slf4j's provider, if no other logging backend is on the classpath and nothing else names one. Must
	 * run before anything creates an slf4j logger: {@code ERXApplication}'s static initializer invokes it, which the
	 * JVM runs before {@code main()}. A program that doesn't start through {@code ERXApplication} can invoke it first
	 * thing, as long as nothing has created a logger before (a static logger field in its main class has).
	 */
	public static void activateIfNoBackend() {
		if( System.getProperty( PROVIDER_PROPERTY ) != null ) {
			return;
		}

		if( ServiceLoader.load( ERXLoggingBackend.class ).stream().findAny().isPresent() ) {
			return;
		}

		System.setProperty( PROVIDER_PROPERTY, ERXConsoleServiceProvider.class.getName() );

		// slf4j says which provider it loads when one is named: once is enough, in this class's documentation
		if( System.getProperty( VERBOSITY_PROPERTY ) == null ) {
			System.setProperty( VERBOSITY_PROPERTY, "WARN" );
		}
	}

	/**
	 * @return true if slf4j logs through this
	 */
	static boolean isActive() {
		final ILoggerFactory factory = LoggerFactory.getILoggerFactory();
		return factory instanceof ERXConsoleServiceProvider.Factory;
	}

	@Override
	public String name() {
		return "console";
	}

	@Override
	public void configureDefault() {
		_levels = Map.of( ERXLoggingConfiguration.ROOT, LEVEL_VALUES.get( "INFO" ) );
		_layout = ERXConsoleLayout.of( ERXLoggingConfiguration.DEFAULT_PATTERN );
		_generation++;
	}

	/**
	 * Configures from the system properties as they are when slf4j initializes the provider, for a program that doesn't
	 * start through {@code ERXApplication}, where nothing else configures logging: the legacy and neutral levels, and
	 * the pattern. Doesn't consult {@code ERXConfigurationManager}, whose own logger would be created while slf4j is
	 * still initializing; nothing is set on the running instance yet anyway.
	 */
	void configureInitially() {
		final Properties properties = System.getProperties();
		final Map<String, Integer> levels = new HashMap<>();
		levels.put( ERXLoggingConfiguration.ROOT, LEVEL_VALUES.get( "INFO" ) );
		ERXLoggingConfiguration.legacyLevels( properties ).forEach( ( logger, level ) -> levels.put( logger, LEVEL_VALUES.get( level ) ) );
		ERXLoggingConfiguration.allLevels( properties ).forEach( ( logger, level ) -> levels.put( logger, LEVEL_VALUES.get( level ) ) );

		_levels = Map.copyOf( levels );
		_layout = ERXConsoleLayout.of( ERXLoggingConfiguration.pattern( properties ) );
		_generation++;
	}

	@Override
	public void configure() {
		final Properties properties = System.getProperties();
		final Map<String, Integer> levels = new HashMap<>();
		levels.put( ERXLoggingConfiguration.ROOT, LEVEL_VALUES.get( "INFO" ) );

		// The layers, lowest first: the legacy levels and the neutral ones, then those set on the running instance
		ERXLoggingConfiguration.belowNativeConfiguration( properties, true ).forEach( ( logger, level ) -> levels.put( logger, LEVEL_VALUES.get( level ) ) );
		ERXLoggingConfiguration.levels( properties, true ).forEach( ( logger, level ) -> levels.put( logger, LEVEL_VALUES.get( level ) ) );

		_levels = Map.copyOf( levels );
		_layout = ERXConsoleLayout.of( ERXLoggingConfiguration.pattern( properties ) );
		_generation++;
	}

	/**
	 * Nothing to do: lines are written to whatever {@code System.out} is at the time
	 */
	@Override
	public void reattachConsole() {}

	@Override
	public synchronized boolean installCapture() {
		if( _captured == null ) {
			_captured = new ArrayDeque<>( CAPTURE_LINES );
		}

		return true;
	}

	@Override
	public List<String> capturedLines( final String contains, final int tail ) {
		final Deque<String> captured = _captured;

		if( captured == null ) {
			return List.of();
		}

		final List<String> lines;

		synchronized( captured ) {
			lines = new ArrayList<>( captured );
		}

		final List<String> matching = contains == null || contains.isEmpty() ? lines : lines.stream().filter( line -> line.contains( contains ) ).toList();
		return tail <= 0 || matching.size() <= tail ? matching : matching.subList( matching.size() - tail, matching.size() );
	}

	@Override
	public boolean readsLog4jConfiguration() {
		return false;
	}

	@Override
	public String nativeConfiguration() {
		return null;
	}

	@Override
	public Map<String, String> nativeLevels() {
		return Map.of();
	}

	@Override
	public List<LoggerLevel> loggers() {
		final List<LoggerLevel> loggers = new ArrayList<>();
		loggers.add( new LoggerLevel( ERXLoggingConfiguration.ROOT, levelName( _levels.get( ERXLoggingConfiguration.ROOT ) ), levelName( effectiveLevel( ERXLoggingConfiguration.ROOT ) ) ) );

		for( final String name : ERXConsoleServiceProvider.FACTORY.names() ) {
			if( !name.equals( ERXLoggingConfiguration.ROOT ) ) {
				loggers.add( new LoggerLevel( name, levelName( _levels.get( name ) ), levelName( effectiveLevel( name ) ) ) );
			}
		}

		loggers.subList( 1, loggers.size() ).sort( Comparator.comparing( LoggerLevel::name ) );
		return loggers;
	}

	int generation() {
		return _generation;
	}

	/**
	 * @return The level the named logger logs at: its own, or that of the nearest logger above it, the root's last
	 */
	int effectiveLevel( final String name ) {
		final Map<String, Integer> levels = _levels;
		String current = name;

		while( true ) {
			final Integer level = levels.get( current );

			if( level != null ) {
				return level;
			}

			final int dot = current.lastIndexOf( '.' );

			if( dot < 0 ) {
				return levels.getOrDefault( ERXLoggingConfiguration.ROOT, LEVEL_VALUES.get( "INFO" ) );
			}

			current = current.substring( 0, dot );
		}
	}

	/**
	 * Writes a line to the console, and keeps it if capture is installed
	 */
	void write( final String level, final String logger, final String message, final Throwable throwable ) {
		final String line = _layout.format( new ERXConsoleLayout.Event( level, logger, message, Thread.currentThread().getName(), LocalDateTime.now() ), throwable );
		final PrintStream out = System.out;

		synchronized( this ) {
			out.print( line );
			out.flush();
		}

		final Deque<String> captured = _captured;

		if( captured != null ) {
			synchronized( captured ) {
				if( captured.size() == CAPTURE_LINES ) {
					captured.removeFirst();
				}

				captured.addLast( line.stripTrailing() );
			}
		}
	}

	private static String levelName( final Integer level ) {
		if( level == null ) {
			return null;
		}

		for( final Map.Entry<String, Integer> entry : LEVEL_VALUES.entrySet() ) {
			if( entry.getValue().equals( level ) ) {
				return entry.getKey();
			}
		}

		return null;
	}
}
