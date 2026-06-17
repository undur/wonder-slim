/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.appserver.ajax;

import java.io.Serializable;
import java.util.Enumeration;
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
 * ERXAjaxSession is the part of ERXSession that handles Ajax requests.
 * If you want to use the Ajax framework without using other parts of Project
 * Wonder (i.e. ERXSession or ERXApplication), you should steal all of the code
 * in ERXAjaxSession, ERXAjaxApplication, and ERXAjaxContext.
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
   * ERTransactionRecord is a reimplementation of WOTransactionRecord for
   * use with Ajax background request page caching.
   * 
   * @author mschrag
   */
  static class TransactionRecord implements Serializable {

    private String _contextID;
    private WOComponent _page;
    private String _key;
    private String _pageID;
    private boolean _oldPage;
    private long _lastModified;

    public TransactionRecord(WOComponent page, WOContext context, String key) {
      this(page, context, key, null);
    }

    public TransactionRecord(WOComponent page, WOContext context, String key, String pageID) {
      _page = page;
      _contextID = context._requestContextID();
      _key = key;
      _pageID = pageID;
      touch();
    }

    /** The identity of the page this fragment belongs to (the originalContextID the ajax session
     *  sprang from). All fragments of one page share a pageID and are evicted together. */
    public String pageID() {
      return _pageID;
    }
    
    public void touch() {
      _lastModified = System.currentTimeMillis();
    }

    @Override
    public int hashCode() {
      return _key.hashCode();
    }

    @Override
    public boolean equals(Object _obj) {
      return (_obj instanceof TransactionRecord && ((TransactionRecord) _obj)._key.equals(_key));
    }

    public WOComponent page() {
      return _page;
    }

    // MS: The preferrable behavior here is for Ajax records to expire
    // when the original context it's associated with expires from the 
    // page cache, but we can't get to the _contextRecords map in
    // WOSession, so for now, we just turn off explicit expiration.  As
    // a result, entries will fall out of the cache when the cache gets
    // too big only unless it's an "old page," in which case it will expire 
    // within 5 minutes.
    public boolean isExpired() {
      boolean expired = _oldPage && ((System.currentTimeMillis() - _lastModified) > 5 * 60 * 1000 /* 5 minutes */);
      return expired;
    }

    public String key() {
      return _key;
    }

    public void setOldPage(boolean oldPage) {
      _oldPage = oldPage;
      touch();
    }

    public boolean isOldPage() {
      return _oldPage;
    }

    @Override
    public String toString() {
      return "[TransactionRecord: page = " + _page.name() + "; context = " + _contextID + "; key = " + _key + "; oldPage? " + _oldPage + "]";
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
    	  pageCacheKey = response.headerForKey(ERXAjaxSession.PAGE_REPLACEMENT_CACHE_LOOKUP_KEY);
      }
      if (pageCacheKey == null && request != null) {
    	  pageCacheKey = request.headerForKey(ERXAjaxSession.PAGE_REPLACEMENT_CACHE_LOOKUP_KEY);
      }

      // A null pageCacheKey should mean an Ajax request that is not returning a content update or an expliclty not cached non-Ajax request
      if (pageCacheKey != null) {
        log.debug("Will use pageCacheKey {}", pageCacheKey);
        // The cache key identifies a FRAGMENT by (page, container): originalContextID is the page the
        // ajax session sprang from; pageCacheKey is the update-container id. A container can only hold
        // one valid state at a time, so the cache is sized by (pages x containers), NOT by the number
        // of interactions - see the eviction logic below and cleanPageReplacementCacheIfNecessary.
        String originalContextID = context.request().headerForKey(ERXAjaxSession.ORIGINAL_CONTEXT_ID_KEY);
        pageCacheKey = originalContextID + "_" + pageCacheKey;
        LinkedHashMap pageReplacementCache = (LinkedHashMap) objectForKey(ERXAjaxSession.PAGE_REPLACEMENT_CACHE_KEY);
        if (pageReplacementCache == null) {
          pageReplacementCache = new LinkedHashMap();
          setObjectForKey(pageReplacementCache, ERXAjaxSession.PAGE_REPLACEMENT_CACHE_KEY);
        }

        // Age the PRIOR state of THIS fragment (mark it old / drop the one already marked old). This
        // keeps at most two records per (page, container) - the current state and one previous state
        // for the brief race window where the browser still shows the prior HTML and clicks its link.
        cleanPageReplacementCacheIfNecessary(pageCacheKey);

        // Memory bound: cap the number of distinct PAGES (originalContextIDs) in the cache, not the
        // number of fragments. When a page falls out, all of its fragment leaves go together - the
        // page is dead, so its fragments are too. This is what stops the cache growing one entry per
        // interaction: an unrelated, unchanged container's still-valid state is never evicted just
        // because it is old; only whole expired pages are dropped.
        enforcePageReplacementCachePageLimit(pageReplacementCache, originalContextID);

        // Storing a fragment is also an access of its page: pull this page's existing fragments to the
        // tail so the whole page stays grouped and most-recent before we append the new one. Keeps the
        // LRU order coherent even when a save isn't immediately preceded by a restore of the same page.
        touchPageInReplacementCache(pageReplacementCache, originalContextID);

        TransactionRecord pageRecord = new TransactionRecord(page, context, pageCacheKey, originalContextID);
        pageReplacementCache.put(context.contextID(), pageRecord);
        log.debug("{} new context = {}", pageCacheKey, context.contextID());
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
   * Iterates through the page replacement cache (if there is one) and removes expired records.
   */
  protected void cleanPageReplacementCacheIfNecessary() {
    cleanPageReplacementCacheIfNecessary(null);
  }

  /**
   * Iterates through the page replacement cache (if there is one) and removes expired records.
   * 
   * @param _cacheKeyToAge optional cache key to age via setOldPage
   * @return whether or not a cache entry was removed
   */
protected boolean cleanPageReplacementCacheIfNecessary(String _cacheKeyToAge) {
    boolean removedCacheEntry = false;
    LinkedHashMap pageReplacementCache = (LinkedHashMap) objectForKey(ERXAjaxSession.PAGE_REPLACEMENT_CACHE_KEY);
    if (log.isDebugEnabled()) log.debug("keys in pageReplacementCache: {}", pageReplacementCache.keySet());
    if (pageReplacementCache != null) {
      Iterator transactionRecordsEnum = pageReplacementCache.entrySet().iterator();
      while (transactionRecordsEnum.hasNext()) {
        Map.Entry pageRecordEntry = (Map.Entry) transactionRecordsEnum.next();
        TransactionRecord tempPageRecord = (TransactionRecord) pageRecordEntry.getValue();
        // If the page has been GC'd, toss the transaction record ...
        if (tempPageRecord.isExpired()) {
          log.debug("deleting expired page record {}", tempPageRecord);
          transactionRecordsEnum.remove();
          removedCacheEntry = true;
        }
        else if (_cacheKeyToAge != null) {
          String transactionRecordKey = tempPageRecord.key();
          if (_cacheKeyToAge.equals(transactionRecordKey)) {
            // If this is the "old page", then delete the entry ...
            if (tempPageRecord.isOldPage()) {
              log.debug("{} removing old page {}", _cacheKeyToAge, tempPageRecord);
              transactionRecordsEnum.remove();
              removedCacheEntry = true;
            }
            // Otherwise, flag this entry as the old page ...
            else {
              log.debug("{} marking as old page", _cacheKeyToAge);
              tempPageRecord.setOldPage(true);
            }
          }
        }
      }

      // Only remove the replacement cache is there wasn't a cache key.  If there WAS a
      // cache key, then we're being called by savePage and it's going to expect a cache
      // to exist.
      if (_cacheKeyToAge == null && pageReplacementCache.isEmpty()) {
        removeObjectForKey(ERXAjaxSession.PAGE_REPLACEMENT_CACHE_KEY);
        log.debug("Removing empty page cache");
      }
    }
    return removedCacheEntry;
  }

  /**
   * A human-readable summary of the page-replacement (fragment) cache for the current session: total
   * fragment-record count, distinct page count, and the tree of pages -> their cached container
   * fragments (with how many records each container is holding - normally 1, or 2 during the
   * old-page race window). Useful for confirming the cache stays sized by (pages x containers) rather
   * than growing one entry per interaction, and for debugging stale-link issues in real apps.
   *
   * @return a single-line summary string, e.g.
   *         {@code pageReplacementCache: 4 fragments / 2 page(s) | page 0.3[linesContainer, totalsPanel, renderedInvoice] | page 1.7[detailPanel]}
   */
  public String pageReplacementCacheSummary() {
    @SuppressWarnings("unchecked")
    LinkedHashMap<String, TransactionRecord> cache = (LinkedHashMap<String, TransactionRecord>) objectForKey(ERXAjaxSession.PAGE_REPLACEMENT_CACHE_KEY);
    if (cache == null || cache.isEmpty()) {
      return "pageReplacementCache: empty";
    }
    // page id -> (container id -> record count). The composite key is "<pageID>_<containerID>", and a
    // container id may itself contain '_', so we recover the container by stripping the known pageID
    // prefix rather than splitting on '_'.
    LinkedHashMap<String, LinkedHashMap<String, Integer>> byPage = new LinkedHashMap<>();
    for (TransactionRecord record : cache.values()) {
      String pageID = record.pageID() == null ? "(none)" : record.pageID();
      String container = record.key();
      String prefix = pageID + "_";
      if (container != null && container.startsWith(prefix)) {
        container = container.substring(prefix.length());
      }
      LinkedHashMap<String, Integer> containers = byPage.computeIfAbsent(pageID, k -> new LinkedHashMap<>());
      containers.merge(container, 1, Integer::sum);
    }
    // Single line (no embedded newlines) so it survives line-based log capture and stays grep-friendly:
    //   pageReplacementCache: 4 fragments / 1 page(s) | page 0[linebox-1, totalsPanel, renderedInvoice]
    StringBuilder sb = new StringBuilder();
    sb.append("pageReplacementCache: ").append(cache.size()).append(" fragments / ").append(byPage.size()).append(" page(s)");
    for (Map.Entry<String, LinkedHashMap<String, Integer>> pageEntry : byPage.entrySet()) {
      sb.append(" | page ").append(pageEntry.getKey()).append("[");
      boolean first = true;
      for (Map.Entry<String, Integer> c : pageEntry.getValue().entrySet()) {
        if (!first) sb.append(", ");
        first = false;
        sb.append(c.getKey());
        if (c.getValue() > 1) sb.append("(x").append(c.getValue()).append(")");
      }
      sb.append("]");
    }
    return sb.toString();
  }

  /**
   * Resolves a cached page record by the request's TARGET CONTAINER(S) ({@code _u}) rather than its
   * context id, for the case where a still-rendered action link carries a context that has aged out of
   * the cache. The link's {@code _u} names the container(s) it targets; we match ANY of them against a
   * cached fragment whose key ends in {@code _<container>}. Because every fragment of a page shares one
   * page instance, any surviving fragment for any of the targeted containers resolves the live page.
   * <p>
   * Trying ALL of {@code _u}'s containers matters for multi-target updates: such a request renders
   * several containers but only stores ONE fragment, keyed by whichever container the update pass
   * rendered LAST (each container's render overwrites the page-replacement-cache-key request header).
   * So for {@code _u=a;b;c} the stored fragment may be under {@code c}, not {@code a} - we must check
   * all three, not just the first.
   *
   * @param pageReplacementCache the fragment cache
   * @return a matching record, or null if the request has no {@code _u} or no fragment matches
   */
  protected TransactionRecord pageRecordForRequestContainer(LinkedHashMap pageReplacementCache, WOComponent ownPage) {
    WOContext context = context();
    WORequest request = context != null ? context.request() : null;
    if (request == null || ownPage == null) {
      // Without a known page instance to scope to, the container-name match is unsafe (it could return a
      // different live page's fragment), so we don't attempt it - see restorePageForContextID.
      return null;
    }
    String updateContainerIDs = request.stringFormValueForKey(ERXAjaxApplication.KEY_UPDATE_CONTAINER_ID);
    if (updateContainerIDs == null || updateContainerIDs.length() == 0) {
      return null;
    }
    // _u may name several containers ("a;b;c"); the stored fragment is keyed by one of them (the last
    // rendered), so check them all. Container ids are not page-unique, so we only accept records that
    // belong to ownPage (the page instance this request actually belongs to) - a same-named container on
    // another live page instance is skipped so its content can't bleed in. Prefer a current (not old-page)
    // record over an old-page one.
    String[] containers = updateContainerIDs.split(";");
    TransactionRecord fallback = null;
    for (String container : containers) {
      if (container.length() == 0) {
        continue;
      }
      String suffix = "_" + container;
      Iterator recordsEnum = pageReplacementCache.values().iterator();
      while (recordsEnum.hasNext()) {
        TransactionRecord record = (TransactionRecord) recordsEnum.next();
        String key = record.key();
        if (key != null && key.endsWith(suffix) && record.page() == ownPage) {
          if (!record.isOldPage()) {
            return record;
          }
          if (fallback == null) {
            fallback = record;
          }
        }
      }
    }
    return fallback;
  }

  /**
   * Marks a page as just-accessed by moving all of its fragment records to the most-recent end of the
   * cache's insertion order. The page-limit eviction reads that order oldest-first, so touching a page
   * on access turns the eviction policy from "drop the first-inserted page" (FIFO) into "drop the
   * least-recently-<em>used</em> page" (LRU) - which is the only correct policy for a cache whose job is
   * to keep the pages the user is actually working with. Without this, a page you open first and keep
   * returning to (a receipt you edit while wandering off to look up related data) is the oldest by
   * insertion and so the first evicted, even though it is the page you care about most.
   * <p>
   * Moving a record is a {@code remove}+{@code put} on the {@link LinkedHashMap}, which re-appends it at
   * the tail. We move every record sharing this {@code pageID} so the whole page travels together and
   * stays grouped (the eviction and {@link #pageReplacementCacheSummary()} both reason per page).
   *
   * @param pageReplacementCache the cache
   * @param pageID the page (originalContextID) that was just accessed; no-op if null or not present
   */
  protected void touchPageInReplacementCache(LinkedHashMap pageReplacementCache, String pageID) {
    if (pageReplacementCache == null || pageID == null) {
      return;
    }
    // Collect this page's entries first (can't re-put while iterating), then re-append them at the tail
    // in their existing relative order.
    LinkedHashMap<Object, TransactionRecord> moved = new LinkedHashMap<>();
    Iterator entriesEnum = pageReplacementCache.entrySet().iterator();
    while (entriesEnum.hasNext()) {
      Map.Entry entry = (Map.Entry) entriesEnum.next();
      TransactionRecord record = (TransactionRecord) entry.getValue();
      if (pageID.equals(record.pageID())) {
        moved.put(entry.getKey(), record);
      }
    }
    if (moved.isEmpty()) {
      return;
    }
    for (Map.Entry<Object, TransactionRecord> e : moved.entrySet()) {
      pageReplacementCache.remove(e.getKey());
      pageReplacementCache.put(e.getKey(), e.getValue());
    }
  }

  /**
   * Bounds the page-replacement cache by the number of distinct PAGES it holds, not by the number of
   * fragment entries (and not by a blind insertion-order LRU over individual fragments).
   * <p>
   * The legacy behaviour evicted the single oldest fragment entry whenever the cache passed
   * {@code MAX_PAGE_REPLACEMENT_CACHE_SIZE * 2}. That is wrong for pages that do many <em>targeted</em>
   * partial updates: a container that is rarely re-rendered keeps a perfectly valid cached state, but
   * its entry is the oldest by insertion, so it gets evicted - and a still-current action link inside
   * it then 500s with "backtracked too far". Here we instead evict whole pages: a fragment's state is
   * only dropped when the <em>page</em> it belongs to is the oldest and we are over the page limit, in
   * which case all of that page's fragments go together (the page is dead, so its fragments are too).
   * The cache therefore stays sized by (live pages x containers-per-page), independent of how many
   * interactions have happened.
   *
   * @param pageReplacementCache the cache being added to
   * @param currentPageID the page id (originalContextID) of the entry about to be added; never evicted
   */
  protected void enforcePageReplacementCachePageLimit(LinkedHashMap pageReplacementCache, String currentPageID) {
    // Distinct page ids in cache order, which is least-recently-USED first: a page's fragments are
    // moved to the tail on every access (see touchPageInReplacementCache), so the head is the page the
    // user has gone longest without touching - the right one to evict (LRU, not FIFO).
    LinkedHashSet<String> pageIDs = new LinkedHashSet<>();
    Iterator recordsEnum = pageReplacementCache.values().iterator();
    while (recordsEnum.hasNext()) {
      TransactionRecord record = (TransactionRecord) recordsEnum.next();
      if (record.pageID() != null) {
        pageIDs.add(record.pageID());
      }
    }
    // Already tracking this page, or still under the limit -> nothing to evict.
    if (pageIDs.contains(currentPageID) || pageIDs.size() < ERXAjaxSession.MAX_PAGE_REPLACEMENT_CACHE_SIZE) {
      return;
    }
    // Over the page limit and adding a NEW page: drop every fragment of the oldest page.
    String oldestPageID = pageIDs.iterator().next();
    Iterator evictEnum = pageReplacementCache.entrySet().iterator();
    while (evictEnum.hasNext()) {
      Map.Entry entry = (Map.Entry) evictEnum.next();
      TransactionRecord record = (TransactionRecord) entry.getValue();
      if (oldestPageID.equals(record.pageID())) {
        if (log.isDebugEnabled()) log.debug("Page-limit reached; evicting fragment {} of oldest page {}", record.key(), oldestPageID);
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
    LinkedHashMap pageReplacementCache = (LinkedHashMap) objectForKey(ERXAjaxSession.PAGE_REPLACEMENT_CACHE_KEY);

    WOComponent page = null;
    if (pageReplacementCache != null) {
      TransactionRecord pageRecord = (TransactionRecord) pageReplacementCache.get(contextID);
      if (pageRecord == null) {
        // Fallback: a still-rendered action link carries the context it was rendered in, which may have
        // aged out of the cache - but it also carries its target container(s) in _u, and every fragment
        // of one page shares ONE page instance, so we can resolve the page by container instead. This is
        // what stops a long-lived link from 500ing once its original context falls out of the cache.
        //
        // Container ids are NOT page-unique: two instances of the same component in one session render the
        // same container names, so a name-only match could return a DIFFERENT page instance's fragment and
        // bleed its content into this request's container. So we resolve the page this contextID really
        // belongs to first - WO's own backtrack cache maps the link's render context to the right live page
        // - and only ever accept a container match on THAT same instance. If WO can no longer identify the
        // page (its context has aged out of WO's cache too) we deliberately do NOT guess: a different page
        // may still be sitting in the fragment cache, and serving it would be exactly the bleed we are
        // preventing. We return null instead and let the request honestly 500 ("backtracked too far") - a
        // dead-link error is strictly better than restoring the wrong object's content.
        WOComponent ownPage = super.restorePageForContextID(contextID);
        if (ownPage != null) {
          pageRecord = pageRecordForRequestContainer(pageReplacementCache, ownPage);
          if (pageRecord == null) {
            // No fragment for this page's container - serve WO's own (un-fragmented) page instance.
            page = ownPage;
          }
        }
        else {
          // We could not identify the page this stale context belongs to (it has aged out of BOTH the
          // fragment cache and WO's backtrack cache), so we refuse rather than risk serving a different
          // page's fragment. For an AJAX request (one carrying target containers in _u) this means the
          // request is about to 500 ("backtracked too far"). Log WHY at WARN - not behind the debug flag
          // - so a real-world dead-link failure leaves a breadcrumb instead of failing silently: the
          // missed context, the containers it wanted, and the cache it missed in. Gated on _u so a normal
          // non-ajax backtrack (which legitimately falls through to super below) doesn't cry wolf.
          WORequest rq = context() != null ? context().request() : null;
          String wantedContainers = rq != null ? rq.stringFormValueForKey(ERXAjaxApplication.KEY_UPDATE_CONTAINER_ID) : null;
          if (wantedContainers != null && wantedContainers.length() > 0 && log.isWarnEnabled()) {
            log.warn("Ajax page-cache MISS, request will fail: contextID={} could not be resolved - aged "
                + "out of both the fragment cache and WO's backtrack cache. _u={} | {}",
              contextID, wantedContainers, pageReplacementCacheSummary());
          }
        }
      }
      if (pageRecord != null) {
          log.debug("Restoring page for contextID: {} pageRecord = {}", contextID, pageRecord);
          page = pageRecord.page();
          // Access = most-recently-used: move this page's fragments to the tail so the page-limit
          // eviction drops the least-recently-USED page, not the first-inserted one (LRU, not FIFO).
          touchPageInReplacementCache(pageReplacementCache, pageRecord.pageID());
      }
      else if (page == null) {
        log.debug("No page in pageReplacementCache for contextID: {}", contextID);
        // If we got the page out of the replacement cache above, then we're obviously still
        // using Ajax, and it's likely our cache will be cleaned out in an Ajax update.  If the
        // requested page was not in the cache, though, then we might be done with Ajax,
        // so give the cache a quick run-through for expired pages.
        cleanPageReplacementCacheIfNecessary();
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
        request.setHeader(contextID, ERXAjaxSession.ORIGINAL_CONTEXT_ID_KEY);
      }
    }

    return page;
  }
}