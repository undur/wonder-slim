package er.extensions;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
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
 * Access point for Monitor operations that get info and/or perform admin operations. Currently one: a thread dump,
 * {@code /monitor/jstack}.
 *
 * Served by the JDK's HTTP server on a port of its own (the application's port + 10000), on virtual threads, rather
 * than by a request handler: it doesn't go through WO's request dispatch, so it still answers when every request
 * thread is stuck, which is when a thread dump is needed. It starts only when WOMonitorServicePassword is set, and every
 * request must carry that password in the monitor-service-password header.
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
	 * @return A thread dump as a string, every thread with its complete stack. Written out here because ThreadInfo.toString(),
	 * which has the same shape, cuts each stack off after eight frames.
	 */
	private static String threadDumpAsString( boolean lockedMonitors, boolean lockedSynchronizers ) {
		final StringBuilder threadDump = new StringBuilder();
		final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

		for( ThreadInfo threadInfo : threadMXBean.dumpAllThreads( lockedMonitors, lockedSynchronizers ) ) {
			appendThreadInfo( threadDump, threadInfo );
		}

		return threadDump.toString();
	}

	private static void appendThreadInfo( final StringBuilder sb, final ThreadInfo threadInfo ) {
		sb.append( '"' ).append( threadInfo.getThreadName() ).append( '"' );

		if( threadInfo.isDaemon() ) {
			sb.append( " daemon" );
		}

		sb.append( " prio=" ).append( threadInfo.getPriority() );
		sb.append( " Id=" ).append( threadInfo.getThreadId() );
		sb.append( ' ' ).append( threadInfo.getThreadState() );

		if( threadInfo.getLockName() != null ) {
			sb.append( " on " ).append( threadInfo.getLockName() );
		}

		if( threadInfo.getLockOwnerName() != null ) {
			sb.append( " owned by \"" ).append( threadInfo.getLockOwnerName() ).append( "\" Id=" ).append( threadInfo.getLockOwnerId() );
		}

		if( threadInfo.isSuspended() ) {
			sb.append( " (suspended)" );
		}

		if( threadInfo.isInNative() ) {
			sb.append( " (in native)" );
		}

		sb.append( '\n' );

		final StackTraceElement[] stackTrace = threadInfo.getStackTrace();

		for( int i = 0; i < stackTrace.length; i++ ) {
			sb.append( "\tat " ).append( stackTrace[i] ).append( '\n' );

			if( i == 0 && threadInfo.getLockInfo() != null ) {
				final Thread.State state = threadInfo.getThreadState();

				if( state == Thread.State.BLOCKED ) {
					sb.append( "\t-  blocked on " ).append( threadInfo.getLockInfo() ).append( '\n' );
				}
				else if( state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING ) {
					sb.append( "\t-  waiting on " ).append( threadInfo.getLockInfo() ).append( '\n' );
				}
			}

			for( MonitorInfo monitorInfo : threadInfo.getLockedMonitors() ) {
				if( monitorInfo.getLockedStackDepth() == i ) {
					sb.append( "\t-  locked " ).append( monitorInfo ).append( '\n' );
				}
			}
		}

		final LockInfo[] lockedSynchronizers = threadInfo.getLockedSynchronizers();

		if( lockedSynchronizers.length > 0 ) {
			sb.append( "\n\tNumber of locked synchronizers = " ).append( lockedSynchronizers.length ).append( '\n' );

			for( LockInfo lockInfo : lockedSynchronizers ) {
				sb.append( "\t- " ).append( lockInfo ).append( '\n' );
			}
		}

		sb.append( '\n' );
	}
}