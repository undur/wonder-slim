/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.appserver;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOCookie;
import com.webobjects.appserver.WOCookie.SameSite;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOSession;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSKeyValueCodingAdditions;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSTimestamp;

import er.extensions.appserver.ajax.ERXAjaxSession;
import er.extensions.browser.ERXBrowser;
import er.extensions.browser.ERXBrowserFactory;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXThreadStorage;
import er.extensions.foundation.ERXUtilities;
import er.extensions.localization.ERXLocalizer;

/**
 * Improvements and fixes for WOSession 
 */

public class ERXSession extends ERXAjaxSession implements Serializable {

	private static final Logger log = LoggerFactory.getLogger(ERXSession.class);

	/**
	 * Notification posted when a session is about to sleep.
	 */
	public static final String SessionWillSleepNotification = "SessionWillSleepNotification";

	/**
	 * SameSite for session and instance cookies
	 */
	private static final SameSite _sameSite = ERXProperties.enumValueForKey(SameSite.class, "er.extensions.ERXSession.cookies.SameSite");

	/**
	 * Localizer used for this session
	 */
	transient private ERXLocalizer _localizer;

	/**
	 * special variable to hold language name only for when session object gets serialized.
	 * Do not use this value to get the language name; use {@link #language} method instead.
	 */
	private String _serializableLanguageName;

	/** 
	 * The browser used for this session
	 */
	transient private ERXBrowser _browser;

	/**
	 * Receiver of the various notifications
	 */
	transient private Observer _observer;

	/**
	 * the original name from the WorkerThread which is the value before executing <code>awake()</code>
	 */
	public String _originalThreadName;

	public ERXSession() {
		super();
	}

	public ERXSession(String sessionID) {
		super(sessionID);
	}

	/**
	 * @return the observer object for this session. If it doesn't ever exist, one will be created.
	 */
	public Observer observer() {
		if (_observer == null) {
			_observer = new Observer(this);
		}

		return _observer;
	}

	/**
	 * The Observer inner class encapsulates functions to handle various notifications.
	 */
	public static class Observer {

		/**
		 * The owning session
		 */
		transient protected ERXSession session;

		/**
		 * Prevents instantiation in this way
		 */
		private Observer() {}

		/**
		 * Create observer objects for the given session
		 */
		public Observer(ERXSession session) {
			this.session = session;
		}

		/**
		 * Reset the reference to localizer when localization templates or localizer class itself is updated.
		 */
		public void localizationDidReset(NSNotification n) {

			if (session._localizer == null) {
				return;
			}

			final String currentLanguage = session._localizer.language();
			session._localizer = ERXLocalizer.localizerForLanguage(currentLanguage);
			log.debug("Detected changes in the localizers. Reset reference to {} localizer for session {}", currentLanguage, session.sessionID());
		}

		/**
		 * Registers this observer object for {@link er.extensions.localization.ERXLocalizer#LocalizationDidResetNotification}
		 */
		protected void registerForLocalizationDidResetNotification() {
			NSNotificationCenter.defaultCenter().addObserver(this, ERXUtilities.notificationSelector("localizationDidReset"), ERXLocalizer.LocalizationDidResetNotification, null);
		}
	}

	/**
	 * Method to get the current localizer for this session. If local instance
	 * variable is null then a localizer is fetched for the session's
	 * <code>languages</code> array. See
	 * {@link er.extensions.localization.ERXLocalizer} for more information
	 * about using a localizer.
	 * 
	 * @return the current localizer for this session
	 */
	public ERXLocalizer localizer() {
		if (_localizer == null) {
			_localizer = ERXLocalizer.localizerForLanguages(languages());

			if (!WOApplication.application().isCachingEnabled()) {
				observer().registerForLocalizationDidResetNotification();
			}
		}

		return _localizer;
	}

	/**
	 * @return The primary language of the current session's localizer. This
	 * method is just a cover for calling the method <code>localizer().language()</code>.
	 */
	public String language() {
		return localizer().language();
	}

