package er.extensions.logging.logback;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.JoranException;

import er.extensions.ERXLoggingBackend;
import er.extensions.ERXLoggingConfiguration;

/**
 * The logback logging backend, listed in META-INF/services/er.extensions.ERXLoggingBackend. Configures logback in the
 * layers {@link ERXLoggingConfiguration} describes:
 *
 * <ol>
 * <li>the levels in Project Wonder style log4j configuration ({@code log4j.logger.X}, {@code log4j.rootLogger}); the
 * rest of it (appenders, layouts) can't be translated, and is named in a warning</li>
 * <li>{@code er.extensions.logging.level.*}, and console output in the layout {@code er.extensions.logging.pattern}</li>
 * <li>logback's own configuration: the file {@code logback.configurationFile} names, or {@code logback.xml} on the
 * classpath. If it gives the root logger output of its own, the console output above stands aside.</li>
 * <li>{@code er.extensions.logging.level.*} set on the running instance</li>
 * </ol>
 *
 * logback's console output writes to whatever {@code System.out} is at the time, so it follows WebObjects' redirect to
 * the {@code WOOutputPath} file without being told.
 */
public class ERXLogbackLoggingBackend implements ERXLoggingBackend {

	private static final String CONSOLE_APPENDER = "console";

	private static final String CAPTURE_APPENDER = "capture";

	/**
	 * Set once the Project Wonder style log4j output configuration that can't be honoured has been named, so it's named once
	 */
	private boolean _warnedAboutLegacyOutput;

	/**
	 * Recent log output kept in memory, see {@link #installCapture()}, null until installed
	 */
	private volatile CaptureAppender _capture;

	private static LoggerContext context() {
		return (LoggerContext)LoggerFactory.getILoggerFactory();
	}

	private static Logger root() {
		return context().getLogger( org.slf4j.Logger.ROOT_LOGGER_NAME );
	}

	@Override
	public synchronized void configureDefault() {
		context().reset();
		root().setLevel( Level.INFO );
		root().addAppender( console( ERXLoggingConfiguration.DEFAULT_PATTERN ) );
		reattachCapture();
	}

	@Override
	public synchronized void configure() {
		final Properties properties = System.getProperties();
		final LoggerContext context = context();
		context.reset();

		final Logger root = root();
		root.setLevel( Level.INFO );
		root.addAppender( console( ERXLoggingConfiguration.pattern( properties ) ) );

		// 1 and 2: the legacy levels and the neutral ones
		setLevels( ERXLoggingConfiguration.belowNativeConfiguration( properties, true ) );
		warnAboutLegacyOutput( properties );

		// 3: logback's own configuration, which wins where it names a logger
		final URL nativeConfiguration = nativeConfiguration( properties );

		if( nativeConfiguration != null ) {
			final JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext( context );

			try {
				configurator.doConfigure( nativeConfiguration );
			}
			catch( JoranException e ) {
				root.error( "Unable to read logback's configuration from {}", nativeConfiguration, e );
			}

			if( hasOutputOtherThan( root, CONSOLE_APPENDER, CAPTURE_APPENDER ) ) {
				root.detachAppender( CONSOLE_APPENDER );
			}
		}

		// 4: the levels set on the running instance
		setLevels( ERXLoggingConfiguration.levels( properties, true ) );

		reattachCapture();
	}

	/**
	 * Nothing to do: logback's console output writes to whatever {@code System.out} is at the time
	 */
	@Override
	public void reattachConsole() {}

	@Override
	public synchronized boolean installCapture() {
		if( _capture == null ) {
			_capture = new CaptureAppender();
			_capture.setContext( context() );
			_capture.setName( CAPTURE_APPENDER );
			_capture.start();
		}

		reattachCapture();
		return true;
	}

	@Override
	public List<String> capturedLines( final String contains, final int tail ) {
		final CaptureAppender capture = _capture;
		return capture == null ? List.of() : capture.lines( contains, tail );
	}

	/**
	 * Adds the capture appender to the root logger again, since resetting the context removes it
	 */
	private void reattachCapture() {
		final CaptureAppender capture = _capture;

		if( capture != null && root().getAppender( CAPTURE_APPENDER ) == null ) {
			// Resetting the context stopped it as well as detaching it
			if( !capture.isStarted() ) {
				capture.start();
			}

			root().addAppender( capture );
		}
	}

