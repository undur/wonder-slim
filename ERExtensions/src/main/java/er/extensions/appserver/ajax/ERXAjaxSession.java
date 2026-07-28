/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.appserver.ajax;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOSession;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.appserver.cachemonitor.PageCacheReuseStats;
import er.extensions.foundation.ERXProperties;

/**
 * ERXAjaxSession owns THE page cache: one session-side map of {@code contextID -> live page instance}
 * that serves every page restore - the back button, plain component actions and ajax updates alike.
 * WO's private backtrack cache ({@code _contextRecords}) is never fed: {@link #savePage} stores here
 * instead of calling super, so the stock cache stays empty and there is exactly one place a page can
 * live and exactly one eviction policy deciding its lifetime.
 * <p>
 * The cache keys each rendered page by its render contextID. Every request mints a new contextID, so
 * one page instance accumulates many contextID keys over its life - a still-rendered link carries the
 * contextID it was rendered in, and that key is still here, so it resolves directly however many
 * interactions ago it was rendered. The cache is bounded by the number of distinct page INSTANCES (not
 * contextIDs), configured by WO's own knob: {@link WOApplication#pageCacheSize()} ({@code WOPageCacheSize},
 * default 30). Note the semantic shift from stock WO: the bound counts live page instances retained,
 * not backtrack steps - each retained instance is a real page tree in memory, so prefer small values.
 * Eviction is LRU over instances; evicting an instance drops all its contextID keys together.
 * <p>
 * Unifying the caches also absorbs the one job WO's backtrack cache did besides storing pages: the
 * repeated-request guard behind page-refresh-on-backtrack. When (and only when)
 * {@link WOApplication#isPageRefreshOnBacktrackEnabled()} is on, entries stored for component actions
 * record the provenance of the request that produced them (request contextID + senderID + form values);
 * {@link #contextIDForRepeatedRequest} answers "was this exact request already handled?" and the
 * component request handler then re-renders the stored result instead of re-invoking the action - the
 * same protection stock WO provided through {@code _contextIDMatchingIDs}, with the same gating. With
 * the flag off (the default), an identical re-submit re-executes the action, as it always has in WO.
 * <p>
 * WO's permanent page cache (a frames-era feature: exempt a long-lived, never-re-requested page - a
 * frameset, a navigation frame - from context-churn eviction) is deliberately obsolete: instance-LRU
 * eviction removed the problem it solved, and {@link #savePageInPermanentCache} now THROWS rather than
 * silently meaning something weaker than the caller asked for.
 *
 * @property WOPageCacheSize number of distinct live page instances retained per session (default 30)
 * @property er.extensions.appserver.ajax.ERXAjaxSession.storesPageInfo=false
 * @property er.extensions.appserver.ajax.ERXAjaxSession.logPageCache log cache structure on every store
 *
 * @author mschrag
 */
public class ERXAjaxSession extends WOSession {

	/**
	 * Key that tells the session not to store the current page. Checks both the
	 * response userInfo and the response headers if this key is present. The value doesn't matter,
	 * but you need to update the corresponding value in AjaxUtils.  This is to keep the dependencies
	 * between the two frameworks independent.
	 */
	public static final String DONT_STORE_PAGE = "erxsession.dont_store_page";
	public static final String FORCE_STORE_PAGE = "erxsession.force_store_page";

	/**
	 * Key under which ajax update elements announce the container an update rendered
	 * (set as a request/response header by AjaxUtils). With the unified cache this no longer decides
	 * WHERE a page is stored - it marks that an ajax render produced restorable content (so the render's
	 * contextID is worth an entry at all), and the value is kept on the entry as a diagnostic tag.
	 */
	public static final String PAGE_REPLACEMENT_CACHE_LOOKUP_KEY = "page_cache_key";

	/** When true, log the cache size + page-&gt;container structure each time a page is stored
	 *  (see {@link #pageCacheSummary()}). Off by default. */
	private static boolean logPageCache = ERXProperties.booleanForKey("er.extensions.appserver.ajax.ERXAjaxSession.logPageCache");

	private static boolean storesPageInfo = ERXProperties.booleanForKeyWithDefault("er.extensions.appserver.ajax.ERXAjaxSession.storesPageInfo", false);

	private NSMutableDictionary<WOComponent, NSMutableDictionary<String, Object>> pageInfoDictionary;

	private static final Logger log = LoggerFactory.getLogger(ERXAjaxSession.class);

