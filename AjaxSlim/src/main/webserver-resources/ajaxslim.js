// ===========================================================================
// ajaxslim.js - the AjaxSlim client runtime
//
// A dependency-free, fetch-based replacement for the legacy Ajax framework's
// Prototype/Scriptaculous runtime (wonder.js). NO Prototype, NO $, NO
// Ajax.Updater, NO Effects. The only dependency is idiomorph.js (loaded
// alongside this file by AjaxUpdateContainer), used for DOM-preserving updates.
//
// Public surface:
//   AjaxSlim.AUC.register(id, options)               - remember a container's options
//   AjaxSlim.AUC.update(id [, options])              - fetch + morph (or replace) a container
//   AjaxSlim.AUC.registerPeriodic(id, canStop, stopped, frequencySeconds)
//   AjaxSlim.AUC.observeField(id, fieldId, fullSubmit)
//   AjaxSlim.AUL.update(targetId, actionUrl, options)  - update-link: fetch an action URL + morph
//   AjaxSlim.AUL.request(actionUrl, options)           - update-link with no target (fire + forget)
//   AjaxSlim.ASB.update(targetId, form, options)       - submit a form in the background + morph
//   AjaxSlim.ASB.request(form, options)                - submit a form, run scripts, no morph target
//   AjaxSlim.ASB.partial(targetId, fieldId, options)   - partial submit of one field + morph
//   AjaxSlim.ASB.observeField(...) / observeDescendentFields(...) - field observers (AjaxObserveField)
//
// The HTML contract (emitted by AjaxUpdateContainer.java):
//   data-updateUrl  - the ajax component-action URL to refetch the container from
//   data-morph      - "true" => Idiomorph reconcile, "false" => innerHTML replace
//
// Registry is a Map keyed by DOM id (NOT eval'd window globals by name), so ids
// that aren't valid JS identifiers - notably UUIDs with '-' - work fine. This
// also lets register/update be called repeatedly (idempotent) under morphing
// without leaking globals or duplicate timers.
// ===========================================================================
(function (window, document) {
	'use strict';

	// One namespace, created once and reused across morph-driven re-evaluations.
	var AjaxSlim = window.AjaxSlim || (window.AjaxSlim = {});

	// -----------------------------------------------------------------------
	// Morph engine (extracted from the legacy AjaxMorph block, de-Prototyped).
	// -----------------------------------------------------------------------
	var Morph = {
		// Escape hatches for self-managing widgets. Widgets opt their managed DOM out of
		// reconciliation with:
		//   data-morph-ignore          - do not morph this element/subtree at all (widget owns it)
		//   data-morph-preserve        - do not remove this node even if absent from the new HTML
		//   data-morph-preserve-style  - do not overwrite this node's style/class attributes
		// Idiomorph's own im-preserve="true" is honored by Idiomorph automatically.
		callbacks: {
			beforeNodeMorphed: function (oldNode, newNode) {
				if (oldNode.nodeType === 1 && oldNode.getAttribute && oldNode.getAttribute('data-morph-ignore') != null) {
					return false;
				}
				return true;
			},
			beforeNodeRemoved: function (node) {
				if (node.nodeType === 1 && node.getAttribute &&
						(node.getAttribute('data-morph-preserve') != null || node.getAttribute('data-morph-ignore') != null)) {
					return false;
				}
				return true;
			},
			beforeAttributeUpdated: function (attributeName, node, mutationType) {
				if ((attributeName === 'style' || attributeName === 'class') &&
						node.getAttribute && node.getAttribute('data-morph-preserve-style') != null) {
					return false;
				}
				return true;
			}
		},

		// Reconcile receiver's children with html using Idiomorph (innerHTML mode), then run any
		// <script>s the fragment carried - Idiomorph leaves injected <script> nodes inert. If
		// Idiomorph isn't present, degrade to a plain replacement so the update still happens.
		morph: function (receiver, html) {
			if (typeof Idiomorph === 'undefined') {
				Morph.replace(receiver, html);
				return;
			}
			var scriptless = Morph.stripScripts(html);
			Idiomorph.morph(receiver, scriptless, {
				morphStyle: 'innerHTML',
				callbacks: Morph.callbacks
			});
			Morph.runScripts(html);
		},

		// Classic innerHTML replacement (data-morph="false"). Assigning innerHTML does NOT execute
		// embedded <script>s, so we run them explicitly afterwards to match morph() semantics.
		replace: function (receiver, html) {
			receiver.innerHTML = Morph.stripScripts(html);
			Morph.runScripts(html);
		},

		// Remove <script>...</script> blocks from an HTML string.
		stripScripts: function (html) {
			return html.replace(/<script[^>]*>([\s\S]*?)<\/script\s*>/gi, '');
		},

		// Extract and run each <script> in the fragment, each in its OWN try/catch so one bad
		// fragment degrades to a logged error rather than killing its siblings. Indirect eval
		// keeps each script in global scope.
		runScripts: function (html) {
			var re = /<script[^>]*>([\s\S]*?)<\/script\s*>/gi;
			var match;
			while ((match = re.exec(html)) !== null) {
				var code = match[1];
				if (!code || !code.trim()) {
					continue;
				}
				try {
					(0, eval)(code);
				}
				catch (e) {
					if (window.console) {
						console.error('AjaxSlim: error evaluating a script in updated content (continuing with the rest)', e, code);
					}
				}
			}
		}
	};

	// -----------------------------------------------------------------------
	// AjaxUpdateContainer runtime
	// -----------------------------------------------------------------------

	// id -> { onRefreshComplete: fn|undefined }
	var registry = new Map();
	// id -> interval handle, so we can clear a prior timer before starting a new one.
	var timers = new Map();

	function elementFor(id) {
		return document.getElementById(id);
	}

	// Append _u=<id> (the ajax-update-pass marker the server keys on) plus a cache-buster to the
	// container's data-updateUrl, using URLSearchParams so existing query params are preserved.
	function buildUpdateUrl(element, id) {
		return addUpdateParams(element.getAttribute('data-updateUrl'), id, false);
	}

	// Add the update/replace marker for a target id plus a neutral cache-buster to an action URL.
	// When replace is true the '_r' (ajax-replacement) marker is used instead of '_u' (ajax-update
	// pass) - this mirrors ERXAjaxApplication's two ways of targeting a region.
	function addUpdateParams(raw, id, replace) {
		var qIndex = raw.indexOf('?');
		var base = qIndex === -1 ? raw : raw.substring(0, qIndex);
		var params = new URLSearchParams(qIndex === -1 ? '' : raw.substring(qIndex + 1));
		if (id != null) {
			params.set(replace ? '_r' : '_u', id);
		}
		// Cache-buster under a neutral key. Do NOT use '_r' here for the buster: ERXAjaxApplication
		// treats '_r' as the "ajax replacement" marker (isAjaxReplacement), which changes behavior.
		params.set('_', String(Date.now()));
		return base + '?' + params.toString();
	}

	// The shared fetch + morph core used by every update path (AUC / AUL / ASB / observeField).
	// Fetches url (optionally POSTing body) and morphs (or replaces) the response into the element
	// with id targetId, then runs onDone(). The x-requested-with header is REQUIRED:
	// ERXAjaxApplication.isAjaxRequest keys on it, so without it the server won't return a fragment.
	//
	// targetId may be null - that's a "fire and forget" request (AUL.request / ASB.request) whose
	// response is JUST scripts to run (e.g. an onClickServer that itself calls AUC.update). In that
	// case we run the response's <script>s in global scope rather than morphing into a container.
	function fetchAndMorph(targetId, url, body, onDone) {
		var init = {
			credentials: 'same-origin',
			headers: { 'x-requested-with': 'XMLHttpRequest' }
		};
		if (body != null) {
			init.method = 'POST';
			init.body = body;
			// URLSearchParams/FormData set their own content-type; we only need the header below for
			// URLSearchParams (form-urlencoded). FormData sets multipart automatically, so leave it.
			if (typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) {
				init.headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=UTF-8';
			}
		}
		return fetch(url, init).then(function (response) {
			return response.text();
		}).then(function (text) {
			if (targetId == null) {
				// No container to update - the response is just scripts to run globally.
				Morph.runScripts(text);
			}
			else {
				var receiver = elementFor(targetId);
				if (receiver == null) {
					if (window.console) {
						console.warn('AjaxSlim: no element with id "' + targetId + '" to update');
					}
					return;
				}
				var doMorph = receiver.getAttribute('data-morph') !== 'false';
				if (doMorph) {
					Morph.morph(receiver, text);
				}
				else {
					Morph.replace(receiver, text);
				}
			}
			if (typeof onDone === 'function') {
				onDone();
			}
		}).catch(function (e) {
			if (window.console) {
				console.error('AjaxSlim: ajax update failed for "' + targetId + '"', e);
			}
		});
	}

	var AUC = {
		// Remember a container's options (currently the onRefreshComplete hook) keyed by id. Safe to
		// call repeatedly - later calls overwrite, which is exactly what we want after a morph
		// re-renders the inline registration script.
		register: function (id, options) {
			registry.set(id, options || {});
		},

		// Fetch the container's update URL and morph (or replace) the response into the element.
		// The x-requested-with header is REQUIRED: ERXAjaxApplication.isAjaxRequest keys on it, so
		// without it the server won't treat this as an ajax request and won't return a fragment.
		update: function (id, options) {
			var element = elementFor(id);
			if (element == null) {
				if (window.console) {
					console.warn('AjaxSlim.AUC.update: no element with id "' + id + '"');
				}
				return;
			}
			if (options) {
				// A direct update(id, options) call refreshes the stored options too.
				registry.set(id, options);
			}
			var url = buildUpdateUrl(element, id);
			fetchAndMorph(id, url, null, function () {
				AUC.fireRefreshComplete(id);
			});
		},

		// Run the registered onRefreshComplete hook, if any, after an update/morph completes.
		fireRefreshComplete: function (id) {
			var options = registry.get(id);
			if (options && typeof options.onRefreshComplete === 'function') {
				try {
					options.onRefreshComplete();
				}
				catch (e) {
					if (window.console) {
						console.error('AjaxSlim: onRefreshComplete for "' + id + '" threw', e);
					}
				}
			}
		},

		// setInterval-based periodic refresh, replacing Prototype's PeriodicalUpdater. Idempotent:
		// clear any prior timer for this id first so a periodic container nested inside another
		// morphed container does not accumulate duplicate timers when its registration script
		// re-runs after a morph. canStop/stopped control whether the timer is installed at all;
		// frequencySeconds is the interval in seconds.
		registerPeriodic: function (id, canStop, stopped, frequencySeconds) {
			AUC.stopPeriodic(id);
			if (canStop && stopped) {
				return; // loaded as stopped - install nothing
			}
			var ms = Math.max(0, Number(frequencySeconds) || 0) * 1000;
			if (ms <= 0) {
				return;
			}
			var handle = window.setInterval(function () {
				// If the element is gone (page changed), stop ticking.
				if (elementFor(id) == null) {
					AUC.stopPeriodic(id);
					return;
				}
				AUC.update(id);
			}, ms);
			timers.set(id, handle);
		},

		// Stop and forget any periodic timer for this id.
		stopPeriodic: function (id) {
			var handle = timers.get(id);
			if (handle != null) {
				window.clearInterval(handle);
				timers.delete(id);
			}
		},

		// Field-observer convenience used by AjaxUpdateContainer's observeFieldID binding: when the
		// named field changes, refresh container id. This is the "just refresh the container"
		// shortcut; the full AjaxObserveField element (which can do partial/full form submits to an
		// action) lives in AjaxSlim.ASB.observeField. fullSubmit is honored here too: when true we
		// submit the field's whole form to the container's update URL before morphing; when false we
		// simply re-fetch the container.
		observeField: function (id, fieldId, fullSubmit) {
			ASB.observeField(id, fieldId, null, !fullSubmit, null, {});
		}
	};

	// -----------------------------------------------------------------------
	// AjaxUpdateLink runtime (AUL) - "fetch this action URL and morph the result into targetId".
	// Replaces the legacy AjaxUpdateLink / Ajax.Updater client object, fetch-based, no Prototype.
	// options: { replace: bool, onClick: fn, onComplete: fn, onSuccess: fn }
	//   replace    - target via '_r' (ajax replacement) instead of '_u' (update pass)
	//   onClick    - run before the request (a client hook; AUL.update is only reached if it ran)
	//   onComplete - run after the morph completes (also runs onSuccess if present)
	// -----------------------------------------------------------------------
	var AUL = {
		// Update targetId by fetching actionUrl (already an absolute ajax action URL) and morphing.
		update: function (targetId, actionUrl, options) {
			options = options || {};
			var url = addUpdateParams(actionUrl, targetId, !!options.replace);
			fetchAndMorph(targetId, url, null, function () {
				runHook(options.onSuccess);
				runHook(options.onComplete);
			});
		},

		// Fire an action with no container to update (e.g. the action returns scripts to run, or the
		// page is updated by an AjaxUpdateTrigger in the response). Runs any returned <script>s.
		request: function (actionUrl, options) {
			options = options || {};
			var url = addUpdateParams(actionUrl, null, false);
			fetchAndMorph(null, url, null, function () {
				runHook(options.onSuccess);
				runHook(options.onComplete);
			});
		}
	};

	// -----------------------------------------------------------------------
	// AjaxSubmitButton runtime (ASB) - background form submit + morph. Replaces the legacy
	// ASB.update/request/partial (Prototype Ajax.Request + Form.serializeWithoutSubmits). Forms are
	// serialized with FormData/URLSearchParams; the AJAX_SUBMIT_BUTTON_NAME wire contract that
	// ERXAjaxApplication relies on is preserved (we append it to the submitted body).
	// -----------------------------------------------------------------------
	var AjaxSubmitButtonNameKey = 'AJAX_SUBMIT_BUTTON_NAME';
	var PartialFormSenderIDKey = '_partialSenderID';

	// Serialize a form into a URLSearchParams, excluding submit/image buttons (the legacy
	// "serializeWithoutSubmits" behavior) so a background submit doesn't masquerade as a click on
	// some unrelated submit button. We then append the ajax submit button name explicitly.
	function serializeForm(form) {
		var params = new URLSearchParams();
		var elements = form.elements;
		for (var i = 0; i < elements.length; i++) {
			var el = elements[i];
			if (!el.name || el.disabled) {
				continue;
			}
			var type = (el.type || '').toLowerCase();
			if (type === 'submit' || type === 'image' || type === 'button' || type === 'reset' || type === 'file') {
				continue;
			}
			if ((type === 'checkbox' || type === 'radio') && !el.checked) {
				continue;
			}
			if (el.tagName.toLowerCase() === 'select' && el.multiple) {
				for (var j = 0; j < el.options.length; j++) {
					if (el.options[j].selected) {
						params.append(el.name, el.options[j].value);
					}
				}
				continue;
			}
			params.append(el.name, el.value);
		}
		return params;
	}

	// The form's action URL, switched onto the ajax request handler path the same way
	// AjaxUtils.ajaxComponentActionUrl does server-side (/wo/ -> /ajax/), with the target marker.
	function submitUrl(form, targetId, replace) {
		var action = form.getAttribute('action') || form.action || '';
		action = action.replace('/wo/', '/ajax/');
		return addUpdateParams(action, targetId, replace);
	}

	var ASB = {
		AJAX_SUBMIT_BUTTON_NAME: AjaxSubmitButtonNameKey,

		// Submit form in the background and morph the result into targetId. submitButtonName, when
		// given, is sent as AJAX_SUBMIT_BUTTON_NAME so the server invokes THIS button's action in a
		// multiple-submit form (the partial-submit protocol ERXAjaxApplication keys on).
		update: function (targetId, form, options) {
			ASB._submitBody(targetId, form, options, serializeForm(form));
		},

		// Submit form with no container to update; runs any returned scripts.
		request: function (form, options) {
			ASB._submitBody(null, form, options, serializeForm(form));
		},

		// Partial submit: send ONLY the changed field plus the partial-sender marker (so ERXWOForm
		// treats the field's form as submitted), then morph targetId. Mirrors the legacy ASB.partial.
		partial: function (targetId, fieldId, options) {
			var field = elementFor(fieldId);
			if (field == null) {
				return;
			}
			var body = new URLSearchParams();
			if (field.name) {
				body.append(field.name, fieldValue(field));
				body.append(PartialFormSenderIDKey, field.name);
			}
			ASB._submitBody(targetId, field.form, options, body);
		},

		_submitBody: function (targetId, form, options, body) {
			options = options || {};
			if (options.submitButtonName != null) {
				body.append(AjaxSubmitButtonNameKey, options.submitButtonName);
			}
			var url = submitUrl(form, targetId, !!options.replace);
			fetchAndMorph(targetId, url, body, function () {
				runHook(options.onSuccess);
				runHook(options.onComplete);
			});
		},

		// AjaxObserveField: watch a single field. partial=true => partial submit (just this field);
		// partial=false + targetId => full form submit; partial=false + no targetId => fire+forget.
		// frequencySeconds (legacy poll) is treated as a debounce; observeDelaySeconds also debounces.
		// Idempotent across morphs via a per-signature flag on the (preserved) field node.
		observeField: function (targetId, fieldId, frequencySeconds, partial, observeDelaySeconds, options) {
			var field = elementFor(fieldId);
			if (field == null) {
				return;
			}
			var signature = 'p:' + (partial ? 1 : 0) + '|u:' + (targetId != null ? targetId : '')
				+ '|f:' + frequencySeconds + '|d:' + observeDelaySeconds;
			var observed = field._ajaxObservedSignatures || (field._ajaxObservedSignatures = {});
			if (observed[signature]) {
				return; // this exact observer is already attached to this preserved element
			}
			observed[signature] = true;
			options = options || {};

			var fire = function () {
				if (options.onBeforeSubmit && options.onBeforeSubmit(fieldId) === false) {
					return;
				}
				if (partial) {
					ASB.partial(targetId, fieldId, options);
				}
				else if (targetId != null) {
					ASB.update(targetId, field.form, options);
				}
				else {
					ASB.request(field.form, options);
				}
			};

			var delayMs = debounceMsFor(frequencySeconds, observeDelaySeconds);
			var handler = delayMs > 0 ? debounce(fire, delayMs) : fire;
			// 'change' fires on commit (blur / option pick); 'input' gives live debounced behavior
			// for text fields. We bind 'change' always, and 'input' too when debounced so typing
			// triggers the debounced submit (matching the legacy frequency-poll feel) without firing
			// on every keystroke. Radios/checkboxes/selects only emit 'change', which is correct.
			field.addEventListener('change', handler);
			if (delayMs > 0) {
				field.addEventListener('input', handler);
			}
		},

		// Observe every descendant form field of containerId (the no-observeFieldID form of
		// AjaxObserveField, which renders a wrapper div). Idempotency is per-field (see observeField).
		observeDescendentFields: function (targetId, containerId, frequencySeconds, partial, observeDelaySeconds, options) {
			var container = elementFor(containerId);
			if (container == null) {
				return;
			}
			var fields = container.querySelectorAll('input, select, textarea');
			for (var i = 0; i < fields.length; i++) {
				var f = fields[i];
				if ((f.type || '').toLowerCase() === 'hidden') {
					continue;
				}
				if (!f.id) {
					// observeField keys on an id; give anonymous fields a stable one so the
					// per-field idempotency flag and lookups work.
					f.id = containerId + '__f' + i;
				}
				ASB.observeField(targetId, f.id, frequencySeconds, partial, observeDelaySeconds, options);
			}
		}
	};

	// --- small shared helpers ------------------------------------------------

	function runHook(fn) {
		if (typeof fn === 'function') {
			try {
				fn();
			}
			catch (e) {
				if (window.console) {
					console.error('AjaxSlim: a post-update hook threw', e);
				}
			}
		}
	}

	// Current value of a form field for a partial submit (multi-selects join with the field name
	// repeated by the caller; here we return the single current value).
	function fieldValue(field) {
		if (field.tagName && field.tagName.toLowerCase() === 'select' && field.multiple) {
			var values = [];
			for (var i = 0; i < field.options.length; i++) {
				if (field.options[i].selected) {
					values.push(field.options[i].value);
				}
			}
			return values.join(',');
		}
		return field.value;
	}

	// Legacy observeFieldFrequency was a poll interval in SECONDS; observeDelay was a min gap in
	// SECONDS. We collapse both to a single debounce in milliseconds (the larger of the two), which
	// is the behavior real apps actually wanted from either knob.
	function debounceMsFor(frequencySeconds, observeDelaySeconds) {
		var f = Number(frequencySeconds) || 0;
		var d = Number(observeDelaySeconds) || 0;
		return Math.max(f, d) * 1000;
	}

	function debounce(fn, ms) {
		var timer = null;
		return function () {
			var self = this;
			var args = arguments;
			if (timer != null) {
				window.clearTimeout(timer);
			}
			timer = window.setTimeout(function () {
				timer = null;
				fn.apply(self, args);
			}, ms);
		};
	}

	// Turn an additionalParams value (a plain object of name->value, a query string, or null/
	// undefined) into a leading-'&' query fragment to append to an action URL. Used by named-function
	// update links (AjaxUpdateLink functionName), which receive their extra params at call time.
	AjaxSlim.queryString = function (additionalParams) {
		if (additionalParams == null) {
			return '';
		}
		if (typeof additionalParams === 'string') {
			var s = additionalParams.replace(/^[?&]/, '');
			return s ? '&' + s : '';
		}
		var params = new URLSearchParams();
		for (var key in additionalParams) {
			if (Object.prototype.hasOwnProperty.call(additionalParams, key)) {
				params.append(key, additionalParams[key]);
			}
		}
		var out = params.toString();
		return out ? '&' + out : '';
	};

	AjaxSlim.AUC = AjaxSlim.AUC || AUC;
	AjaxSlim.AUL = AjaxSlim.AUL || AUL;
	AjaxSlim.ASB = AjaxSlim.ASB || ASB;
	AjaxSlim.Morph = AjaxSlim.Morph || Morph;

})(window, document);
