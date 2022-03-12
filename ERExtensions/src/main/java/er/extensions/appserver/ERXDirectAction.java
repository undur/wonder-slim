package er.extensions.appserver;

import com.webobjects.appserver.WORequest;

/**
 * Added as a placeholder for the actual DirectAction stuff
 * 
 * FIXME: This should not inherit from ERXAdminDirectAction, we only do that for now since JavaMonitor and others  still depend on those actions // Hugi 2022-03-12 
 */

public class ERXDirectAction extends ERXAdminDirectAction {

	public ERXDirectAction(WORequest r) {
		super(r);
	}
}