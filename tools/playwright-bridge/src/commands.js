// Command core - transport-agnostic browser logic.
//
// Given a Playwright `page` and a run script ({ url, steps }), execute the steps and return a
// structured result (console messages, network requests, per-step reads, assertion failures).
// This module knows nothing about HOW the script arrived (CLI today, maybe an HTTP endpoint in
// Parslips later) - keep it that way so the transport can be swapped without touching this logic.

/**
 * Step vocabulary (each step is { do: "<name>", ... }):
 *   goto    { url? }                       - navigate (defaults to the run's url)
 *   click   { selector }                   - click an element
 *   fill    { selector, text }             - set an input's value (fires input/change)
 *   type    { selector, text, delay? }     - type key by key (more realistic; fires keydown/up)
 *   focus   { selector }                   - focus an element
 *   press   { selector?, key }             - press a key (on selector, or globally)
 *   wait    { ms? , selector? }            - wait for a timeout and/or a selector to appear
 *   read    { name, selector?, what }      - capture state; `what` in:
 *                                              value | text | attribute(:attr) | activeElementId |
 *                                              count | exists | requestCount
 *   assert  { name, that, equals|contains|atMost|atLeast } - check a prior read or a live value
 *   marker  { label }                      - tag subsequent requests with a label (for counting)
 */

export async function runScript(page, script) {
	const result = {
		url: script.url,
		console: [],
		requests: [],
		steps: [],
		failures: [],
	};

	let currentMarker = 'init';

	page.on('console', (msg) => {
		result.console.push({ level: msg.type(), text: msg.text() });
	});
	page.on('pageerror', (err) => {
		result.console.push({ level: 'pageerror', text: String(err && err.message || err) });
	});
	page.on('request', (req) => {
		result.requests.push({ marker: currentMarker, method: req.method(), url: req.url() });
	});

	// Reads are stored by name so later asserts can reference them.
	const reads = {};

	const requestsSince = (marker) => result.requests.filter((r) => r.marker === marker);

	for (let i = 0; i < (script.steps || []).length; i++) {
		const step = script.steps[i];
		const tag = `step[${i}] ${step.do}`;
		try {
			switch (step.do) {
				case 'goto':
					await page.goto(step.url || script.url, { waitUntil: 'networkidle' });
					break;
				case 'click':
					await page.click(step.selector);
					break;
				case 'fill':
					await page.fill(step.selector, step.text ?? '');
					break;
				case 'type':
					await page.type(step.selector, step.text ?? '', { delay: step.delay ?? 30 });
					break;
				case 'focus':
					await page.focus(step.selector);
					break;
				case 'press':
					if (step.selector) await page.press(step.selector, step.key);
					else await page.keyboard.press(step.key);
					break;
				case 'comment':
					// No-op; lets run scripts document themselves ({ "do": "comment", "text": "..." }).
					break;
				case 'wait':
					if (step.selector) await page.waitForSelector(step.selector, { timeout: step.ms ?? 5000 });
					if (step.ms && !step.selector) await page.waitForTimeout(step.ms);
					break;
				case 'marker':
					currentMarker = step.label;
					break;
				case 'read': {
					const v = await readValue(page, step, reads, requestsSince);
					reads[step.name] = v;
					result.steps.push({ tag, name: step.name, value: v });
					break;
				}
				case 'assert': {
					const ok = evaluateAssert(step, reads);
					if (!ok.pass) {
						result.failures.push({ tag, name: step.name, detail: ok.detail });
					}
					result.steps.push({ tag, name: step.name, assert: ok });
					break;
				}
				default:
					result.failures.push({ tag, detail: `unknown step '${step.do}'` });
			}
		} catch (err) {
			result.failures.push({ tag, detail: String(err && err.message || err) });
		}
	}

	return result;
}

async function readValue(page, step, reads, requestsSince) {
	switch (step.what) {
		case 'value':
			return await page.inputValue(step.selector);
		case 'text':
			return (await page.textContent(step.selector))?.trim();
		case 'attribute':
			return await page.getAttribute(step.selector, step.attr);
		case 'activeElementId':
			return await page.evaluate(() => document.activeElement && document.activeElement.id);
		case 'activeElementSelectionStart':
			return await page.evaluate(() => {
				const el = document.activeElement;
				return el && 'selectionStart' in el ? el.selectionStart : null;
			});
		case 'count':
			return await page.locator(step.selector).count();
		case 'exists':
			return (await page.locator(step.selector).count()) > 0;
		case 'requestCount':
			return requestsSince(step.marker).length;
		case 'withinViewport':
			// True if the element's bounding rect fits entirely within the viewport (small tolerance
			// for sub-pixel rounding). Used to assert a dropdown never extends beyond the viewport.
			return await page.evaluate((sel) => {
				var el = document.querySelector(sel);
				if (!el) return null;
				var r = el.getBoundingClientRect();
				var tol = 1;
				return r.top >= -tol && r.left >= -tol
					&& r.right <= (window.innerWidth || document.documentElement.clientWidth) + tol
					&& r.bottom <= (window.innerHeight || document.documentElement.clientHeight) + tol;
			}, step.selector);
		case 'boundingEdge':
			// A single edge of the element's rect (step.edge: top|left|right|bottom|width|height), so
			// overflow can be asserted numerically (e.g. right atMost viewport width).
			return await page.evaluate((arg) => {
				var el = document.querySelector(arg.sel);
				if (!el) return null;
				var r = el.getBoundingClientRect();
				return Math.round(r[arg.edge]);
			}, { sel: step.selector, edge: step.edge });
		default:
			throw new Error(`unknown read 'what': ${step.what}`);
	}
}

function evaluateAssert(step, reads) {
	const actual = step.that in reads ? reads[step.that] : undefined;
	if ('equals' in step) {
		return { pass: actual === step.equals, detail: `expected ${JSON.stringify(step.equals)}, got ${JSON.stringify(actual)}` };
	}
	if ('contains' in step) {
		return { pass: String(actual ?? '').includes(step.contains), detail: `expected to contain ${JSON.stringify(step.contains)}, got ${JSON.stringify(actual)}` };
	}
	if ('atMost' in step) {
		return { pass: Number(actual) <= step.atMost, detail: `expected <= ${step.atMost}, got ${actual}` };
	}
	if ('atLeast' in step) {
		return { pass: Number(actual) >= step.atLeast, detail: `expected >= ${step.atLeast}, got ${actual}` };
	}
	return { pass: false, detail: 'assert had no comparison (equals/contains/atMost/atLeast)' };
}