	static {
		if (ERXProperties.stringForKey("er.extensions.maxPageReplacementCacheSize") != null) {
			log.warn("er.extensions.maxPageReplacementCacheSize is no longer used. The page cache is bounded by WOApplication.pageCacheSize() (WOPageCacheSize, default 30) - remove the old property.");
		}
		if (ERXProperties.stringForKey("er.extensions.appserver.ajax.ERXAjaxSession.logPageReplacementCache") != null) {
			log.warn("er.extensions.appserver.ajax.ERXAjaxSession.logPageReplacementCache was renamed to er.extensions.appserver.ajax.ERXAjaxSession.logPageCache - update the property.");
		}
		if (ERXProperties.stringForKey("er.extensions.overridePrivateCache") != null) {
			log.warn("er.extensions.overridePrivateCache is no longer used - the unified page cache always replaces WO's private caches. Remove the property.");
		}
	}

	/**
	 * The unified page cache: render contextID -> record holding the live page instance. Insertion
	 * order doubles as LRU order over instances (see {@link #touchInstanceInPageCache}). Access is safe
	 * without synchronization because WO serializes request handling per session; the read-only
	 * monitoring accessors defensively copy instead.
	 */
	private LinkedHashMap<String, TransactionRecord> _pageCache;

	protected LinkedHashMap<String, TransactionRecord> _pageCache() {
		if (_pageCache == null) {
			_pageCache = new LinkedHashMap<>();
		}
		return _pageCache;
	}

	public boolean storesPageInfo() {
		return storesPageInfo;
	}

	public NSMutableDictionary<WOComponent, NSMutableDictionary<String,Object>> pageInfoDictionary() {
		if(pageInfoDictionary == null) {
			pageInfoDictionary = new NSMutableDictionary<WOComponent, NSMutableDictionary<String,Object>>();
		}
		return pageInfoDictionary;
	}

	/**
	 * One cache entry: a live page instance plus, for entries created by a plain component action, the
	 * provenance of the request that produced this page state - the request's contextID, senderID and a
	 * fingerprint of its form values. Provenance feeds {@link #contextIDForRepeatedRequest} (the
	 * refresh/double-submit guard). Ajax-produced entries carry no provenance (the guard doesn't apply
	 * to them) but may carry a container key, kept purely as a diagnostic tag.
	 * <p>
	 * Two timestamps are tracked purely for diagnostics (e.g. the exception-page cache report): when the
	 * entry was first stored ({@link #createdAt()}) and when it was last resolved by a restore
	 * ({@link #lastAccessedAt()}). They do not drive eviction - that is LRU over instances via the cache's
	 * insertion order - so they are free to read and never affect cache behavior.
	 */
	static class TransactionRecord implements Serializable {

		private WOComponent _page;
		private String _requestContextID;
		private String _senderID;
		private String _formValuesFingerprint;
		private String _key;
		private Instant _createdAt;
		private Instant _lastAccessedAt;

		public TransactionRecord(WOComponent page, WOContext context, String key) {
			_page = page;
			_requestContextID = context._requestContextID();
			_key = key;
			_createdAt = Instant.now();
			_lastAccessedAt = _createdAt;
		}

		public WOComponent page() {
			return _page;
		}

		public String key() {
			return _key;
		}

		/** The contextID of the REQUEST that produced this page state (null for a fresh page). */
		public String requestContextID() {
			return _requestContextID;
		}

		/** The senderID of the producing request; non-null only when provenance was recorded. */
		public String senderID() {
			return _senderID;
		}

		/** Fingerprint of the producing request's form values; non-null only with provenance. */
		public String formValuesFingerprint() {
			return _formValuesFingerprint;
		}

		/** Record the producing request's identity for the repeated-request guard. */
		public void recordProvenance(String senderID, String formValuesFingerprint) {
			_senderID = senderID;
			_formValuesFingerprint = formValuesFingerprint;
		}

		/** When this entry was stored in the cache. */
		public Instant createdAt() {
			return _createdAt;
		}

		/** When this entry was last resolved by a restore (= the created time until it is first reused). */
		public Instant lastAccessedAt() {
			return _lastAccessedAt;
		}

		/** Mark this entry as just used (called when a restore resolves it). */
		public void markAccessed() {
			_lastAccessedAt = Instant.now();
		}

		@Override
		public String toString() {
			return "[TransactionRecord: page = " + _page.name() + "; requestContext = " + _requestContextID + "; key = " + _key + "]";
		}
	}

	public ERXAjaxSession() {
		super();
	}

	public ERXAjaxSession(String sessionID) {
		super(sessionID);
	}

