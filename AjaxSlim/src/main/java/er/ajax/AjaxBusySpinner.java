package er.ajax;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

/**
 * Shows a busy indicator while AjaxSlim has one or more ajax requests in flight, and hides it when
 * idle. Drop a bare <code>&lt;wo:AjaxBusySpinner/&gt;</code> anywhere on the page.
 *
 * <p>
 * Unlike the legacy AjaxBusySpinner this is a pure CSS spinner driven by the runtime's ajax-activity
 * broker: {@code ajaxslim.js} sets a <code>data-ajaxslim-busy</code> attribute on the document element
 * while any request is in flight, and the spinner's CSS keys off that attribute - so by default the
 * spinner needs no JavaScript of its own. There is no spin.js, no Prototype Ajax.Responder, and no
 * per-spinner configuration: a busy indicator is the same on every page, so it is not worth a binding
 * surface. (Apps wanting a bespoke indicator can style <code>.ajaxslim-busy-spinner</code> or react to
 * the <code>ajaxslim:busy</code>/<code>ajaxslim:idle</code> events the runtime dispatches on document.)
 * </p>
 *
 * @binding id optional id for the spinner element
 * @binding class optional extra CSS class(es) on the spinner element
 * @binding style optional inline style on the spinner element
 */
public class AjaxBusySpinner extends AjaxComponent {

	private static final long serialVersionUID = 1L;

	public AjaxBusySpinner(WOContext context) {
		super(context);
	}

	@Override
	public boolean isStateless() {
		return true;
	}

	@Override
	public boolean synchronizesVariablesWithBindings() {
		return false;
	}

	/**
	 * The base marker class (the CSS hides/shows it via the document's data-ajaxslim-busy attribute),
	 * with any developer-supplied "class" binding appended.
	 */
	public String spinnerClass() {
		String base = "ajaxslim-busy-spinner";
		Object extra = valueForBinding("class", null);
		return extra == null ? base : base + " " + extra;
	}

	public Object id() {
		return valueForBinding("id", null);
	}

	public Object style() {
		return valueForBinding("style", null);
	}

	@Override
	protected void addRequiredWebResources(WOResponse response) {
		addScriptResourceInHead(response, "ajaxslim.js");
		addStylesheetResourceInHead(response, "ajaxslim-busy.css");
	}

	/**
	 * The spinner does not handle requests of its own; it reacts entirely client-side to the runtime's
	 * activity broker.
	 */
	@Override
	public WOActionResults handleRequest(WORequest request, WOContext context) {
		return null;
	}
}