	/**
	 * Cover method to set the current localizer to the localizer for that
	 * language.
	 * <p>
	 * Also updates languages list with the new single language.
	 * 
	 * @param language to set the current localizer for.
	 * @see #language
	 * @see #setLanguages
	 */
	public void setLanguage(String language) {
		final ERXLocalizer newLocalizer = ERXLocalizer.localizerForLanguage(language);

		if (!newLocalizer.equals(_localizer)) {
			if (_localizer == null && !WOApplication.application().isCachingEnabled()) {
				observer().registerForLocalizationDidResetNotification();
			}

			_localizer = newLocalizer;
			ERXLocalizer.setCurrentLocalizer(_localizer);

			final NSMutableArray languageList = new NSMutableArray(_localizer.language());

			if (!languageList.containsObject("Nonlocalized")) {
				languageList.addObject("Nonlocalized");
			}

			setLanguages(languageList);
		}
	}

	/**
	 * Sets the languages list for which the session is localized. The ordering
	 * of language strings in the array determines the order in which the
	 * application will search .lproj directories for localized strings, images,
	 * and component definitions.
	 * <p>
	 * Also updates localizer
	 * 
	 * @param languageList the array of languages for the session
	 * @see #language
	 * @see #setLanguage
	 */
	@Override
	public void setLanguages(NSArray languageList) {
		super.setLanguages(languageList);

		final ERXLocalizer newLocalizer = ERXLocalizer.localizerForLanguages(languageList);

		if (!newLocalizer.equals(_localizer)) {
			if (_localizer == null && !WOApplication.application().isCachingEnabled()) {
				observer().registerForLocalizationDidResetNotification();
			}

			_localizer = newLocalizer;
			ERXLocalizer.setCurrentLocalizer(_localizer);
		}
	}

	/**
	 * Returns the NSArray of language names available for this application.
	 * This is simply a cover method of
	 * {@link er.extensions.localization.ERXLocalizer#availableLanguages}, but
	 * will be convenient for binding to dynamic elements like language selector popup.
	 * 
	 * @return NSArray of language name strings available for this application
	 * @see #availableLanguagesForThisSession
	 * @see er.extensions.localization.ERXLocalizer#availableLanguages
	 */
	public NSArray availableLanguagesForTheApplication() {
		return ERXLocalizer.availableLanguages();
	}

	/**
	 * Returns the NSArray of language names available for this particular
	 * session. The resulting array is an intersect of web browser's language
	 * array ({@link ERXRequest#browserLanguages()}) and localizer's available
	 * language array
	 * ({@link er.extensions.localization.ERXLocalizer#availableLanguages()}).
	 * <p>
	 * Note that the order of the resulting language names is not defined at this moment.
	 * 
	 * @return NSArray of language name strings available for this particular session
	 * @see #availableLanguagesForTheApplication
	 * @see ERXRequest#browserLanguages()
	 * @see er.extensions.localization.ERXLocalizer#availableLanguages
	 */
	public NSArray availableLanguagesForThisSession() {
		final HashSet<String> languages = new HashSet<>(ERXLocalizer.availableLanguages());

		if (context() != null && context().request() != null) {
			languages.retainAll(context().request().browserLanguages());
		}

		return new NSArray<>(languages);
	}

	/**
	 * @return Browser object representing the web browser's "user-agent"
	 * string. You can obtain browser name, version, platform and Mozilla
	 * version, etc. through this object. <br>
	 * Good for WOConditional's condition binding to deal with different browser versions.
	 */
	public ERXBrowser browser() {
		if (_browser == null && context() != null) {
			final WORequest request = context().request();

			if (request != null) {
				final ERXBrowserFactory browserFactory = ERXBrowserFactory.factory();

				if (request instanceof ERXRequest) {
					_browser = ((ERXRequest) request).browser();
				}
				else {
					_browser = browserFactory.browserMatchingRequest(request);
				}

				browserFactory.retainBrowser(_browser);
			}
		}

		return _browser;
	}

