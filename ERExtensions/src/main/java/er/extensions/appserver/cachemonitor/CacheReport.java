package er.extensions.appserver.cachemonitor;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A framework-neutral snapshot of one cache: its name, its configured cap, and the entries it holds. Built
 * by a per-cache adapter (see {@link SessionCacheReporter}); consumed by reporting/UI code that knows
 * nothing about which cache it came from.
 * <p>
 * All roll-ups (by-class counts, active vs. held, the stale tail) are derived from the entries, so the same
 * derivations apply uniformly to every cache - and entries from caches that don't track age simply report
 * no active/stale split.
 *
 * @param cacheName a human label, e.g. "Ajax replacement", "WO backtrack", "WO permanent"
 * @param cap the configured maximum size (0 = "disabled"; negative = "unbounded/unknown"), for the occupancy bar
 * @param tracksAge whether this cache's entries carry age/idle timestamps (false for WO's caches)
 * @param entries the cached pages
 */
public record CacheReport(
		String cacheName,
		int cap,
		boolean tracksAge,
		List<CachedPageEntry> entries) {

	/** Total entries held. */
	public int size() {
		return entries.size();
	}

	/** Held / cap as a percentage (0 when cap <= 0), for the occupancy bar. */
	public int occupancyPercent() {
		return cap <= 0 ? 0 : Math.min( 100, (int) Math.round( 100.0 * entries.size() / cap ) );
	}

	/**
	 * "Active" = entries touched within the window. The live working set. Only meaningful when the cache
	 * tracks age; returns size() (i.e. "all, unknown") when it does not, so callers can show "-".
	 */
	public int activeCount( Duration window, Instant now ) {
		if( !tracksAge ) {
			return -1;
		}
		int active = 0;
		for( CachedPageEntry e : entries ) {
			Duration idle = e.idle( now );
			if( idle != null && idle.compareTo( window ) <= 0 ) {
				active++;
			}
		}
		return active;
	}

	/** Entries idle longer than the threshold - the abandoned-but-retained tail. -1 when age isn't tracked. */
	public int staleCount( Duration threshold, Instant now ) {
		if( !tracksAge ) {
			return -1;
		}
		int stale = 0;
		for( CachedPageEntry e : entries ) {
			if( e.isIdleLongerThan( threshold, now ) ) {
				stale++;
			}
		}
		return stale;
	}

	/** Per-page-class breakdown, ordered by descending count (which classes eat the cache). */
	public List<ClassUsage> byPageClass( Instant now ) {
		Map<String, int[]> counts = new TreeMap<>(); // class -> [total, idleAccumulatorSeconds, withIdle]
		Map<String, Instant> oldest = new LinkedHashMap<>();
		Map<String, Instant> mostIdle = new LinkedHashMap<>();
		for( CachedPageEntry e : entries ) {
			counts.computeIfAbsent( e.pageClass(), k -> new int[1] )[0]++;
			if( e.createdAt() != null ) {
				oldest.merge( e.pageClass(), e.createdAt(), ( a, b ) -> a.isBefore( b ) ? a : b );
			}
			if( e.lastAccessedAt() != null ) {
				mostIdle.merge( e.pageClass(), e.lastAccessedAt(), ( a, b ) -> a.isBefore( b ) ? a : b );
			}
		}
		List<ClassUsage> usages = new java.util.ArrayList<>();
		for( Map.Entry<String, int[]> c : counts.entrySet() ) {
			Instant o = oldest.get( c.getKey() );
			Instant mi = mostIdle.get( c.getKey() );
			usages.add( new ClassUsage(
					c.getKey(),
					c.getValue()[0],
					o == null ? null : Duration.between( o, now ),
					mi == null ? null : Duration.between( mi, now ) ) );
		}
		usages.sort( ( a, b ) -> Integer.compare( b.count(), a.count() ) );
		return usages;
	}

	/**
	 * Per-class roll-up row.
	 *
	 * @param pageClass the class
	 * @param count how many entries of this class are held
	 * @param oldestAge age of the oldest held entry of this class (null if age not tracked)
	 * @param mostIdle idle time of the most-idle (longest-untouched) entry of this class - the dead-weight hint (null if not tracked)
	 */
	public record ClassUsage(String pageClass, int count, Duration oldestAge, Duration mostIdle) {}
}
