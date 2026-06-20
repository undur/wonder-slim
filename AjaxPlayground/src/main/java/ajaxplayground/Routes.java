package ajaxplayground;

import ajaxplayground.components.AjaxOverview;
import ajaxplayground.components.AjaxSlimReference;
import ajaxplayground.components.Main;
import ajaxplayground.components.gallery.GalleryAjaxBrowser;
import ajaxplayground.components.gallery.GalleryAjaxPopUpButton;
import ajaxplayground.components.gallery.GalleryAjaxSlimUpdateContainer;
import ajaxplayground.components.gallery.GalleryLargeList;
import ajaxplayground.components.gallery.GalleryUpdateContainer;
import ajaxplayground.components.gallery.GalleryWonderSelect;
import ajaxplayground.components.scenario.InvoiceSimple;
import ajaxplayground.components.scenario.ScenarioAccumulation;
import ajaxplayground.components.scenario.ScenarioCheckbox;
import ajaxplayground.components.scenario.ScenarioDropdownViewport;
import ajaxplayground.components.scenario.ScenarioFocus;
import ajaxplayground.components.scenario.ScenarioInvoice;
import ajaxplayground.components.scenario.ScenarioMultiObserve;
import ajaxplayground.components.scenario.ScenarioMultiUpdate;
import ajaxplayground.components.scenario.ScenarioNested;
import ajaxplayground.components.scenario.ScenarioRowIdentity;
import ajaxplayground.components.scenario.ScenarioScripts;
import ajaxplayground.components.scenario.ScenarioUuidIds;
import er.extensions.routes.RouteTable;

/**
 * The playground's URL routes - one clean, flat URL per page, so the navigation between pages shows
 * off the routing instead of opaque component-action URLs. Each entry maps a path directly to the page
 * component the request handler should render. (The in-page Ajax functionality still uses regular
 * component actions; only page-to-page navigation is routed.)
 * <p>
 * Kept out of {@link Application} so the app bootstrap stays uncluttered; {@link Application} just calls
 * {@link #register()}.
 */
public class Routes {

	private Routes() {}

	public static void register() {
		final RouteTable routes = RouteTable.defaultRouteTable();

		// Index
		routes.map( "/", Main.class );

		// Invoice editors
		routes.map( "/invoice", InvoiceSimple.class );
		routes.map( "/invoice-stress", ScenarioInvoice.class );

		// Reference
		routes.map( "/reference", AjaxSlimReference.class );
		routes.map( "/overview", AjaxOverview.class );

		// Scenario pages (danger matrix)
		routes.map( "/focus", ScenarioFocus.class );
		routes.map( "/accumulation", ScenarioAccumulation.class );
		routes.map( "/nested", ScenarioNested.class );
		routes.map( "/uuid-ids", ScenarioUuidIds.class );
		routes.map( "/scripts", ScenarioScripts.class );
		routes.map( "/dropdown-viewport", ScenarioDropdownViewport.class );
		routes.map( "/row-identity", ScenarioRowIdentity.class );
		routes.map( "/multi-observe", ScenarioMultiObserve.class );
		routes.map( "/multi-update", ScenarioMultiUpdate.class );
		routes.map( "/checkbox", ScenarioCheckbox.class );

		// Component gallery
		routes.map( "/gallery-update-container", GalleryUpdateContainer.class );
		routes.map( "/gallery-ajaxslim-update-container", GalleryAjaxSlimUpdateContainer.class );
		routes.map( "/wonder-select", GalleryWonderSelect.class );
		routes.map( "/popup-button", GalleryAjaxPopUpButton.class );
		routes.map( "/browser", GalleryAjaxBrowser.class );
		routes.map( "/large-list", GalleryLargeList.class );
	}
}