	/**
	 * Overridden to provide a few checks to see if javascript is enabled.
	 */
	@Override
	public void awake() {
		super.awake();
		ERXSession.setSession(this);
		ERXLocalizer.setCurrentLocalizer(localizer());
		NSNotificationCenter.defaultCenter().postNotification(SessionDidRestoreNotification, this);

		WORequest request = context() != null ? context().request() : null;

		if (request != null && log.isDebugEnabled() && request.headerForKey("content-type") != null) {
			if ((request.headerForKey("content-type")).toLowerCase().indexOf("multipart/form-data") == -1)
				log.debug("Form values {}", request.formValues());
			else
				log.debug("Multipart Form values found");
		}

		_originalThreadName = Thread.currentThread().getName();
		Thread.currentThread().setName(threadName());

	}

	@Override
	public void sleep() {
		NSNotificationCenter.defaultCenter().postNotification(SessionWillSleepNotification, this);
		super.sleep();
		ERXLocalizer.setCurrentLocalizer(null);
		ERXSession.setSession(null);
		// reset backtracking
		_didBacktrack = null;
		Thread.currentThread().setName(_originalThreadName);
		removeObjectForKey("ERXActionLogging");
	}

	/**
	 * Override this method in order to provide a different name for the
	 * WorkerThread for this request-response loop very useful for logging
	 * stuff: assign a log statement to a log entry. Something useful could be:
	 * 
	 * <blockquote><code>return session().sessionID() + valueForKeyPath("user.username");</code></blockquote>
	 * 
	 * @return name of the current thread
	 */
	public String threadName() {
		return Thread.currentThread().getName();
	}

	/*
	 * Backtrack detection - Pulled from David Neumann's wonderful security
	 * framework.
	 */

	/**
	 * flag to indicate if the user is currently backtracking, meaning they hit
	 * the back button and then clicked on a link.
	 */
	private Boolean _didBacktrack = null;

	/**
	 * flag to indicate if the last action was a direct action
	 */
	public boolean lastActionWasDA = false;

	/**
	 * Utility method that gets the context ID string from the passed in request.
	 * 
	 * @param aRequest request to get the context id from
	 * @return the context id as a string
	 */
	private String requestsContextID(WORequest aRequest) {
		String uri = aRequest.uri();
		int idx = uri.indexOf('?');
		if (idx != -1)
			uri = uri.substring(0, idx);
		String eID = NSPathUtilities.lastPathComponent(uri);
		NSArray eIDs = NSArray.componentsSeparatedByString(eID, ".");
		String reqCID = "1";
		if (eIDs.count() > 0) {
			reqCID = (String) eIDs.objectAtIndex(0);
		}
		return reqCID;
	}

	/**
	 * Method inspects the passed in request to see if the user backtracked. If
	 * the context ID for the request is 2 clicks less than the context ID for
	 * the current WOContext, we know the backtracked.
	 * 
	 * @return if the user has backtracked or not.
	 */
	public boolean didBacktrack() {
		if (_didBacktrack == null) {
			_didBacktrack = Boolean.FALSE;
			// If the current request is a direct action, no way the user could have backtracked.
			if (!context().request().requestHandlerKey().equals(WOApplication.application().directActionRequestHandlerKey())) {
				int reqCID = Integer.parseInt(requestsContextID(context().request()));
				int cid = Integer.parseInt(context().contextID());
				int delta = cid - reqCID;
				if (delta > 2) {
					_didBacktrack = Boolean.TRUE;
				}
				else if (delta > 1) {
					// Might not have backtracked if their last action was a direct action.
					// ERXDirectActionRequestHandler, which is the framework
					// built-in default direct action handler, sets this variable
					// to true at the end of its handleRequest method.
					if (!lastActionWasDA) {
						_didBacktrack = Boolean.TRUE;
					}
				}
			}
			lastActionWasDA = false;
		}

		return _didBacktrack.booleanValue();
	}

	/**
	 * @return Cast application
	 */
	public ERXApplication application() {
		return ERXApplication.erxApplication();
	}

