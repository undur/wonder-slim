package er.extensions.logging;

import java.util.List;
import java.util.Map;

/**
 * The logging implementation behind slf4j, as the framework drives it. Found through {@link java.util.ServiceLoader}: a
 * jar lists its backend in {@code META-INF/services/er.extensions.logging.ERXLoggingBackend}; at most one may. ERLoggingReload4j
 * provides the reload4j one. See {@link ERXLoggingSupport}.
 */
public interface ERXLoggingBackend {

	/**
	 * Sets up console output at INFO, so logging works before the configuration is read. Invoked first thing at startup.
	 */
	public void configureDefault();

	/**
	 * Configures logging from the configuration, replacing the default setup. Invoked at startup, and again when the
	 * configuration changes.
	 */
	public void configure();

	/**
	 * Reattaches console output to {@code System.out}/{@code System.err}, which WebObjects replaces while the application
	 * is constructed when {@code WOOutputPath} is set
	 */
	public void reattachConsole();

	/**
	 * Starts keeping recent log output in memory, for reading back in development (see {@code ERXConsoleCapture})
	 *
	 * @return true if capture is active
	 */
	public boolean installCapture();

	/**
	 * @return Recently captured lines, oldest first: those containing {@code contains} (all if null or empty), the last
	 *         {@code tail} of them (all if {@code <= 0})
	 */
	public List<String> capturedLines( String contains, int tail );

	/**
	 * @return The backend's name, as the control panel shows it
	 */
	public String name();

	/**
	 * @return true if the backend reads Project Wonder style log4j configuration ({@code log4j.*}) as its own, so the
	 *         {@code log4j.logger.*} levels are its own configuration's rather than translated (see
	 *         {@link ERXLoggingConfiguration#legacyLevels(java.util.Properties)})
	 */
	public boolean readsLog4jConfiguration();

	/**
	 * @return Where the backend's own configuration was last read from, null if it read none
	 */
	public String nativeConfiguration();

	/**
	 * @return The levels the backend's own configuration set when logging was last configured, by logger name
	 *         ({@link ERXLoggingConfiguration#ROOT} for the root logger)
	 */
	public Map<String, String> nativeLevels();

	/**
	 * @return Every logger the backend knows, the root logger ({@link ERXLoggingConfiguration#ROOT}) first, then by name
	 */
	public List<LoggerLevel> loggers();

	/**
	 * A logger, as the backend has it
	 *
	 * @param name The logger's name ({@link ERXLoggingConfiguration#ROOT} for the root logger)
	 * @param level The level set on the logger itself, null if it inherits one
	 * @param effectiveLevel The level it logs at
	 */
	public record LoggerLevel( String name, String level, String effectiveLevel ) {}
}
