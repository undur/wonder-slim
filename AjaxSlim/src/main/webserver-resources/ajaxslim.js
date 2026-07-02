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
				// Don't let a morph overwrite the live state of the form control the user is currently
				// interacting with. When two debounced observers overlap (e.g. typing in a search field
				// AND toggling a checkbox), the search field's morph response can land just after the
				// checkbox click and reset the box's checked state back to the server's rendered value -
				// undoing the click before its own request round-trips, so the toggle silently reverts.
				// Protect value/checked/selected on the active element; its own update will carry the
				// real value to the server. (value is also covered by Idiomorph's ignoreActiveValue, but
				// checked/selected are not, so we guard all three here.)
				if ((attributeName === 'value' || attributeName === 'checked' || attributeName === 'selected') &&
						node === document.activeElement) {
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
				Morph.evalIsolated(code);
			}
		},

		// Run the scripts from a script-only ajax response. The server returns such a response either as
		// a raw text/javascript body (no <script> wrapper - the case with no _u update marker) or with
		// the JS wrapped in <script> tags. Eval a raw body directly; otherwise extract and run the tags.
		runResponseScripts: function (text, contentType) {
			var isRawJs = /(?:application|text)\/(?:x-)?(?:java|ecma)script/i.test(contentType || '');
			if (isRawJs || text.indexOf('<script') === -1) {
				if (text && text.trim()) {
					Morph.evalIsolated(text);
				}
				return;
			}
			Morph.runScripts(text);
		},

		// Indirect eval keeps each script in global scope; isolate so one failure doesn't kill the rest.
		evalIsolated: function (code) {
			try {
				(0, eval)(code);
			}
			catch (e) {
				if (window.console) {
					console.error('AjaxSlim: error evaluating a script in an ajax response (continuing)', e, code);
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
		// Params may be joined with '?' OR, if a caller appended additional params to a URL that had no
		// query yet (AjaxUpdateLink functionName + AjaxSlim.queryString returns '&key=val'), with a
		// leading '&' before any '?'. Treat the FIRST '?' or '&' as the start of the query so those
		// params aren't trapped in the path. Without this, ".../ajax/0.5&_oldIndex=0" + "?_u=.." would
		// put _oldIndex in the path and the server would never see it.
		var sepIndex = raw.search(/[?&]/);
		var base = sepIndex === -1 ? raw : raw.substring(0, sepIndex);
		var params = new URLSearchParams(sepIndex === -1 ? '' : raw.substring(sepIndex + 1));
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
	// --- ajax-activity broker --------------------------------------------------
	// fetchAndMorph is the single choke point for ALL ajax activity, so we track in-flight requests
	// here and dispatch document-level events on the 0<->1 transitions. This is the modern replacement
	// for Prototype's global Ajax.Responders: anything that wants to react to "ajax busy / idle"
	// (e.g. AjaxBusySpinner) listens for 'ajaxslim:busy' / 'ajaxslim:idle' instead of registering a
	// responder. document.documentElement carries a 'data-ajaxslim-busy' attribute while >0 requests
	// are in flight, so a pure-CSS spinner can show/hide with no JS of its own.
	var inFlight = 0;

	function activityStart() {
		inFlight++;
		if (inFlight === 1) {
			document.documentElement.setAttribute('data-ajaxslim-busy', 'true');
			dispatchActivity('ajaxslim:busy');
		}
	}

	function activityEnd() {
		inFlight = Math.max(0, inFlight - 1);
		if (inFlight === 0) {
			document.documentElement.removeAttribute('data-ajaxslim-busy');
			dispatchActivity('ajaxslim:idle');
		}
	}

	function dispatchActivity(name) {
		try {
			document.dispatchEvent(new CustomEvent(name, { detail: { inFlight: inFlight } }));
		}
		catch (e) { /* CustomEvent unsupported in ancient browsers - the data-attribute still works */ }
	}

	// --- error reporting -------------------------------------------------------
	// When an ajax request FAILS (a network error, or an HTTP error status from the server such as a
	// 500 "backtracked too far" when a page has aged out of the cache), we must NOT silently swallow it
	// and we must NOT morph the server's error page into the target container. Instead we surface it to
	// the user. The contract mirrors the busy/idle broker: a cancelable document-level CustomEvent
	// 'ajaxslim:error' carries the failure detail, so an app can render its own UX (and call
	// preventDefault() to suppress the built-in presenter). If nobody cancels it, a minimal built-in
	// banner shows - so a failure is NEVER invisible, even in an app that wires up nothing.
	//
	// detail = { targetId, url, status (HTTP status or 0 for a network error), statusText, body, error }
	function reportError(detail) {
		if (window.console) {
			console.error('AjaxSlim: ajax request failed', detail);
		}
		var cancelled = false;
		try {
			var evt = new CustomEvent('ajaxslim:error', { detail: detail, cancelable: true });
			cancelled = !document.dispatchEvent(evt); // dispatchEvent returns false if preventDefault was called
		}
		catch (e) { /* CustomEvent unsupported - fall through to the default presenter */ }
		if (!cancelled) {
			AjaxSlim.Notify.error(detail);
		}
	}

	// The built-in error presenter: a single dismissible banner pinned to the top of the viewport. It is
	// intentionally tiny and dependency-free. Apps that want their own look replace AjaxSlim.Notify.error
	// wholesale, or listen for 'ajaxslim:error' and preventDefault(). Re-uses one banner element so a
	// burst of failures doesn't stack.
	//
	// When the failure carries a server response body (an HTTP error - typically a WebObjects/Wonder
	// exception page, which is genuinely informative), the banner also offers a "Show details" button
	// that opens that exact HTML in an iframe overlay (see AjaxSlim.Notify.showDetails). The summary
	// message stays the at-a-glance line; the full page is one click away.
	AjaxSlim.Notify = AjaxSlim.Notify || {
		error: function (detail) {
			try {
				var id = 'ajaxslim-error-banner';
				var bar = document.getElementById(id);
				if (!bar) {
					bar = document.createElement('div');
					bar.id = id;
					bar.setAttribute('role', 'alert');
					bar.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:2147483647;'
						+ 'background:#b00020;color:#fff;font:14px/1.4 system-ui,sans-serif;'
						+ 'padding:10px 40px 10px 14px;box-shadow:0 1px 4px rgba(0,0,0,.3);'
						+ 'display:flex;align-items:center;gap:12px;';
					bar._msg = document.createElement('span');
					bar._msg.style.cssText = 'flex:1 1 auto;';
					bar.appendChild(bar._msg);
					// "Show details" - only wired up below when there's a body to show; hidden otherwise.
					bar._details = document.createElement('button');
					bar._details.type = 'button';
					bar._details.textContent = 'Show details';
					bar._details.style.cssText = 'flex:0 0 auto;background:rgba(255,255,255,.15);'
						+ 'border:1px solid rgba(255,255,255,.6);color:#fff;font:inherit;'
						+ 'padding:3px 10px;border-radius:4px;cursor:pointer;';
					bar.appendChild(bar._details);
					var close = document.createElement('button');
					close.type = 'button';
					close.setAttribute('aria-label', 'Dismiss');
					close.textContent = '×';
					close.style.cssText = 'position:absolute;top:6px;right:10px;background:none;border:0;'
						+ 'color:#fff;font-size:20px;line-height:1;cursor:pointer;';
					close.onclick = function () { if (bar.parentNode) { bar.parentNode.removeChild(bar); } };
					bar.appendChild(close);
					document.body.appendChild(bar);
				}
				var msg = (detail && detail.status)
					? ('A server error occurred (' + detail.status + ') and the page could not be updated. '
						+ 'Please reload the page and try again.')
					: 'The server could not be reached and the page could not be updated. '
						+ 'Please check your connection and try again.';
				bar._msg.textContent = msg;
				// Offer the full server response only when there actually is one (HTTP errors carry the
				// exception page; a bare network failure - status 0 - has no body).
				var body = detail && detail.body;
				if (body && String(body).trim().length > 0) {
					bar._details.style.display = '';
					bar._details.onclick = function () { AjaxSlim.Notify.showDetails(detail); };
				}
				else {
					bar._details.style.display = 'none';
					bar._details.onclick = null;
				}
			}
			catch (e) { /* last-resort: never let the error presenter itself throw */ }
		},

		// Open the server's returned HTML (a WO/Wonder exception page, usually) in a modal overlay. The
		// body is rendered inside an iframe via srcdoc, so the exception page's own styles AND scripts
		// behave exactly as a normal full-page load would - the collapsible stack-trace rows (onclick
		// handlers) stay interactive. The body always comes from our own server (it's just an error
		// response to an ajax request we made), so no cross-origin sandboxing is needed. One reused
		// overlay; ESC or the close button dismisses it.
		showDetails: function (detail) {
			try {
				var id = 'ajaxslim-error-overlay';
				var overlay = document.getElementById(id);
				if (!overlay) {
					overlay = document.createElement('div');
					overlay.id = id;
					overlay.style.cssText = 'position:fixed;inset:0;z-index:2147483647;'
						+ 'background:rgba(0,0,0,.55);display:flex;flex-direction:column;'
						+ 'padding:24px;box-sizing:border-box;';

					var dismiss = function () {
						overlay.style.display = 'none';
						overlay._frame.removeAttribute('srcdoc');
						document.removeEventListener('keydown', overlay._onKey);
					};
					overlay._onKey = function (e) { if (e.key === 'Escape') { dismiss(); } };
					// Click on the backdrop (outside the panel) closes; clicks inside don't bubble out.
					overlay.onclick = function (e) { if (e.target === overlay) { dismiss(); } };

					var panel = document.createElement('div');
					panel.style.cssText = 'background:#fff;border-radius:8px;overflow:hidden;'
						+ 'box-shadow:0 8px 40px rgba(0,0,0,.4);display:flex;flex-direction:column;'
						+ 'flex:1 1 auto;min-height:0;width:80vw;margin:0 auto;';

					var head = document.createElement('div');
					head.style.cssText = 'flex:0 0 auto;display:flex;align-items:center;gap:12px;'
						+ 'background:#b00020;color:#fff;font:14px/1.4 system-ui,sans-serif;padding:10px 14px;';
					overlay._title = document.createElement('strong');
					overlay._title.style.cssText = 'flex:1 1 auto;';
					head.appendChild(overlay._title);
					var closeBtn = document.createElement('button');
					closeBtn.type = 'button';
					closeBtn.setAttribute('aria-label', 'Close');
					closeBtn.textContent = '×';
					closeBtn.style.cssText = 'background:none;border:0;color:#fff;font-size:22px;'
						+ 'line-height:1;cursor:pointer;';
					closeBtn.onclick = dismiss;
					head.appendChild(closeBtn);
					panel.appendChild(head);

					overlay._frame = document.createElement('iframe');
					overlay._frame.setAttribute('title', 'Server error details');
					overlay._frame.style.cssText = 'flex:1 1 auto;width:100%;border:0;background:#fff;';
					panel.appendChild(overlay._frame);

					overlay.appendChild(panel);
					document.body.appendChild(overlay);
					overlay._dismiss = dismiss;
				}
				overlay._title.textContent = (detail && detail.status)
					? ('Server error ' + detail.status + (detail.statusText ? ' — ' + detail.statusText : ''))
					: 'Request failed';
				overlay._frame.srcdoc = String(detail && detail.body || '');
				overlay.style.display = 'flex';
				document.addEventListener('keydown', overlay._onKey);
			}
			catch (e) { /* last-resort: never let the detail presenter itself throw */ }
		}
	};

	// Headers for an ajax request. Always sends x-requested-with (ERXAjaxApplication.isAjaxRequest keys
	// on it). For a URLSearchParams POST body we set form-urlencoded; FormData sets its own multipart
	// content-type, so we leave it.
	function postHeaders(body) {
		var headers = { 'x-requested-with': 'XMLHttpRequest' };
		if (body != null && typeof URLSearchParams !== 'undefined' && body instanceof URLSearchParams) {
			headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=UTF-8';
		}
		return headers;
	}

	// --- tab-into-during-morph focus rescue (a WORKAROUND) ---------------------
	//
	// WHY THIS EXISTS - and why it's a workaround, not a clean solution:
	//
	// When you Tab out of an observed field, two things race: (1) the field's 'change' fires -> a morph
	// of the container, and (2) the browser moves focus to the NEXT element. The morph reconciles the
	// container and (especially for a wonder-select widget, whose trigger is client-injected) re-creates
	// the very node focus was arriving at - so the browser's in-flight focus move lands on nothing and
	// focus falls to <body>. Net effect: Tab out of a field that triggers an update, and you end up
	// focused on nothing.
	//
	// The "correct" fix would be to restore focus to the element the user was tabbing INTO - but the
	// browser doesn't reliably tell us what that was: on the change/focusout events here, relatedTarget
	// is null (the destination is mid-transition, and a hidden <select> behind a wonder-select trigger
	// confuses relatedTarget resolution). So we can't KNOW the destination.
	//
	// THE WORKAROUND: remember the field that triggered the update, and after the morph - only if focus
	// actually fell to <body> - move focus to the next tabbable element after that field. That
	// RECONSTRUCTS what the browser's Tab was trying to do. It happens to be right for the common case
	// (Tab moves forward to the next field), but it is a reconstruction, not the browser's actual intent:
	// it assumes forward-Tab, can't see Shift+Tab, and picks "next tabbable" by DOM order + tabindex
	// heuristics rather than the browser's real focus model. Introduced because the morph destroys the
	// destination before we can observe it - the right reasons would be a browser that preserved the
	// in-flight focus target across the DOM change, which we don't get.
	var _tabFrom = null;

	// Record the field whose change triggered the update, so we can find the "next" field after a morph.
	// Only for fields whose focus the browser will try to move OFF on the change (text/number inputs,
	// textareas). A wonder-select-managed <select> handles its OWN post-morph focus (see the widget's
	// _refocus), so we must NOT also move focus to the next field for it - that would fight the widget
	// and steal focus away from the select the user just used.
	function rememberTabSource(field) {
		_tabFrom = null;
		if (!field || field.nodeType !== 1 || !field.id) {
			return;
		}
		if (field.tagName === 'SELECT' && field.classList && field.classList.contains('ajax-popup-button')) {
			return; // wonder-select owns its own refocus
		}
		_tabFrom = { id: field.id, at: now() };
	}

	// After a morph, if focus fell to <body> and we remembered a trigger field, move focus to the next
	// tabbable element after it. Short deadline so a stale record can't later steal focus.
	function restoreTabFocus() {
		var rec = _tabFrom;
		_tabFrom = null;
		if (!rec || now() - rec.at > 4000) {
			return;
		}
		if (document.activeElement && document.activeElement !== document.body) {
			return; // focus already landed somewhere real - don't fight it
		}
		var from = document.getElementById(rec.id);
		if (!from) {
			return;
		}
		var next = nextTabbable(from);
		if (!next) {
			return;
		}
		// If the destination is a wonder-select's underlying <select>, focus goes to its trigger. But the
		// morph re-creates a BARE <select>; wonder-select's re-enhance (which wraps it and builds the
		// trigger) runs on its own async pass, which may not have happened yet. So resolve the focus
		// target lazily and retry for a few frames until the trigger exists, rather than racing it.
		var attempts = 0;
		(function tryFocus() {
			if (document.activeElement && document.activeElement !== document.body) {
				return; // user/enhancer already moved focus - stop
			}
			var target = next;
			if (next.tagName === 'SELECT' && next.classList.contains('ajax-popup-button')) {
				var host = next.closest('wonder-select');
				var trigger = host && host.querySelector('.ws-trigger');
				if (!trigger) {
					// Not enhanced yet - wait a frame and retry (bounded).
					if (attempts++ < 20) {
						(window.requestAnimationFrame || window.setTimeout)(tryFocus, 16);
					}
					return;
				}
				target = trigger;
			}
			if (target && typeof target.focus === 'function') {
				target.focus();
			}
		})();
	}

	// The next element in tab order after `from`. Best-effort DOM-order walk over focusable elements
	// (the browser's real algorithm, incl. tabindex ordering, is more complex - this covers the common
	// forward-Tab-through-a-form case the workaround targets).
	function nextTabbable(from) {
		var focusable = document.querySelectorAll(
			'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), ' +
			'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])');
		var list = Array.prototype.filter.call(focusable, function (el) {
			// Skip the hidden <select> behind a wonder-select (you tab to its trigger instead, which is
			// itself in the list). Skip elements with no layout box (display:none / visibility:hidden).
			if (el.tagName === 'SELECT' && el.closest && el.closest('wonder-select')) {
				return false;
			}
			return !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
		});
		var i = list.indexOf(from);
		return i >= 0 && i + 1 < list.length ? list[i + 1] : null;
	}

	function now() {
		return (window.performance && window.performance.now) ? window.performance.now() : Date.now();
	}

	function fetchAndMorph(targetId, url, body, onDone) {
		var init = {
			credentials: 'same-origin',
			headers: postHeaders(body)
		};
		if (body != null) {
			init.method = 'POST';
			init.body = body;
		}
		activityStart();
		var responseContentType = '';
		return fetch(url, init).then(function (response) {
			responseContentType = response.headers.get('Content-Type') || '';
			// fetch() only rejects on a NETWORK error - an HTTP 500/404/etc. resolves normally. So we
			// must check response.ok ourselves: otherwise we would morph the server's error page (e.g.
			// "backtracked too far") straight into the target container. Read the body for diagnostics,
			// then throw a typed error to divert to the .catch (which reports rather than morphs).
			if (!response.ok) {
				return response.text().catch(function () { return ''; }).then(function (body) {
					var err = new Error('AjaxSlim HTTP ' + response.status);
					err.ajaxslim = { status: response.status, statusText: response.statusText, body: body };
					throw err;
				});
			}
			return response.text();
		}).then(function (text) {
			// The response describes itself, and we act on what it IS - not on what we asked for:
			//   - it carries <ajaxslim-fragment>s => demux + morph each into its container. This is the
			//     UNIFORM update path: every container update (single or multi, client- or server-targeted)
			//     comes back framed, so there is no single-vs-multi special case here. applyFragments
			//     handles one fragment or many, skips containers no longer on the page, and honours each
			//     one's data-morph.
			//   - otherwise it's JS to run globally (a text/javascript body, or <script> tags) - e.g. the
			//     the AjaxUpdater.triggerUpdate JS-command path, or an action returning arbitrary JS.
			// targetId is only a hint (for logging / a fire-and-forget caller); the body decides.
			if (/<ajaxslim-fragment\b/i.test(text)) {
				applyFragments(text);
			}
			else {
				Morph.runResponseScripts(text, responseContentType);
			}
			if (typeof onDone === 'function') {
				onDone();
			}
			// If a Tab-out triggered this morph and focus was lost to <body>, move it to the next
			// tabbable element (the workaround above). Deferred so it runs after the DOM settles AND
			// after wonder-select's post-morph re-enhance pass (which re-creates the widget trigger) -
			// otherwise the destination may not exist yet.
			(window.requestAnimationFrame || window.setTimeout)(function () {
				restoreTabFocus();
			}, 0);
		}).catch(function (e) {
			// Either a network failure (no e.ajaxslim) or an HTTP error we threw above (e.ajaxslim set).
			// Surface it to the user; never morph an error response into the container.
			var info = e && e.ajaxslim ? e.ajaxslim : { status: 0, statusText: '', body: '' };
			reportError({ targetId: targetId, url: url, status: info.status, statusText: info.statusText,
				body: info.body, error: e });
		}).then(activityEnd, activityEnd); // finally: always settle the activity counter
	}

	// Split a ";"-joined update target ("a;b;c") into trimmed, non-empty ids.
	function splitIds(value) {
		var out = [];
		var parts = String(value).split(';');
		for (var i = 0; i < parts.length; i++) {
			var t = parts[i].trim();
			if (t) {
				out.push(t);
			}
		}
		return out;
	}

	// Demux a multi-update response: parse out each <ajaxslim-fragment data-id="…"> and morph its inner
	// content into the live container of that id. Each fragment is applied independently - a container
	// whose id is no longer on the page is skipped, the rest still update; each honours its own
	// data-morph and fires its onRefreshComplete. Scripts inside a fragment run via Morph (same as a
	// single update). Robust to fragments arriving in any order.
	// Pull each <ajaxslim-fragment data-id="...">...</ajaxslim-fragment> out of the response as a RAW
	// HTML string (id + inner html), via regex - NOT by DOM-parsing the whole response. This matters for
	// table-context content: a fragment's inner html may be bare <td>/<tr>/<tbody> (when the container is
	// a <tr>/<table>), and parsing those through an out-of-context <template>/<div> makes the HTML parser
	// silently DROP the table tags. Keeping the inner html as a string lets Morph.morph hand it to
	// Idiomorph, which parses it in the RECEIVER's context (e.g. inside a <tr>), so the cells survive.
	function parseFragments(text) {
		var out = [];
		var re = /<ajaxslim-fragment\b[^>]*\bdata-id\s*=\s*"([^"]*)"[^>]*>([\s\S]*?)<\/ajaxslim-fragment\s*>/gi;
		var m;
		while ((m = re.exec(text)) !== null) {
			out.push({ id: m[1], html: m[2] });
		}
		return out;
	}

	function applyFragments(text) {
		var fragments = parseFragments(text);
		if (!fragments.length) {
			// No frames found - a multi response always frames, so this is unexpected. Run any scripts the
			// body carried (a script-only push still works) and warn.
			Morph.runResponseScripts(text, 'text/html');
			if (window.console) {
				console.warn('AjaxSlim: multi-update response carried no <ajaxslim-fragment>s');
			}
			return;
		}
		for (var i = 0; i < fragments.length; i++) {
			var id = fragments[i].id;
			if (!id) {
				continue;
			}
			var receiver = elementFor(id);
			if (receiver == null) {
				// container gone from the page - skip it, keep going with the others
				continue;
			}
			var html = fragments[i].html;
			var doMorph = receiver.getAttribute('data-morph') !== 'false';
			if (doMorph) {
				Morph.morph(receiver, html);
			}
			else {
				Morph.replace(receiver, html);
			}
			// NOTE: do NOT also call AUC.fireRefreshComplete(id) here. The server frames each container's
			// onRefreshComplete script INSIDE its fragment (AjaxUpdateContainer.handleRequest), and
			// Morph.morph/replace runs embedded <script>s - so the hook already fires exactly once,
			// matching the single-update path (fetchAndMorph also relies on the morphed-in script, not
			// the registry). Calling fireRefreshComplete here too would run it a SECOND time.
		}
		// Run any scripts that live OUTSIDE the fragments. A response can carry a container fragment AND
		// trailing server-pushed JS - e.g. a client update of allBox1 whose action also called
		// AjaxUpdater.triggerUpdate('allBox2'), which appends <script>AUC.update('allBox2')
		// </script> after the fragment. Those scripts are not inside any fragment, so the morph loop above
		// never runs them; run them here. (Scripts INSIDE a fragment already ran via Morph.morph, and
		// stripping whole <ajaxslim-fragment> blocks first means we don't double-run them.)
		var outside = text.replace(/<ajaxslim-fragment\b[\s\S]*?<\/ajaxslim-fragment\s*>/gi, '');
		Morph.runScripts(outside);
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

		// Update SEVERAL containers from ONE request. actionUrl is the trigger's action URL (e.g. an
		// AjaxUpdateLink's); we fetch it once with _u=a;b;c, and the server returns each container's
		// content framed in <ajaxslim-fragment data-id="…">. We demux those and morph each into its live
		// container, honouring each one's data-morph, then fire its onRefreshComplete. One round-trip.
		updateMany: function (ids, actionUrl, options) {
			options = options || {};
			if (!ids || !ids.length) {
				return;
			}
			if (ids.length === 1) {
				// Degenerate case: not actually multi - route through the normal single path (which also
				// demuxes the single framed fragment the server returns).
				return AUC.update(ids[0], options);
			}
			var joined = ids.join(';');
			var url = addUpdateParams(actionUrl, joined, !!options.replace);
			activityStart();
			return fetch(url, {
				credentials: 'same-origin',
				headers: { 'x-requested-with': 'XMLHttpRequest' }
			}).then(function (response) {
				// Same as the single path: don't apply an HTTP error response as fragments - check
				// response.ok and divert failures to reportError instead of morphing the error page.
				if (!response.ok) {
					return response.text().catch(function () { return ''; }).then(function (body) {
						var err = new Error('AjaxSlim HTTP ' + response.status);
						err.ajaxslim = { status: response.status, statusText: response.statusText, body: body };
						throw err;
					});
				}
				return response.text();
			}).then(function (text) {
				applyFragments(text);
				runHook(options.onSuccess);
				runHook(options.onComplete);
			}).catch(function (e) {
				var info = e && e.ajaxslim ? e.ajaxslim : { status: 0, statusText: '', body: '' };
				reportError({ targetId: ids.join(';'), url: url, status: info.status,
					statusText: info.statusText, body: info.body, error: e });
			}).then(activityEnd, activityEnd);
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
		// A targetId of "a;b;c" is a multi-container update - a single container id never contains ';' -
		// so route those to updateMany (one request, framed fragments, morph each). Single ids are
		// unaffected.
		update: function (targetId, actionUrl, options) {
			options = options || {};
			if (targetId != null && targetId.indexOf(';') !== -1) {
				return AUC.updateMany(splitIds(targetId), actionUrl, options);
			}
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
		partial: function (targetId, fieldOrId, options) {
			var field = (fieldOrId && fieldOrId.nodeType === 1) ? fieldOrId : elementFor(fieldOrId);
			if (field == null) {
				return;
			}
			var body = new URLSearchParams();
			if (field.name) {
				appendFieldValues(body, field);
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
			// A ";"-joined targetId ("a;b;c") is a multi-container update: one POST, framed fragments,
			// morph each. A single container id never contains ';', so single submits are unaffected.
			if (targetId != null && targetId.indexOf(';') !== -1) {
				activityStart();
				return fetch(url, {
					method: 'POST',
					credentials: 'same-origin',
					headers: postHeaders(body),
					body: body
				}).then(function (response) {
					return response.text();
				}).then(function (text) {
					applyFragments(text);
					runHook(options.onSuccess);
					runHook(options.onComplete);
				}).catch(function (e) {
					if (window.console) {
						console.error('AjaxSlim: multi submit failed for "' + targetId + '"', e);
					}
				}).then(activityEnd, activityEnd);
			}
			fetchAndMorph(targetId, url, body, function () {
				runHook(options.onSuccess);
				runHook(options.onComplete);
			});
		},

		// AjaxObserveField: watch a single field. partial=true => partial submit (just this field);
		// partial=false + targetId => full form submit; partial=false + no targetId => fire+forget.
		// frequencySeconds (legacy poll) is treated as a debounce; observeDelaySeconds also debounces.
		// Idempotent across morphs via a per-signature flag on the (preserved) field node.
		observeField: function (targetId, fieldOrId, frequencySeconds, partial, observeDelaySeconds, options) {
			// Accept either a DOM node or an id. We bind to and key off the NODE directly - never
			// assigning a synthetic id - so anonymous fields (e.g. repetition rows with no id) keep
			// working. Assigning positional ids was the cause of a focus regression: it mutated the DOM
			// on every re-registration, and when a morph changed the field count/order the index-based
			// ids drifted, so Idiomorph's id-keyed focus restoration landed on the wrong node. Idiomorph
			// matches unchanged nodes positionally and preserves their focus on its own; we just must not
			// fight it by churning ids.
			var field = (fieldOrId && fieldOrId.nodeType === 1) ? fieldOrId : elementFor(fieldOrId);
			if (field == null) {
				return;
			}
			options = options || {};

			// The observer's IDENTITY (which logical AjaxObserveField this is) vs its positional
			// SUBMIT NAME. The submitButtonName is the field's WO element-id (e.g. "5.3.3.1.1.7"); it is
			// POSITIONAL, so when a row moves (drag-reorder, insert/remove above it) the same logical
			// observer must now submit a DIFFERENT name. Idiomorph preserves the field node across that
			// morph, so the stale `fire` closure - capturing the OLD submitButtonName - would otherwise
			// stay bound and submit the wrong row's element-id, landing the server's repetition on the
			// wrong item. So: key idempotency on the stable identity, but treat a changed submitButtonName
			// as a re-registration that REBINDS (detaching the prior handlers first), not a no-op.
			var identity = 'p:' + (partial ? 1 : 0) + '|u:' + (targetId != null ? targetId : '')
				+ '|f:' + frequencySeconds + '|d:' + observeDelaySeconds;
			var submitName = options.submitButtonName != null ? String(options.submitButtonName) : '';
			var observers = field._ajaxObservers || (field._ajaxObservers = {});
			var prior = observers[identity];
			if (prior) {
				if (prior.submitName === submitName) {
					return; // this exact observer (same target AND same submit name) is already attached
				}
				// Same logical observer, but the positional submit name changed under a morph: detach the
				// stale listeners before rebinding so the node never carries a handler that submits an
				// outdated element-id (and never accumulates duplicates).
				field.removeEventListener('change', prior.changeListener || prior.handler);
				if (prior.delayMs > 0) {
					field.removeEventListener('input', prior.handler);
				}
			}

			var fire = function () {
				if (options.onBeforeSubmit && options.onBeforeSubmit(field) === false) {
					return;
				}
				if (partial) {
					ASB.partial(targetId, field, options);
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
			//
			// A 'change' that fires from a blur (e.g. Tab out of the field) triggers a morph that can
			// re-create the element the user is tabbing INTO, dropping focus to <body>. Remember this
			// field so the morph can move focus to the next tabbable element afterwards, reconstructing
			// the Tab the browser lost. See the rememberTabSource / restoreTabFocus workaround above.
			var changeListener = function (e) {
				rememberTabSource(field);
				handler(e);
			};
			observers[identity] = { submitName: submitName, handler: handler, changeListener: changeListener, delayMs: delayMs };
			field.addEventListener('change', changeListener);
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
				// Pass the NODE, not an id. observeField keys its idempotency flag off the node and
				// ASB.partial sends the field's form-value NAME (not its DOM id) as the partial sender,
				// so no DOM id is needed. Crucially we do NOT assign synthetic positional ids here:
				// that mutated the DOM and drifted under morphs, breaking focus restoration.
				ASB.observeField(targetId, f, frequencySeconds, partial, observeDelaySeconds, options);
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
	// Append a field's value(s) to a URLSearchParams body, the way a real form submit would. A
	// multiple <select> contributes ONE entry per selected option under the same name (NOT a single
	// comma-joined value) - WOBrowser reconstitutes its selections by matching repeated form values
	// to its option list, so a joined string would round-trip as no selection. Everything else is a
	// single value.
	function appendFieldValues(body, field) {
		var type = (field.type || '').toLowerCase();
		// A checkbox/radio contributes its value ONLY when checked - an unchecked one submits nothing,
		// exactly as a real form does (and as serializeForm() does). Appending field.value regardless
		// (a checkbox's value is its value attribute, not its checked state) made a partial-submit
		// observer on a checkbox always look "on", so toggling it off never registered server-side.
		if (type === 'checkbox' || type === 'radio') {
			if (field.checked) {
				body.append(field.name, field.value);
			}
			return;
		}
		if (field.tagName && field.tagName.toLowerCase() === 'select' && field.multiple) {
			for (var i = 0; i < field.options.length; i++) {
				if (field.options[i].selected) {
					body.append(field.name, field.options[i].value);
				}
			}
			return;
		}
		body.append(field.name, field.value);
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
