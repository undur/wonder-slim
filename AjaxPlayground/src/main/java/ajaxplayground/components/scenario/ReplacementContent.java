package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;

/**
 * The markup an AjaxUpdateLink replaceID action returns: rendered as the replacement response's whole
 * body (unframed - see ScenarioReplace), it becomes the target element's new outerHTML. Renders a NEW
 * element carrying the SAME id, so the region stays replaceable on subsequent clicks.
 */
public class ReplacementContent extends WOComponent {

	/** Which replacement this is (1 on the first click, ...), so a test can assert progression. */
	public int generation;

	public ReplacementContent(WOContext context) {
		super(context);
	}
}
