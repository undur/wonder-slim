package er.extensions.appserver;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

import javax.management.NotificationEmitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOSession;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSSelector;

import er.extensions.appserver.ajax.ERXAjaxSession;
import er.extensions.appserver.cachemonitor.PageCacheReuseStats;
import er.extensions.appserver.cachemonitor.SessionCacheReporter;
import er.extensions.foundation.ERXProperties;

/**
 * Sheds page cache weight when the JVM comes under genuine memory pressure. The unified page
 * cache retains instances for the whole session lifetime; measurement shows the overwhelming
 * majority sit idle for hours - a large, cheaply reclaimable pool. Rather than paying for that
 * with an always-on TTL (which breaks the occasional legitimate deep reach even when memory is
 * plentiful), this valve evicts ONLY when memory is actually short: zero cost in normal
 * operation, relief exactly when it matters.
 *
 * <h2>The pressure signal</h2>
 * The old-generation memory pool's COLLECTION USAGE threshold: the JVM notifies when a garbage
 * collection completes and old gen is STILL above the threshold - the truthful "genuinely full"
 * signal (pre-GC fullness is routine and meaningless). Push, not poll: no watcher thread, no
 * forced GCs, and it works even when no requests are flowing. Two tiers, both expressed as a
 * fraction of the page cache's instance cap ({@code WOPageCacheSize}) - deliberately gentle
 * defaults that only touch genuinely heavy sessions, tunable more aggressive where needed:
 * <ul>
 * <li>above {@code threshold} (default 0.85 of old gen's max): trim each session to
 *     {@code trimToFraction} (default 0.50) of the cap, least recently used instances first.</li>
 * <li>above {@code aggressiveThreshold} (default 0.90): trim each session to
 *     {@code aggressiveTrimToFraction} (default 0.25) of the cap.</li>
 * </ul>
 * A session is never trimmed below its most recently used instance - the minimum that keeps its
 * next click working.
 * The valve also honors {@link ERXLowMemoryHandler}'s LowMemory/StarvedMemory notifications
 * (posted when the {@code memoryLowThreshold}/{@code memoryStarvedThreshold} properties are set),
 * finally giving that long-standing "register your caches" contract a subscriber.
 *
 * <h2>Properties</h2>
 * <ul>
 * <li>{@code er.extensions.ERXPageCachePressureValve.enabled} - default true</li>
 * <li>{@code er.extensions.ERXPageCachePressureValve.threshold} - default 0.85</li>
 * <li>{@code er.extensions.ERXPageCachePressureValve.aggressiveThreshold} - default 0.90</li>
 * <li>{@code er.extensions.ERXPageCachePressureValve.trimToFraction} - default 0.50</li>
 * <li>{@code er.extensions.ERXPageCachePressureValve.aggressiveTrimToFraction} - default 0.25</li>
 * </ul>
 * Purges are throttled to one per 30 seconds (sustained pressure notifies on every collection),
 * logged loudly, and recorded in {@link PageCacheReuseStats} so the cache overview page shows
 * what pressure has cost.
 */
public class ERXPageCachePressureValve {

	private static final Logger log = LoggerFactory.getLogger( ERXPageCachePressureValve.class );

	private static final long PURGE_THROTTLE_MILLIS = 30_000;

	private final double _threshold;
	private final double _aggressiveThreshold;
	private final double _trimToFraction;
	private final double _aggressiveTrimToFraction;
	private final MemoryPoolMXBean _oldGenPool;

	private volatile long _lastPurgeMillis;

	/**
	 * Installs the valve unless disabled by property. Returns null when disabled or when no
	 * suitable old-generation pool exists (exotic collectors) - in which case only the
	 * ERXLowMemoryHandler notifications would ever trigger it, so we still install for those.
	 */
	public static ERXPageCachePressureValve installIfEnabled() {
		if( !ERXProperties.booleanForKeyWithDefault( "er.extensions.ERXPageCachePressureValve.enabled", true ) ) {
			log.info( "Page cache pressure valve disabled by property" );
			return null;
		}
		return new ERXPageCachePressureValve();
	}

