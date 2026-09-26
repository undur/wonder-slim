/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.appserver;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOCookie;
import com.webobjects.appserver.WOCookie.SameSite;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOSession;
import com.webobjects.foundation.NSKeyValueCodingAdditions;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSTimestamp;

import er.extensions.appserver.ajax.ERXAjaxSession;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXThreadStorage;
import er.extensions.foundation.ERXUtilities;

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
	 * The locale this session formats numbers and dates in, when set explicitly. See {@link ERXLocale}.
	 */
	private Locale _locale;

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
	 * @return The locale this session formats numbers and dates in, null when none was set (see {@link ERXLocale})
	 */
	public Locale locale() {
		return _locale;
	}

	/**
	 * Sets the locale this session formats numbers and dates in, taking precedence over the request's and the
	 * application's. null reverts to those. See {@link ERXLocale}.
	 */
	public void setLocale( final Locale locale ) {
		_locale = locale;
	}

	/**
	 * Overridden to provide a few checks to see if javascript is enabled.
	 */
	@Override
	public void awake() {
		super.awake();
		ERXSession.setSession(this);
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
		ERXSession.setSession(null);
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
	private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
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
		String thisString = " locale=" + _locale;

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
