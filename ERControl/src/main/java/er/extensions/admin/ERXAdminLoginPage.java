package er.extensions.admin;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXRedirect;
import er.extensions.components.ERXComponent;

/**
 * Asks for the admin password, then sends the visitor on to the admin page they asked for.
 */
public class ERXAdminLoginPage extends ERXComponent {

	/**
	 * The admin path the visitor was on their way to
	 */
	public String destination = ERXAdmin.PATH;

	public String password;
	public boolean refused;

	public ERXAdminLoginPage( WOContext context ) {
		super( context );
	}

	public WOActionResults logIn() {
		final String entered = password;
		password = null;

		if( !ERXAdmin.logIn( context(), entered ) ) {
			refused = true;
			return null;
		}

		final ERXRedirect redirect = pageWithName( ERXRedirect.class );
		redirect.setUrl( ERXAdmin.url( context(), destination == null || !destination.startsWith( ERXAdmin.PATH ) ? ERXAdmin.PATH : destination ) );
		return redirect;
	}

	/**
	 * @return true if no admin password is configured, in which case nobody can log in
	 */
	public boolean noPasswordConfigured() {
		return !ERXAdmin.hasPassword();
	}

	public String styleSheetURL() {
		return WOApplication.application().resourceManager().urlForResourceNamed( "admin/erx-admin.css", "ERControl", null, context().request() );
	}

	public String applicationName() {
		return WOApplication.application().name();
	}
}
