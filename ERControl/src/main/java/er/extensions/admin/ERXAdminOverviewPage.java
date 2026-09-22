package er.extensions.admin;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXApplication;
import er.extensions.components.ERXComponent;

/**
 * What is running, and the few things one can do to it.
 */
public class ERXAdminOverviewPage extends ERXComponent {

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ).withZone( ZoneId.systemDefault() );

	public String notice;
	public boolean confirmingStop;

	public ERXAdminOverviewPage( WOContext context ) {
		super( context );
	}

	private static RuntimeMXBean runtime() {
		return ManagementFactory.getRuntimeMXBean();
	}

	public String applicationName() {
		return WOApplication.application().name();
	}

	public String hostAndPort() {
		return WOApplication.application().host() + ":" + WOApplication.application().port();
	}

	public String mode() {
		return ERXApplication.isDevelopmentModeSafe() ? "Development" : "Deployment";
	}

	public boolean isDevelopmentMode() {
		return ERXApplication.isDevelopmentModeSafe();
	}

	public long pid() {
		return ProcessHandle.current().pid();
	}

	public String startedAt() {
		return TIMESTAMP.format( Instant.ofEpochMilli( runtime().getStartTime() ) );
	}

	public String uptime() {
		final Duration d = Duration.ofMillis( runtime().getUptime() );
		final long days = d.toDays();
		return ( days > 0 ? days + "d " : "" ) + d.toHoursPart() + "h " + d.toMinutesPart() + "m " + d.toSecondsPart() + "s";
	}

	public String javaVersion() {
		return System.getProperty( "java.vm.name" ) + " " + System.getProperty( "java.version" );
	}

	public String javaHome() {
		return System.getProperty( "java.home" );
	}

	public int processors() {
		return Runtime.getRuntime().availableProcessors();
	}

	public String memoryUsed() {
		final Runtime r = Runtime.getRuntime();
		return megabytes( r.totalMemory() - r.freeMemory() ) + " of " + megabytes( r.maxMemory() ) + " MB";
	}

	public int memoryPercent() {
		final Runtime r = Runtime.getRuntime();
		return (int)( ( r.totalMemory() - r.freeMemory() ) * 100 / r.maxMemory() );
	}

	private static long megabytes( final long bytes ) {
		return bytes / ( 1024 * 1024 );
	}

	public int threadCount() {
		return ManagementFactory.getThreadMXBean().getThreadCount();
	}

	public int activeSessions() {
		return WOApplication.application().activeSessionsCount();
	}

	public int sessionTimeoutMinutes() {
		return (int)( WOApplication.application().sessionTimeOut().doubleValue() / 60 );
	}

	public int loggedExceptions() {
		return ERXApplication.erxApplication().exceptionManager().loggedExceptions().size();
	}

	public String shortURLs() {
		return ERXApplication.erxApplication().shortURLs() ? "On" : "Off";
	}

	public String adaptor() {
		return String.valueOf( WOApplication.application().adaptors().objectAtIndex( 0 ).getClass().getSimpleName() );
	}

	public String workingDirectory() {
		return System.getProperty( "user.dir" );
	}

	public boolean refusingNewSessions() {
		return WOApplication.application().isRefusingNewSessions();
	}

	public WOActionResults collectGarbage() {
		final String before = memoryUsed();
		System.gc();
		notice = "Requested a garbage collection. Memory in use went from " + before + " to " + memoryUsed() + ".";
		return null;
	}

	public WOActionResults askToStop() {
		confirmingStop = true;
		return null;
	}

	public WOActionResults cancelStop() {
		confirmingStop = false;
		return null;
	}

	public WOActionResults stop() {
		confirmingStop = false;
		notice = "The application is shutting down.";
		WOApplication.application().terminate();
		return null;
	}
}
