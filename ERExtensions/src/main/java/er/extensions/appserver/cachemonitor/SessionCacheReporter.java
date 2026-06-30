package er.extensions.appserver.cachemonitor;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOSession;
import com.webobjects.appserver._private.WOServerSessionStore;
import com.webobjects.appserver._private.WOTransactionRecord;

import er.extensions.appserver.ajax.ERXAjaxSession;

/**
 * Builds framework-neutral {@link CacheReport}s for a session's page caches, over BOTH cache families:
 * <ul>
 * <li>ERXAjaxSession's replacement cache - via the read-only {@code replacementCacheSnapshot()} accessor
 *     (full age data).</li>
 * <li>WO's own backtrack and permanent caches - via reflection on {@code WOSession._contextRecords} and
 *     {@code _permanentPageCache} (private, no getter; WO records carry no wall-clock age, so age is null).</li>
 * </ul>
 * Read-only throughout: it never mutates a cache. The UI/component consumes only the returned reports and
 * is identical across cache families; a future third cache is just another adapter method here.
 */
public class SessionCacheReporter {

	private static final Logger logger = LoggerFactory.getLogger( SessionCacheReporter.class );

	private SessionCacheReporter() {}

	/**
	 * All in-memory sessions held by the {@link WOServerSessionStore} (WO's default). Uses the store's public
	 * {@code _sessions()} accessor (a defensive copy of its session dictionary). Returns an empty list for a
	 * custom/non-server session store - callers fall back to the current session.
	 * <p>
	 * Note: a session enters this map only once it has been checked back in after a completed request, so a
	 * brand-new session that hasn't finished a request yet may not appear. The count therefore tracks
	 * "sessions that have completed at least one request", which is what we want for cache analysis.
	 */
	public static List<WOSession> activeSessions() {
		List<WOSession> sessions = new ArrayList<>();
		if( WOApplication.application().sessionStore() instanceof WOServerSessionStore store ) {
			// _sessions() returns a copy, so iterating it can't collide with concurrent check-in/out.
			for( Object value : store._sessions().values() ) {
				if( value instanceof WOSession session ) {
					sessions.add( session );
				}
			}
		}
		return sessions;
	}

	/**
	 * All cache reports for one session, in display order: Ajax replacement, WO backtrack, WO permanent.
	 * Any cache that can't be read (e.g. reflection blocked) is reported empty rather than throwing.
	 */
	public static List<CacheReport> reportsFor( WOSession session ) {
		List<CacheReport> reports = new ArrayList<>();
		reports.add( ajaxReplacementReport( session ) );
		reports.add( woBacktrackReport( session ) );
		reports.add( woPermanentReport( session ) );
		return reports;
	}

	// ---- ERXAjaxSession replacement cache (our cache; full age data) ----

	private static CacheReport ajaxReplacementReport( WOSession session ) {
		List<CachedPageEntry> entries = new ArrayList<>();
		if( session instanceof ERXAjaxSession ajax ) {
			for( Object[] t : ajax.replacementCacheSnapshot() ) {
				entries.add( new CachedPageEntry(
						(String) t[0],
						(String) t[1],
						(Instant) t[2],
						(Instant) t[3],
						null ) ); // reachDepth deferred
			}
		}
		return new CacheReport( "Ajax replacement", ERXAjaxSession.maxPageReplacementCacheSize(), true, entries );
	}

	// ---- WO backtrack cache (_contextRecords) - reflection, no age ----

	private static CacheReport woBacktrackReport( WOSession session ) {
		List<CachedPageEntry> entries = woEntriesFromTransactionRecordMap( session, "_contextRecords" );
		return new CacheReport( "WO backtrack", WOApplication.application().pageCacheSize(), false, entries );
	}

	// ---- WO permanent cache (_permanentPageCache: contextID -> WOComponent) - reflection, no age ----

	private static CacheReport woPermanentReport( WOSession session ) {
		List<CachedPageEntry> entries = new ArrayList<>();
		for( Map.Entry<?, ?> e : copyEntries( readField( session, "_permanentPageCache" ) ) ) {
			Object page = e.getValue();
			entries.add( new CachedPageEntry(
					page instanceof WOComponent c ? c.getClass().getSimpleName() : "(unknown)",
					String.valueOf( e.getKey() ),
					null, null, null ) );
		}
		return new CacheReport( "WO permanent", WOApplication.application().permanentPageCacheSize(), false, entries );
	}

	/** WO's backtrack cache is contextID -> WOTransactionRecord; map each record's responsePage to an entry. */
	private static List<CachedPageEntry> woEntriesFromTransactionRecordMap( WOSession session, String fieldName ) {
		List<CachedPageEntry> entries = new ArrayList<>();
		for( Map.Entry<?, ?> e : copyEntries( readField( session, fieldName ) ) ) {
			String pageClass = "(unknown)";
			if( e.getValue() instanceof WOTransactionRecord rec ) {
				WOComponent page = rec.responsePage();
				pageClass = page == null ? "(null)" : page.getClass().getSimpleName();
			}
			entries.add( new CachedPageEntry( pageClass, String.valueOf( e.getKey() ), null, null, null ) );
		}
		return entries;
	}

	/**
	 * Defensively copy a (possibly live) cache map's entries before iterating. Another request can mutate
	 * the session's caches concurrently (eviction), so iterating the live map could throw
	 * ConcurrentModificationException - especially when reporting across sessions under concurrent request
	 * handling. On any failure this returns empty: a missing/partial cache card beats a broken page.
	 */
	private static List<Map.Entry<?, ?>> copyEntries( Object raw ) {
		List<Map.Entry<?, ?>> copy = new ArrayList<>();
		if( raw instanceof Map<?, ?> map ) {
			try {
				copy.addAll( map.entrySet() );
			}
			catch( RuntimeException e ) {
				logger.debug( "Cache map changed during read, reporting empty: {}", e.toString() );
				return new ArrayList<>();
			}
		}
		return copy;
	}

	/**
	 * Read a private field off a WOSession by name (the WO caches have no getters). Returns null and logs
	 * at debug on any failure - the report degrades to "empty" for that cache rather than breaking the page.
	 */
	private static Object readField( WOSession session, String fieldName ) {
		return readDeclaredField( session, fieldName );
	}

	/**
	 * Read a private field by name off any object, searching up its class hierarchy (WO's caches and the
	 * session store have no getters). Returns null and logs at debug on any failure - reporting degrades to
	 * "empty" rather than breaking the page.
	 */
	private static Object readDeclaredField( Object target, String fieldName ) {
		if( target == null ) {
			return null;
		}
		for( Class<?> c = target.getClass(); c != null; c = c.getSuperclass() ) {
			try {
				Field field = c.getDeclaredField( fieldName );
				field.setAccessible( true );
				return field.get( target );
			}
			catch( NoSuchFieldException e ) {
				// try the superclass
			}
			catch( ReflectiveOperationException | RuntimeException e ) {
				logger.debug( "Could not read {}.{} for cache report: {}", target.getClass().getSimpleName(), fieldName, e.toString() );
				return null;
			}
		}
		logger.debug( "Field {} not found on {} for cache report", fieldName, target.getClass().getSimpleName() );
		return null;
	}
}