	/**
	 * Overrides terminate to free up resources and unregister for
	 * notifications.
	 */
	@Override
	public void terminate() {
		if (_observer != null) {
			NSNotificationCenter.defaultCenter().removeObserver(_observer);
			_observer = null;
		}

		if (_browser != null) {
			ERXBrowserFactory.factory().releaseBrowser(_browser);
			_browser = null;
		}

		log.debug("Will terminate, sessionId is {}", sessionID());

		super.terminate();
	}

	private transient NSKeyValueCodingAdditions _objectStore;

	/**
	 * This is a cover method which enables use of the session's object store
	 * which is usually access with setObjectForKey and objectForKey. One can
	 * use this method with KVC, like for example in .wod bindings:
	 * 
	 * <code>
	 * myString: WOString {
	 *      value = session.objectStore.myLastSearchResult.count;
	 * }
	 * </code>
	 * 
	 * @return an Object which implements KVC + KVC additions
	 */
	public NSKeyValueCodingAdditions objectStore() {
		if (_objectStore == null) {
			_objectStore = new NSKeyValueCodingAdditions() {
				public void takeValueForKey(Object arg0, String arg1) {
					if (arg0 == null) {
						removeObjectForKey(arg1);
					}
					else {
						setObjectForKey(arg0, arg1);
					}
				}

				public Object valueForKey(String arg0) {
					return objectForKey(arg0);
				}

				public void takeValueForKeyPath(Object arg0, String arg1) {
					if (arg0 == null) {
						removeObjectForKey(arg1);
					}
					else {
						setObjectForKey(arg0, arg1);
					}
				}

				public Object valueForKeyPath(String arg0) {
					Object theObject = objectForKey(arg0);
					if (theObject == null && arg0.indexOf(".") > -1) {
						String key = "";
						String oriKey = arg0;
						do {
							key = key + oriKey.substring(0, oriKey.indexOf("."));
							oriKey = oriKey.substring(oriKey.indexOf(".") + 1);
							theObject = objectForKey(key);
							key += ".";
						}
						while (theObject == null && oriKey.indexOf(".") > -1);
						if (theObject != null && !ERXUtilities.stringIsNullOrEmpty(oriKey)) {
							theObject = NSKeyValueCodingAdditions.Utility.valueForKeyPath(theObject, oriKey);
						}
					}
					return theObject;
				}
			};
		}
		return _objectStore;
	}

	/*
	 * Serialization support - enables to use a variety of session stores
	 */
	private void writeObject(ObjectOutputStream stream) throws IOException {
		if (_localizer == null)
			_serializableLanguageName = null;
		else
			_serializableLanguageName = language();
		stream.defaultWriteObject();
	}

