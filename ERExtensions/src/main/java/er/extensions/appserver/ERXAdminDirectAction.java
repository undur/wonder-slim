/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr 
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.appserver;

import java.lang.reflect.Field;
import java.util.Properties;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WODirectAction;
import com.webobjects.appserver.WOMessage;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.WOStatisticsStore;

import er.extensions.ERXLoggingSupport;
import er.extensions.foundation.ERXConfigurationManager;
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
	 * WO's statistics and event pages are component pages behind a password form. These actions fill in the form and
	 * submit it on the caller's behalf, which gives each page a URL that can be bookmarked or requested by a monitoring
	 * script. The page itself checks the password. The page is returned, not what its submit action returns: that's a form
	 * action, and the event pages' returns null to show the same page again, which as a direct action's result is an
	 * error.
	 */
    public WOActionResults statsAction() {
        // The page lives in ERControl, which need not be present: reached by name, not by class
        return submittedPage("WOStatsPage");
    }

	/**
	 * @return WOEventDisplay page using password in the "pw" query parameter
	 *
	 * See {@link #statsAction()}. The event pages' password is {@code EOEventLoggingPassword}, not the statistics
	 * password; with none set, nobody can log in.
	 */
	public WOActionResults eventsAction() {
		return submittedPage("WOEventDisplayPage");
	}

	/**
	 * @return WOEventSetup page using password in the "pw" query parameter
	 *
	 * See {@link #statsAction()}. Turns on logging for every event type, then shows the event display.
	 */
	public WOActionResults eventsSetupAction() {
		final WOComponent setupPage = submittedPage("WOEventSetupPage");
		setupPage.valueForKey("selectAll");
		return eventsAction();
	}

	/**
	 * @return The named page, its password form filled in with the "pw" query parameter and submitted
	 */
	private WOComponent submittedPage(final String pageName) {
		final WOComponent page = pageWithName(pageName);
		page.takeValueForKey(context().request().stringFormValueForKey("pw"), "password");
		page.valueForKey("submit");
		return page;
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
				// The configuration's properties, or with "user", the ones that user would get (their Properties.<user> variants)
				String user = request().stringFormValueForKey("user");
				ERXConfigurationManager configuration = user != null ? ERXConfigurationManager.previewForUser(user) : ERXConfigurationManager.current();
				Properties props = new Properties();
				props.putAll(configuration.properties());
				r.appendContentString(ERXConfigurationManager.logString(props));
			}
			else {
				String value = request().stringFormValueForKey("value");
				value = ERXUtilities.stringIsNullOrEmpty(value) ? "" : value;
				ERXConfigurationManager.setProperty(key, value);
				java.util.Properties p = System.getProperties();
				ERXLoggingSupport.configureLoggingWithSystemProperties();
				for (java.util.Enumeration e = p.keys(); e.hasMoreElements();) {
					Object k = e.nextElement();
					final String line = WOMessage.stringByEscapingHTMLString(k + "=" + ERXConfigurationManager.maskedValue((String)k, String.valueOf(p.get(k))));
					if (k.equals(key)) {
						r.appendContentString("<b>'" + line + "'     <= you changed this</b><br>");
					}
					else {
						r.appendContentString("'" + line + "'<br>");
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
	 * @return true if the request parameter "pw" matches the password set on the application's WOStatisticsStore
	 * (see {@link #statisticsStorePassword()})
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

		return password.equals( statisticsStorePassword() );
	}

	/**
	 * @return The password held by the application's statistics store, read off its private "_password" field. Null if unset.
	 *
	 * The store offers no way to read its password back: WOStatisticsStore.validateLogin() needs a session to mark,
	 * and the field is private. Applications commonly set the password in code rather than through the
	 * WOStatisticsPassword property, so comparing against the property is not an option. Hence reflection.
	 */
	private static String statisticsStorePassword() {
		final WOStatisticsStore store = WOApplication.application().statisticsStore();

		try {
			final Field field = declaredField( store.getClass(), "_password" );
			field.setAccessible( true );
			return (String)field.get( store );
		}
		catch( NoSuchFieldException | IllegalAccessException e ) {
			throw new IllegalStateException( "Could not read the statistics store's password", e );
		}
	}

	/**
	 * @return The named field declared by the given class or the nearest superclass declaring it
	 */
	private static Field declaredField( Class<?> c, final String name ) throws NoSuchFieldException {

		while( c != null ) {
			try {
				return c.getDeclaredField( name );
			}
			catch( NoSuchFieldException e ) {
				c = c.getSuperclass();
			}
		}

		throw new NoSuchFieldException( name );
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