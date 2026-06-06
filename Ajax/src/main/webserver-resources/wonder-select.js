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
			ws.search.focus();
			// Put the caret after the seeded character.
			if (initialSearch) {
				var len = ws.search.value.length;
				if (ws.search.setSelectionRange) ws.search.setSelectionRange(len, len);
			}
		}

		function close(host) {
			host._ws.dropdown.hidden = true;
			host.classList.remove('ws-open');
		}

		function wireEvents(host) {
			var ws = host._ws;
			ws.trigger.addEventListener('click', function () {
				if (host.classList.contains('ws-open')) close(host); else open(host);
			});
			ws.trigger.addEventListener('keydown', function (e) {
				if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown' || e.key === 'ArrowUp') {
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
			// Close on outside click.
			document.addEventListener('click', function (e) {
				if (!host.contains(e.target)) close(host);
			});
		}

		function renderOptions(host, filter) {
			var ws = host._ws;
			var f = (filter || '').toLowerCase();
			ws.list.textContent = '';
			var anyShown = false;
			Array.prototype.forEach.call(ws.select.options, function (opt) {
				if (isPlaceholderOption(opt) && !host._wsMultiple) return; // the placeholder isn't pickable
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
				renderOptions(host, host._ws.search.value);
			} else {
				select.value = value;
				close(host);
				host._ws.trigger.focus();
			}
			syncDisplay(host);
			// Native change so AjaxObserveField (and any other listener) reacts exactly as before.
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
						x.addEventListener('click', function (e) { e.stopPropagation(); choose(host, o.value); });
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
