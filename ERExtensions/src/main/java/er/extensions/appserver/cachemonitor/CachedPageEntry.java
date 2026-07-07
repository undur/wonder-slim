package er.extensions.appserver.cachemonitor;

import java.time.Duration;
import java.time.Instant;

/**
 * One cached page, described in framework-neutral terms - so the same reporting/UI code works over both
 * ERXAjaxSession's replacement cache (which records age) and WO's own page caches (which do not).
 * <p>
 * {@code createdAt} / {@code lastAccessedAt} / {@code reachDepth} are nullable: present where the source
 * cache tracks them, absent where it does not (e.g. WO's backtrack/permanent caches carry no wall-clock
 * age). Absence is shown honestly as "-", never faked.
 *
 * @param pageClass the simple class name of the cached page (the grouping key for "which classes eat the cache")
 * @param contextID the context id this entry is keyed under (diagnostic)
 * @param instanceKey identity of the live page INSTANCE this entry resolves to - many entries typically
 *        share one instance (one entry per interaction), so reporting dedupes by this key to count
 *        instances (the number a cache cap actually bounds). Null when the source can't identify it;
 *        such entries are conservatively counted as one instance each
 * @param createdAt when the entry was stored, or null if the source cache doesn't track it
 * @param lastAccessedAt when the entry was last resolved by a restore, or null if not tracked
 * @param reachDepth the LRU-rank at which a restore last resolved this entry (0 = most-recent), or null if not measured
 */
public record CachedPageEntry(
		String pageClass,
		String contextID,
		Integer instanceKey,
		Instant createdAt,
		Instant lastAccessedAt,
		Integer reachDepth) {

	/** Age = now - createdAt, or null if createdAt is unknown. */
	public Duration age( Instant now ) {
		return createdAt == null ? null : Duration.between( createdAt, now );
	}

	/** Idle time = now - lastAccessedAt, or null if unknown. This is the "abandoned tail" signal. */
	public Duration idle( Instant now ) {
		return lastAccessedAt == null ? null : Duration.between( lastAccessedAt, now );
	}

	/** Whether this entry has been idle longer than the given threshold (false if idle time is unknown). */
	public boolean isIdleLongerThan( Duration threshold, Instant now ) {
		Duration idle = idle( now );
		return idle != null && idle.compareTo( threshold ) > 0;
	}
}
