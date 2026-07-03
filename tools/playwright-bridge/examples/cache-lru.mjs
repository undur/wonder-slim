// Edit-and-wander endurance harness. Run against an app started with a SMALL
// page cache (e.g. -DWOPageCacheSize=3; the unified cache bounds INSTANCES by
// WOApplication.pageCacheSize) so the page limit bites quickly.
//
// Scenario (the receipt-editing pattern): open a "receipt" (page A) and edit it, then wander through
// many OTHER pages to look up data, returning to edit A between each. Asserts A survives the whole
// session - never evicted, never bled - and its stale links still resolve at the end.
//
// NOTE ON WHAT THIS DOES AND DOESN'T PROVE: editing A re-renders one of its containers, which STORES
// a fragment and so re-inserts A at the tail. That self-touch keeps A alive under BOTH eviction
// policies (insertion-order FIFO and access-order LRU) - so this test passing does NOT by itself
// isolate the LRU change from FIFO. (Empirically it passes with the LRU touch disabled too.) The LRU
// touch in restorePageForContextID matters for the narrower restore-WITHOUT-store case - a page
// restored as the sender of an action that returns a DIFFERENT page (navigating away from the receipt
// to look something up), where no fragment of A is re-stored. This harness does not yet model that
// motion; it stands as an end-to-end endurance/regression check, not an LRU-vs-FIFO discriminator.
import { chromium } from '/Users/hugi/git/wonder-slim/tools/playwright-bridge/node_modules/playwright/index.mjs';

const BASE = process.env.BASE
  || 'http://localhost:1200/cgi-bin/WebObjects/AjaxPlayground.woa/wa/page?name=ScenarioInvoice';

let pass = 0, fail = 0; const fails = [];
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; fails.push(name); console.log(`  FAIL  ${name}  ${detail}`); }
}

async function gotoInvoice(ctx) {
  const p = await ctx.newPage();
  p.on('response', r => { if (r.status() >= 500) console.log(`   [HTTP ${r.status()}] ${r.url()}`); });
  await p.goto(BASE, { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('#qty-1');
  return p;
}
async function setQty(p, id, q) { await p.fill(`#qty-${id}`, String(q)); await p.press(`#qty-${id}`, 'Tab'); await p.waitForTimeout(450); }
async function grandTotal(p) { return parseInt(await p.$eval('#grandTotal', e => e.textContent.trim()), 10); }

async function main() {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext();

  // Page A = the receipt we keep coming back to. Distinctive total.
  const a = await gotoInvoice(ctx);
  await setQty(a, 1, 50); // 50*500 + 1200 + 300 = 26500
  ok('A initial total', await grandTotal(a) === 26500, `got ${await grandTotal(a)}`);

  // Wander through MANY other pages, editing A between each visit. A page survives a stale-link click
  // if it is in the fragment cache (maxsize pages) OR in WO's backtrack cache (~30 contexts), so we
  // open >30 distinct pages to roll WO's backtrack cache past A and leave the fragment cache as A's
  // lifeline. Editing A each loop re-stores it (self-touch), so A stays warm under either eviction
  // policy - see the file header for why this is an endurance check, not an LRU-vs-FIFO discriminator.
  const WANDER = 40; // > WO's 30-entry backtrack cache, so A's survival depends on the fragment cache
  let lastSprocketQty = 0;
  for (let i = 0; i < WANDER; i++) {
    const w = await gotoInvoice(ctx);          // a new distinct page (new pageID)
    await setQty(w, 1, i + 1);                  // touch the wander page
    // return to A and edit it -> re-stores A (self-touch), proving A is still alive and its own
    lastSprocketQty = (i % 8) + 1;              // vary so each edit really changes A
    await setQty(a, 3, lastSprocketQty);
    // free the wander page so the headless browser doesn't accumulate 40 live tabs
    await w.close();
  }

  // Final check: A is still alive and still ITS OWN data (not evicted, not bled).
  // A: line1 50*500=25000, line2 Gadget 1*1200=1200, line3 Sprocket lastSprocketQty*75.
  const expected = 25000 + 1200 + lastSprocketQty * 75;
  const aFinal = await grandTotal(a);
  ok('A survived the edit-and-wander session (not evicted, not bled)', aFinal === expected, `got ${aFinal}, want ${expected}`);

  // And one more stale-link operation on A to be sure its links still resolve (no 500).
  const movedUp = await a.$$("a[title='Move up']");
  if (movedUp[0]) { await movedUp[0].click(); await a.waitForTimeout(450); }
  const aAfterMove = await grandTotal(a);
  ok('A stale link still works after wander (no backtrack 500)', aAfterMove === expected, `got ${aAfterMove}, want ${expected}`);

  await browser.close();
  console.log(`\n================  ${pass} passed, ${fail} failed  ================`);
  if (fail) { console.log('FAILED:', fails.join(', ')); process.exitCode = 1; }
}
main().catch(e => { console.error('HARNESS ERROR', e); process.exitCode = 2; });