	private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		if (_serializableLanguageName != null)
			setLanguage(_serializableLanguageName);
		log.debug("Session has been deserialized: {}", this);
	}

	/**
	 * Overridden to make method public
	 */
	@Override
	public NSTimestamp _birthDate() {
		return super._birthDate();
	}

	@Override
	public String toString() {
		String superString = super.toString();
		String thisString = " localizer=" + (_localizer == null ? "null" : _localizer.toString()) + " browser=" + (_browser == null ? "null" : _browser.toString());

		int lastIndex = superString.lastIndexOf(">");
		String toStr;
		if (lastIndex > 0) { // ignores if ">" is the first char (lastIndex == 0)
			toStr = superString.substring(0, lastIndex - 1) + thisString + ">";
		}
		else {
			toStr = superString + thisString;
		}
		return toStr;
	}

	public static WOSession anySession() {
		return (WOSession) ERXThreadStorage.valueForKey("session");
	}

	public static ERXSession session() {
		return (ERXSession) ERXThreadStorage.valueForKey("session");
	}

	public static String currentSessionID() {
		return (String) ERXThreadStorage.valueForKey("ERXSession.sessionID");
	}

	public static void setSession(ERXSession session) {
		ERXThreadStorage.takeValueForKey(session, "session");
		ERXThreadStorage.takeValueForKey(session == null ? null : session.sessionID(), "ERXSession.sessionID");
	}

	/**
	 * Override and return true, or set
	 * er.extensions.ERXSession.useSecureSessionCookies if you want secure-only
	 * session and instance cookies. This prevents cookie hijacking
	 * man-in-the-middle attacks. If the cookies aren't set as secure only and
	 * an HTTP request is made, the cookies will be sent over HTTP. So if
	 * someone manages to do an HTTP injection that causes an HTTP request to be
	 * made, they can compromise your session id. For example, if you have a CMS
	 * on https://www.mycms.com and you set a session id, and I hack in and
	 * trick your site and manage to do an injection where i do an &lt;img
	 * src="http://www.mycms.com/whatever"/&gt; in the content, like I post in a
	 * comment and you don't strip out HTML tags. secure-only just gives you
	 * peace-of-mind. If you intended the cookies to only be behind HTTPS,
	 * secure-only makes it actually true and enforced.
	 * 
	 * Note that to make this effective (and for sessions to work at all), your
	 * site must be behind HTTPS at all times. In development mode, you can
	 * disable secure mode (@see er.extensions.ERXRequest.isSecureDisabled) for
	 * running in direct-connect with this mode enabled.
	 * 
	 * @return whether or not secure cookies are enabled
	 */
	public boolean useSecureSessionCookies() {
		return ERXProperties.booleanForKeyWithDefault("er.extensions.ERXSession.useSecureSessionCookies", false);
	}

	/**
	 * Override and return true, or set
	 * er.extensions.ERXSession.useHttpOnlySessionCookies if you want http-only
	 * session and instance cookies. This prevents the XSS attack. Note that
	 * after setting this true, you will not allowed to read this cookies from
	 * yours javascript code.
	 * 
	 * @return whether or not http-only cookies are enabled
	 */
	public static boolean useHttpOnlySessionCookies() {
		return ERXProperties.booleanForKeyWithDefault("er.extensions.ERXSession.useHttpOnlySessionCookies", false);
	}

	protected void _setCookieSameSite(WOResponse response) {
		if (storesIDsInCookies() && _sameSite != null) {
			for (WOCookie cookie : response.cookies()) {
				String sessionIdKey = application().sessionIdKey();
				String instanceIdKey = application().instanceIdKey();
				String cookieName = cookie.name();
				if (sessionIdKey.equals(cookieName) || instanceIdKey.equals(cookieName)) {
					cookie.setSameSite(_sameSite);
				}
			}
		}
	}

	protected void _convertSessionCookiesToSecure(WOResponse response) {
		if (storesIDsInCookies() && !ERXRequest._isSecureDisabled()) {
			for (WOCookie cookie : response.cookies()) {
				String sessionIdKey = application().sessionIdKey();
				String instanceIdKey = application().instanceIdKey();
				String cookieName = cookie.name();
				if (sessionIdKey.equals(cookieName) || instanceIdKey.equals(cookieName)) {
					cookie.setIsSecure(true);
				}
			}
		}
	}

	protected void _convertSessionCookiesToHttpOnly(final WOResponse response) {
		if (storesIDsInCookies()) {
			for (WOCookie cookie : response.cookies()) {
				String sessionIdKey = application().sessionIdKey();
				String instanceIdKey = application().instanceIdKey();
				String cookieName = cookie.name();
				if (sessionIdKey.equals(cookieName) || instanceIdKey.equals(cookieName)) {
					cookie.setIsHttpOnly(true);
				}
			}
		}
	}

	@Override
	public void _appendCookieToResponse(WOResponse response) {
		super._appendCookieToResponse(response);
		if (useSecureSessionCookies()) {
			_convertSessionCookiesToSecure(response);
		}
		if (useHttpOnlySessionCookies()) {
			_convertSessionCookiesToHttpOnly(response);
		}
		_setCookieSameSite(response);
	}

	@Override
	public void _clearCookieFromResponse(WOResponse response) {
		super._clearCookieFromResponse(response);
		if (useSecureSessionCookies()) {
			_convertSessionCookiesToSecure(response);
		}
		if (useHttpOnlySessionCookies()) {
			_convertSessionCookiesToHttpOnly(response);
		}
	}
}
