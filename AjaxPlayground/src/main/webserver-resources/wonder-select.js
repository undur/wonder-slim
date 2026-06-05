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
 * <select class="chosen-select"> (drop-in for Chosen), and you can also author <wonder-select>
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

	function placeholderFor(select) {
		if (select.getAttribute('data-placeholder')) return select.getAttribute('data-placeholder');
		// A leading empty-value option is the common "noSelectionString" pattern.
		var first = select.options[0];
		if (first && first.value === '' ) return first.textContent.trim();
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

		function open(host) {
			var ws = host._ws;
			ws.dropdown.hidden = false;
			host.classList.add('ws-open');
			ws.search.value = '';
			renderOptions(host, '');
			ws.search.focus();
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
				if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') { e.preventDefault(); open(host); }
			});
			ws.search.addEventListener('input', function () { renderOptions(host, ws.search.value); });
			ws.search.addEventListener('keydown', function (e) {
				if (e.key === 'Escape') { close(host); ws.trigger.focus(); }
				if (e.key === 'Enter') {
					e.preventDefault();
					var firstOpt = ws.list.querySelector('.ws-option:not(.ws-disabled)');
					if (firstOpt) choose(host, firstOpt.getAttribute('data-value'));
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
				if (opt.value === '' && !host._wsMultiple) return; // the placeholder option isn't pickable
				var label = opt.textContent;
				if (f && label.toLowerCase().indexOf(f) === -1) return;
				anyShown = true;
				var li = h('li', { class: 'ws-option', 'data-value': opt.value, text: label });
				if (isSelected(ws.select, opt.value)) li.classList.add('ws-selected');
				li.addEventListener('click', function () { choose(host, opt.value); });
				ws.list.appendChild(li);
			});
			if (!anyShown) {
				ws.list.appendChild(h('li', { class: 'ws-option ws-disabled', text: host._wsNoResults }));
			}
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
				var chosen = Array.prototype.filter.call(select.options, function (o) { return o.selected && o.value !== ''; });
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
				if (sel && sel.value !== '') {
					ws.trigger.appendChild(h('span', { class: 'ws-value', text: sel.textContent }));
				} else {
					ws.trigger.appendChild(h('span', { class: 'ws-placeholder', text: placeholderFor(select) }));
				}
			}
		}

		// Auto-enhance any <select class="chosen-select"> by wrapping it in a <wonder-select> host.
		// Idempotent and morph-safe: a select already inside a wonder-select is skipped, and a
		// host that survives a morph is just re-synced.
		function enhanceAll(root) {
			(root || document).querySelectorAll('select.chosen-select').forEach(function (select) {
				if (select.closest('wonder-select')) return;
				var host = document.createElement('wonder-select');
				select.parentNode.insertBefore(host, select);
				host.appendChild(select);
				mount(host);
			});
			// Re-sync any already-mounted hosts (e.g. after a morph changed a selection server-side).
			(root || document).querySelectorAll('wonder-select.ws-host').forEach(syncDisplay);
		}

		return { define: define, mount: mount, enhanceAll: enhanceAll, syncDisplay: syncDisplay };
	})();

	window.WonderSelect = WonderSelect;
	WonderSelect.define();

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', function () { WonderSelect.enhanceAll(document); });
	} else {
		WonderSelect.enhanceAll(document);
	}
})();
