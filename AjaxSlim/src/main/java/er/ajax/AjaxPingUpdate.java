package er.ajax;

import java.util.Objects;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;

/**
 * Refreshes a target AjaxUpdateContainer when its cache key changes. Used inside an {@link AjaxPing}
 * (which ping-refreshes a tiny container periodically); you can also place several AjaxPingUpdates in
 * one AjaxPing to drive multiple targets from a single ping.
 *
 * <p>
 * It tracks the <code>cacheKey</code> across requests; when the key differs from the last seen value it
 * emits a script that refreshes the target. Unchanged from the legacy element except the emitted
 * refresh goes through {@code AjaxSlim.AUC.update(id)} instead of Prototype's <code>AUC.update</code>.
 * </p>
 *
 * @binding updateContainerID the id of the AjaxUpdateContainer to refresh when the cacheKey changes
 * @binding targetContainerID deprecated alias for {@code updateContainerID}; kept for backward
 *          compatibility, prefer {@code updateContainerID}
 * @binding cacheKey some value that represents the state of the target container
 * @binding onBeforeUpdate (optional) a javascript function to call before updating (returns true to allow the update)
 *
 * @author mschrag
 */
public class AjaxPingUpdate extends WOComponent {

	private static final long serialVersionUID = 1L;

	private Boolean _refreshTarget;
	private static final Object NOT_INITIALIZED = new Object();
	private Object _lastCacheKey = NOT_INITIALIZED;

	public AjaxPingUpdate(WOContext context) {
		super(context);
	}

	@Override
	public boolean synchronizesVariablesWithBindings() {
		return false;
	}

	/**
	 * @return whether the target should be refreshed (true once, the first render after the cacheKey changed)
	 */
	public boolean refreshTarget() {
		boolean refreshTarget = false;
		if (_refreshTarget == null) {
			Object cacheKey = valueForBinding("cacheKey");
			if (_lastCacheKey == NOT_INITIALIZED) {
				_lastCacheKey = cacheKey;
			}
			if (!Objects.equals(_lastCacheKey, cacheKey)) {
				refreshTarget = true;
				_lastCacheKey = cacheKey;
			}
			_refreshTarget = Boolean.valueOf(refreshTarget);
		}
		return _refreshTarget.booleanValue();
	}

	@Override
	public void sleep() {
		super.sleep();
		_refreshTarget = null;
	}

	public boolean hasOnBeforeUpdate() {
		return valueForBinding("onBeforeUpdate") != null;
	}

	public Object onBeforeUpdate() {
		return valueForBinding("onBeforeUpdate");
	}

	/**
	 * The id of the AjaxUpdateContainer to refresh when the cacheKey changes. Reads {@code updateContainerID},
	 * falling back to the deprecated {@code targetContainerID} alias.
	 */
	public String updateContainerID() {
		String id = (String) valueForBinding("updateContainerID");
		// FIXME: Remove. Deprecated alias - prefer updateContainerID (consistent with AjaxUpdateTrigger).
		if (id == null) {
			id = (String) valueForBinding("targetContainerID");
		}
		return id;
	}
}
