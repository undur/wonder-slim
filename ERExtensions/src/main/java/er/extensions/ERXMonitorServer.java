package er.extensions;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.webobjects.appserver.WOApplication;

import er.extensions.foundation.ERXProperties;

/**
 * Access point for Monitor operations that get info and/or perform admin operations
 */

public class ERXMonitorServer {

	private static Logger logger = LoggerFactory.getLogger( ERXMonitorServer.class );

	public static void start() {
		// We'll only start up the monitor server if a password is set for it
		final String monitorServerPassword = password();

		if( monitorServerPassword != null ) {
			try {
				// FIXME: This method of obtaining a port for the monitor service absolutely sucks
				final int monitorServerPort = WOApplication.application().port().intValue() + 10000;
				ERXMonitorServer.start( monitorServerPort );
			}
			catch( IOException e ) {
				logger.error( "Failed to start up the monitor service", e );
			}
		}		
	}

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

			final String providedPassword = exchange.getRequestHeaders().getFirst( "monitor-service-password" );

			if( providedPassword == null || !passwordMatches( providedPassword ) ) {
				respond( exchange, 401, "Missing or wrong monitor-service-password" );
				return;
			}

			if( exchange.getRequestURI().getPath().equals( "/monitor/jstack" ) ) {
				respond( exchange, 200, threadDumpAsString( true, true ) );
			}
			else {
				respond( exchange, 404, "Unknown operation" );
			}
		}

		private static boolean passwordMatches( final String providedPassword ) {
			final String password = password();
			return password != null && MessageDigest.isEqual( password.getBytes( StandardCharsets.UTF_8 ), providedPassword.getBytes( StandardCharsets.UTF_8 ) );
		}

		private static void respond( final HttpExchange exchange, final int status, final String body ) throws IOException {
			final byte[] bytes = body.getBytes( StandardCharsets.UTF_8 );
			exchange.getResponseHeaders().set( "content-type", "text/plain; charset=utf-8" );
			exchange.sendResponseHeaders( status, bytes.length );

			try( final OutputStream os = exchange.getResponseBody() ) {
				os.write( bytes );
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