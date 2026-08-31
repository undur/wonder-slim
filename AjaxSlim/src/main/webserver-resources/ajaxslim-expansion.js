/*
 * ajaxslim-expansion.js - client behaviour for AjaxExpansion, built on the native <details> element.
 *
 * The platform does the disclosure; this script's only job is keeping the SERVER's notion of the
 * expansion state truthful. A <details> toggle is pure client state - without this, a morph of an
 * enclosing region would reconcile an open section against the server's collapsed markup and snap
 * it shut mid-use. On every toggle we ping the section's component action with the new state; every
 * subsequent render, morphs included, then carries the right `open` attribute.
 *
 * The ping is idempotent (it states the new value, not "flip"), fire-and-forget (an empty ajax
 * response), and event delegation keeps it working across morphs with no per-element re-init.
 * Note the capture flag: 'toggle' does not bubble, so a document-level listener only sees it in
 * the capture phase.
 */
(function () {
	'use strict';

	document.addEventListener('toggle', function (e) {
		var details = e.target;
		if (!details || details.tagName !== 'DETAILS' || !details.hasAttribute('data-ajaxslim-expansion')) {
			return;
		}
		var url = details.getAttribute('data-ajaxslim-expansion');
		if (!url) {
			return;
		}
		url += (url.indexOf('?') === -1 ? '?' : '&') + 'expanded=' + (details.open ? 'true' : 'false');
		// x-requested-with marks the request as ajax server-side (ERXAjaxApplication.isAjaxRequest),
		// which is what keeps the content-less ping response out of the page cache.
		fetch(url, { headers: { 'x-requested-with': 'XMLHttpRequest' } });
	}, true);
})();
