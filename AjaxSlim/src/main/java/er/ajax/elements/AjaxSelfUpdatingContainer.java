package er.ajax.elements;

import er.ajax.*;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;

import er.extensions.foundation.ERXValueUtilities;

/**
 * An update container that refreshes <b>itself</b> - on a timer ({@code frequency}) and/or when a
 * field changes ({@code observeFieldID}) - and may invoke its own {@code action} when it does.
 *
 * <p>
 * This is the counterpart to the passive {@link AjaxUpdateContainer}. They were one element with an
 * easily-misread API: an {@code action} on a plain update container fires only when the container is the
 * request <i>sender</i> (i.e. refreshes itself), never when some other trigger updates it - the opposite
 * of the natural reading. Splitting the two makes the intent explicit:
 * </p>
 * <ul>
 * <li>{@link AjaxUpdateContainer} - a passive region others refresh. No self-refresh, no action.</li>
 * <li>{@code AjaxSelfUpdatingContainer} - refreshes itself; {@code action} firing on self-refresh is the
 *     expected behaviour.</li>
 * </ul>
 *
 * <p>
 * Renders exactly like its passive superclass; it only adds, to the container's client registration, the
 * periodic-refresh / observe-field hookup, and invokes its {@code action} when it handles its own
 * refresh request.
 * </p>
 *
 * @binding frequency the frequency (in seconds) of a periodic self-refresh
 * @binding stopped whether a periodic container loads stopped (so it can be started later)
 * @binding observeFieldID the DOM id of a field whose changes trigger a self-refresh of this container
 * @binding fullSubmit when observing a field, whether to submit the whole form (true) or just the field (false)
 * @binding action the action to invoke when this container refreshes itself via its PERIODIC
 *          self-refresh. It does NOT fire on an observeFieldID-triggered refresh - that path submits
 *          the observed field's form (so the form's action pipeline runs) and the container is only
 *          the render target.
 */
public class AjaxSelfUpdatingContainer extends AjaxUpdateContainer {

	/** The self-update bindings this subclass consumes, on top of the ones the base handles. */
	private static final NSArray<String> HANDLED_BINDINGS = new NSArray<>(new String[] {
		"frequency", "stopped", "observeFieldID", "fullSubmit", "action"
	});

	public AjaxSelfUpdatingContainer(String name, NSDictionary<String, WOAssociation> associations, WOElement children) {
		super(name, associations, children);
	}

	@Override
	protected NSArray<String> handledBindingNames() {
		return super.handledBindingNames().arrayByAddingObjectsFromArray(HANDLED_BINDINGS);
	}

	/**
	 * Emit the client registration for periodic refresh and/or field observation. Identical output to
	 * what the legacy combined AjaxUpdateContainer emitted, just only on this self-updating variant.
	 */
	@Override
	protected void appendSelfUpdateScript(WOResponse response, WOContext context, String id) {
		WOComponent component = context.component();

		Object frequency = valueForBinding("frequency", component);
		if (frequency != null) {
			boolean isNotZero = true;
			try {
				float numberFrequency = ERXValueUtilities.floatValue(frequency);
				if (numberFrequency == 0.0) {
					isNotZero = false;
				}
			}
			catch (RuntimeException e) {
				throw new IllegalStateException("Error parsing float from value : <" + frequency + ">");
			}

			if (isNotZero) {
				boolean canStop = false;
				boolean stopped = false;
				if (associations().objectForKey("stopped") != null) {
					canStop = true;
					stopped = booleanValueForBinding("stopped", false, component);
				}
				response.appendContentString("AjaxSlim.AUC.registerPeriodic(" + AjaxUtils.quote(id) + "," + canStop + "," + stopped + "," + ERXValueUtilities.floatValue(frequency) + ");");
			}
		}

		String observeFieldID = (String) valueForBinding("observeFieldID", component);
		if (observeFieldID != null) {
			boolean fullSubmit = booleanValueForBinding("fullSubmit", false, component);
			response.appendContentString("AjaxSlim.AUC.observeField(" + AjaxUtils.quote(id) + "," + AjaxUtils.quote(observeFieldID) + "," + fullSubmit + ");");
		}
	}

	/**
	 * Invoke this container's own refresh action when it is the request sender (it refreshed itself).
	 */
	@Override
	protected void performSelfUpdateAction(WORequest request, WOContext context) {
		if (associations().objectForKey("action") != null) {
			@SuppressWarnings("unused")
			WOActionResults results = (WOActionResults) valueForBinding("action", context.component());
			// the action's result is intentionally ignored; the container re-renders its content
		}
	}
}
