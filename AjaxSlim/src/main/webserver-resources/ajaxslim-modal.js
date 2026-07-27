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
