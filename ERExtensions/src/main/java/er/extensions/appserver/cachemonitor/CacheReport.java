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

	/**
	 * The cache names the reporter emits - shared constants so consumers that dispatch on the name
	 * (e.g. the summary's per-cache columns) cannot silently drift out of sync with the reporter.
	 */
	public static final String NAME_UNIFIED = "Page cache (unified)";
	public static final String NAME_WO_BACKTRACK = "WO backtrack";
	public static final String NAME_WO_PERMANENT = "WO permanent";

	/** Total entries held. NOTE: an entry is a contextID key; one page instance typically holds MANY. */
	public int size() {
		return entries.size();
	}

	/**
	 * Distinct page INSTANCES held - the number a cache cap actually bounds. Entries are deduplicated by
	 * {@link CachedPageEntry#instanceKey()}; entries whose instance can't be identified (null key) are
	 * conservatively counted as one instance each.
	 */
	public int distinctInstances() {
		java.util.Set<Integer> keys = new java.util.HashSet<>();
		int unidentified = 0;
		for( CachedPageEntry e : entries ) {
			if( e.instanceKey() == null ) {
				unidentified++;
			}
			else {
				keys.add( e.instanceKey() );
			}
		}
		return keys.size() + unidentified;
	}

	/**
	 * Instances / cap as a percentage (0 when cap <= 0), for the occupancy bar. Measured in INSTANCES,
	 * not entries, because that is what the cap bounds.
	 */
	public int occupancyPercent() {
		return cap <= 0 ? 0 : Math.min( 100, (int) Math.round( 100.0 * distinctInstances() / cap ) );
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

	/** Per-page-class breakdown, ordered by descending instance count (which classes eat the cache). */
	public List<ClassUsage> byPageClass( Instant now ) {
		Map<String, int[]> counts = new TreeMap<>(); // class -> [entryCount, unidentifiedCount]
		Map<String, java.util.Set<Integer>> instances = new LinkedHashMap<>();
		Map<String, Instant> oldest = new LinkedHashMap<>();
		Map<String, Instant> mostIdle = new LinkedHashMap<>();
		for( CachedPageEntry e : entries ) {
			int[] c = counts.computeIfAbsent( e.pageClass(), k -> new int[2] );
			c[0]++;
			if( e.instanceKey() == null ) {
				c[1]++;
			}
			else {
				instances.computeIfAbsent( e.pageClass(), k -> new java.util.HashSet<>() ).add( e.instanceKey() );
			}
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
			java.util.Set<Integer> keys = instances.get( c.getKey() );
			usages.add( new ClassUsage(
					c.getKey(),
					( keys == null ? 0 : keys.size() ) + c.getValue()[1],
					c.getValue()[0],
					o == null ? null : Duration.between( o, now ),
					mi == null ? null : Duration.between( mi, now ) ) );
		}
		usages.sort( ( a, b ) -> Integer.compare( b.instances(), a.instances() ) );
		return usages;
	}

	/**
	 * Per-class roll-up row.
	 *
	 * @param pageClass the class
	 * @param instances how many distinct page INSTANCES of this class are held (what the cap bounds)
	 * @param count how many entries (contextID keys) of this class are held
	 * @param oldestAge age of the oldest held entry of this class (null if age not tracked)
	 * @param mostIdle idle time of the most-idle (longest-untouched) entry of this class - the dead-weight hint (null if not tracked)
	 */
	public record ClassUsage(String pageClass, int instances, int count, Duration oldestAge, Duration mostIdle) {}
}
