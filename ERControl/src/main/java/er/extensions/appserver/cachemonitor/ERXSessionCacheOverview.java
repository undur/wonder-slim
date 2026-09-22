package er.extensions.appserver.cachemonitor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOSession;

import er.extensions.components.ERXStatelessComponent;

/**
 * A standalone, reusable overview of one session's page caches - occupancy vs. cap, a by-page-class
 * breakdown, and age/idle columns - so an application can right-size its cache caps from real data and
 * see the "abandoned but still retained" tail.
 * <p>
 * Framework-neutral: it renders {@link CacheReport}s built by {@link SessionCacheReporter}, which cover
 * both ERXAjaxSession's replacement cache (with age) and WO's own backtrack/permanent caches (no age,
 * shown as "-"). The template uses a Bootstrap 3/5-safe class subset.
 * <p>
 * Bindings:
 * <ul>
 * <li>{@code session} - the WOSession to report on. Defaults to {@code session()} (the current one).</li>
 * <li>{@code activeWindowMinutes} - "active" = touched within this many minutes (default 5).</li>
 * <li>{@code staleThresholdMinutes} - "stale/abandoned" = idle longer than this (default 10).</li>
 * </ul>
 */
public class ERXSessionCacheOverview extends ERXStatelessComponent {

	/** Repetition state. */
	public CacheReport currentReport;
	public CacheReport.ClassUsage currentUsage;

	private Instant _now;

	public ERXSessionCacheOverview( WOContext context ) {
		super( context );
	}

	/** Captured once per render so every relative time on the page is consistent. */
	public Instant now() {
		if( _now == null ) {
			_now = Instant.now();
		}
		return _now;
	}

	public WOSession reportedSession() {
		WOSession bound = (WOSession) valueForBinding( "session" );
		return bound != null ? bound : session();
	}

	public List<CacheReport> reports() {
		return SessionCacheReporter.reportsFor( reportedSession() );
	}

	private Duration activeWindow() {
		return Duration.ofMinutes( intBinding( "activeWindowMinutes", 5 ) );
	}

	private Duration staleThreshold() {
		return Duration.ofMinutes( intBinding( "staleThresholdMinutes", 10 ) );
	}

	private int intBinding( String name, int dflt ) {
		Object v = valueForBinding( name );
		if( v instanceof Number n ) {
			return n.intValue();
		}
		if( v instanceof String s && !s.isBlank() ) {
			try {
				return Integer.parseInt( s.trim() );
			}
			catch( NumberFormatException e ) {
				// fall through to default
			}
		}
		return dflt;
	}

	// ---- per-cache (the outer repetition over reports()) ----

	public int currentActiveCount() {
		return currentReport.activeCount( activeWindow(), now() );
	}

	public int currentStaleCount() {
		return currentReport.staleCount( staleThreshold(), now() );
	}

	public boolean currentTracksAge() {
		return currentReport.tracksAge();
	}

	public List<CacheReport.ClassUsage> currentByClass() {
		return currentReport.byPageClass( now() );
	}

	/** Occupancy in INSTANCES (what the cap bounds), e.g. "7 / 30". */
	public String occupancyLabel() {
		int cap = currentReport.cap();
		String capStr = cap <= 0 ? "unbounded" : String.valueOf( cap );
		return currentReport.distinctInstances() + " / " + capStr;
	}

	public int occupancyPercent() {
		return currentReport.occupancyPercent();
	}

	/** Inline width for the occupancy bar (avoids depending on Bootstrap's progress-bar markup). */
	public String occupancyBarStyle() {
		return "width: " + occupancyPercent() + "%";
	}

	public boolean currentReportIsEmpty() {
		return currentReport.size() == 0;
	}

	/** active / stale shown as "-" when the cache doesn't track age. */
	public String activeLabel() {
		int a = currentActiveCount();
		return a < 0 ? "–" : String.valueOf( a );
	}

	public String staleLabel() {
		int s = currentStaleCount();
		return s < 0 ? "–" : String.valueOf( s );
	}

	// ---- per-class row (the inner repetition over currentByClass()) ----

	public String usageOldestAge() {
		return humanize( currentUsage.oldestAge() );
	}

	public String usageMostIdle() {
		return humanize( currentUsage.mostIdle() );
	}

	/** A compact "3m 12s ago" style string, or "-" when the duration is unknown (age not tracked). */
	public static String humanize( Duration d ) {
		if( d == null ) {
			return "–";
		}
		long s = Math.max( 0, d.getSeconds() );
		if( s < 60 ) {
			return s + "s";
		}
		long m = s / 60;
		if( m < 60 ) {
			return m + "m " + ( s % 60 ) + "s";
		}
		long h = m / 60;
		if( h < 24 ) {
			return h + "h " + ( m % 60 ) + "m";
		}
		long days = h / 24;
		return days + "d " + ( h % 24 ) + "h";
	}
}
