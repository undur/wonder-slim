package er.extensions.admin;

import java.util.List;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXApplication;
import er.extensions.components.ERXStatelessComponent;

/**
 * The chrome around every page of the admin UI: navigation, page header, footer. The page's own content goes inside.
 *
 * Bindings: [title] the page's title, [section] the name of the section to mark as current in the navigation,
 * [subtitle] an optional line beneath the title.
 */
public class ERXAdminLayout extends ERXStatelessComponent {

	public ERXAdmin.Section currentSection;

	public ERXAdminLayout( WOContext context ) {
		super( context );
	}

	public String title() {
		return stringValueForBinding( "title", "Admin" );
	}

	public String subtitle() {
		return stringValueForBinding( "subtitle", null );
	}

	public List<ERXAdmin.Section> sections() {
		return ERXAdmin.sections();
	}

	public String currentSectionURL() {
		return ERXAdmin.url( context(), currentSection.path() );
	}

	public String currentSectionClass() {
		return currentSection.name().equals( stringValueForBinding( "section", "" ) ) ? "nav-item active" : "nav-item";
	}

	public String logoutURL() {
		return ERXAdmin.url( context(), ERXAdmin.PATH + "/logout" );
	}

	/**
	 * @return true if there is a login to log out of (development mode lets everyone in without one)
	 */
	public boolean showLogout() {
		return !ERXApplication.isDevelopmentModeSafe();
	}

	public String styleSheetURL() {
		return WOApplication.application().resourceManager().urlForResourceNamed( "admin/erx-admin.css", "ERControl", null, context().request() );
	}

	public String applicationName() {
		return WOApplication.application().name();
	}

	public String instanceDescription() {
		final WOApplication application = WOApplication.application();
		return application.host() + ":" + application.port();
	}

	public String modeDescription() {
		return ERXApplication.isDevelopmentModeSafe() ? "development" : "deployment";
	}
}