	/**
	 * Stores the current page in the unified cache, keyed by the contextID this render happened in -
	 * which is exactly the contextID a link rendered now will carry when the user later clicks it, so a
	 * returning link (or the back button) does a direct get(contextID) hit. Never calls super: WO's
	 * private backtrack cache is deliberately left empty, so this map is the only place pages live and
	 * {@link WOApplication#pageCacheSize()} is the only bound.
	 * <p>
	 * Every interaction mints a new contextID, so one PAGE INSTANCE accumulates many contextID keys over
	 * its life - that is fine: they are cheap pointers to the same live object. We do NOT bound the
	 * contextID count; we bound the number of distinct page INSTANCES (see
	 * {@link #enforcePageCacheInstanceLimit}). When an instance is evicted, all of its contextID keys go
	 * together. ContextIDs are session-unique, so two pages never collide (no bleed).
	 * <p>
	 * One class of render is deliberately NOT stored: an ajax request that produced no restorable
	 * content (flagged {@link #DONT_STORE_PAGE} with no {@link #PAGE_REPLACEMENT_CACHE_LOOKUP_KEY}) -
	 * e.g. a background progress ping. Its response rendered no links, so nothing will ever reference
	 * its contextID, and storing an entry per ping would bloat the alias count for pollers.
	 * <p>
	 * Entries stored for plain (non-ajax) component actions additionally record the provenance of the
	 * producing request, feeding the repeated-request guard ({@link #contextIDForRepeatedRequest}).
	 */
	@Override
	public void savePage(WOComponent page) {
		WOContext context = context();
		if (page == null || context == null) {
			return;
		}
		if (WOApplication.application().pageCacheSize() == 0) {
			// Page caching disabled app-wide - same meaning the knob always had.
			return;
		}
		if (ERXAjaxApplication.shouldNotStorePage(context)) {
			// An ajax render. Store only if an update element announced rendered content via the
			// page-cache-key header; a keyless ajax response rendered nothing restorable.
			WORequest request = context.request();
			WOResponse response = context.response();
			String pageCacheKey = null;
			if (response != null) {
				pageCacheKey = response.headerForKey(PAGE_REPLACEMENT_CACHE_LOOKUP_KEY);
			}
			if (pageCacheKey == null && request != null) {
				pageCacheKey = request.headerForKey(PAGE_REPLACEMENT_CACHE_LOOKUP_KEY);
			}
			if (pageCacheKey != null) {
				storePage(page, context, pageCacheKey, false);
				ERXAjaxApplication.cleanUpHeaders(response);
			}
			else {
				log.debug("Not caching ajax render with no page cache key (contextID {})", context.contextID());
			}
		}
		else {
			storePage(page, context, null, true);
		}
	}

	/**
	 * The single store path: bound by instances, touch for LRU, then put under this render's contextID.
	 *
	 * @param page the page to store
	 * @param context the context this render happened in
	 * @param diagnosticKey the ajax container key, if any (diagnostics only)
	 * @param mayRecordProvenance whether this save may record request provenance for the repeat guard
	 */
	private void storePage(WOComponent page, WOContext context, String diagnosticKey, boolean mayRecordProvenance) {
		LinkedHashMap<String, TransactionRecord> pageCache = _pageCache();

		// The map instance doubles as the lock: mutations from the session's own request thread are
		// already serialized by session checkout, but the memory-pressure purge (and the cross-session
		// cache overview) touch this map from other threads. Contention is nil in normal operation.
		synchronized (pageCache) {
			// Memory bound: cap the number of distinct PAGE INSTANCES, not contextIDs. Adding a context for
			// an instance we already hold is free; a genuinely new instance over the limit evicts the
			// least-recently-used instance (all its contextID keys).
			enforcePageCacheInstanceLimit(pageCache, page);

			// Touch: move this instance's existing contextID entries to the tail so eviction is LRU over
			// instances (drop the instance gone longest without use), not FIFO by first-seen contextID.
			touchInstanceInPageCache(pageCache, page);

			TransactionRecord record = new TransactionRecord(page, context, diagnosticKey);
			if (mayRecordProvenance && WOApplication.application().isPageRefreshOnBacktrackEnabled()) {
				WORequest request = context.request();
				String senderID = context.senderID();
				// Provenance only makes sense for a component action (a request with a sender), and only where a
				// byte-identical replay is detectable: multipart bodies are streamed, not comparable, and ajax
				// requests are excluded because the guard never applies to them (see contextIDForRepeatedRequest).
				// Recorded only under page-refresh-on-backtrack - the one mode where the guard can fire.
				if (request != null && senderID != null && !senderID.isEmpty()
						&& !request.isMultipartFormData() && !ERXAjaxApplication.isAjaxRequest(request)) {
					record.recordProvenance(senderID, requestFingerprint(request));
				}
			}
			pageCache.put(context.contextID(), record);

			// Per-request visibility into the page cache. Off by default (free in production); set
			// er.extensions.appserver.ajax.ERXAjaxSession.logPageCache=true to watch the cache size +
			// page->container structure on each store - the easiest way to confirm it stays bounded by
			// instances and to debug stale-link issues. (Inside the lock: pageCacheSummary iterates.)
			if (logPageCache && log.isInfoEnabled()) {
				log.info("[page-cache] stored {} for context {} -> {}", record.key() == null ? page.name() : record.key(), context.contextID(), pageCacheSummary());
			}
		}
	}

