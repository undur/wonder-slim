package er.extensions;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import er.extensions.foundation.ERXProperties;

/**
 * Access point for Monitor operations that get info and/or perform admin operations
 */

public class ERXMonitorServer {

	private static Logger logger = LoggerFactory.getLogger( ERXMonitorServer.class );

	public static void start( int port ) throws IOException {
		// Just for logging startup time / how expensive the monitoring service is
		long monitorStartupTime = System.currentTimeMillis();

		// This is where we'll throw an exception if the given port is already bound
		final HttpServer server = HttpServer.create( new InetSocketAddress( port ), 0 );

		server.createContext( "/monitor", new MonitorHandler() );
		server.setExecutor( Executors.newVirtualThreadPerTaskExecutor() );
		server.start();

		// Log the startup time
		monitorStartupTime = System.currentTimeMillis() - monitorStartupTime;
		logger.info( "Started monitor server at address {} in {}ms", server.getAddress(), monitorStartupTime );
	}

	private static String password() {
		return ERXProperties.stringForKey( "WOMonitorServicePassword" );
	}

	private static class MonitorHandler implements HttpHandler {

		@Override
		public void handle( HttpExchange exchange ) throws IOException {

			final List<String> providedPassword = exchange.getRequestHeaders().get( "monitor-service-password" );

			if( providedPassword.isEmpty() ) {
				throw new IllegalStateException( "No password provided" );
			}

			if( !Objects.equals( password(), providedPassword.getFirst() ) ) {
				throw new IllegalStateException( "Wrong password" );
			}


			if( exchange.getRequestURI().toString().equals( "/monitor/jstack" ) ) {
				final String responseString = threadDumpAsString( true, true );
				final byte[] responseBytes = responseString.getBytes();
				exchange.sendResponseHeaders( 200, responseBytes.length );

				try( final OutputStream os = exchange.getResponseBody()) {
					os.write( responseBytes );
				}
			}
			else {
				try( final OutputStream os = exchange.getResponseBody()) {
					os.write( "Unknown operation".getBytes() );
				}
			}
		}
	}

	/**
	 * @return A thread dump as a string
	 */
	private static String threadDumpAsString( boolean lockedMonitors, boolean lockedSynchronizers ) {
		final StringBuilder threadDump = new StringBuilder();
		final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

		for( ThreadInfo threadInfo : threadMXBean.dumpAllThreads( lockedMonitors, lockedSynchronizers ) ) {
			threadDump.append( threadInfo.toString() );
		}

		return threadDump.toString();
	}
}