// Repeated-request harness (raw HTTP, no browser): drives ScenarioReplay's plain component actions
// and re-sends BYTE-IDENTICAL requests - what a browser does on refresh-of-a-POST, a double-fired
// link, or backtrack-and-click-the-same-link.
//
// The repeated-request guard is gated on WOApplication.isPageRefreshOnBacktrackEnabled(), exactly
// like stock WO's _contextIDMatchingIDs (verified empirically against the pre-unification stack).
// So this harness asserts BOTH modes; pick with MODE:
//
//   MODE=replay (default) - app started normally (flag off): an identical repeat RE-EXECUTES the
//                           action. The classic WO behavior; idempotency is the app's business.
//   MODE=guard            - app started with -DWOPageRefreshOnBacktrackEnabled=true: an identical
//                           repeat is answered from the stored page WITHOUT re-invoking the action,
//                           while a genuinely new click (fresh render, fresh contextID) executes.
//
// e.g.  BASE=http://localhost:55432 MODE=guard node replay-guard.mjs

const BASE = process.env.BASE || 'http://localhost:55432';
const MODE = process.env.MODE === 'guard' ? 'guard' : 'replay';
const GUARDED = MODE === 'guard';
const PAGE_URL = `${BASE}/cgi-bin/WebObjects/AjaxPlayground.woa/wa/page?name=ScenarioReplay`;

let pass = 0, fail = 0; const fails = [];
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; fails.push(name); console.log(`  FAIL  ${name}  ${detail}`); }
}

let cookie = '';

async function req(url, options = {}) {
  const res = await fetch(url, { redirect: 'follow', ...options, headers: { ...(options.headers || {}), ...(cookie ? { cookie } : {}) } });
  const setCookie = res.headers.get('set-cookie');
  if (setCookie) cookie = setCookie.split(';')[0];
  return { status: res.status, html: await res.text() };
}

function attr(html, elementId, attribute) {
  // Find the tag carrying id="<elementId>" and pull <attribute> off it (order-independent).
  const tag = html.match(new RegExp(`<[^>]*id="${elementId}"[^>]*>`));
  if (!tag) return null;
  const m = tag[0].match(new RegExp(`${attribute}="([^"]*)"`));
  return m ? m[1] : null;
}

function count(html, elementId) {
  const m = html.match(new RegExp(`id="${elementId}"[^>]*>(\\d+)<`));
  return m ? Number(m[1]) : null;
}

const abs = url => url.startsWith('http') ? url : `${BASE}${url}`;

async function main() {
  console.log(`Mode: ${MODE} (page-refresh-on-backtrack ${GUARDED ? 'ON - repeats must be swallowed' : 'OFF - repeats must re-execute'})`);

  // Fresh session + first render.
  let { html } = await req(PAGE_URL);
  ok('page loads with zeroed counters', count(html, 'linkCount') === 0 && count(html, 'submitCount') === 0,
     `link=${count(html, 'linkCount')} submit=${count(html, 'submitCount')}`);

  // --- Link: double-fire the SAME rendered link ---
  const link1 = attr(html, 'incrementLink', 'href');
  ok('link href parsed', !!link1, 'no href for incrementLink');

  let r = await req(abs(link1));
  ok('first click increments', count(r.html, 'linkCount') === 1, `got ${count(r.html, 'linkCount')}`);

  r = await req(abs(link1)); // byte-identical repeat
  const afterRepeat = GUARDED ? 1 : 2;
  ok(GUARDED ? 'REPEAT of same link does NOT increment (guard)' : 'REPEAT of same link re-executes (no guard)',
     count(r.html, 'linkCount') === afterRepeat, `got ${count(r.html, 'linkCount')}`);

  // A genuinely new click, parsed from the fresh render (fresh contextID), always executes.
  const link2 = attr(r.html, 'incrementLink', 'href');
  ok('fresh render carries a NEW link url', link2 && link2 !== link1, `old=${link1} new=${link2}`);
  r = await req(abs(link2));
  ok('new click increments', count(r.html, 'linkCount') === afterRepeat + 1, `got ${count(r.html, 'linkCount')}`);

  // --- Form: refresh-of-a-POST ---
  ({ html } = await req(PAGE_URL));
  const formAction = html.match(/<form[^>]*action="([^"]*)"/)?.[1];
  const noteName = attr(html, 'note', 'name');
  const submitName = attr(html, 'submitBtn', 'name');
  ok('form parsed', !!(formAction && noteName && submitName), `action=${formAction} note=${noteName} submit=${submitName}`);

  const body = new URLSearchParams({ [noteName]: 'hello', [submitName]: 'Submit' }).toString();
  const post = () => req(abs(formAction), { method: 'POST', body, headers: { 'content-type': 'application/x-www-form-urlencoded' } });

  r = await post();
  ok('first POST increments', count(r.html, 'submitCount') === 1, `got ${count(r.html, 'submitCount')}`);

  r = await post(); // the browser-refresh replay: identical url + identical body
  const afterRepost = GUARDED ? 1 : 2;
  ok(GUARDED ? 'REPLAYED POST does NOT increment (guard)' : 'REPLAYED POST re-executes (no guard)',
     count(r.html, 'submitCount') === afterRepost, `got ${count(r.html, 'submitCount')}`);

  // A DIFFERENT body to the same rendered form is a new request and always executes.
  const body2 = new URLSearchParams({ [noteName]: 'world', [submitName]: 'Submit' }).toString();
  r = await req(abs(formAction), { method: 'POST', body: body2, headers: { 'content-type': 'application/x-www-form-urlencoded' } });
  ok('same form, different values: executes', count(r.html, 'submitCount') === afterRepost + 1, `got ${count(r.html, 'submitCount')}`);

  console.log(`\n${pass} passed, ${fail} failed${fail ? '  [' + fails.join(', ') + ']' : ''}`);
  process.exit(fail ? 1 : 0);
}

main().catch(e => { console.error(e); process.exit(1); });
