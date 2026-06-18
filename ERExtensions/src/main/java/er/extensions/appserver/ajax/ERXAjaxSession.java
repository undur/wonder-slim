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
import java.util.Enumeration;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOSession;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.foundation.ERXProperties;
import er.extensions.hacks.ERXPrivateKVC;

/**
 * ERXAjaxSession is the part of ERXSession that handles Ajax requests. It owns the ajax
 * page-replacement cache: the overrides of {@link #savePage}, {@link #restorePageForContextID},
 * {@link #_saveCurrentPage} and {@link #savePageInPermanentCache}, plus the permanent-page cache, that
 * let component actions live inside ajax-updated regions without filling WO's small backtrack cache.
 * <p>
 * The cache keys each rendered page by its render contextID. Every request mints a new contextID, so
 * one page instance accumulates many contextID keys over its life - a still-rendered link carries the
 * contextID it was rendered in, and that key is still here, so it resolves directly however many
 * interactions ago it was rendered. The cache is bounded by the number of distinct page INSTANCES (not
 * contextIDs); evicting an instance drops all its contextID keys together.
 * <p>
 * If you want to use the Ajax framework without using other parts of Project Wonder (i.e. ERXSession or
 * ERXApplication), you should steal all of the code in ERXAjaxSession, ERXAjaxApplication, and
 * ERXAjaxContext.
 *
 * @property er.extensions.maxPageReplacementCacheSize=30
 * @property er.extensions.appserver.ajax.ERXAjaxSession.storesPageInfo=false
 * @property er.extensions.overridePrivateCache
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
   * Key that is used to specify that a page should go in the replacement cache instead of
   * the backtrack cache.  This is used for Ajax components that actually generate component
   * actions in their output.  The value doesn't matter, but you need to update the 
   * corresponding value in AjaxUtils.  This is to keep the dependencies between the two
   * frameworks independent.
   */
  public static final String PAGE_REPLACEMENT_CACHE_LOOKUP_KEY = "page_cache_key";

  private static final String ORIGINAL_CONTEXT_ID_KEY = "original_context_id";

  private static final String PAGE_REPLACEMENT_CACHE_KEY = "page_replacement_cache";

  private static int MAX_PAGE_REPLACEMENT_CACHE_SIZE = Integer.parseInt(System.getProperty("er.extensions.maxPageReplacementCacheSize", "30"));

  /** When true, log the fragment-cache size + page-&gt;container structure each time a fragment is
   *  stored (see {@link #pageReplacementCacheSummary()}). Off by default. */
  private static boolean logPageReplacementCache = ERXProperties.booleanForKey("er.extensions.appserver.ajax.ERXAjaxSession.logPageReplacementCache");
  
  private static boolean storesPageInfo = ERXProperties.booleanForKeyWithDefault("er.extensions.appserver.ajax.ERXAjaxSession.storesPageInfo", false);
  
  private NSMutableDictionary<WOComponent, NSMutableDictionary<String, Object>> pageInfoDictionary;

  private static boolean overridePrivateCache = storesPageInfo || ERXProperties.booleanForKey("er.extensions.overridePrivateCache");
  
  private static final Logger log = LoggerFactory.getLogger(ERXAjaxSession.class);

  /**
   * The maximum number of distinct page INSTANCES the ajax replacement cache holds per session (one
   * instance accumulates many contextID keys; only the instance count is bounded). Configured via
   * {@code er.extensions.maxPageReplacementCacheSize} (default 30).
   *
   * @return the configured instance limit
   */
  public static int maxPageReplacementCacheSize() {
    return MAX_PAGE_REPLACEMENT_CACHE_SIZE;
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
   * One cache entry: a live page instance plus the container key it was rendered for (for diagnostics).
   * The map is keyed by contextID; the record carries the instance the lookup resolves to.
   * <p>
   * Two timestamps are tracked purely for diagnostics (e.g. the exception-page cache report): when the
   * entry was first stored ({@link #createdAt()}) and when it was last resolved by a restore
   * ({@link #lastAccessedAt()}). They do not drive eviction - that is LRU over instances via the cache's
   * insertion order - so they are free to read and never affect cache behavior.
   */
  static class TransactionRecord implements Serializable {

    private String _contextID;
    private WOComponent _page;
    private String _key;
    private Instant _createdAt;
    private Instant _lastAccessedAt;

    public TransactionRecord(WOComponent page, WOContext context, String key) {
      _page = page;
      _contextID = context._requestContextID();
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

    public String contextID() {
      return _contextID;
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
      return "[TransactionRecord: page = " + _page.name() + "; context = " + _contextID + "; key = " + _key + "]";
    }
  }
  
  public ERXAjaxSession() {
	  super();
  }

  public ERXAjaxSession(String sessionID) {
	  super(sessionID);
  }
  
  /**
   * Overridden so that Ajax requests are not saved in the page cache.  Checks both the 
   * response userInfo and the response headers if the DONT_STORE_PAGE key is present. The value doesn't matter.
   * <p>
   * Page Replacement cache is specifically designed to support component actions in Ajax updates.  The problem with
   * component actions in Ajax is that if you let them use the normal page cache, then after only 30 (or whatever your backtrack
   * cache is set to) updates from Ajax, you will fill your backtrack cache.  Unfortunately for the user, though, the backtrack cache
   * filled up with background ajax requests, so when the user clicks on a component action on the FOREGROUND page, the
   * foreground page has fallen out of the cache, and the request cannot be fulfilled (because its context is gone).  If you simply
   * turn off backtrack cache entirely for a request, then you can't have component actions inside of an Ajax updated area, because
   * the context of the Ajax update that generated the link will never get stored, and so you will ALWAYS get a backtrack error. 
   * <p> 
   * Enter page replacement cache.  If you look at the behavior of Ajax, it turns out that what you REALLY want is a hybrid page cache.  You
   * want to keep the backtrack of just the LAST update for a particular ajax component -- you don't care about its previous 29 states
   * because the user can't use the back button to get to them anyway, but if you have the MOST RECENT cached version of the page
   * then you can click on links in Ajax updated areas.  Page Replacement cache implements this logic.  For each Ajax component on 
   * your page that is updating, it keeps a cache entry of its most recent backtrack state (note the difference between this and the
   * normal page cache.  The normal page cache contains one entry per user-backtrackable-request.  The replacement cache contains
   * one entry per Ajax component*, allowing up to replacement_page_cache_size many components per page). Each time the Ajax area 
   * refreshes, the most recent state is replaced*.  When a restorePage request comes in, the replacement cache is checked first.  If 
   * the replacement cache can service the page, then it does so.  If the replacement cache doesn't contain the context, then it 
   * passes up to the standard page cache.  If you are not using Ajax, no replacement cache will exist in your session, and all the code 
   * related to it will be skipped, so it should be minimally invasive under those conditions.
   * <p>
   * <b>*</b> It turns out that we have to keep the last TWO states, because of a race condition in the scenario where the replacement page 
   * cache replaces context 2 with the context 3 update, but the user's browser hasn't been updated yet with the HTML from 
   * context 3.  When the user clicks, they are clicking the context 2 link, which has now been removed from the replacement cache.
   * By keeping the last two states, you allow for the brief period where that transition occurs.
   * <p>
   * Random note (that I will find useful in 2 weeks when I forget this again): The first time through savePage, the request is saved
   * in the main cache.  It's only on a subsequent Ajax update that it uses page replacement cache.  So even though the cache
   * is keyed off of context ID, the explanation of the cache being components-per-page-sized works out because each component
   * is requesting in its own thread and generating their own non-overlapping context ids.
   */
  @Override
  public void savePage(WOComponent page) {
	  WOContext context = context();
    if (ERXAjaxApplication.shouldNotStorePage(context)) {
      if (log.isDebugEnabled()) log.debug("Considering pageReplacementCache for {} with contextID {}", context.request().uri(), context.contextID());
      WORequest request = context.request();
      WOResponse response = context.response();
      String pageCacheKey = null;
      if (response != null) {
    	  pageCacheKey = response.headerForKey(PAGE_REPLACEMENT_CACHE_LOOKUP_KEY);
      }
      if (pageCacheKey == null && request != null) {
    	  pageCacheKey = request.headerForKey(PAGE_REPLACEMENT_CACHE_LOOKUP_KEY);
      }

      // A null pageCacheKey should mean an Ajax request that is not returning a content update or an expliclty not cached non-Ajax request
      if (pageCacheKey != null) {
        log.debug("Will use pageCacheKey {}", pageCacheKey);
        // Key by context.contextID() - the context THIS render happened in, which is exactly the
        // contextID a link rendered now will carry when the user later clicks it. So a returning link
        // does a direct get(contextID) hit. Every interaction mints a new contextID, so one PAGE INSTANCE
        // accumulates many contextID keys over its life - that is fine: they are cheap pointers to the
        // same live object. We do NOT bound the contextID count; we bound the number of distinct page
        // INSTANCES (see enforcePageReplacementCacheInstanceLimit). When an instance is evicted, all of
        // its contextID keys go together. No reverse-map through WO's backtrack cache, no aging-out of a
        // live page's links; contextIDs are globally unique so two pages never collide (no bleed).
        LinkedHashMap pageReplacementCache = (LinkedHashMap) objectForKey(PAGE_REPLACEMENT_CACHE_KEY);
        if (pageReplacementCache == null) {
          pageReplacementCache = new LinkedHashMap();
          setObjectForKey(pageReplacementCache, PAGE_REPLACEMENT_CACHE_KEY);
        }

        // Memory bound: cap the number of distinct PAGE INSTANCES, not contextIDs. Adding a context for
        // an instance we already hold is free; a genuinely new instance over the limit evicts the
        // least-recently-used instance (all its contextID keys).
        enforcePageReplacementCacheInstanceLimit(pageReplacementCache, page);

        // Touch: move this instance's existing contextID entries to the tail so eviction is LRU over
        // instances (drop the instance gone longest without use), not FIFO by first-seen contextID.
        touchInstanceInReplacementCache(pageReplacementCache, page);

        TransactionRecord pageRecord = new TransactionRecord(page, context, pageCacheKey);
        pageReplacementCache.put(context.contextID(), pageRecord);
        log.debug("{} = {}", pageCacheKey, pageReplacementCache.keySet());

        // Per-request visibility into the fragment cache. Off by default (free in production); set
        // er.extensions.appserver.ajax.ERXAjaxSession.logPageReplacementCache=true to watch the cache
        // size + page->container structure each time a fragment is stored - the easiest way to confirm
        // it stays sized by (pages x containers) and to debug stale-link issues.
        if (logPageReplacementCache && log.isInfoEnabled()) {
          log.info("[fragment-cache] stored {} for context {} -> {}", pageCacheKey, context.contextID(), pageReplacementCacheSummary());
        }

        ERXAjaxApplication.cleanUpHeaders(response);
      }
      else {
          // A null pageCacheKey should mean an Ajax request that is not returning a content update or an explicitly not cached non-Ajax request
    	  log.debug("Not caching as no pageCacheKey found");
      }
    }
    else {
    	log.debug("Calling super.savePage for contextID {}", context.contextID());
    	super.savePage(page);
    }
  }

  /**
   * A human-readable summary of the page-replacement cache for the current session: how many contextID
   * entries it holds and how many DISTINCT page instances those point at. The cache is bounded by the
   * instance count (each instance accumulates many contextID keys over its life), so this is the line to
   * watch: entries can climb freely while instances stays small and bounded.
   *
   * @return a single-line summary, e.g. {@code pageReplacementCache: 42 contexts / 2 instance(s)}
   */
  public String pageReplacementCacheSummary() {
    @SuppressWarnings("unchecked")
    LinkedHashMap<String, TransactionRecord> cache = (LinkedHashMap<String, TransactionRecord>) objectForKey(PAGE_REPLACEMENT_CACHE_KEY);
    if (cache == null || cache.isEmpty()) {
      return "pageReplacementCache: empty";
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
    return "pageReplacementCache: " + cache.size() + " contexts / " + instances.size() + " instance(s)"
        + " (oldest stored " + ago(oldestStored, now) + ", last used " + ago(newestUsed, now) + ")";
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
   * @param pageReplacementCache the cache
   * @param page the page instance that was just stored or restored; no-op if null or not present
   */
  protected void touchInstanceInReplacementCache(LinkedHashMap pageReplacementCache, WOComponent page) {
    if (pageReplacementCache == null || page == null) {
      return;
    }
    LinkedHashMap<Object, TransactionRecord> moved = new LinkedHashMap<>();
    for (Object o : pageReplacementCache.entrySet()) {
      Map.Entry entry = (Map.Entry) o;
      TransactionRecord record = (TransactionRecord) entry.getValue();
      if (record.page() == page) {
        moved.put(entry.getKey(), record);
      }
    }
    for (Map.Entry<Object, TransactionRecord> e : moved.entrySet()) {
      pageReplacementCache.remove(e.getKey());
      pageReplacementCache.put(e.getKey(), e.getValue());
    }
  }

  /**
   * Bounds the cache by the number of distinct page INSTANCES it holds, not by the number of contextID
   * entries. One page instance accumulates many contextID keys over its life (one per interaction) -
   * those are cheap pointers to the same live object and are NOT bounded. What is bounded is the count
   * of distinct live instances: when a genuinely new instance would push us over the limit, the
   * least-recently-used instance is evicted and ALL of its contextID keys go with it.
   * <p>
   * Instances are compared by object identity. The cache's insertion order is LRU-over-instances (see
   * {@link #touchInstanceInReplacementCache}), so the instance owning the head entry is the one gone
   * longest without use - the right one to drop.
   *
   * @param pageReplacementCache the cache being added to
   * @param currentPage the page instance about to be (re)stored; never evicted
   */
  protected void enforcePageReplacementCacheInstanceLimit(LinkedHashMap pageReplacementCache, WOComponent currentPage) {
    // Distinct instances in cache order (least-recently-used first), using identity.
    LinkedHashSet<WOComponent> instances = new LinkedHashSet<>();
    for (Object o : pageReplacementCache.values()) {
      WOComponent p = ((TransactionRecord) o).page();
      if (p != null) {
        instances.add(p);
      }
    }
    // Already holding this instance, or under the limit -> nothing to evict.
    if (instances.contains(currentPage) || instances.size() < MAX_PAGE_REPLACEMENT_CACHE_SIZE) {
      return;
    }
    // Over the limit and adding a NEW instance: drop every entry of the least-recently-used instance.
    WOComponent oldest = instances.iterator().next();
    Iterator evictEnum = pageReplacementCache.entrySet().iterator();
    while (evictEnum.hasNext()) {
      Map.Entry entry = (Map.Entry) evictEnum.next();
      if (((TransactionRecord) entry.getValue()).page() == oldest) {
        if (log.isDebugEnabled()) log.debug("Instance-limit reached; evicting context {} of LRU instance", entry.getKey());
        evictEnum.remove();
      }
    }
  }
  

  	/**
	 * A dict of contextID/pages
	 */
	protected NSMutableDictionary _permanentPageCache;
	
	/**
	 * The currently active contextIDs for the permanent pages.
	 */
	protected NSMutableArray _permanentContextIDArray;

	/**
	 * Returns the permanent page cache. Initializes it if needed.
	 */
	protected NSMutableDictionary _permanentPageCache() {
		if (_permanentPageCache == null) {
			_permanentPageCache = new NSMutableDictionary(64);
			_permanentContextIDArray = new NSMutableArray(64);
		}
		return _permanentPageCache;
	}

	/**
	 * Returns the page for the given contextID, null if none is present.
	 */
	protected WOComponent _permanentPageWithContextID(String contextID) {
		WOComponent wocomponent = null;
		if (_permanentPageCache != null)
			wocomponent = (WOComponent) _permanentPageCache.objectForKey(contextID);
		return wocomponent;
	}

	/**
	 * Semi-private method that saves the current page. Overridden to put the page in the
	 * permanent page cache if it's already in there.
	 */
    @Override
	public void _saveCurrentPage() {
		if(overridePrivateCache) {
			WOContext _currentContext = context();
			if (_currentContext != null) {
				String contextID = context().contextID();
				log.debug("Saving page for contextID: {}", contextID);
				WOComponent currentPage = _currentContext._pageComponent();
				if (currentPage != null && currentPage._isPage()) {
					WOComponent permanentSenderPage = _permanentPageWithContextID(_currentContext._requestContextID());
					WOComponent permanentCurrentPage = _permanentPageWithContextID(contextID);
					if (permanentCurrentPage == null && _permanentPageCache().containsValue(currentPage)) {
						// AK: note that we put it directly in the cache, not bothering with
						// savePageInPermanentCache() as this one would clear out the old IDs
						_permanentPageCache.setObjectForKey(currentPage, contextID);
					}
					else if (permanentCurrentPage != currentPage) {
						WOApplication woapplication = WOApplication.application();
						if (permanentSenderPage == currentPage && woapplication.permanentPageCacheSize() != 0) {
							if (_shouldPutInPermanentCache(currentPage))
								savePageInPermanentCache(currentPage);
						}
						else if (woapplication.pageCacheSize() != 0)
							savePage(currentPage);

					}
				}
			}
		} else {
			super._saveCurrentPage();
		}
	}

	/**
	 * Reimplementation of the rather weird super imp which references an interface probably no
	 * one has ever heard of...
	 */
	protected boolean _shouldPutInPermanentCache(WOComponent wocomponent) {
		boolean flag = true;
		if ((com.webobjects.appserver._private._PermanentCacheSingleton.class).isInstance(wocomponent)) {
			flag = false;
		}
		else {
			NSArray nsarray = (NSArray) ERXPrivateKVC.privateValueForKey(wocomponent, "_subcomponents");
			if (nsarray != null && nsarray != NSArray.EmptyArray) {
				for(Enumeration enumeration = nsarray.objectEnumerator(); flag && enumeration.hasMoreElements(); ) {
					if (!_shouldPutInPermanentCache((WOComponent) enumeration.nextElement()))
						flag = false;
				}
			}
		}
		return flag;
	}
	
	
	/**
	 * Saves a page in the permanent cache. Overridden to not save in the super implementation's iVars but in our own.
	 */
	// FIXME: ak: as we save the perm pages under a lot of context IDs, we should have a way to actually limit the size...
	// not sure how, though
    @Override
	public void savePageInPermanentCache(WOComponent wocomponent) {
		if(overridePrivateCache) {
			WOContext wocontext = context();
			String contextID = wocontext.contextID();
			log.debug("Saving page for contextID: {}", contextID);
			NSMutableDictionary permanentPageCache = _permanentPageCache();
			for (int i = WOApplication.application().permanentPageCacheSize(); _permanentContextIDArray.count() >= i; _permanentContextIDArray.removeObjectAtIndex(0)) {
				String s1 = (String) _permanentContextIDArray.objectAtIndex(0);
				WOComponent page = (WOComponent) permanentPageCache.removeObjectForKey(s1);
				if(storesPageInfo()) {
					pageInfoDictionary().removeObjectForKey(page);
				}
			}

			permanentPageCache.setObjectForKey(wocomponent, contextID);
			_permanentContextIDArray.addObject(contextID);
		} else {
			super.savePageInPermanentCache(wocomponent);
		}

	}
	
	/**
	 * Extension of restorePageForContextID that implements the other side of
	 * Page Replacement Cache.
	 */
    @Override
  public WOComponent restorePageForContextID(String contextID) {
	log.debug("Restoring page for contextID: {}", contextID);
    LinkedHashMap pageReplacementCache = (LinkedHashMap) objectForKey(PAGE_REPLACEMENT_CACHE_KEY);

    WOComponent page = null;
    if (pageReplacementCache != null) {
      // The incoming contextID is the context a still-rendered link was RENDERED in - and we keyed the
      // page under exactly that contextID when we rendered it. So this is a direct O(1) hit: no
      // reverse-map through WO's backtrack cache, no container scan. ContextIDs are globally unique, so a
      // hit is always THIS page (no cross-instance bleed), and a link never ages out while its instance
      // is still in the cache - however many interactions ago it was rendered.
      TransactionRecord pageRecord = (TransactionRecord) pageReplacementCache.get(contextID);
      if (pageRecord != null) {
          log.debug("Restoring page for contextID: {} pageRecord = {}", contextID, pageRecord);
          page = pageRecord.page();
          pageRecord.markAccessed();
          // Access = most-recently-used for this INSTANCE: move all its contextID entries to the tail so
          // eviction drops the least-recently-used instance, not the first-inserted one.
          touchInstanceInReplacementCache(pageReplacementCache, page);
      }
      else {
        log.debug("No page in pageReplacementCache for contextID: {}", contextID);
        // Not one of ours - fall through to the permanent / backtrack caches below.
      }
    }
    // AK: this will get handled last in the super implementation, so we do it here
    if(page == null && overridePrivateCache) {
    	page = _permanentPageWithContextID(contextID); 
    	if(page != null)
    		page._awakeInContext(context());
    }
    if (page == null) {
    	page = super.restorePageForContextID(contextID);
    }

    if (page != null) {
      WOContext context = page.context();
      if(context == null) {
          page._awakeInContext(context());
          context = page.context();
      }
      WORequest request = context.request();
      // MS: I suspect we don't have to do this all the time, but I don't know if we have
      // enough information at this point to know whether to do it or not, unfortunately.
      if (request != null) {
        request.setHeader(contextID, ORIGINAL_CONTEXT_ID_KEY);
      }
    }

    return page;
  }

  // ---------------------------------------------------------------------------
  // Diagnostics / formatting helpers. Read-only, derived from already-held state;
  // kept at the end so they don't crowd the load-bearing cache logic above.
  // ---------------------------------------------------------------------------

  /**
   * A multi-line, human-readable diagnostic of this session's ajax page caches, for inclusion in the
   * exception extra-info (see {@code ERXExceptionManager}). Where the raw session {@code toString()}
   * dumps the page-replacement cache as a flat, unreadable contextID-&gt;record soup, this groups it by
   * the live page INSTANCE (the thing the cache is actually bounded by), and for each instance lists the
   * container keys it is cached under, its contextIDs, and how long ago it was stored / last used.
   * <p>
   * Everything here is read-only and derived from already-held state, so it is safe to call at any time
   * (notably while handling an exception). Returns a short "empty" line when there is nothing cached.
   *
   * @return the formatted report
   */
  public String ajaxCacheDiagnostics() {
    final Instant now = Instant.now();
    final StringBuilder out = new StringBuilder();

    out.append("config: maxInstances=").append(MAX_PAGE_REPLACEMENT_CACHE_SIZE)
       .append("  overridePrivateCache=").append(overridePrivateCache)
       .append("  storesPageInfo=").append(storesPageInfo).append('\n');

    @SuppressWarnings("unchecked")
    LinkedHashMap<String, TransactionRecord> cache = (LinkedHashMap<String, TransactionRecord>) objectForKey(PAGE_REPLACEMENT_CACHE_KEY);
    if (cache == null || cache.isEmpty()) {
      out.append("pageReplacementCache: empty");
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

      out.append("pageReplacementCache: ").append(cache.size()).append(" contexts / ")
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

    // The permanent page cache (only populated when overridePrivateCache is on).
    if (_permanentPageCache != null && !_permanentPageCache.isEmpty()) {
      out.append('\n').append("permanentPageCache: ").append(_permanentPageCache.count()).append(" context(s)\n");
      for (Object contextID : _permanentPageCache.allKeys()) {
        WOComponent p = (WOComponent) _permanentPageCache.objectForKey(contextID);
        out.append("  ctx ").append(contextID).append(" -> ").append(p == null ? "(null)" : p.name()).append('\n');
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