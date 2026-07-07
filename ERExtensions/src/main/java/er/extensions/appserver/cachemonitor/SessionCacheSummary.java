package er.extensions.appserver.cachemonitor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * A one-line condensation of a single session's page caches - for a scannable table where each row is a
 * session. Rolls up the per-cache {@link CacheReport}s into totals plus per-cache held counts, and picks
 * out the single most-idle page class as a "likely culprit" hint.
 * <p>
 * Built by {@link #of}. Counts only (no bytes); cheap to compute for hundreds of sessions.
 */
public record SessionCacheSummary(
		String sessionID,
		int instancesHeld,
		int totalHeld,
		int activeHeld,
		int staleHeld,
		int unifiedHeld,
		int backtrackHeld,
		int permanentHeld,
		String topStaleClass,
		Duration topStaleIdle) {

	/**
	 * Condense one session.
	 *
	 * @param sessionID the session id (for the row label / drill-down link)
	 * @param reports that session's cache reports (from {@link SessionCacheReporter#reportsFor})
	 * @param activeWindow "active" = touched within this window
	 * @param staleThreshold "stale" = idle longer than this
	 * @param now the render instant (shared so all rows are consistent)
	 */
	public static SessionCacheSummary of( String sessionID, List<CacheReport> reports, Duration activeWindow, Duration staleThreshold, Instant now ) {
		int instances = 0, total = 0, active = 0, stale = 0, unified = 0, backtrack = 0, permanent = 0;
		String topClass = null;
		Duration topIdle = null;

		for( CacheReport report : reports ) {
			instances += report.distinctInstances();
			total += report.size();

			int a = report.activeCount( activeWindow, now );
			if( a >= 0 ) {
				active += a;
			}
			int s = report.staleCount( staleThreshold, now );
			if( s >= 0 ) {
				stale += s;
			}

			// Dispatch on the reporter's shared name constants - a string literal here once drifted from
			// the reporter's actual cache name, silently zeroing a column.
			switch( report.cacheName() ) {
				case CacheReport.NAME_UNIFIED -> unified = report.size();
				case CacheReport.NAME_WO_BACKTRACK -> backtrack = report.size();
				case CacheReport.NAME_WO_PERMANENT -> permanent = report.size();
				default -> {
					// unknown cache - still counted in total above
				}
			}

			// Most-idle class across the age-tracking caches: the strongest "this is dead weight" hint.
			if( report.tracksAge() ) {
				for( CacheReport.ClassUsage usage : report.byPageClass( now ) ) {
					if( usage.mostIdle() != null && ( topIdle == null || usage.mostIdle().compareTo( topIdle ) > 0 ) ) {
						topIdle = usage.mostIdle();
						topClass = usage.pageClass();
					}
				}
			}
		}

		return new SessionCacheSummary( sessionID, instances, total, active, stale, unified, backtrack, permanent, topClass, topIdle );
	}
}