	private ERXPageCachePressureValve() {
		_threshold = ERXProperties.bigDecimalForKeyWithDefault( "er.extensions.ERXPageCachePressureValve.threshold", new java.math.BigDecimal( "0.85" ) ).doubleValue();
		_aggressiveThreshold = ERXProperties.bigDecimalForKeyWithDefault( "er.extensions.ERXPageCachePressureValve.aggressiveThreshold", new java.math.BigDecimal( "0.90" ) ).doubleValue();
		_trimToFraction = ERXProperties.bigDecimalForKeyWithDefault( "er.extensions.ERXPageCachePressureValve.trimToFraction", new java.math.BigDecimal( "0.50" ) ).doubleValue();
		_aggressiveTrimToFraction = ERXProperties.bigDecimalForKeyWithDefault( "er.extensions.ERXPageCachePressureValve.aggressiveTrimToFraction", new java.math.BigDecimal( "0.25" ) ).doubleValue();

		_oldGenPool = findOldGenPool();

		if( _oldGenPool != null ) {
			final long max = poolMax( _oldGenPool );
			if( max > 0 ) {
				_oldGenPool.setCollectionUsageThreshold( (long)(max * _threshold) );
				final NotificationEmitter emitter = (NotificationEmitter)ManagementFactory.getMemoryMXBean();
				emitter.addNotificationListener( ( notification, handback ) -> {
					if( java.lang.management.MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED.equals( notification.getType() ) ) {
						onCollectionThresholdExceeded();
					}
				}, null, null );
				// Fractions, not instance counts: this runs at construction time, before the app's
				// WOPageCacheSize has necessarily been applied - concrete targets are computed lazily
				// at purge time (and shown in the launch banner, which prints after configuration).
				log.info( "Page cache pressure valve armed: old gen pool \"{}\", {}% trims sessions to {}% of the page cache cap, {}% trims to {}%",
						_oldGenPool.getName(), Math.round( _threshold * 100 ), Math.round( _trimToFraction * 100 ), Math.round( _aggressiveThreshold * 100 ), Math.round( _aggressiveTrimToFraction * 100 ) );
			}
		}
		else {
			log.info( "Page cache pressure valve: no old-generation pool with collection-usage threshold support found; GC-driven triggering unavailable" );
		}

		// Honor ERXLowMemoryHandler's notifications too (active when its threshold properties are set).
		final NSNotificationCenter center = NSNotificationCenter.defaultCenter();
		center.addObserver( this, new NSSelector<>( "lowMemory", new Class[] { NSNotification.class } ), ERXLowMemoryHandler.LowMemoryNotification, null );
		center.addObserver( this, new NSSelector<>( "starvedMemory", new Class[] { NSNotification.class } ), ERXLowMemoryHandler.StarvedMemoryNotification, null );
	}

	/**
	 * A one-line description of the armed configuration, for the startup cache-configuration banner.
	 */
	public String bannerDescription() {
		if( _oldGenPool == null ) {
			return "armed for low-memory notifications only (no old-gen pool with collection-usage threshold support)";
		}
		return String.format( "old gen > %d%% trims sessions to %d instance(s); > %d%% trims to %d (pool \"%s\")",
				Math.round( _threshold * 100 ), trimTarget( _trimToFraction ), Math.round( _aggressiveThreshold * 100 ), trimTarget( _aggressiveTrimToFraction ), _oldGenPool.getName() );
	}

	/** ERXLowMemoryHandler's low-memory notification: tier-1 purge. */
	public void lowMemory( NSNotification notification ) {
		purge( false, "LowMemoryNotification" );
	}

	/** ERXLowMemoryHandler's starved-memory notification: tier-2 purge. */
	public void starvedMemory( NSNotification notification ) {
		purge( true, "StarvedMemoryNotification" );
	}

	private void onCollectionThresholdExceeded() {
		final MemoryUsage usage = _oldGenPool.getCollectionUsage();
		final long max = poolMax( _oldGenPool );
		if( usage == null || max <= 0 ) {
			return;
		}
		final double occupancy = (double)usage.getUsed() / max;
		purge( occupancy >= _aggressiveThreshold, String.format( "old gen at %d%% after GC", Math.round( occupancy * 100 ) ) );
	}

	/**
	 * Trims every checked-in session's cache to the tier's instance target (a fraction of the
	 * page cache cap; aggressive uses the smaller fraction), least recently used instances first.
	 * Sessions currently checked out by a request are absent from the store snapshot and thus
	 * untouched - their caches get trimmed on a later trigger if pressure persists.
	 */
	private synchronized void purge( boolean aggressive, String cause ) {
		final long now = System.currentTimeMillis();
		if( now - _lastPurgeMillis < PURGE_THROTTLE_MILLIS ) {
			return;
		}
		_lastPurgeMillis = now;

		final int trimTo = trimTarget( aggressive ? _aggressiveTrimToFraction : _trimToFraction );
		int purgedInstances = 0;
		int touchedSessions = 0;

		for( final WOSession session : SessionCacheReporter.activeSessions() ) {
			if( session instanceof ERXAjaxSession ajaxSession ) {
				final int purged = ajaxSession.trimPageCacheToInstanceCount( trimTo );
				if( purged > 0 ) {
					purgedInstances += purged;
					touchedSessions++;
				}
			}
		}

		PageCacheReuseStats.recordPressurePurge( purgedInstances );
		log.warn( "Memory pressure ({}): {} trim to {} instance(s)/session evicted {} page instance(s) from {} session(s)",
				cause, aggressive ? "AGGRESSIVE" : "standard", trimTo, purgedInstances, touchedSessions );
	}

	/** The per-session instance target for a trim fraction: fraction of the page cache cap, floored at one instance. */
	private static int trimTarget( double fraction ) {
		return Math.max( 1, (int)Math.round( com.webobjects.appserver.WOApplication.application().pageCacheSize() * fraction ) );
	}

	/** The old-generation heap pool, per collector naming conventions; null when none qualifies. */
	private static MemoryPoolMXBean findOldGenPool() {
		for( final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans() ) {
			if( pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported() ) {
				final String name = pool.getName().toLowerCase();
				if( name.contains( "old" ) || name.contains( "tenured" ) ) {
					return pool;
				}
			}
		}
		return null;
	}

	private static long poolMax( MemoryPoolMXBean pool ) {
		final MemoryUsage usage = pool.getCollectionUsage() != null ? pool.getCollectionUsage() : pool.getUsage();
		if( usage != null && usage.getMax() > 0 ) {
			return usage.getMax();
		}
		return Runtime.getRuntime().maxMemory();
	}
}