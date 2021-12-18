/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr 
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.appserver;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WODirectAction;
import com.webobjects.appserver.WOMessage;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.woextensions.events.WOEventDisplayPage;
import com.webobjects.woextensions.events.WOEventSetupPage;
import com.webobjects.woextensions.stats.WOStatsPage;

import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXStringUtilities;
import er.extensions.localization.ERXLocalizer;
import er.extensions.logging.ERXLog4JConfiguration;
import er.extensions.logging.ERXLogger;
import er.extensions.statistics.ERXStats;

/**
 * Basic collector for direct action additions. All of the actions are password
 * protected, you need to give an argument "pw" that matches the corresponding
 * system property for the action.
 */

public class ERXDirectAction extends WODirectAction {

	private Logger log = LoggerFactory.getLogger( ERXDirectAction.class );

	public ERXDirectAction(WORequest r) {
		super(r);
	}

	/**
	 * Checks if the action can be executed.
	 * 
	 * @param passwordKey the password to test
	 * @return <code>true</code> if action is allowed to be invoked
	 */
	protected boolean canPerformActionWithPasswordKey(String passwordKey) {

		if (ERXApplication.isDevelopmentModeSafe()) {
			return true;
		}

//		String password = ERXProperties.decryptedStringForKey(passwordKey); // FIXME: This password used to be encrypted. Can't we just eliminate this DA instead?
		String password = passwordKey;

		if (password == null || password.length() == 0) {
			log.error("Attempt to use action when key is not set: {}", passwordKey);
			return false;
		}

		String requestPassword = request().stringFormValueForKey("pw");

		if (requestPassword == null) {
			requestPassword = (String) context().session().objectForKey("ERXDirectAction." + passwordKey);
		}
		else {
			context().session().setObjectForKey(requestPassword, "ERXDirectAction." + passwordKey);
		}

		if (requestPassword == null || requestPassword.length() == 0) {
			return false;
		}

		return password.equals(requestPassword);
	}

	/**
	 * Flushes the component cache to allow reloading components even when WOCachingEnabled=true.
	 * 
	 * @return "OK"
	 */
	public WOActionResults flushComponentCacheAction() {

		if (canPerformActionWithPasswordKey("er.extensions.ERXFlushComponentCachePassword")) {
			WOApplication.application()._removeComponentDefinitionCacheContents();
			WOResponse response = new WOResponse();
			response.setContent("OK");
			return response;
		}

		return forbiddenResponse();
	}

	/**
	 * Direct access to WOStats by giving over the password in the "pw" parameter.
	 * 
	 * @return statistics page
	 * 
	 * FIXME: This is now just a copy of WOStats/defaultAction() // Hugi 2021-06-27
	 */
	public WOActionResults statsAction() {
		WOStatsPage nextPage = pageWithName(WOStatsPage.class);
		nextPage.password = context().request().stringFormValueForKey("pw");
		return nextPage.submit();
	}

	/**
	 * Direct access to reset the stats by giving over the password in the "pw" parameter. This calls ERXStats.reset();
	 * 
	 * @return statistics page
	 */
	public WOActionResults resetStatsAction() {

		if (canPerformActionWithPasswordKey("WOStatisticsPassword")) {
			ERXStats.reset();
			ERXRedirect redirect = pageWithName(ERXRedirect.class);
			redirect.setDirectActionName("stats");
			redirect.setDirectActionClass("ERXDirectAction");
			return redirect;
		}

		return forbiddenResponse();
	}

	/**
	 * Direct access to WOEventDisplay by giving over the password in the "pw" parameter.
	 * 
	 * @return event page
	 */
	public WOActionResults eventsAction() {
		WOEventDisplayPage nextPage = pageWithName(WOEventDisplayPage.class);
		nextPage.password = context().request().stringFormValueForKey("pw");
		nextPage.submit();
		return nextPage;
	}

	/**
	 * Direct access to WOEventDisplay by giving over the password in the "pw" parameter and turning on all events.
	 * 
	 * @return event setup page
	 */
	public WOActionResults eventsSetupAction() {
		WOEventSetupPage nextPage = pageWithName(WOEventSetupPage.class);
		nextPage.password = context().request().stringFormValueForKey("pw");
		nextPage.submit();
		nextPage.selectAll();
		return eventsAction();
	}

