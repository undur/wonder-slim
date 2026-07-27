package er.extensions.appserver.cachemonitor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOSession;

import er.extensions.components.ERXComponent;

/**
 * A self-contained admin page giving a scannable, one-row-per-session overview of every in-memory
 * session's page caches - so you can spot the heavy / stale sessions across hundreds of them, then drill
 * into one for the full per-cache, per-class detail.
 * <p>
 * Fully framework-neutral: it enumerates sessions via {@link SessionCacheReporter#activeSessions()} (which
 * reflects the WO server session store), so no app-specific session registry is needed. An app installs it
 * just by pointing a menu/route at this class. Bring your own chrome by subclassing, or embed
 * {@link ERXSessionCacheOverview} directly for a single session.
 *
 * @binding activeWindowMinutes "active" = touched within this many minutes (default 5)
 * @binding staleThresholdMinutes "stale/abandoned" = idle longer than this many minutes (default 10)
 */
public class ERXSessionCacheOverviewPage extends ERXComponent {

	/** Repetition state: the summary row currently being rendered. */
	public SessionCacheSummary currentSummary;

	/** The session whose detail is expanded (null = none); set by the drill-down link. */
	private String _expandedSessionID;

	private Instant _now;

	public ERXSessionCacheOverviewPage( WOContext context ) {
		super( context );
	}

	/** Captured once per render so every relative time on the page is consistent. */
	public Instant now() {
		if( _now == null ) {
			_now = Instant.now();
		}
		return _now;
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

	/**
	 * All sessions the store knows, plus the current one if it isn't already listed. The viewing session is
	 * checked out of the store while its own request is in flight, so it can be absent from the store's map -
	 * we union it in (deduped by id) so you always at least see your own.
	 */
	private List<WOSession> sessions() {
		List<WOSession> sessions = SessionCacheReporter.activeSessions();
		if( context().hasSession() ) {
			WOSession current = context().session();
			boolean alreadyListed = sessions.stream().anyMatch( s -> s.sessionID().equals( current.sessionID() ) );
			if( !alreadyListed ) {
				sessions.add( current );
			}
		}
		return sessions;
	}

	public int sessionCount() {
		return sessions().size();
	}

	/** One condensed row per session, heaviest (most total held) first. */
	public List<SessionCacheSummary> summaries() {
		Instant now = now();
		Duration window = activeWindow();
		Duration stale = staleThreshold();
		List<SessionCacheSummary> rows = new ArrayList<>();
		for( WOSession s : sessions() ) {
			rows.add( SessionCacheSummary.of( s.sessionID(), SessionCacheReporter.reportsFor( s ), window, stale, now ) );
		}
		rows.sort( ( a, b ) -> Integer.compare( b.totalHeld(), a.totalHeld() ) );
		return rows;
	}

	// ---- reuse profile (app-wide, since startup; see PageCacheReuseStats) ----

	/** Repetition state: the histogram row currently being rendered. */
	public PageCacheReuseStats.ProfileRow currentProfileRow;

	/** Repetition state: the notable reach currently being rendered. */
	public PageCacheReuseStats.NotableReach currentReach;

	public List<PageCacheReuseStats.ProfileRow> idleProfile() {
		return PageCacheReuseStats.idleProfile();
	}

	public List<PageCacheReuseStats.ProfileRow> depthProfile() {
		return PageCacheReuseStats.depthProfile();
	}

	public List<PageCacheReuseStats.NotableReach> notableReaches() {
		return PageCacheReuseStats.notableReaches();
	}

	public long reuseHits() {
		return PageCacheReuseStats.hits();
	}

	public long reuseMisses() {
		return PageCacheReuseStats.misses();
	}

	public long reuseExpiredSessionAttempts() {
		return PageCacheReuseStats.expiredSessionAttempts();
	}

	public boolean hasReuseData() {
		return reuseHits() > 0;
	}

	public boolean hasNotableReaches() {
		return !notableReaches().isEmpty();
	}

	public String reuseSince() {
		return ERXSessionCacheOverview.humanize( Duration.between( PageCacheReuseStats.since(), now() ) );
	}

	public String currentReachAgo() {
		return ERXSessionCacheOverview.humanize( Duration.between( currentReach.at(), now() ) );
	}

	public String currentReachIdle() {
		return ERXSessionCacheOverview.humanize( Duration.ofSeconds( currentReach.idleSeconds() ) );
	}

	// ---- per-row helpers ----

	public String currentTopStale() {
		if( currentSummary.topStaleClass() == null ) {
			return "–";
		}
		return currentSummary.topStaleClass() + " (" + ERXSessionCacheOverview.humanize( currentSummary.topStaleIdle() ) + ")";
	}

	/** Highlight rows whose held set is mostly stale - the ones worth investigating. */
	public boolean currentRowIsMostlyStale() {
		return currentSummary.totalHeld() > 0 && currentSummary.staleHeld() * 2 >= currentSummary.totalHeld();
	}

	/** Row css: always clickable; flagged when mostly stale. */
	public String currentRowClass() {
		return currentRowIsMostlyStale() ? "erx-expand erx-stale-row" : "erx-expand";
	}

	public boolean isCurrentExpanded() {
		return currentSummary.sessionID().equals( _expandedSessionID );
	}

	/** The WOSession matching the expanded id, for the embedded detail component (null if none/gone). */
	public WOSession expandedSession() {
		if( _expandedSessionID == null ) {
			return null;
		}
		for( WOSession s : sessions() ) {
			if( _expandedSessionID.equals( s.sessionID() ) ) {
				return s;
			}
		}
		return null;
	}

	public boolean hasExpandedSession() {
		return expandedSession() != null;
	}

	/** Toggle the drill-down for the row's session (collapses if it's already the expanded one). */
	public WOComponent toggleExpand() {
		String id = currentSummary.sessionID();
		_expandedSessionID = id.equals( _expandedSessionID ) ? null : id;
		return context().page();
	}
}
