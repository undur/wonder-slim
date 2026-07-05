// ===========================================================================
// AjaxSlim.Sortable - reusable drag-to-reorder for a list of rows.
// ===========================================================================
// A dependency-free, morph-safe sortable. The reorderable rows and their drag grips are MARKED with
// data attributes (not described by CSS selectors in a binding) - matching AjaxSlim's data-morph-*
// marker idiom and keeping the container's internal DOM structure out of the server template:
//
//   data-sortable-item   on each draggable row (need not be a direct child of the container)
//   data-sortable-grip   on the grab handle inside a row (a drag only starts from a grip, so the
//                        row's own controls - inputs, links - don't initiate one)
//
// On drop it reports the move to the server as two integers - the dragged row's ORIGINAL index and its
// FINAL index in the list - which the server applies with list.add(newIndex, list.remove(oldIndex)).
// No per-row id binding: the contract is purely positional, the existing move-up/move-down generalized
// to an arbitrary distance. The live drag is optimistic (rows reorder under the pointer); the
// authoritative server response morphs the list back in and makes it real.
//
// Declarative usage (the AjaxSortable WO element renders the registration; no hand-written JS):
//   <div id="myList">
//     ...rows, each:  <tr data-sortable-item> <td data-sortable-grip>::</td> ... </tr>
//   </div>
//   AjaxSlim.Sortable.register('myList', { url: '<ajax action url>', update: 'myList;...' });
//
// On drop it fires  url?_oldIndex=2&_newIndex=0  via AUL.update against `update`, so the authoritative
// server response morphs the list back in. (A data-sortable-fn / 'fn' option naming a JS function to
// call with { _oldIndex, _newIndex } is also honoured, for callers wiring their own reorder function.)
//
// Interaction with morphing: while a drag is in flight the list is marked data-morph-ignore so a
// background poll / overlapping update can't yank the DOM out from under the pointer. The mark is
// removed on drop (and the drop's own morph then reconciles the authoritative order).
(function (window, document) {
	'use strict';

	var AjaxSlim = window.AjaxSlim || (window.AjaxSlim = {});

	// Per-container config. We read it from data-* attributes when present, but also accept it via the
	// register(id, options) call - because some server containers (e.g. AjaxUpdateContainer) only emit
	// the bindings they know about and drop arbitrary data-* attributes. Options passed to register win.
	function cfg(container, key) {
		var opts = container._ajaxslimSortableOpts;
		if (opts && opts[key] != null) {
			return opts[key];
		}
		return container.getAttribute('data-sortable-' + key);
	}

	// The reorderable rows are MARKED, not selected: each carries a data-sortable-item attribute. This
	// keeps the container's internal DOM structure out of the server bindings - the app tags what is
	// draggable (matching AjaxSlim's data-morph-* marker idiom) instead of describing a CSS path to it.
	// They need not be direct children (e.g. <tr> inside <div> > <table> > <tbody>); we collect them all
	// in document order, which is their visual order.
	function itemsOf(container) {
		var nodes = container.querySelectorAll('[data-sortable-item]');
		var out = [];
		for (var i = 0; i < nodes.length; i++) {
			out.push(nodes[i]);
		}
		return out;
	}

	// The row moved when you grab a grip: the nearest ancestor of the grip carrying data-sortable-item.
	function itemForGrip(container, grip) {
		var node = grip;
		while (node && node !== container.parentNode) {
			if (node.nodeType === 1 && node.hasAttribute && node.hasAttribute('data-sortable-item')) {
				return node;
			}
			node = node.parentNode;
		}
		return null;
	}

	function indexOfItem(container, item) {
		var items = itemsOf(container);
		for (var i = 0; i < items.length; i++) {
			if (items[i] === item) {
				return i;
			}
		}
		return -1;
	}

	// The item currently under the pointer's Y, among the container's items (excluding the dragged one).
	// We compare against each item's vertical midpoint so the drop lands above/below as expected.
	function itemUnderPointer(container, dragged, clientY) {
		var items = itemsOf(container);
		for (var i = 0; i < items.length; i++) {
			var it = items[i];
			if (it === dragged) {
				continue;
			}
			var r = it.getBoundingClientRect();
			if (clientY < r.top + r.height / 2) {
				return it;
			}
		}
		return null; // past the last item -> append at end
	}

	// The drop animation duration (ms) for the FLIP slide of the rows that shift to make room. Read from
	// the --ajaxslim-drag-flip custom property so an app can tune or disable it (0 = no animation).
	function flipDurationMs(container) {
		var raw = getComputedStyle(container).getPropertyValue('--ajaxslim-drag-flip').trim();
		var n = parseFloat(raw);
		return isFinite(n) ? n : 150;
	}

	// FLIP (First-Last-Invert-Play): the dragged row is reordered in the DOM by onMove; the OTHER rows
	// jump to their new slots instantly. To make them SLIDE instead, we snapshot each row's top BEFORE a
	// reorder (first), let the reorder happen (last), then for each row whose position changed we set an
	// inverse translateY with no transition and, next frame, transition it back to 0 - so it animates
	// from where it was to where it now is. The dragged item is excluded (it's positioned by the cursor).
	// Pure transform, so it works on <tr> table rows (which can't be positioned) as well as div/li lists.
	function firstRects(items, dragged) {
		var map = new Map();
		for (var i = 0; i < items.length; i++) {
			if (items[i] !== dragged) {
				map.set(items[i], items[i].getBoundingClientRect().top);
			}
		}
		return map;
	}

	function playFlip(container, items, dragged, firstTops, durationMs) {
		if (!durationMs) {
			return;
		}
		for (var i = 0; i < items.length; i++) {
			var el = items[i];
			if (el === dragged) {
				continue;
			}
			var prevTop = firstTops.get(el);
			if (prevTop == null) {
				continue;
			}
			var delta = prevTop - el.getBoundingClientRect().top;
			if (!delta) {
				continue;
			}
			el.style.transition = 'none';
			el.style.transform = 'translateY(' + delta + 'px)';
		}
		// Next frame: clear the inverse transform WITH a transition, so each shifted row slides home.
		requestAnimationFrame(function () {
			for (var j = 0; j < items.length; j++) {
				var e = items[j];
				if (e === dragged || firstTops.get(e) == null) {
					continue;
				}
				e.style.transition = 'transform ' + durationMs + 'ms ease';
				e.style.transform = '';
			}
		});
	}

	// Clear any transform/transition we parked on the non-dragged rows (on drop, or when degrading).
	function clearFlip(items, dragged) {
		for (var i = 0; i < items.length; i++) {
			if (items[i] !== dragged) {
				items[i].style.transition = '';
				items[i].style.transform = '';
			}
		}
	}

	// One live drag. Pointer-events based so it works for mouse and touch uniformly.
	//
	// The dragged row FOLLOWS the cursor (a translateY tracking the pointer, so it isn't snapped to its
	// grid slot), while the other rows SLIDE (FLIP) to open a gap where it will land - previewing the
	// result before the drop. Both effects are pure transforms and degrade safely: if anything in the
	// enhancement throws, the drag still reorders the DOM (today's snap behaviour), so the move is never
	// lost. The transform tracking can be disabled wholesale with --ajaxslim-drag-follow: none.
	function beginDrag(container, item, startEvent) {
		startEvent.preventDefault();
		var startIndex = indexOfItem(container, item);
		var moved = false;

		var startY = startEvent.touches ? startEvent.touches[0].clientY : startEvent.clientY;
		var follow = getComputedStyle(container).getPropertyValue('--ajaxslim-drag-follow').trim() !== 'none';
		var flipMs = flipDurationMs(container);
		// As the dragged row is reordered into a new DOM slot, its own laid-out position jumps; we track
		// the cumulative jump so the follow-cursor transform stays anchored to the pointer, not the slot.
		var slotShift = 0;

		container.setAttribute('data-morph-ignore', '');      // freeze morphs during the drag
		item.classList.add('ajaxslim-dragging');
		document.documentElement.classList.add('ajaxslim-sorting');

		function track(clientY) {
			if (follow) {
				try { item.style.transform = 'translateY(' + ((clientY - startY) - slotShift) + 'px)'; }
				catch (e) { follow = false; }
			}
		}

		function onMove(ev) {
			var clientY = ev.touches ? ev.touches[0].clientY : ev.clientY;
			var before = itemUnderPointer(container, item, clientY);
			var parent = item.parentNode;
			var willMove = (before == null) ? (item.nextSibling !== null) : (before !== item.nextSibling);

			if (willMove) {
				var items = itemsOf(container);
				var firstTops = firstRects(items, item);          // FLIP: measure before
				var ownTopBefore = item.getBoundingClientRect().top;
				if (before == null) { parent.appendChild(item); }
				else { parent.insertBefore(item, before); }
				moved = true;
				// the dragged row's own slot moved by this much; fold it into the follow offset so it
				// doesn't visually jump when it changes DOM position.
				slotShift += item.getBoundingClientRect().top - ownTopBefore;
				playFlip(container, itemsOf(container), item, firstTops, flipMs);  // FLIP: invert + play
			}
			track(clientY);
		}

		function onUp() {
			document.removeEventListener('mousemove', onMove, true);
			document.removeEventListener('mouseup', onUp, true);
			document.removeEventListener('touchmove', onMove, { passive: false });
			document.removeEventListener('touchend', onUp, true);
			document.removeEventListener('touchcancel', onUp, true);
			item.classList.remove('ajaxslim-dragging');
			document.documentElement.classList.remove('ajaxslim-sorting');
			// drop all the transient transforms BEFORE the authoritative morph lands, so the server's
			// reconciled order doesn't double-animate against our FLIP/follow transforms.
			item.style.transition = '';
			item.style.transform = '';
			clearFlip(itemsOf(container), item);
			container.removeAttribute('data-morph-ignore');

			var endIndex = indexOfItem(container, item);
			if (!moved || endIndex === startIndex || endIndex < 0) {
				return; // no net move - nothing to tell the server
			}
			fireReorder(container, startIndex, endIndex);
		}

		document.addEventListener('mousemove', onMove, true);
		document.addEventListener('mouseup', onUp, true);
		document.addEventListener('touchmove', onMove, { passive: false });
		document.addEventListener('touchend', onUp, true);
		// touchcancel: the system can end a touch drag without a touchend (incoming call, OS gesture,
		// tab switch). Without this, onUp never runs - the document listeners stay attached and, worse,
		// the container keeps data-morph-ignore, silently freezing every later update of the list.
		document.addEventListener('touchcancel', onUp, true);
	}

	// Tell the server: item moved from oldIndex to newIndex. Preferred path is a named function (rendered
	// by an AjaxUpdateLink functionName=...), which owns the url + target + morph; we just pass the move.
	// Fallback path fires data-sortable-url directly via AUL.update for callers not using a link.
	function fireReorder(container, oldIndex, newIndex) {
		var fnName = cfg(container, 'fn');
		if (fnName && typeof window[fnName] === 'function') {
			window[fnName]({ _oldIndex: oldIndex, _newIndex: newIndex });
			return;
		}
		var url = cfg(container, 'url');
		if (url) {
			var update = cfg(container, 'update') || container.id;
			var sep = url.indexOf('?') === -1 ? '?' : '&';
			AjaxSlim.AUL.update(update, url + sep + '_oldIndex=' + oldIndex + '&_newIndex=' + newIndex, {});
			return;
		}
		if (window.console) {
			console.warn('AjaxSlim.Sortable: no data-sortable-fn or data-sortable-url on "' + container.id + '"');
		}
	}

	var Sortable = {
		// Make container[id] sortable. Idempotent across morphs: we bind ONE delegated mousedown/
		// touchstart on the container and flag it, so re-registration after a morph is a no-op. The
		// container is preserved across morphs (it's the update target), so the flag survives with it.
		register: function (id, options) {
			var container = (id && id.nodeType === 1) ? id : document.getElementById(id);
			if (container == null) {
				if (window.console) { console.warn('AjaxSlim.Sortable.register: no element "' + id + '"'); }
				return;
			}
			// Stash options for cfg() (covers containers that drop arbitrary data-* attributes). We do this
			// even on re-registration so a morph that re-runs register() keeps the config current.
			if (options) {
				container._ajaxslimSortableOpts = options;
			}
			if (container._ajaxslimSortableBound) {
				return;
			}
			container._ajaxslimSortableBound = true;

			function onStart(ev) {
				// Primary button only (mouse). A drag must start on a GRIP - an element marked
				// data-sortable-grip, or a descendant of one - so clicks on the row's own controls
				// (inputs, links) don't start a drag. Walk up from the event target to find the grip.
				if (ev.button != null && ev.button !== 0 && !ev.touches) {
					return;
				}
				var grip = gripFor(container, ev.target);
				if (!grip) {
					return;
				}
				var item = itemForGrip(container, grip);
				if (item) {
					beginDrag(container, item, ev);
				}
			}

			container.addEventListener('mousedown', onStart);
			container.addEventListener('touchstart', onStart, { passive: false });
		}
	};

	// The grip the pointer went down on: the nearest ancestor of the event target (within the container)
	// carrying data-sortable-grip. Returns null if the press wasn't on a grip.
	function gripFor(container, target) {
		var node = target;
		while (node && node !== container.parentNode) {
			if (node.nodeType === 1 && node.hasAttribute && node.hasAttribute('data-sortable-grip')) {
				return node;
			}
			node = node.parentNode;
		}
		return null;
	}

	AjaxSlim.Sortable = AjaxSlim.Sortable || Sortable;

})(window, document);