	private static Appender<ILoggingEvent> console( final String pattern ) {
		final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext( context() );
		encoder.setPattern( pattern );
		encoder.start();

		final ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
		console.setContext( context() );
		console.setName( CONSOLE_APPENDER );
		console.setEncoder( encoder );
		console.start();
		return console;
	}

	/**
	 * Sets the given levels, by logger name ({@link ERXLoggingConfiguration#ROOT} for the root logger)
	 */
	private static void setLevels( final Map<String, String> levels ) {
		levels.forEach( ( name, level ) -> {
			final Logger logger = ERXLoggingConfiguration.ROOT.equals( name ) ? root() : context().getLogger( name );
			logger.setLevel( Level.toLevel( level ) );
		} );
	}

	/**
	 * @return logback's own configuration: the file or URL {@code logback.configurationFile} names, or {@code logback.xml}
	 *         on the classpath; null if there's none
	 */
	static URL nativeConfiguration( final Properties properties ) {
		final String configurationFile = properties.getProperty( "logback.configurationFile" );

		if( configurationFile != null && !configurationFile.isBlank() ) {
			final File file = new File( configurationFile );

			if( file.isFile() ) {
				try {
					return file.toURI().toURL();
				}
				catch( MalformedURLException e ) {
					throw new IllegalStateException( e );
				}
			}

			try {
				return URI.create( configurationFile ).toURL();
			}
			catch( IllegalArgumentException | MalformedURLException e ) {
				final URL resource = ERXLogbackLoggingBackend.class.getClassLoader().getResource( configurationFile );

				if( resource != null ) {
					return resource;
				}

				root().warn( "logback.configurationFile names '{}', which isn't a file, a URL or a resource", configurationFile );
				return null;
			}
		}

		return ERXLogbackLoggingBackend.class.getClassLoader().getResource( "logback.xml" );
	}

	/**
	 * @return true if the logger has an appender other than the named ones
	 */
	private static boolean hasOutputOtherThan( final Logger logger, final String... names ) {
		for( final Iterator<Appender<ILoggingEvent>> appenders = logger.iteratorForAppenders(); appenders.hasNext(); ) {
			if( !List.of( names ).contains( appenders.next().getName() ) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Names, once, the Project Wonder style log4j output configuration this backend can't honour
	 */
	private void warnAboutLegacyOutput( final Properties properties ) {
		final List<String> keys = ERXLoggingConfiguration.legacyOutputKeys( properties );

		if( !keys.isEmpty() && !_warnedAboutLegacyOutput ) {
			_warnedAboutLegacyOutput = true;
			root().warn( "Logging goes through logback, which doesn't read log4j's output configuration. Its levels are used, but these are ignored (use logback's own configuration, logback.xml, for output): {}", keys );
		}
	}

	/**
	 * Keeps the most recent lines of log output in memory
	 */
	private static final class CaptureAppender extends AppenderBase<ILoggingEvent> {

		/**
		 * Most recent lines kept, so memory stays flat
		 */
		private static final int MAX_LINES = 2000;

		private final Deque<String> _lines = new ArrayDeque<>( MAX_LINES );

		private final PatternLayout _layout = new PatternLayout();

		@Override
		public void start() {
			_layout.setContext( getContext() );
			_layout.setPattern( "%d{MMM dd HH:mm:ss} %-5p %c - %m" );
			_layout.start();
			super.start();
		}

		@Override
		protected void append( final ILoggingEvent event ) {
			final String line = _layout.doLayout( event );

			synchronized( _lines ) {
				if( _lines.size() == MAX_LINES ) {
					_lines.removeFirst();
				}

				_lines.addLast( line );
			}
		}

		private List<String> lines( final String contains, final int tail ) {
			final List<String> lines;

			synchronized( _lines ) {
				lines = new ArrayList<>( _lines );
			}

			final List<String> matching = contains == null || contains.isEmpty() ? lines : lines.stream().filter( line -> line.contains( contains ) ).toList();
			return tail <= 0 || matching.size() <= tail ? matching : matching.subList( matching.size() - tail, matching.size() );
		}
	}
}
