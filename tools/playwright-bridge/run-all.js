// Serial regression runner for the whole examples/ suite (or a filtered subset).
//
// WHY THIS EXISTS: the AjaxPlayground is a single WebObjects app instance, which serialises
// requests. Driving it from TWO browsers at once (e.g. a naive `for f in examples/*; do node
// run.js $f & done`) makes scenarios intermittently time out or read stale state - they show up
// as flaky "errors" that are really just contention. This runner launches ONE browser and runs
// every scenario one-at-a-time, so results are deterministic. Always use this for the suite;
// reserve `node run.js <one.json>` for iterating on a single scenario.
//
// Usage:
//   node run-all.js                 # run every examples/*.json against the discovered port
//   node run-all.js multi           # run only scenarios whose filename contains "multi"
//   PORT=1200 node run-all.js        # pin the port instead of auto-discovering
//
// Exit code is non-zero if ANY scenario has a failure (or errors), so CI / an agent can branch on it.

import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { execSync } from 'node:child_process';
import { chromium } from 'playwright';
import { runScript } from './src/commands.js';

const here = dirname(fileURLToPath(import.meta.url));
const examplesDir = join(here, 'examples');

// --- port discovery -------------------------------------------------------------------------
// Prefer an explicit PORT env; otherwise PROBE candidate ports for a live AjaxPlayground (its Main
// page carries the title "Ajax Playground"). Probing is robust to how the app was launched - the
// WO process command line does NOT reliably carry the app name or a -WOPort flag (the port can come
// from a Properties file), so parsing `ps` is unreliable. Fail loudly with guidance if none answer.
const PROBE_PORTS = [1200, 1201, 1202, 1300, 8080];

function isPlaygroundOn(port) {
	try {
		const url = `http://localhost:${port}/cgi-bin/WebObjects/AjaxPlayground.woa/wa/page?name=Main`;
		// -s silent, -m 4 timeout; grep the title out. Empty (curl fail / wrong app) => not it.
		const out = execSync(`curl -s -m 4 ${JSON.stringify(url)} 2>/dev/null`, { encoding: 'utf8' });
		return /Ajax Playground/.test(out);
	} catch {
		return false;
	}
}

function discoverPort() {
	if (process.env.PORT) {
		if (!isPlaygroundOn(process.env.PORT)) {
			throw new Error(`PORT=${process.env.PORT} is set but no AjaxPlayground answered there.`);
		}
		return process.env.PORT;
	}
	for (const port of PROBE_PORTS) {
		if (isPlaygroundOn(port)) {
			return String(port);
		}
	}
	throw new Error(
		`Could not find a running AjaxPlayground on any of: ${PROBE_PORTS.join(', ')}.\n` +
		'Start the app, or pass the port explicitly: PORT=<port> node run-all.js');
}

// --- scenario selection ---------------------------------------------------------------------
const filter = process.argv[2];
const files = readdirSync(examplesDir)
	.filter((f) => f.endsWith('.json'))
	.filter((f) => !filter || f.includes(filter))
	.sort();

if (files.length === 0) {
	console.error(filter ? `No examples match "${filter}".` : 'No examples found.');
	process.exit(2);
}

function withPort(raw, port) {
	// Examples use a <PORT> placeholder; also tolerate any stray localhost:NNNN already baked in.
	return raw.replace(/<PORT>/g, port).replace(/localhost:\d+/g, `localhost:${port}`);
}

async function main() {
	const port = discoverPort();
	console.log(`AjaxPlayground port: ${port}\nRunning ${files.length} scenario(s) serially...\n`);

	const browser = await chromium.launch({ headless: true });
	const summary = [];
	let anyFailed = false;

	try {
		for (const file of files) {
			const name = file.replace(/\.json$/, '');
			let result, errored = null;
			const script = JSON.parse(withPort(readFileSync(join(examplesDir, file), 'utf8'), port));

			// Fresh context per scenario: no cookie/session bleed between scenarios that mutate
			// server-side state (counters, periodic refresh). A fresh WO session each time keeps them
			// independent.
			const context = await browser.newContext(script.viewport ? { viewport: script.viewport } : {});
			const page = await context.newPage();
			try {
				result = await runScript(page, script);
			} catch (e) {
				errored = String(e && e.stack || e);
			} finally {
				await context.close();
			}

			if (errored) {
				anyFailed = true;
				summary.push({ name, status: 'ERROR', detail: errored.split('\n')[0] });
				console.log(`  ✗ ${name}  ERROR: ${errored.split('\n')[0]}`);
				continue;
			}

			const failures = result.failures || [];
			const asserts = result.steps.filter((s) => s.assert);
			const passed = asserts.length - failures.length;

			// A scenario may deliberately trigger page errors (e.g. `scripts` throws mid-batch to prove
			// one bad inline script does not abort the others). It declares them via `allowPageErrors`:
			// `true` to allow any, or an array of substrings - a page error is tolerated if it matches one.
			const allow = script.allowPageErrors;
			const isAllowed = (text) => allow === true || (Array.isArray(allow) && allow.some((s) => text.includes(s)));
			const pageerrors = (result.console || [])
				.filter((c) => c.level === 'pageerror')
				.filter((c) => !isAllowed(c.text));

			// An assertion-free scenario passes vacuously, which can hide a scenario that silently stopped
			// doing anything. Surface it as a warning rather than a confident green.
			const vacuous = asserts.length === 0;

			if (failures.length === 0 && pageerrors.length === 0) {
				const note = vacuous ? '0 assertions - WARN: nothing asserted' : `${asserts.length} assertion(s)`;
				summary.push({ name, status: vacuous ? 'WARN' : 'PASS', detail: note });
				console.log(`  ${vacuous ? '!' : '✓'} ${name}  (${note})`);
			} else {
				anyFailed = true;
				summary.push({ name, status: 'FAIL', detail: `${passed}/${asserts.length} passed` });
				console.log(`  ✗ ${name}  (${passed}/${asserts.length} assertion(s) passed)`);
				for (const f of failures) {
					console.log(`      - ${f.name || f.tag}: ${f.detail}`);
				}
				for (const e of pageerrors) {
					console.log(`      - unexpected page error: ${e.text}`);
				}
			}
		}
	} finally {
		await browser.close();
	}

	const pass = summary.filter((s) => s.status === 'PASS').length;
	console.log(`\n${pass}/${summary.length} scenario(s) passed.`);
	process.exit(anyFailed ? 1 : 0);
}

main().catch((err) => {
	console.error('run-all error:', err && err.stack || err);
	process.exit(2);
});
