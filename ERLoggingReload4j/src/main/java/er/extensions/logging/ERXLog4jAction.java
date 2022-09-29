package er.extensions.logging;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WORequest;

import er.extensions.appserver.ERXAdminDirectAction;

public class ERXLog4jAction extends ERXAdminDirectAction {

	public ERXLog4jAction( WORequest r ) {
		super( r );
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

		if( canPerformAction() ) {
			session().setObjectForKey( Boolean.TRUE, "ERXLog4JConfiguration.enabled" );
			return pageWithName( ERXLog4JConfiguration.class );
		}

		return forbiddenResponse();
	}
}