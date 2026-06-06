/*
 * wonder-select - a morph-native, dependency-free replacement for the Chosen jQuery widget.
 *
 * WHY: Chosen turns a <select> into a hidden select plus a GENERATED sibling .chosen-container.
 * Under DOM morphing that sibling is foreign DOM the server never rendered, so morph strips it and
 * Chosen's re-init no-ops on the preserved (already-initialized) select - the widget breaks.
 *
 * wonder-select avoids that by being a CUSTOM ELEMENT: <wonder-select> owns the native <select>
 * (its source of truth) and renders its trigger / search box / options list as its OWN children.
 * Because the whole widget is one element's subtree, morph reconciles it like any other tag - and
 * the element re-applies its display state in connectedCallback. No foreign sibling, nothing to
 * strip, no re-init hack.
 *
 * INTEGRATION: it dispatches a native 'change' event on the underlying <select> when the selection
 * changes, so existing AjaxObserveField wiring keeps working unchanged. An auto-enhancer adopts any
 * <select class="ajax-popup-button"> (drop-in for Chosen), and you can also author <wonder-select>
 * explicitly around a <select>.
 *
 * SUPPORTED (matches actual nb usage): single + multiple select, type-to-filter, placeholder text
 * (from the select's data-placeholder or a leading empty/disabled option), no-results text,
 * width:100%. Deliberately small - not a full Chosen clone.
 */
