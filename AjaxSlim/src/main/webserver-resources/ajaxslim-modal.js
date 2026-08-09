/*
 * ajaxslim-modal.js - client behaviour for AjaxModalContainer, built on the native <dialog> element.
 *
 * A trigger button carries data-ajaxslim-modal-open="<dialogId>"; clicking it opens that dialog with
 * showModal() (which gives a backdrop, focus trapping and Esc-to-close for free). The dialog's close
 * button is a <form method="dialog"> submit, which the platform closes natively - no JS needed for that.
 * A dialog marked data-ajaxslim-modal-openonload opens as soon as it is connected.
 *
 * Event delegation on document means this keeps working across morphs with no per-element re-init:
 * triggers and dialogs rendered into an updated container are handled by the same single listener.
 */
(function () {
	'use strict';

	function openDialog(dialog) {
		if (!dialog) {
			return;
		}
		// An OPEN dialog must be opted out of morphing entirely. showModal() state (the top-layer
		// promotion, ::backdrop, focus trap) hangs off the `open` attribute, which the server always
		// renders ABSENT (dialogs render closed) - so a morph of any enclosing region would reconcile
		// the live dialog against closed markup, strip `open`, and collapse the modal mid-use. While
		// open, the dialog owns its subtree (same ownership model as wonder-select); the mark comes
		// off on close so server-rendered content changes flow again from the next update on. Set
		// BEFORE opening so no morph can land in between.
		dialog.setAttribute('data-morph-ignore', '');
		// Marks the dialog as ours - light dismiss (below) only applies to marked dialogs.
		dialog.setAttribute('data-ajaxslim-modal', '');
		if (!dialog._ajaxslimCloseWired) {
			dialog._ajaxslimCloseWired = true;
			dialog.addEventListener('close', function () {
				dialog.removeAttribute('data-morph-ignore');
			});
		}
		if (typeof dialog.showModal === 'function') {
			if (!dialog.open) {
				dialog.showModal();
			}
		}
		else {
			// Very old browser without <dialog> support: degrade to just showing it.
			dialog.setAttribute('open', '');
		}
	}

	// One delegated click handler for every trigger, now and after any morph.
	document.addEventListener('click', function (e) {
		var trigger = e.target.closest ? e.target.closest('[data-ajaxslim-modal-open]') : null;
		if (!trigger) {
			return;
		}
		e.preventDefault();
		openDialog(document.getElementById(trigger.getAttribute('data-ajaxslim-modal-open')));
	});

	// Light dismiss: clicking the backdrop closes the dialog (a tiny close button alone is
	// irritating). Backdrop clicks report the <dialog> ELEMENT as their target (::backdrop can't
	// be an event target), and they are the only dialog-targeted clicks whose coordinates fall
	// OUTSIDE the dialog's box - a click in the dialog's own padding also targets the dialog but
	// lands inside the rect, and must not dismiss. Only dialogs opened by this script (marked in
	// openDialog) participate, so an app's own <dialog>s keep their behaviour.
	//
	// The pointerdown guard handles the classic dismissal trap: a drag that STARTS inside the
	// dialog (text selection, a slipped click) and releases over the backdrop fires the click on
	// the dialog with outside coordinates - dismissing would eat the user's selection gesture.
	// Only a press that itself began on the backdrop qualifies.
	function onBackdrop(dialog, e) {
		var r = dialog.getBoundingClientRect();
		return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
	}

	var pressBeganOnBackdrop = false;

	document.addEventListener('pointerdown', function (e) {
		var t = e.target;
		pressBeganOnBackdrop = !!(t && t.tagName === 'DIALOG' && t.open
				&& t.hasAttribute('data-ajaxslim-modal') && onBackdrop(t, e));
	});

	document.addEventListener('click', function (e) {
		var t = e.target;
		if (pressBeganOnBackdrop && t && t.tagName === 'DIALOG' && t.open
				&& t.hasAttribute('data-ajaxslim-modal') && onBackdrop(t, e)) {
			t.close();
		}
		pressBeganOnBackdrop = false;
	});

	// Open any dialog flagged to start open. Run on DOM ready and observe later insertions (morphs).
	function openMarked(root) {
		var dialogs = (root || document).querySelectorAll('dialog[data-ajaxslim-modal-openonload]');
		for (var i = 0; i < dialogs.length; i++) {
			var d = dialogs[i];
			if (!d._ajaxslimOpened) {
				d._ajaxslimOpened = true;
				openDialog(d);
			}
		}
	}

	function boot() {
		openMarked(document);
		if (window.MutationObserver && !window._ajaxslimModalObserving) {
			window._ajaxslimModalObserving = true;
			new MutationObserver(function (mutations) {
				for (var i = 0; i < mutations.length; i++) {
					var added = mutations[i].addedNodes;
					for (var j = 0; j < added.length; j++) {
						var n = added[j];
						if (n.nodeType === 1) {
							if (n.matches && n.matches('dialog[data-ajaxslim-modal-openonload]')) {
								openMarked(n.parentNode || document);
							}
							else if (n.querySelectorAll) {
								openMarked(n);
							}
						}
					}
				}
			}).observe(document.documentElement, { childList: true, subtree: true });
		}
	}

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', boot);
	}
	else {
		boot();
	}
})();
