package ajaxplayground.components.gallery;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Gallery: AjaxUpdateContainer baseline.
 *
 * The simplest possible morph test: a container with a counter and an update link. Clicking the
 * link bumps the counter and refreshes the container. Verifies the basic update path works under
 * both morph and classic replacement (toggle), and that the container's data-morph attribute
 * reflects the binding - the foundation every other scenario builds on.
 */
public class GalleryUpdateContainer extends PlaygroundPage {

	private int _count;

	public GalleryUpdateContainer( WOContext context ) {
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