	/**
	 * The repeated-request guard, replacing what WO's backtrack cache provided through
	 * {@code _contextIDMatchingIDs}: detects that the incoming component-action request is a byte-level
	 * repeat of one this session already handled - same request contextID, same senderID, same form
	 * values - and returns the contextID of the page state that request produced, so the component
	 * request handler can re-render that stored result WITHOUT re-invoking the action.
	 * <p>
	 * ACTIVE ONLY under {@link WOApplication#isPageRefreshOnBacktrackEnabled()}, exactly like stock WO
	 * (verified empirically against the pre-unification stack: with the flag off, an identical re-submit
	 * re-executes the action; with it on, the repeat is answered from the stored page). The gating is the
	 * point, not an accident: page-refresh-on-backtrack disables client caching so the BROWSER re-issues
	 * requests - including re-POSTs - as part of ordinary history navigation, and those replays must not
	 * re-execute actions. Without that mode, an identical re-submit is a deliberate client act, and
	 * re-executing it is the expected WO behavior; idempotency is the application's business.
	 * <p>
	 * Returns null when the guard is inactive, the request is new, or the request is one the guard
	 * deliberately ignores: ajax requests (their contexts never entered WO's cache either), multipart
	 * uploads (streamed bodies aren't comparable) and requests without a sender. Note the built-in
	 * asymmetry: every response re-renders its links under a fresh contextID, so only a genuinely
	 * un-re-rendered repeat can ever match.
	 *
	 * @param context the current request's context (request contextID and senderID already parsed)
	 * @return the contextID whose stored page answers this repeat, or null to process the request normally
	 */
	public String contextIDForRepeatedRequest(WOContext context) {
		if (!WOApplication.application().isPageRefreshOnBacktrackEnabled()) {
			return null;
		}
		if (_pageCache == null || _pageCache.isEmpty() || context == null) {
			return null;
		}
		WORequest request = context.request();
		if (request == null || ERXAjaxApplication.isAjaxRequest(request) || request.isMultipartFormData()) {
			return null;
		}
		String requestContextID = context._requestContextID();
		String senderID = context.senderID();
		if (requestContextID == null || senderID == null || senderID.isEmpty()) {
			return null;
		}
		String fingerprint = requestFingerprint(request);
		String match = null;
		for (Map.Entry<String, TransactionRecord> entry : _pageCache.entrySet()) {
			TransactionRecord record = entry.getValue();
			if (record.senderID() != null
					&& requestContextID.equals(record.requestContextID())
					&& senderID.equals(record.senderID())
					&& fingerprint.equals(record.formValuesFingerprint())) {
				// Keep scanning: with several identical repeats the LAST (most recent) state wins.
				match = entry.getKey();
			}
		}
		if (match != null) {
			log.debug("Repeated request detected (request context {}, sender {}) -> stored context {}", requestContextID, senderID, match);
		}
		return match;
	}

	/**
	 * A stable, order-independent fingerprint of a request's form values, compared to detect a repeated
	 * request. Keys are sorted so multi-value ordering quirks can't cause false negatives; values are
	 * kept verbatim (a hash could collide, and a false match would silently SKIP an action).
	 */
	private static String requestFingerprint(WORequest request) {
		NSDictionary<String, ?> formValues = request.formValues();
		if (formValues == null || formValues.count() == 0) {
			return "";
		}
		TreeMap<String, String> sorted = new TreeMap<>();
		for (String key : formValues.allKeys()) {
			sorted.put(key, String.valueOf(formValues.objectForKey(key)));
		}
		return sorted.toString();
	}

