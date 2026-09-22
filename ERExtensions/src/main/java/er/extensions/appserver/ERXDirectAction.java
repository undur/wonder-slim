package er.extensions.appserver;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

/**
 * Added as a placeholder for the actual DirectAction stuff
 *
 * FIXME: This should not inherit from ERXAdminDirectAction, we only do that for now since JavaMonitor and others  still depend on those actions // Hugi 2022-03-12
 */

public class ERXDirectAction extends ERXAdminDirectAction {

	public ERXDirectAction(WORequest r) {
		super(r);
	}

	/**
	 * Identifies the framework to management tools - wotaskd probes this to type an instance
	 * (see wonder-slim-deployment#56). Reachable on any wonder-slim application: as /wa/describe when the
	 * application's default DirectAction extends this class (inherited actions dispatch), and as
	 * /wa/ERXDirectAction/describe by classname routing regardless. Deliberately minimal: framework name only.
	 *
	 * FIXME: Temporary. Goes once ng-objects and wonder-slim share a common response structure for describing an
	 * application to the deployment stack, which this then implements // Hugi 2026-09-22
	 */
	public WOActionResults describeAction() {
		final WOResponse response = new WOResponse();
		response.setHeader( "application/json", "content-type" );
		response.setContent( "{\"framework\":\"wonder-slim\"}" );
		return response;
	}
}