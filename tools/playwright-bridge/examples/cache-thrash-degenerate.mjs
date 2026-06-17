// Degenerate-corner harness: the page the request belongs to has aged out of WO's caches entirely,
// while a DIFFERENT instance is still cached. Per the chosen design, the request must FAIL (no page)
// rather than restore the other instance's content. Run this against an app started with a tiny
// er.extensions.maxPageReplacementCacheSize (e.g. 1) so eviction is forced.
import { chromium } from '/Users/hugi/git/wonder-slim/tools/playwright-bridge/node_modules/playwright/index.mjs';

const BASE = process.env.BASE
  || 'http://localhost:1200/cgi-bin/WebObjects/AjaxPlayground.woa/wa/page?name=ScenarioInvoice';

let pass = 0, fail = 0; const fails = [];
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; fails.push(name); console.log(`  FAIL  ${name}  ${detail}`); }
}

async function gotoInvoice(p) { await p.goto(BASE, { waitUntil: 'domcontentloaded' }); await p.waitForSelector('#qty-1'); }
async function setQty(p, id, q) { await p.fill(`#qty-${id}`, String(q)); await p.press(`#qty-${id}`, 'Tab'); await p.waitForTimeout(450); }

async function main() {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext();
  const a = await ctx.newPage();
  const b = await ctx.newPage();
  await gotoInvoice(a);
  await gotoInvoice(b);

  // A: distinctive 111 -> line1 = 55500.  B: distinctive 999 -> line1 = 499500.
  await setQty(a, 1, 111);
  await setQty(b, 1, 999);

  // Grab A's stale move-down link (its render context), then keep hammering B so that, with maxsize=1,
  // A's page is evicted from the fragment cache (and ideally A is gone from WO's backtrack cache too).
  const aMoveUrl = await a.$eval("#linebox-1 a[title='Move down']", el => {
    const oc = el.getAttribute('onclick') || '';
    const m = oc.match(/'(\/[^']*ajax[^']*)'/); return m ? m[1] : null;
  });

  // Hammer B many times to age A out.
  for (let i = 0; i < 12; i++) await setQty(b, 2, i + 1);

  // Now invoke A's stale link directly. The KEY assertion: the response must NOT contain B's data (999).
  const res = await b.evaluate(async (url) => {
    try {
      const r = await fetch(url + '?_u=linesContainer;totalsPanel;renderedInvoice',
        { headers: { 'x-requested-with': 'XMLHttpRequest' } });
      const t = await r.text();
      return { status: r.status, len: t.length,
               has111: /\b111\b|55500/.test(t), has999: /\b999\b|499500/.test(t),
               snippet: t.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').slice(0, 160) };
    } catch (e) { return { error: String(e) }; }
  }, aMoveUrl);

  console.log('  stale-A-link result:', JSON.stringify(res));
  // The whole point: B's content must NEVER bleed into A's request.
  ok('DEGENERATE: no B bleed (has999 false)', res.has999 === false, JSON.stringify(res));
  // Acceptable outcomes for A: either A's own data (if WO still had it) OR an error/empty (refused).
  // What is NOT acceptable is silently returning B. A 500 / non-200 / no-999 are all fine.
  if (res.status && res.status >= 400) {
    ok('DEGENERATE: refused with error (>=400) rather than bleed', true);
  } else if (res.has111) {
    ok('DEGENERATE: served A\'s own content (WO still had it)', true);
  } else {
    ok('DEGENERATE: served neither B nor A wrongly', res.has999 === false,
       'returned 200 but with no recognizable invoice data - acceptable if not B');
  }

  await browser.close();
  console.log(`\n================  ${pass} passed, ${fail} failed  ================`);
  if (fail) { console.log('FAILED:', fails.join(', ')); process.exitCode = 1; }
}
main().catch(e => { console.error('HARNESS ERROR', e); process.exitCode = 2; });
