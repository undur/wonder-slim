package er.extensions;

import java.util.List;

/**
 * The logging implementation behind slf4j, as the framework drives it. Found through {@link java.util.ServiceLoader}: a
 * jar lists its backend in {@code META-INF/services/er.extensions.ERXLoggingBackend}; at most one may. ERLoggingReload4j
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
}