	/**
	 * A human-readable summary of the page cache for the current session: how many contextID entries it
	 * holds and how many DISTINCT page instances those point at. The cache is bounded by the instance
	 * count (each instance accumulates many contextID keys over its life), so this is the line to watch:
	 * entries can climb freely while instances stays small and bounded.
	 *
	 * @return a single-line summary, e.g. {@code pageCache: 42 contexts / 2 instance(s)}
	 */
	public String pageCacheSummary() {
		LinkedHashMap<String, TransactionRecord> cache = _pageCache;
		if (cache == null || cache.isEmpty()) {
			return "pageCache: empty";
		}
		LinkedHashSet<WOComponent> instances = new LinkedHashSet<>();
		Instant oldestStored = null;
		Instant newestUsed = null;
		for (TransactionRecord record : cache.values()) {
			if (record.page() != null) {
				instances.add(record.page());
			}
			if (oldestStored == null || record.createdAt().isBefore(oldestStored)) {
				oldestStored = record.createdAt();
			}
			if (newestUsed == null || record.lastAccessedAt().isAfter(newestUsed)) {
				newestUsed = record.lastAccessedAt();
			}
		}
		Instant now = Instant.now();
		return "pageCache: " + cache.size() + " contexts / " + instances.size() + " instance(s)"
				+ " (oldest stored " + ago(oldestStored, now) + ", last used " + ago(newestUsed, now) + ")";
	}

	/**
	 * A read-only snapshot of the page cache for monitoring/reporting. Returns one neutral
	 * {@code [pageClass, contextID, createdAt, lastAccessedAt, instanceKey]} tuple per entry, in the
	 * cache's current (LRU) order. {@code instanceKey} identifies the live page INSTANCE the entry
	 * resolves to ({@code System.identityHashCode}) - many contextID entries typically point at one
	 * instance (one entry per interaction), and the cache's cap bounds INSTANCES, so a consumer must
	 * dedupe by this key to report anything meaningful against the cap. Purely additive: it reads the
	 * existing {@link TransactionRecord} fields and does NOT touch eviction, storage, ordering, or access
	 * timestamps - calling it never changes cache behavior.
	 * <p>
	 * The element type is {@code Object[]} of {@code {String, String, Instant, Instant, Integer}} to avoid
	 * coupling this framework package to the cache-monitor model; the monitor adapter maps these to its
	 * own DTO.
	 *
	 * @return one tuple per cached entry (empty if the cache is absent/empty)
	 */
	public List<Object[]> pageCacheSnapshot() {
		LinkedHashMap<String, TransactionRecord> cache = _pageCache;
		List<Object[]> snapshot = new ArrayList<>();
		if (cache != null) {
			// Copy the values out before reading them: a concurrent request may evict/touch the cache while a
			// monitoring page iterates it (esp. across sessions, under concurrent request handling), which could
			// otherwise throw ConcurrentModificationException. A best-effort copy degrades to a partial/empty
			// snapshot rather than failing the page.
			List<TransactionRecord> records;
			try {
				records = new ArrayList<>(cache.values());
			}
			catch (RuntimeException e) {
				log.debug("pageCacheSnapshot: cache changed during copy, returning empty: {}", e.toString());
				return snapshot;
			}
			for (TransactionRecord record : records) {
				WOComponent page = record.page();
				snapshot.add(new Object[] {
						page == null ? "(null)" : page.getClass().getSimpleName(),
						record.requestContextID(),
						record.createdAt(),
						record.lastAccessedAt(),
						page == null ? null : Integer.valueOf(System.identityHashCode(page)) });
			}
		}
		return snapshot;
	}

