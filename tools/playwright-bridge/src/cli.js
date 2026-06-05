// CLI transport. Reads a run script (JSON file path as argv[2], or stdin), launches a headless
// browser, runs the script via the command core, and prints the result JSON to stdout.
//
// This is the ONLY part coupled to "how the work arrives". A future Parslips HTTP transport would
// replace this file with an endpoint that calls runScript(page, body) and returns the JSON - the
// command core (commands.js) is reused unchanged.

import { readFileSync } from 'node:fs';
import { chromium } from 'playwright';
import { runScript } from './commands.js';

async function readInput() {
	const path = process.argv[2];
	if (path) {
		return JSON.parse(readFileSync(path, 'utf8'));
	}
	// stdin fallback
	const chunks = [];
	for await (const chunk of process.stdin) chunks.push(chunk);
	return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

async function main() {
	const script = await readInput();
	const browser = await chromium.launch({ headless: true });
	try {
		const context = await browser.newContext();
		const page = await context.newPage();
		const result = await runScript(page, script);
		process.stdout.write(JSON.stringify(result, null, 2) + '\n');
		// Non-zero exit if any assertion failed, so callers can branch on it.
		process.exitCode = result.failures.length > 0 ? 1 : 0;
	} finally {
		await browser.close();
	}
}

main().catch((err) => {
	process.stderr.write('playwright-bridge error: ' + (err && err.stack || err) + '\n');
	process.exitCode = 2;
});
