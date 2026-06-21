package er.ajax.elements;

import er.ajax.*;

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
 * <p>
 * By default the spinner is an in-flow inline element - drop it next to a button or in a toolbar. For a
 * site-wide indicator (e.g. one placed in a layout component, where it would otherwise take up space and
 * push content down when it appears), add <code>class="fixed"</code>: the bundled CSS then floats it in
 * the bottom-right corner of the viewport, out of the document flow.
 * </p>
 *
 * @binding id optional id for the spinner element
 * @binding class optional extra CSS class(es) on the spinner element; use <code>"fixed"</code> for a
 *          viewport-floating indicator instead of the in-flow default
 * @binding delay optional show-delay (anti-flash): the spinner only appears after this much sustained
 *          ajax activity, so a burst of fast requests never flashes it. A bare number is seconds
 *          (<code>delay="0.2"</code>); a CSS time value with its own unit is also accepted
 *          (<code>"200ms"</code>).
 * @binding fade optional fade-in/out duration: the spinner fades over this time instead of appearing
 *          and vanishing instantly, so a brief near-threshold show glides rather than blinks. Same
 *          value format as <code>delay</code>. Independent of <code>delay</code>; use either or both.
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

	/**
	 * The inline style for the spinner. Emits the CSS custom properties the anti-flash CSS reads for any
	 * bound timing: {@code --ajaxslim-busy-delay} (the {@code delay} binding - wait before showing) and
	 * {@code --ajaxslim-busy-fade} (the {@code fade} binding - fade in/out duration), each before any
	 * author-supplied {@code style}. Both accept a bare number (seconds, e.g. {@code "0.2"}) or any
	 * CSS time value with its own unit ({@code "200ms"}, {@code "0.2s"}).
	 */
	public Object style() {
		StringBuilder vars = new StringBuilder();
		appendTimeVar(vars, "--ajaxslim-busy-delay", "delay");
		appendTimeVar(vars, "--ajaxslim-busy-fade", "fade");
		Object style = valueForBinding("style", null);
		if (vars.length() == 0) {
			return style;
		}
		return style == null ? vars.toString() : vars + " " + style;
	}

	/** Append "<cssVar>: <value>;" if the named binding is set, normalising a bare number to seconds. */
	private void appendTimeVar(StringBuilder out, String cssVar, String bindingName) {
		Object value = valueForBinding(bindingName, null);
		if (value == null) {
			return;
		}
		String time = value.toString().trim();
		// A bare number is SECONDS (e.g. "0.2"). A CSS time value with its own unit ("200ms", "0.2s")
		// passes through untouched.
		if (time.matches("\\d+(\\.\\d+)?")) {
			time = time + "s";
		}
		if (out.length() > 0) {
			out.append(' ');
		}
		out.append(cssVar).append(": ").append(time).append(';');
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