	/**
	 * Moves every cache entry pointing at {@code page} to the most-recent end of the map's insertion
	 * order, so the instance limit (which reads that order oldest-first) evicts the least-recently-USED
	 * instance rather than the first-inserted one (LRU, not FIFO). Without this, an instance you open
	 * first and keep returning to is the oldest by insertion and so the first evicted.
	 * <p>
	 * Matching is by object identity ({@code record.page() == page}), so all the contextID keys that point
	 * at this one live instance travel together.
	 *
	 * @param pageCache the cache
	 * @param page the page instance that was just stored or restored; no-op if null or not present
	 */
	protected void touchInstanceInPageCache(LinkedHashMap<String, TransactionRecord> pageCache, WOComponent page) {
		if (pageCache == null || page == null) {
			return;
		}
		LinkedHashMap<String, TransactionRecord> moved = new LinkedHashMap<>();
		for (Map.Entry<String, TransactionRecord> entry : pageCache.entrySet()) {
			if (entry.getValue().page() == page) {
				moved.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, TransactionRecord> e : moved.entrySet()) {
			pageCache.remove(e.getKey());
			pageCache.put(e.getKey(), e.getValue());
		}
	}

	/**
	 * Bounds the cache by the number of distinct page INSTANCES it holds, not by the number of contextID
	 * entries. One page instance accumulates many contextID keys over its life (one per interaction) -
	 * those are cheap pointers to the same live object and are NOT bounded. What is bounded is the count
	 * of distinct live instances: when a genuinely new instance would push us over
	 * {@link WOApplication#pageCacheSize()}, the least-recently-used instance is evicted and ALL of its
	 * contextID keys go with it (along with its pageInfo entry, when page info is enabled).
	 * <p>
	 * Instances are compared by object identity. The cache's insertion order is LRU-over-instances (see
	 * {@link #touchInstanceInPageCache}), so the instance owning the head entry is the one gone
	 * longest without use - the right one to drop.
	 *
	 * @param pageCache the cache being added to
	 * @param currentPage the page instance about to be (re)stored; never evicted
	 */
	protected void enforcePageCacheInstanceLimit(LinkedHashMap<String, TransactionRecord> pageCache, WOComponent currentPage) {
		// Distinct instances in cache order (least-recently-used first), using identity.
		LinkedHashSet<WOComponent> instances = new LinkedHashSet<>();
		for (TransactionRecord record : pageCache.values()) {
			WOComponent p = record.page();
			if (p != null) {
				instances.add(p);
			}
		}
		// Already holding this instance, or under the limit -> nothing to evict.
		if (instances.contains(currentPage) || instances.size() < WOApplication.application().pageCacheSize()) {
			return;
		}
		// Over the limit and adding a NEW instance: drop every entry of the least-recently-used instance.
		WOComponent oldest = instances.iterator().next();
		Iterator<Map.Entry<String, TransactionRecord>> evictEnum = pageCache.entrySet().iterator();
		while (evictEnum.hasNext()) {
			Map.Entry<String, TransactionRecord> entry = evictEnum.next();
			if (entry.getValue().page() == oldest) {
				if (log.isDebugEnabled()) log.debug("Instance-limit reached; evicting context {} of LRU instance", entry.getKey());
				evictEnum.remove();
			}
		}
		if (storesPageInfo()) {
			pageInfoDictionary().removeObjectForKey(oldest);
		}
	}

	/**
	 * Trims the cache down to at most {@code maxInstances} distinct page instances, evicting least
	 * recently used first - the memory-pressure valve's entry point (see
	 * {@code ERXPageCachePressureValve}). Never trims below one instance: the most recently used one
	 * is the session's current page for backtracking purposes, and purging it would break the very
	 * next interaction of a merely-slow user. Safe to call from any thread (synchronizes on the
	 * cache map, like every other cache access).
	 *
	 * @param maxInstances the instance count to trim down to (floored at 1)
	 * @return the number of page instances evicted
	 */
	public int trimPageCacheToInstanceCount(int maxInstances) {
		LinkedHashMap<String, TransactionRecord> cache = _pageCache;
		if (cache == null) {
			return 0;
		}
		final int keep = Math.max(1, maxInstances);
		synchronized (cache) {
			if (cache.isEmpty()) {
				return 0;
			}
			// Distinct instances in cache order = LRU-over-instances, least recently used first.
			final java.util.Set<WOComponent> instances = java.util.Collections.newSetFromMap(new java.util.LinkedHashMap<>());
			for (TransactionRecord record : cache.values()) {
				instances.add(record.page());
			}
			final int evictCount = instances.size() - keep;
			if (evictCount <= 0) {
				return 0;
			}
			final java.util.Set<WOComponent> doomed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
			for (WOComponent page : instances) {
				if (doomed.size() >= evictCount) {
					break;
				}
				doomed.add(page);
			}
			final Iterator<Map.Entry<String, TransactionRecord>> entries = cache.entrySet().iterator();
			while (entries.hasNext()) {
				if (doomed.contains(entries.next().getValue().page())) {
					entries.remove();
				}
			}
			if (storesPageInfo()) {
				for (WOComponent page : doomed) {
					pageInfoDictionary().removeObjectForKey(page);
				}
			}
			return doomed.size();
		}
	}

	/**
	 * Semi-private method that saves the current page. This is the request handler's per-request entry
	 * point into page storage; with the unified cache the permanent-cache branching is gone and every
	 * page goes through {@link #savePage}.
	 */
	@Override
	public void _saveCurrentPage() {
		WOContext currentContext = context();
		if (currentContext != null) {
			WOComponent currentPage = currentContext._pageComponent();
			if (currentPage != null && currentPage._isPage()) {
				savePage(currentPage);
			}
		}
	}

	/**
	 * The permanent page cache is deliberately OBSOLETE under the unified page cache, and asking for it
	 * fails loudly rather than silently meaning something else. It was a frames-era feature: a page
	 * loaded once and never re-requested (a frameset, a navigation frame) needed exemption from the
	 * backtrack cache's context-churn eviction or its links would die while it was still on screen. The
	 * unified cache removed context churn as an eviction force - pages are retained by instance-LRU -
	 * so the problem the feature solved no longer exists, and no pinning exemption is offered.
	 * <p>
	 * Throwing (rather than quietly routing to {@link #savePage}) is the point: a caller of this method
	 * asked for "keep this page for the session's lifetime, regardless of recency", and downgrading that
	 * contract to LRU semantics silently would be a behavior change with no error. If this throws in
	 * your application, the call site either can simply use the normal page cache (most likely), or it
	 * genuinely needs pinning - which would be a {@code pinned} flag on the cache entry, excluded from
	 * the instance limit, not a second cache. Build that when something real needs it.
	 */
	@Override
	public void savePageInPermanentCache(WOComponent page) {
		throw new UnsupportedOperationException("The permanent page cache is obsolete: the unified page cache retains pages by instance-LRU (see docs/UNIFIED_PAGE_CACHE.md), and context churn - the thing permanent pages needed protection from - no longer evicts anything. Use the normal page cache, or if you genuinely need session-lifetime pinning, that's a feature request for the unified cache.");
	}

	/**
	 * Restores the page for the given contextID from the unified cache. The incoming contextID is the
	 * context a still-rendered link was RENDERED in - and we keyed the page under exactly that
	 * contextID when we rendered it. So this is a direct O(1) hit: no reverse-map, no container scan.
	 * ContextIDs are session-unique, so a hit is always THIS page (no cross-instance bleed), and a link
	 * never ages out while its instance is still in the cache - however many interactions ago it was
	 * rendered.
	 */
	@Override
	public WOComponent restorePageForContextID(String contextID) {
		log.debug("Restoring page for contextID: {}", contextID);
		WOComponent page = null;
		if (_pageCache != null) {
			synchronized (_pageCache) {
				TransactionRecord record = _pageCache.get(contextID);
				if (record != null) {
					page = record.page();
					recordReuseProfile(page);
					record.markAccessed();
					// Access = most-recently-used for this INSTANCE: move all its contextID entries to the tail
					// so eviction drops the least-recently-used instance, not the first-inserted one.
					touchInstanceInPageCache(_pageCache, page);
				}
				else {
					PageCacheReuseStats.recordMiss();
				}
			}
		}
		if (page == null) {
			// Belt and braces: nothing feeds WO's private caches anymore, so this should always return
			// null - but if some internal code path stored a page there, honoring it beats losing it.
			page = super.restorePageForContextID(contextID);
		}
		if (page != null && page.context() == null) {
			page._awakeInContext(context());
		}
		return page;
	}

	/**
	 * Feeds {@link PageCacheReuseStats} with the hit's reuse profile: how long the restored instance had
	 * been idle, and how deep it sat in the instance-LRU order (1 = most recently used). Must run BEFORE
	 * the hit is marked accessed / re-topped, since both numbers describe the state the hit found.
	 * Measurement only - a failure here must never break a page restore, hence the catch-all.
	 */
	private void recordReuseProfile(WOComponent hitPage) {
		try {
			// One pass over the cache: distinct instances in LRU order (least recently used first),
			// and the hit instance's most recent access across all its contextID entries.
			List<WOComponent> instances = new ArrayList<>();
			Instant lastAccess = null;
			for (TransactionRecord record : _pageCache.values()) {
				WOComponent page = record.page();
				boolean seen = false;
				for (WOComponent instance : instances) {
					if (instance == page) {
						seen = true;
						break;
					}
				}
				if (!seen) {
					instances.add(page);
				}
				if (page == hitPage && (lastAccess == null || record.lastAccessedAt().isAfter(lastAccess))) {
					lastAccess = record.lastAccessedAt();
				}
			}

			int position = -1;
			for (int i = 0; i < instances.size(); i++) {
				if (instances.get(i) == hitPage) {
					position = i;
					break;
				}
			}

			if (position >= 0 && lastAccess != null) {
				int depth = instances.size() - position;
				long idleSeconds = Duration.between(lastAccess, Instant.now()).getSeconds();
				PageCacheReuseStats.recordHit(idleSeconds, depth, hitPage == null ? "(null)" : hitPage.name());
			}
		}
		catch (RuntimeException e) {
			log.debug("Could not record page cache reuse profile: {}", e.toString());
		}
	}

	// ---------------------------------------------------------------------------
	// Diagnostics / formatting helpers. Read-only, derived from already-held state;
	// kept at the end so they don't crowd the load-bearing cache logic above.
	// ---------------------------------------------------------------------------

	/**
	 * A multi-line, human-readable diagnostic of this session's page cache, for inclusion in the
	 * exception extra-info (see {@code ERXExceptionManager}). Groups the map by the live page INSTANCE
	 * (the thing the cache is actually bounded by), and for each instance lists the container keys it is
	 * cached under, its contextIDs, and how long ago it was stored / last used.
	 * <p>
	 * Everything here is read-only and derived from already-held state, so it is safe to call at any time
	 * (notably while handling an exception). Returns a short "empty" line when there is nothing cached.
	 *
	 * @return the formatted report
	 */
	public String pageCacheDiagnostics() {
		final Instant now = Instant.now();
		final StringBuilder out = new StringBuilder();

		out.append("config: maxInstances=").append(WOApplication.application().pageCacheSize())
			 .append("  storesPageInfo=").append(storesPageInfo).append('\n');

		LinkedHashMap<String, TransactionRecord> cache = _pageCache;
		if (cache == null || cache.isEmpty()) {
			out.append("pageCache: empty");
		}
		else {
			// Group the map by the live page instance it points at, preserving the cache's insertion order
			// (which is LRU-over-instances: least-recently-used instance first). We carry the MAP KEY through
			// - that is the contextID the cache is actually keyed by (context.contextID(), the value a rendered
			// link will carry back), which is the useful one for matching a stale link to its entry.
			LinkedHashMap<WOComponent, NSMutableArray<Map.Entry<String, TransactionRecord>>> byInstance = new LinkedHashMap<>();
			for (Map.Entry<String, TransactionRecord> entry : cache.entrySet()) {
				WOComponent page = entry.getValue().page();
				NSMutableArray<Map.Entry<String, TransactionRecord>> entries = byInstance.get(page);
				if (entries == null) {
					entries = new NSMutableArray<>();
					byInstance.put(page, entries);
				}
				entries.add(entry);
			}

			out.append("pageCache: ").append(cache.size()).append(" contexts / ")
				 .append(byInstance.size()).append(" instance(s)").append("  (oldest-used first)\n");

			for (Map.Entry<WOComponent, NSMutableArray<Map.Entry<String, TransactionRecord>>> e : byInstance.entrySet()) {
				WOComponent page = e.getKey();
				NSMutableArray<Map.Entry<String, TransactionRecord>> entries = e.getValue();
				String pageName = page == null ? "(null page)" : page.name();
				out.append("  ").append(pageName).append("  (").append(entries.count()).append(" context(s))\n");

				// The container keys this instance is cached under (e.g. "28_baseData"), de-duplicated, plus the
				// contextID keys (the map keys), and the oldest-stored / most-recently-used timestamps. Joined
				// by hand into single inline lines (NSArray/Set toString would inject brackets and line breaks).
				LinkedHashSet<String> containerKeys = new LinkedHashSet<>();
				List<String> contextIDs = new ArrayList<>();
				Instant created = null;
				Instant lastAccessed = null;
				for (Map.Entry<String, TransactionRecord> entry : entries) {
					TransactionRecord record = entry.getValue();
					contextIDs.add(entry.getKey());
					if (record.key() != null) {
						containerKeys.add(record.key());
					}
					if (created == null || record.createdAt().isBefore(created)) {
						created = record.createdAt();
					}
					if (lastAccessed == null || record.lastAccessedAt().isAfter(lastAccessed)) {
						lastAccessed = record.lastAccessedAt();
					}
				}
				out.append("    containers: ").append(String.join(", ", containerKeys)).append('\n');
				out.append("    contexts:   ").append(String.join(", ", contextIDs)).append('\n');
				out.append("    stored ").append(ago(created, now))
					 .append(", last used ").append(ago(lastAccessed, now)).append('\n');
			}
		}

		return out.toString().stripTrailing();
	}

	/** Render an Instant as a compact "Ns/Nm Ns/Nh Nm ago" relative age against {@code now}. */
	private static String ago(Instant then, Instant now) {
		if (then == null) {
			return "(unknown)";
		}
		long seconds = Duration.between(then, now).getSeconds();
		if (seconds < 0) {
			seconds = 0;
		}
		if (seconds < 60) {
			return seconds + "s ago";
		}
		if (seconds < 3600) {
			return (seconds / 60) + "m" + (seconds % 60) + "s ago";
		}
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		return hours + "h" + minutes + "m ago";
	}
}