	/**
	 * Action used for changing logging settings at runtime. This method is only
	 * active when WOCachingEnabled is disabled (we take this to mean that the
	 * application is not in production).
	 * <h3>Synopsis:</h3> pw=<i>aPassword</i>
	 * <h3>Form Values:</h3> <b>pw</b> password to be checked against the system
	 * property <code>er.extensions.ERXLog4JPassword</code>.
	 * 
	 * @return {@link ERXLog4JConfiguration} for modifying current logging settings.
	 */
	public WOActionResults log4jAction() {

		if (canPerformActionWithPasswordKey("er.extensions.ERXLog4JPassword")) {
			session().setObjectForKey(Boolean.TRUE, "ERXLog4JConfiguration.enabled");
			return pageWithName(ERXLog4JConfiguration.class);
		}

		return forbiddenResponse();
	}

	/**
	 * Will terminate an existing session and redirect to the default action.
	 * 
	 * @return redirect to default action
	 */
	public WOActionResults logoutAction() {

		if (existingSession() != null) {
			existingSession().terminate();
		}

		ERXRedirect r = pageWithName(ERXRedirect.class);
		r.setDirectActionName("default");
		return r;
	}

	/**
	 * Sets a System property. This is also active in deployment mode because
	 * one might want to change a System property at runtime.
	 * 
	 * <h3>Synopsis:</h3>
	 * pw=<i>aPassword</i>&amp;key=<i>someSystemPropertyKey</i>&amp;value=<i>someSystemPropertyValue</i>
	 * 
	 * @return either null when the password is wrong or a new page showing the System properties
	 */
	public WOActionResults systemPropertyAction() {
		if (canPerformActionWithPasswordKey("er.extensions.ERXDirectAction.ChangeSystemPropertyPassword")) {
			String key = request().stringFormValueForKey("key");
			WOResponse r = new WOResponse();
			if (ERXStringUtilities.isNullOrEmpty(key)) {
				String user = request().stringFormValueForKey("user");
				Properties props = ERXConfigurationManager.defaultManager().defaultProperties();
				if (user != null) {
					System.setProperty("user.name", user);
					props = ERXConfigurationManager.defaultManager().applyConfiguration(props);
				}
				r.appendContentString(ERXProperties.logString(props));
			}
			else {
				String value = request().stringFormValueForKey("value");
				value = ERXStringUtilities.isNullOrEmpty(value) ? "" : value;
				java.util.Properties p = System.getProperties();
				p.put(key, value);
				System.setProperties(p);
				ERXLogger.configureLoggingWithSystemProperties();
				for (java.util.Enumeration e = p.keys(); e.hasMoreElements();) {
					Object k = e.nextElement();
					if (k.equals(key)) {
						r.appendContentString("<b>'" + k + "=" + p.get(k) + "'     <= you changed this</b><br>");
					}
					else {
						r.appendContentString("'" + k + "=" + p.get(k) + "'<br>");
					}
				}
				r.appendContentString("</body></html>");
			}
			return r;
		}
		return forbiddenResponse();
	}

	/**
	 * Will dump all created keys of the current localizer via log4j and returns an empty response.
	 * 
	 * @return empty response
	 */
	public WOActionResults dumpCreatedKeysAction() {
		if (ERXApplication.isDevelopmentModeSafe()) {
			session();
			ERXLocalizer.currentLocalizer().dumpCreatedKeys();
			return new WOResponse();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public <T extends WOComponent> T pageWithName(Class<T> componentClass) {
		return (T) super.pageWithName(componentClass.getName());
	}

	/**
	 * Terminates the application when in development.
	 * 
	 * @return "OK" if application has been shut down
	 */
	public WOActionResults stopAction() {
		WOResponse response = new WOResponse();
		response.setHeader("text/plain", "Content-Type");

		if (ERXApplication.isDevelopmentModeSafe()) {
			WOApplication.application().terminate();
			response.setContent("OK");
		}
		else {
			response.setStatus(401);
		}

		return response;
	}

	/**
	 * Creates a response object with HTTP status code 403.
	 * 
	 * @return 403 response
	 */
	private static WOResponse forbiddenResponse() {
		WOResponse response = new WOResponse();
		response.setStatus( WOMessage.HTTP_STATUS_FORBIDDEN );
		return response;
	}
}