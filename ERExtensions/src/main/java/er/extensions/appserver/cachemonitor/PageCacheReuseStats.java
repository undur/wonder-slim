package er.extensions.appserver.cachemonitor;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * App-wide reuse profile of the unified page cache. Every cache HIT records two numbers about the
 * instance being restored - how long it had been idle, and how deep it sat in the instance-LRU
 * order - into fixed histogram buckets. The two histograms answer the cache-sizing questions
 * empirically, from production traffic:
 * <ul>
 * <li><b>Idle-at-reuse</b>: hits with idle time beyond T are exactly the restores an idle-eviction
 *     TTL of T would have broken.</li>
 * <li><b>Depth-at-reuse</b>: hits at LRU depth beyond N are exactly the restores a cache cap of N
 *     would have broken (depth 1 = the most recently used instance).</li>
 * </ul>
 * Hits that reach unusually deep or unusually old are additionally kept in a small bounded list with
 * their page name and time, so the pages people actually reach back for can be identified.
 * <p>
 * Everything is static (per-JVM, since startup), lock-free on the recording path, and failure-proof
 * by design: the recording site wraps its call in a catch-all, so the stats can never break a page
 * restore.
 */
public final class PageCacheReuseStats {

	/** Upper bounds (inclusive) of the idle-at-reuse buckets, in seconds; beyond the last = final bucket. */
	static final long[] IDLE_BOUNDS_SECONDS = { 10, 60, 300, 900, 1800, 3600, 7200, 14400 };
	private static final String[] IDLE_LABELS = { "≤ 10s", "10s – 1m", "1 – 5m", "5 – 15m", "15 – 30m", "30m – 1h", "1 – 2h", "2 – 4h", "> 4h" };

	/** Upper bounds (inclusive) of the depth-at-reuse buckets; beyond the last = final bucket. */
	static final int[] DEPTH_BOUNDS = { 1, 3, 5, 10, 20, 30, 50, 100 };
	private static final String[] DEPTH_LABELS = { "1", "2 – 3", "4 – 5", "6 – 10", "11 – 20", "21 – 30", "31 – 50", "51 – 100", "> 100" };

	/** A hit deeper than this, or idler than {@link #NOTABLE_IDLE_SECONDS}, lands in the notable-reaches list. */
	private static final int NOTABLE_DEPTH = 20;
	private static final long NOTABLE_IDLE_SECONDS = 1800;
	private static final int NOTABLE_CAPACITY = 50;

	private static final LongAdder[] _idleBuckets = newAdders( IDLE_BOUNDS_SECONDS.length + 1 );
	private static final LongAdder[] _depthBuckets = newAdders( DEPTH_BOUNDS.length + 1 );
	private static final LongAdder _hits = new LongAdder();
	private static final LongAdder _misses = new LongAdder();
	private static final Instant _since = Instant.now();

	private static final ArrayDeque<NotableReach> _notableReaches = new ArrayDeque<>();

	private PageCacheReuseStats() {}

	/**
	 * One row of a reuse histogram: the bucket's count, and the number of hits BEYOND the bucket's
	 * upper bound - i.e. the restores that a TTL/cap set at that bound would have broken.
	 */
	public record ProfileRow( String label, long count, long beyondBound ) {}

	/** A hit that reached notably deep or notably far back in time. */
	public record NotableReach( Instant at, String pageName, int depth, long idleSeconds ) {}

	/**
	 * Records a cache hit. Called by the restore path BEFORE the hit re-tops the instance in the LRU.
	 *
	 * @param idleSeconds how long the restored instance had been idle
	 * @param depth the instance's LRU depth at restore time (1 = most recently used)
	 * @param pageName the restored page's component name
	 */
	public static void recordHit( final long idleSeconds, final int depth, final String pageName ) {
		_hits.increment();
		_idleBuckets[bucketFor( idleSeconds )].increment();
		_depthBuckets[depthBucketFor( depth )].increment();

		if( depth > NOTABLE_DEPTH || idleSeconds > NOTABLE_IDLE_SECONDS ) {
			synchronized( _notableReaches ) {
				if( _notableReaches.size() >= NOTABLE_CAPACITY ) {
					_notableReaches.removeFirst();
				}
				_notableReaches.addLast( new NotableReach( Instant.now(), pageName, depth, idleSeconds ) );
			}
		}
	}

	/** Records a restore attempt whose contextID had no cache entry. */
	public static void recordMiss() {
		_misses.increment();
	}

	public static long hits() {
		return _hits.sum();
	}

	public static long misses() {
		return _misses.sum();
	}

	public static Instant since() {
		return _since;
	}

	/** The idle-at-reuse histogram; each row's {@code beyondBound} = restores a TTL at the row's upper bound would have broken. */
	public static List<ProfileRow> idleProfile() {
		return profile( IDLE_LABELS, counts( _idleBuckets ) );
	}

	/** The depth-at-reuse histogram; each row's {@code beyondBound} = restores a cache cap at the row's upper bound would have broken. */
	public static List<ProfileRow> depthProfile() {
		return profile( DEPTH_LABELS, counts( _depthBuckets ) );
	}

	/** The most recent notably-deep / notably-old hits, oldest first. */
	public static List<NotableReach> notableReaches() {
		synchronized( _notableReaches ) {
			return new ArrayList<>( _notableReaches );
		}
	}

	private static List<ProfileRow> profile( final String[] labels, final long[] counts ) {
		final List<ProfileRow> rows = new ArrayList<>();

		for( int i = 0; i < labels.length; i++ ) {
			long beyond = 0;

			for( int j = i + 1; j < counts.length; j++ ) {
				beyond += counts[j];
			}

			rows.add( new ProfileRow( labels[i], counts[i], beyond ) );
		}

		return rows;
	}

	private static long[] counts( final LongAdder[] buckets ) {
		final long[] counts = new long[buckets.length];

		for( int i = 0; i < buckets.length; i++ ) {
			counts[i] = buckets[i].sum();
		}

		return counts;
	}

	private static int bucketFor( final long idleSeconds ) {
		for( int i = 0; i < IDLE_BOUNDS_SECONDS.length; i++ ) {
			if( idleSeconds <= IDLE_BOUNDS_SECONDS[i] ) {
				return i;
			}
		}

		return IDLE_BOUNDS_SECONDS.length;
	}

	private static int depthBucketFor( final int depth ) {
		for( int i = 0; i < DEPTH_BOUNDS.length; i++ ) {
			if( depth <= DEPTH_BOUNDS[i] ) {
				return i;
			}
		}

		return DEPTH_BOUNDS.length;
	}

	private static LongAdder[] newAdders( final int length ) {
		final LongAdder[] adders = new LongAdder[length];

		for( int i = 0; i < length; i++ ) {
			adders[i] = new LongAdder();
		}

		return adders;
	}
}