(function () {
	'use strict';

	var NO_RESULTS_DEFAULT = 'No results';

	function h(tag, attrs, children) {
		var el = document.createElement(tag);
		if (attrs) {
			for (var k in attrs) {
				if (k === 'class') el.className = attrs[k];
				else if (k === 'text') el.textContent = attrs[k];
				else el.setAttribute(k, attrs[k]);
			}
		}
		(children || []).forEach(function (c) { el.appendChild(c); });
		return el;
	}

	// True for the "no selection" placeholder option. Covers a plain empty value and WebObjects'
	// noSelectionString sentinel ("WONoSelectionString"), which is what every nb ajax-popup-button uses.
	function isPlaceholderOption(opt) {
		return opt.value === '' || opt.value === 'WONoSelectionString';
	}

	function placeholderFor(select) {
		if (select.getAttribute('data-placeholder')) return select.getAttribute('data-placeholder');
		var first = select.options[0];
		if (first && isPlaceholderOption(first)) return first.textContent.trim();
		return '';
	}

	var WonderSelect = (function () {
		function define() {
			if (window.customElements && !window.customElements.get('wonder-select')) {
				window.customElements.define('wonder-select', class extends HTMLElement {
					connectedCallback() { WonderSelect.mount(this); }
				});
			}
		}

		// Build (or rebuild) the widget UI inside the <wonder-select> host, around its <select>.
		function mount(host) {
			var select = host.querySelector('select');
			if (!select) return;

			// Idempotent: if we already built the UI for THIS select instance, just resync display.
			if (host._wsBuilt && host._wsSelect === select) {
				syncDisplay(host);
				return;
			}
			host._wsBuilt = true;
			host._wsSelect = select;
			host._wsMultiple = select.multiple;
			host._wsNoResults = select.getAttribute('data-no-results') || NO_RESULTS_DEFAULT;

			select.style.display = 'none';

			// The trigger + dropdown are generated client-side, so they are NOT in the server HTML.
			// Mark them data-morph-ignore so a morph of an enclosing container leaves them in place
			// (otherwise Idiomorph would strip them as "absent from the new HTML" - the exact bug
			// that broke Chosen). The underlying <select> still morphs normally (its value/options
			// come from the server); we re-sync the trigger label from it after the morph.
			var trigger = h('div', { class: 'ws-trigger', tabindex: '0', 'data-morph-ignore': 'true' });
			var dropdown = h('div', { class: 'ws-dropdown', hidden: '', 'data-morph-ignore': 'true' });
			var search = h('input', { class: 'ws-search', type: 'text', autocomplete: 'off' });
			var list = h('ul', { class: 'ws-options' });
			dropdown.appendChild(search);
			dropdown.appendChild(list);

			host.appendChild(trigger);
			host.appendChild(dropdown);
			host.classList.add('ws-host');
			if (host._wsMultiple) host.classList.add('ws-multiple');

			host._ws = { select: select, trigger: trigger, dropdown: dropdown, search: search, list: list };

			wireEvents(host);
			renderOptions(host, '');
			syncDisplay(host);
		}

		function open(host, initialSearch) {
			var ws = host._ws;
			ws.dropdown.hidden = false;
			host.classList.add('ws-open');
			ws.search.value = initialSearch || '';
			renderOptions(host, ws.search.value);
			positionDropdown(host);
			ws.search.focus();
			// Put the caret after the seeded character.
			if (initialSearch) {
				var len = ws.search.value.length;
				if (ws.search.setSelectionRange) ws.search.setSelectionRange(len, len);
			}
		}

		// Size and orient the dropdown relative to the VIEWPORT so it never extends beyond it
		// (Chosen's annoyance) and uses available space well: open downward by default, but flip up
		// when there's more room above; cap the height to whichever gap we open into (minus a small
		// margin) and to a sensible fraction of the viewport. Measured each open (handles the
		// trigger being inside a scrolling table, different screen sizes, etc.).
		function positionDropdown(host) {
			var ws = host._ws;
			var rect = ws.trigger.getBoundingClientRect();
			var viewportH = window.innerHeight || document.documentElement.clientHeight;
			var margin = 8;                       // breathing room from the viewport edge
			var spaceBelow = viewportH - rect.bottom - margin;
			var spaceAbove = rect.top - margin;
			// Users often BROWSE the list to decide (e.g. accounting keys), so maximise visible
			// height: open into whichever side has more room. A small bias keeps it opening downward
			// when down is already comfortable, to avoid a jarring flip for no real gain.
			var DOWN_BIAS = 40;
			var openUp = spaceAbove > spaceBelow + DOWN_BIAS;
			var avail = openUp ? spaceAbove : spaceBelow;
			// Never exceed the available gap (so it stays within the viewport); also cap at a
			// comfortable fraction of the viewport so a huge screen doesn't make an unwieldy list.
			// Floor so it's always at least usable even in a tight spot.
			var maxH = Math.max(120, Math.min(avail, viewportH * 0.7));
			host.classList.toggle('ws-up', openUp);
			ws.dropdown.style.maxHeight = maxH + 'px';

			// Width: the list grows to its widest label (min = trigger width). Cap it to the viewport
			// so a ~100-char label can't run off-screen. Prefer left-aligned to the trigger; if the
			// content would overflow the right edge, right-align instead. Only if it still doesn't
			// fit even right-aligned do we cap the width (then long lines scroll horizontally).
			var viewportW = window.innerWidth || document.documentElement.clientWidth;
			// Clear any prior caps/alignment so we measure the natural width fresh.
			host.classList.remove('ws-right');
			ws.dropdown.style.maxWidth = '';
			var natural = Math.max(ws.dropdown.scrollWidth, rect.width);
			var spaceRightOfLeftEdge = viewportW - rect.left - margin;   // room if left-aligned to trigger
			var spaceLeftOfRightEdge = rect.right - margin;              // room if right-aligned to trigger
			if (natural <= spaceRightOfLeftEdge) {
				// Fits opening left-aligned - nothing to do.
			} else if (natural <= spaceLeftOfRightEdge) {
				host.classList.add('ws-right');                          // flip to right-align, it fits
			} else {
				// Too wide for either side: take the roomier side and cap (horizontal scroll for the rest).
				if (spaceLeftOfRightEdge > spaceRightOfLeftEdge) {
					host.classList.add('ws-right');
					ws.dropdown.style.maxWidth = Math.max(rect.width, spaceLeftOfRightEdge) + 'px';
				} else {
					ws.dropdown.style.maxWidth = Math.max(rect.width, spaceRightOfLeftEdge) + 'px';
				}
			}
		}

		function close(host) {
			host._ws.dropdown.hidden = true;
			host.classList.remove('ws-open');
			host.classList.remove('ws-up');
			host.classList.remove('ws-right');
			// Clear measured sizing so the next open re-measures from scratch (e.g. after a resize).
			host._ws.dropdown.style.maxHeight = '';
			host._ws.dropdown.style.maxWidth = '';
		}

		function wireEvents(host) {
			var ws = host._ws;
			ws.trigger.addEventListener('click', function () {
				if (host.classList.contains('ws-open')) close(host); else open(host);
			});
			ws.trigger.addEventListener('keydown', function (e) {
				// Enter on the (closed) trigger submits the enclosing form, like Enter in a normal
				// field would. The trigger is a div (not a form control), so it won't submit on its
				// own - we trigger the form's submit explicitly. Enter is NOT an opener (open via
				// Space / ArrowDown / ArrowUp / typing instead).
				if (e.key === 'Enter') {
					e.preventDefault();
					var form = ws.select && ws.select.form;
					if (form) {
						// Submit the enclosing form the way Enter in a normal text field does: through
						// the form's default (first) submit button, so the right WO action fires (WO
						// needs the submit button as the "sender"). Fall back to a plain submit if the
						// form has no button.
						var submitBtn = form.querySelector('input[type=submit], button[type=submit], button:not([type])');
						if (submitBtn) {
							submitBtn.click();
						} else if (form.requestSubmit) {
							form.requestSubmit();
						} else {
							form.submit();
						}
					}
					return;
				}
				// Backspace / Delete on the focused (closed) trigger clears a selection - the keyboard
				// complement of the mouse gestures (the per-tag × button / the "clear" option). In
				// multi-select it peels off the LAST tag, one per press (the conventional tag-input
				// gesture); in single-select it clears back to the placeholder. No-op when nothing is
				// selected, and we don't hijack these keys while the dropdown is open (the search box
				// owns them there).
				if ((e.key === 'Backspace' || e.key === 'Delete') && !host.classList.contains('ws-open')) {
					if (host._wsMultiple) {
						var selected = Array.prototype.filter.call(ws.select.options, function (o) { return o.selected && !isPlaceholderOption(o); });
						if (selected.length) {
							e.preventDefault();
							deselect(host, selected[selected.length - 1].value);
						}
					} else {
						var cur = ws.select.options[ws.select.selectedIndex];
						if (cur && !isPlaceholderOption(cur)) {
							e.preventDefault();
							clearSingle(host);
						}
					}
					return;
				}
				if (e.key === ' ' || e.key === 'ArrowDown' || e.key === 'ArrowUp') {
					e.preventDefault();
					open(host);
					return;
				}
				// Type-to-search: any printable character (not a modifier combo) opens the dropdown
				// and seeds the search with that character, so you can just focus the field and start
				// typing - the way Chosen behaved. e.key is a single char for printable keys.
				if (e.key && e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
					e.preventDefault();
					open(host, e.key);
				}
			});
			ws.search.addEventListener('input', function () { renderOptions(host, ws.search.value); });
			ws.search.addEventListener('keydown', function (e) {
				if (e.key === 'Escape') { close(host); ws.trigger.focus(); return; }
				if (e.key === 'ArrowDown') { e.preventDefault(); moveActive(host, 1); return; }
				if (e.key === 'ArrowUp') { e.preventDefault(); moveActive(host, -1); return; }
				if (e.key === 'Enter') {
					e.preventDefault();
					var active = activeOption(host);
					if (active) choose(host, active.getAttribute('data-value'));
					return;
				}
			});
			// Close on outside click. Guard against our OWN re-render: picking an option in
			// multi-select mode rebuilds the option <li>s, so by the time this bubbled click fires the
			// clicked <li> is detached from the document. A detached target means the click came from
			// inside our (re-rendered) list - not an outside click - so don't close on it.
			document.addEventListener('click', function (e) {
				if (e.target && !document.contains(e.target)) return;
				if (!host.contains(e.target)) close(host);
			});
		}

		function renderOptions(host, filter) {
			var ws = host._ws;
			var f = (filter || '').toLowerCase();
			ws.list.textContent = '';
			var anyShown = false;

			// If a real value is selected and there's a placeholder option, offer it as a "clear"
			// choice so the user can deselect back to nothing (Chosen/native behaviour). Single-select
			// only; multi-select clears by toggling its tags off.
			if (!host._wsMultiple) {
				var placeholder = Array.prototype.filter.call(ws.select.options, isPlaceholderOption)[0];
				var hasRealSelection = ws.select.selectedIndex >= 0 && !isPlaceholderOption(ws.select.options[ws.select.selectedIndex]);
				if (placeholder && hasRealSelection) {
					var label0 = placeholder.textContent;
					if (!f || label0.toLowerCase().indexOf(f) !== -1) {
						anyShown = true;
						var clearLi = h('li', { class: 'ws-option ws-clear', 'data-value': placeholder.value, text: label0 });
						clearLi.addEventListener('click', function () { choose(host, placeholder.value); });
						clearLi.addEventListener('mousemove', function () { setActive(host, clearLi); });
						ws.list.appendChild(clearLi);
					}
				}
			}

			Array.prototype.forEach.call(ws.select.options, function (opt) {
				if (isPlaceholderOption(opt) && !host._wsMultiple) return; // placeholder handled above as the clear option
				var label = opt.textContent;
				if (f && label.toLowerCase().indexOf(f) === -1) return;
				anyShown = true;
				var li = h('li', { class: 'ws-option', 'data-value': opt.value, text: label });
				if (isSelected(ws.select, opt.value)) li.classList.add('ws-selected');
				li.addEventListener('click', function () { choose(host, opt.value); });
				// Hovering moves the keyboard highlight, so mouse and keyboard agree.
				li.addEventListener('mousemove', function () { setActive(host, li); });
				ws.list.appendChild(li);
			});
			if (!anyShown) {
				ws.list.appendChild(h('li', { class: 'ws-option ws-disabled', text: host._wsNoResults }));
			}
			// Highlight a sensible starting option: the selected one if visible, else the first.
			var opts = selectableOptions(host);
			var preferred = opts.filter(function (li) { return li.classList.contains('ws-selected'); })[0] || opts[0];
			setActive(host, preferred || null);
		}

		// All highlightable (non-disabled) option <li>s currently rendered.
		function selectableOptions(host) {
			return Array.prototype.slice.call(host._ws.list.querySelectorAll('.ws-option:not(.ws-disabled)'));
		}

		// The currently keyboard-highlighted option, if any.
		function activeOption(host) {
			return host._ws.list.querySelector('.ws-option.ws-active');
		}

		function setActive(host, li) {
			var prev = activeOption(host);
			if (prev) prev.classList.remove('ws-active');
			if (li) {
				li.classList.add('ws-active');
				// Keep the highlighted item in view as you arrow past the visible edges.
				if (li.scrollIntoView) li.scrollIntoView({ block: 'nearest' });
			}
		}

		// Move the highlight by dir (+1 down / -1 up), wrapping at the ends.
		function moveActive(host, dir) {
			var opts = selectableOptions(host);
			if (!opts.length) return;
			var current = activeOption(host);
			var idx = current ? opts.indexOf(current) : -1;
			idx = (idx + dir + opts.length) % opts.length;
			setActive(host, opts[idx]);
		}

		function isSelected(select, value) {
			return Array.prototype.some.call(select.options, function (o) { return o.value === value && o.selected; });
		}

		function choose(host, value) {
			var select = host._ws.select;
			if (host._wsMultiple) {
				Array.prototype.forEach.call(select.options, function (o) {
					if (o.value === value) o.selected = !o.selected;
				});
				// After a pick, clear the search and close the list (then focus the trigger), so each
				// selection is a discrete pick rather than leaving a stale filter in an open dropdown.
				host._ws.search.value = '';
				close(host);
				host._ws.trigger.focus();
			} else {
				select.value = value;
				close(host);
				host._ws.trigger.focus();
			}
			commit(host);
		}

		// Turn a single option OFF (multi-select) and commit, keeping the trigger focused. Used by the
		// per-tag × button and by Backspace-removes-last-tag - neither needs the open/close dance, since
		// the dropdown is already closed (or the click came from a tag in the closed trigger).
		function deselect(host, value) {
			var select = host._ws.select;
			Array.prototype.forEach.call(select.options, function (o) {
				if (o.value === value) o.selected = false;
			});
			host._ws.trigger.focus();
			commit(host);
		}

		// Clear a single-select back to its placeholder (no-selection) value and commit, keeping the
		// trigger focused. The keyboard equivalent of clicking the "clear" option in the dropdown.
		function clearSingle(host) {
			var select = host._ws.select;
			var placeholder = Array.prototype.filter.call(select.options, isPlaceholderOption)[0];
			// Prefer the explicit placeholder option's value (e.g. WONoSelectionString) so it round-trips
			// to the server exactly as the click-to-clear path does; fall back to clearing selectedIndex.
			if (placeholder) select.value = placeholder.value;
			else select.selectedIndex = -1;
			host._ws.trigger.focus();
			commit(host);
		}

		// Shared tail for any selection change: refresh the trigger label, arm post-morph focus
		// restoration, and fire the native 'change' so AjaxObserveField (and any other listener) reacts.
		function commit(host) {
			var select = host._ws.select;
			syncDisplay(host);
			// The change below may trigger an Ajax morph of the container this widget lives in. That
			// morph can re-create our (client-injected) wrapper, dropping focus to <body>. Arm the
			// post-morph resync to restore focus - keyed on the SELECT'S ID (stable, server-rendered)
			// rather than the host object, since the host instance may be replaced by the morph.
			WonderSelect._refocus = { selectId: select.id, at: Date.now() };
			select.dispatchEvent(new Event('change', { bubbles: true }));
		}

		// Reflect the select's current value(s) into the trigger label. Safe to call any time, so a
		// morph that changes the selected option is picked up just by re-syncing.
		function syncDisplay(host) {
			var ws = host._ws;
			if (!ws) return;
			var select = ws.select;
			ws.trigger.textContent = '';
			if (host._wsMultiple) {
				var chosen = Array.prototype.filter.call(select.options, function (o) { return o.selected && !isPlaceholderOption(o); });
				if (!chosen.length) {
					ws.trigger.appendChild(h('span', { class: 'ws-placeholder', text: placeholderFor(select) }));
				} else {
					chosen.forEach(function (o) {
						var tag = h('span', { class: 'ws-tag', 'data-value': o.value });
						tag.appendChild(document.createTextNode(o.textContent));
						var x = h('button', { class: 'ws-tag-remove', type: 'button', 'aria-label': 'Remove' });
						x.textContent = '×';
						x.addEventListener('click', function (e) { e.stopPropagation(); deselect(host, o.value); });
						tag.appendChild(x);
						ws.trigger.appendChild(tag);
					});
				}
			} else {
				var sel = select.options[select.selectedIndex];
				if (sel && !isPlaceholderOption(sel)) {
					ws.trigger.appendChild(h('span', { class: 'ws-value', text: sel.textContent }));
				} else {
					ws.trigger.appendChild(h('span', { class: 'ws-placeholder', text: placeholderFor(select) }));
				}
			}
		}

		// Enhance one select: wrap it in a <wonder-select> host and build the widget.
		function enhance(select) {
			if (select.closest('wonder-select')) return;
			var host = document.createElement('wonder-select');
			select.parentNode.insertBefore(host, select);
			host.appendChild(select);
			mount(host);
		}

		// Enhance every ajax-popup-button under root, and re-sync any already-mounted hosts.
		function enhanceAll(root) {
			(root || document).querySelectorAll('select.ajax-popup-button').forEach(enhance);
			(root || document).querySelectorAll('wonder-select.ws-host').forEach(syncDisplay);
		}

		// Fully automatic upkeep - NO app involvement (no onRefreshComplete hook). A single
		// document-level observer reacts to whatever the DOM does, including Ajax morphs:
		//   - a newly added <select class="ajax-popup-button"> (e.g. a row a morph revealed) is enhanced
		//   - when a morph reconciles an already-enhanced <select> (its selected option / options
		//     change server-side, set programmatically so no 'change' fires), its host is re-synced
		// This is what makes wonder-select morph-native: the widget keeps itself correct on its own.
		function startAutoObserver() {
			if (WonderSelect._observing || !window.MutationObserver) return;
			WonderSelect._observing = true;

			var resync = (function () {
				// Coalesce bursts of mutations from a single morph into one pass.
				var queued = false;
				return function () {
					if (queued) return;
					queued = true;
					(window.requestAnimationFrame || window.setTimeout)(function () {
						queued = false;
						document.querySelectorAll('select.ajax-popup-button').forEach(enhance);
						document.querySelectorAll('wonder-select.ws-host').forEach(syncDisplay);
						// Restore focus dropped by a morph that reconciled around our wrapper. The morph
						// that drops focus arrives a network round-trip AFTER the pick, so earlier
						// (pre-morph) resyncs run while focus is still fine - we must NOT clear the arm
						// then. We only act when focus has actually fallen to <body>, and only restore
						// to the host the user just picked from (set by choose, with a short deadline so
						// a stale arm can't later steal focus the user moved elsewhere).
						var arm = WonderSelect._refocus;
						if (arm && Date.now() - arm.at < 4000) {
							var active = document.activeElement;
							if (arm.selectId && (!active || active === document.body)) {
								// Re-find the (possibly re-created) host by the select's stable id.
								var sel = document.getElementById(arm.selectId);
								var host2 = sel && sel.closest && sel.closest('wonder-select');
								if (host2 && host2._ws) {
									host2._ws.trigger.focus();
									WonderSelect._refocus = null;
								}
							}
						}
						else {
							WonderSelect._refocus = null;
						}
					}, 0);
				};
			})();

			// Ignore mutations that wonder-select itself causes (building the trigger/dropdown,
			// re-rendering the label), otherwise reacting to our own DOM writes would loop.
			function isOurOwnDom(node) {
				var el = node && (node.nodeType === 1 ? node : node.parentElement);
				return !!(el && el.closest && el.closest('.ws-trigger, .ws-dropdown'));
			}

			new MutationObserver(function (mutations) {
				for (var i = 0; i < mutations.length; i++) {
					var m = mutations[i];
					if (isOurOwnDom(m.target)) continue;
					// A select being added (e.g. a row a morph revealed) needs enhancing; an option's
					// selected/value/text changing (morph reconciling a select) needs a re-sync.
					if (m.type === 'childList' && m.addedNodes.length) {
						for (var j = 0; j < m.addedNodes.length; j++) {
							var n = m.addedNodes[j];
							if (n.nodeType === 1 && (n.matches && n.matches('select.ajax-popup-button') || n.querySelector && n.querySelector('select.ajax-popup-button'))) { resync(); return; }
						}
						continue;
					}
					if ((m.type === 'attributes' || m.type === 'characterData') && m.target) {
						var nn = m.target.nodeName;
						if (nn === 'OPTION' || nn === 'SELECT') { resync(); return; }
					}
				}
			}).observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['selected', 'value'], characterData: true });
		}

		return { define: define, mount: mount, enhance: enhance, enhanceAll: enhanceAll, syncDisplay: syncDisplay, startAutoObserver: startAutoObserver };
	})();

	window.WonderSelect = WonderSelect;
	WonderSelect.define();

	function boot() {
		WonderSelect.enhanceAll(document);
		WonderSelect.startAutoObserver();
	}
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', boot);
	} else {
		boot();
	}
})();
