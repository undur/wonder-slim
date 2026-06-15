package ajaxplayground.components.gallery;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Gallery: AjaxSlim's AjaxUpdateContainer.
 *
 * Exercises the AjaxSlim rewrite of {@code <wo:AjaxUpdateContainer>}: the fetch + Idiomorph runtime
 * (ajaxslim.js), morph vs. classic innerHTML replacement, the periodic-refresh capability
 * (frequency binding -> setInterval) and the onRefreshComplete post-update hook.
 * <p>
 * NOTE: this page only resolves to the AjaxSlim element if AjaxPlayground depends on AjaxSlim
 * (which replaces the legacy Ajax framework on the classpath). The two frameworks both ship
 * {@code er.ajax.AjaxUpdateContainer} and cannot co-exist, so swapping the dependency is a separate
 * migration step. Until then this page is wired up and ready but exercises the legacy element.
 */
public class GalleryAjaxSlimUpdateContainer extends PlaygroundPage {

	private int _count;

	public GalleryAjaxSlimUpdateContainer( WOContext context ) {
		super( context );
	}

	public int count() {
		return _count;
	}

	public WOActionResults increment() {
		_count++;
		return null;
	}
}
