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
import er.extensions.foundation.ERXUtilities;
import er.extensions.statistics.ERXStats;

public class ERXAdminDirectAction extends WODirectAction {

	private static Logger log = LoggerFactory.getLogger( ERXAdminDirectAction.class );

	public ERXAdminDirectAction(WORequest r) {
		super(r);
	}

	@SuppressWarnings("unchecked")
	public <T extends WOComponent> T pageWithName(Class<T> componentClass) {
		return (T) super.pageWithName(componentClass.getName());
	}

	/**
	 * @return true if the request parameter "pw" matches the pw set for WOStatisticsStore
	 * 
	 * FIXME: This is a temporary placeholder until we have a nicer access control implementation // Hugi 2022-03-21 
	 */
	protected boolean canPerformAction() {
		final String password = request().stringFormValueForKey("pw");
		
		if( ERXUtilities.stringIsNullOrEmpty( password ) ) {
			return false;
		}
		
		final Object uglyAssWayToGetThestatisticsStorePassword = ERXUtilities.privateValueForKey(ERXApplication.erxApplication().statisticsStore(), "_password" );

		return password.equals( uglyAssWayToGetThestatisticsStorePassword );
	}
	
	/**
	 * @param passwordKey the password to test
	 * @return <code>true</code> if action is allowed to be invoked
	 */
	@Deprecated
	private boolean canPerformActionWithPasswordKey(String passwordKey) {

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
			if (ERXUtilities.stringIsNullOrEmpty(key)) {
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
				value = ERXUtilities.stringIsNullOrEmpty(value) ? "" : value;
				java.util.Properties p = System.getProperties();
				p.put(key, value);
				System.setProperties(p);
				ERXApplication.configureLoggingWithSystemProperties();
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
	 * @return A response object with HTTP status code 403.
	 */
	protected static WOResponse forbiddenResponse() {
		WOResponse response = new WOResponse();
		response.setStatus( WOMessage.HTTP_STATUS_FORBIDDEN );
		return response;
	}
}