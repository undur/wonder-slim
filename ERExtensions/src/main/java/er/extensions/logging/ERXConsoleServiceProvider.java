package er.extensions.logging;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * slf4j's provider for {@link ERXConsoleLoggingBackend}: loggers that write to the console at the levels it's configured
 * with. Named as slf4j's provider by {@link ERXConsoleLoggingBackend#activateIfNoBackend()}, never found on its own.
 */
public final class ERXConsoleServiceProvider implements SLF4JServiceProvider {

	/**
	 * The loggers, shared by every instance of the provider
	 */
	static final Factory FACTORY = new Factory();

	private final IMarkerFactory _markerFactory = new BasicMarkerFactory();

	private final MDCAdapter _mdcAdapter = new BasicMDCAdapter();

	@Override
	public ILoggerFactory getLoggerFactory() {
		return FACTORY;
	}

	@Override
	public IMarkerFactory getMarkerFactory() {
		return _markerFactory;
	}

	@Override
	public MDCAdapter getMDCAdapter() {
		return _mdcAdapter;
	}

	@Override
	public String getRequestedApiVersion() {
		return "2.0.99";
	}

	@Override
	public void initialize() {}

	/**
	 * The loggers, by name
	 */
	static final class Factory implements ILoggerFactory {

		private final ConcurrentMap<String, ConsoleLogger> _loggers = new ConcurrentHashMap<>();

		@Override
		public Logger getLogger( final String name ) {
			final String loggerName = Logger.ROOT_LOGGER_NAME.equalsIgnoreCase( name ) ? ERXLoggingConfiguration.ROOT : name;
			return _loggers.computeIfAbsent( loggerName, ConsoleLogger::new );
		}

		/**
		 * @return The names of the loggers created
		 */
		List<String> names() {
			return List.copyOf( _loggers.keySet() );
		}
	}

	/**
	 * A logger writing through {@link ERXConsoleLoggingBackend}. Its level is looked up again only after the backend is
	 * configured again.
	 */
	static final class ConsoleLogger extends LegacyAbstractLogger {

		private transient volatile int _generation = -1;

		private transient volatile int _level;

		ConsoleLogger( final String name ) {
			this.name = name;
		}

		private int level() {
			final ERXConsoleLoggingBackend backend = ERXConsoleLoggingBackend.INSTANCE;
			final int generation = backend.generation();

			if( generation != _generation ) {
				_level = backend.effectiveLevel( name );
				_generation = generation;
			}

			return _level;
		}

		@Override
		public boolean isTraceEnabled() {
			return level() <= Level.TRACE.toInt();
		}

		@Override
		public boolean isDebugEnabled() {
			return level() <= Level.DEBUG.toInt();
		}

		@Override
		public boolean isInfoEnabled() {
			return level() <= Level.INFO.toInt();
		}

		@Override
		public boolean isWarnEnabled() {
			return level() <= Level.WARN.toInt();
		}

		@Override
		public boolean isErrorEnabled() {
			return level() <= Level.ERROR.toInt();
		}

		@Override
		protected String getFullyQualifiedCallerName() {
			return null;
		}

		@Override
		protected void handleNormalizedLoggingCall( final Level level, final Marker marker, final String messagePattern, final Object[] arguments, final Throwable throwable ) {
			ERXConsoleLoggingBackend.INSTANCE.write( level.toString(), name, MessageFormatter.basicArrayFormat( messagePattern, arguments ), throwable );
		}
	}
}
