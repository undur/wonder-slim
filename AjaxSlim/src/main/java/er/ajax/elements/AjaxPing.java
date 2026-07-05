package er.ajax.elements;

import er.ajax.*;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXWOContext;
import er.extensions.foundation.ERXValueUtilities;

/**
 * Refreshes a large content area based on periodic refreshes of a very small one. You provide a cache
 * key that, on changing, triggers an update of a target AjaxUpdateContainer.
 *
 * <p>
 * AjaxPing is itself a tiny AjaxUpdateContainer that re-fetches every <code>frequency</code>
 * seconds. Its server-side content depends only on the <code>cacheKey</code>, so while the key is
 * unchanged the periodic refresh returns the same trivial content cheaply; when the key changes, the
 * embedded {@link AjaxPingUpdate} emits a script that refreshes the (potentially large) target
 * container. So the expensive container is rebuilt only when something actually changed.
 * </p>
 *
 * <p>
 * Unchanged from the legacy element except that the periodic self-refresh and the target refresh go
 * through the AjaxSlim runtime ({@code AjaxSlim.AUC.update(id)} / {@code registerPeriodic}) instead of
 * Prototype - there is no Prototype, no eval'd <code>&lt;id&gt;Update()</code> global.
 * </p>
 *
 * @binding frequency the frequency of refresh (in seconds), defaults to 3
 * @binding updateContainerID the id of the AjaxUpdateContainer to refresh when the cacheKey changes
 * @binding targetContainerID deprecated alias for {@code updateContainerID}; kept for backward
 *          compatibility, prefer {@code updateContainerID}
 * @binding cacheKey some value that represents the state of the target container
 * @binding onBeforeUpdate (optional) a javascript function to call before updating (returns true to allow the update)
 * @binding id (optional) the id of the ping container
 * @binding stop (optional) if true, the ping stops; if false it runs (refresh the ping's container to restart it)
 *
 * @author mschrag
 */
public class AjaxPing extends WOComponent {

	private static final long serialVersionUID = 1L;

	private String _id;

	public AjaxPing(WOContext context) {
		super(context);
	}

	@Override
	public boolean synchronizesVariablesWithBindings() {
		return false;
	}

	private static final double DEFAULT_FREQUENCY_SECONDS = 3;

	/**
	 * The refresh frequency, in SECONDS, that gets handed to the AjaxUpdateContainer (whose
	 * <code>frequency</code> binding is also seconds).
	 *
	 * @return the refresh frequency in seconds (default 3)
	 */
	public double frequencyInSeconds() {
		Object frequency = valueForBinding("frequency");
		if (frequency == null) {
			return DEFAULT_FREQUENCY_SECONDS;
		}
		try {
			return Double.parseDouble(String.valueOf(frequency).trim());
		}
		catch (NumberFormatException e) {
			// Fail loudly, like AjaxSelfUpdatingContainer does for the same binding - silently falling
			// back to the default would hide a real authoring error.
			throw new IllegalStateException("AjaxPing: could not parse a number from the 'frequency' binding value <" + frequency + ">");
		}
	}

	/**
	 * @return true if the ping is stopped (the "stop" binding, default false)
	 */
	public boolean stopped() {
		return ERXValueUtilities.booleanValueWithDefault(valueForBinding("stop"), false);
	}

	public Object cacheKey() {
		return valueForBinding("cacheKey");
	}

	/**
	 * The id of the AjaxUpdateContainer to refresh when the cacheKey changes. Reads {@code updateContainerID},
	 * falling back to the deprecated {@code targetContainerID} alias.
	 */
	public String updateContainerID() {
		if (hasBinding("updateContainerID")) {
			return (String) valueForBinding("updateContainerID");
		}
		// FIXME: Remove. Deprecated alias - prefer updateContainerID (consistent with AjaxUpdateTrigger,
		// which uses updateContainerID for the same "signal another container to refresh" job).
		return (String) valueForBinding("targetContainerID");
	}

	public Object onBeforeUpdate() {
		return valueForBinding("onBeforeUpdate");
	}

	public boolean hasUpdateContainerID() {
		return updateContainerID() != null;
	}

	/**
	 * @return the id of the ping container
	 */
	public String id() {
		if (_id == null) {
			_id = (String) valueForBinding("id");
			if (_id == null) {
				_id = ERXWOContext.safeIdentifierName(context(), true);
			}
		}
		return _id;
	}
}
