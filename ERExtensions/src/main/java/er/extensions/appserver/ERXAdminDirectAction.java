/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr 
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.appserver;

import java.util.Properties;

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

	public ERXAdminDirectAction(WORequest r) {
		super(r);
	}

	@SuppressWarnings("unchecked")
	public <T extends WOComponent> T pageWithName(Class<T> componentClass) {
		return (T) super.pageWithName(componentClass.getName());
	}

	/**
	 * Direct access to reset the stats by giving over the password in the "pw" parameter. This calls ERXStats.reset();
	 * 
	 * @return statistics page 
	 */
	public WOActionResults resetStatsAction() {

		if (canPerformAction()) {
			ERXStats.reset();
			ERXRedirect redirect = pageWithName(ERXRedirect.class);
			redirect.setDirectActionName("stats");
			redirect.setDirectActionClass("ERXDirectAction");
			return redirect;
		}

		return forbiddenResponse();
	}

	/**
	 * @return WOStatsPage page using password in the "pw" query parameter
	 * 
	 * FIXME: Why?
	 */
    public WOActionResults statsAction() {
        WOStatsPage nextPage = pageWithName(WOStatsPage.class);
        nextPage.password = context().request().stringFormValueForKey("pw");
        return nextPage.submit();
    }

	/**
	 * @return WOEventDisplay page using password in the "pw" query parameter
	 * 
	 * FIXME: Why?
	 */
	public WOActionResults eventsAction() {
		WOEventDisplayPage nextPage = pageWithName(WOEventDisplayPage.class);
		nextPage.password = context().request().stringFormValueForKey("pw");
		return nextPage.submit();
	}

	/**
	 * @return WOEventSetup page using password in the "pw" query parameter
	 * 
	 * FIXME: Why?
	 */
	public WOActionResults eventsSetupAction() {
		WOEventSetupPage nextPage = pageWithName(WOEventSetupPage.class);
		nextPage.password = context().request().stringFormValueForKey("pw");
		nextPage.submit();
		nextPage.selectAll();
		return eventsAction();
	}

	/**
	 * Sets a System property. Also active in deployment mode.
	 * 
	 * <h3>Synopsis:</h3>
	 * pw=<i>aPassword</i>&amp;key=<i>someSystemPropertyKey</i>&amp;value=<i>someSystemPropertyValue</i>
	 * 
	 * @return either null when the password is wrong or a new page showing the System properties
	 */
	public WOActionResults systemPropertyAction() {
		if (canPerformAction()) {
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
	 * @return true if the request parameter "pw" matches the pw set for WOStatisticsStore
	 * 
	 * FIXME: This is a temporary placeholder until we have a nicer access control implementation // Hugi 2022-03-21 
	 */
	protected boolean canPerformAction() {
		
		if (ERXApplication.isDevelopmentModeSafe()) {
			return true;
		}

		final String password = request().stringFormValueForKey("pw");
		
		if( ERXUtilities.stringIsNullOrEmpty( password ) ) {
			return false;
		}
		
		final Object uglyAssWayToGetThestatisticsStorePassword = ERXUtilities.privateValueForKey(ERXApplication.erxApplication().statisticsStore(), "_password" );

		return password.equals( uglyAssWayToGetThestatisticsStorePassword );
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