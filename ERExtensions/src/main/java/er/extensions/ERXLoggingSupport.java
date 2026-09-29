package er.extensions;

import java.util.List;
import java.util.ServiceLoader;

/**
 * Drives the logging backend (see {@link ERXLoggingBackend}), found through {@link ServiceLoader} the first time it's
 * needed: first thing in {@code ERXApplication.main()}. With none, logging goes wherever slf4j sends it without
 * configuration, which is said once, on the console. With more than one, the launch stops, naming them.
 */
public class ERXLoggingSupport {

	/**
	 * The backend, or null for none, found on first use
	 */
	private static final class Holder {
		private static final ERXLoggingBackend BACKEND = find();
	}

	private static ERXLoggingBackend find() {
		final List<ERXLoggingBackend> backends = ServiceLoader
				.load( ERXLoggingBackend.class )
				.stream()
				.map( ServiceLoader.Provider::get )
				.toList();

		if( backends.size() > 1 ) {
			throw new IllegalStateException( "More than one logging backend is on the classpath, at most one may be: " + backends.stream().map( backend -> backend.getClass().getName() ).toList() );
		}

		if( backends.isEmpty() ) {
			System.out.println( "====== No logging backend (such as ERLoggingReload4j's) is on the classpath: logging is left unconfigured" );
			return null;
		}

		return backends.get( 0 );
	}

	/**
	 * @return The logging backend, null if there's none
	 */
	public static ERXLoggingBackend backend() {
		return Holder.BACKEND;
	}

	/**
	 * Configures logging from the configuration, see {@link ERXLoggingBackend#configure()}
	 */
	public static void configureLoggingWithSystemProperties() {
		if( backend() != null ) {
			backend().configure();
		}
	}

	/**
	 * Sets up console logging for startup, see {@link ERXLoggingBackend#configureDefault()}
	 */
	public static void configureDefaultLogging() {
		if( backend() != null ) {
			backend().configureDefault();
		}
	}

	/**
	 * See {@link ERXLoggingBackend#reattachConsole()}
	 */
	public static void reInitConsoleAppenders() {
		if( backend() != null ) {
			backend().reattachConsole();
		}
	}

	/**
	 * See {@link ERXLoggingBackend#installCapture()}
	 *
	 * @return true if capture is active, false without a backend
	 */
	public static boolean installCapture() {
		return backend() != null && backend().installCapture();
	}

	/**
	 * See {@link ERXLoggingBackend#capturedLines(String, int)}
	 */
	public static List<String> capturedLines( final String contains, final int tail ) {
		return backend() == null ? List.of() : backend().capturedLines( contains, tail );
	}
}
