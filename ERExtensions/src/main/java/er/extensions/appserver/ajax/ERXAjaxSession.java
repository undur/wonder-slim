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
import er.extensions.foundation.ERXUtilities.ERXPrivateKVC;

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
    private boolean _oldPage;
    private long _lastModified;

    public TransactionRecord(WOComponent page, WOContext context, String key) {
      _page = page;
      _contextID = context._requestContextID();
      _key = key;
      touch();
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
        String originalContextID = context.request().headerForKey(ERXAjaxSession.ORIGINAL_CONTEXT_ID_KEY);
        pageCacheKey = originalContextID + "_" + pageCacheKey;
        LinkedHashMap pageReplacementCache = (LinkedHashMap) objectForKey(ERXAjaxSession.PAGE_REPLACEMENT_CACHE_KEY);
        if (pageReplacementCache == null) {
          pageReplacementCache = new LinkedHashMap();
          setObjectForKey(pageReplacementCache, ERXAjaxSession.PAGE_REPLACEMENT_CACHE_KEY);
        }

        // Remove the oldest entry if we're about to add a new one and that would put us over the cache size ...
        // We do a CACHE_SIZE*2 here because for every page, we have to potentially store its previous contextid to prevent
        // race conditions, so there technically can be 2x cache size many pages in the cache.
        boolean removedCacheEntry = cleanPageReplacementCacheIfNecessary(pageCacheKey);
        if (!removedCacheEntry && pageReplacementCache.size() >= ERXAjaxSession.MAX_PAGE_REPLACEMENT_CACHE_SIZE * 2) {
          Iterator entryIterator = pageReplacementCache.entrySet().iterator();
          Map.Entry oldestEntry = (Map.Entry) entryIterator.next();
          entryIterator.remove();
          if (log.isDebugEnabled()) log.debug("{} pageReplacementCache too large, removing oldest entry = {}", pageCacheKey, ((TransactionRecord)oldestEntry.getValue()).key());
        }

        TransactionRecord pageRecord = new TransactionRecord(page, context, pageCacheKey);
        pageReplacementCache.put(context.contextID(), pageRecord);
        log.debug("{} new context = {}", pageCacheKey, context.contextID());
        log.debug("{} = {}", pageCacheKey, pageReplacementCache.keySet());

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
      if (pageRecord != null) {
          log.debug("Restoring page for contextID: {} pageRecord = {}", contextID, pageRecord);
          page = pageRecord.page();
      }
      else {
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