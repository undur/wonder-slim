package er.extensions.logging;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WORequest;

import er.extensions.appserver.ERXAdminDirectAction;

public class ERXLog4jAction extends ERXAdminDirectAction {

	public ERXLog4jAction( WORequest r ) {
		super( r );
	}

	/**
	 * @return A page for modifying logging settings
	 */
	public WOActionResults log4jAction() {

		if( canPerformAction() ) {
			session().setObjectForKey( Boolean.TRUE, "ERXLog4JConfiguration.enabled" );
			return pageWithName( ERXLog4JConfiguration.class );
		}

		return forbiddenResponse();
	}
}