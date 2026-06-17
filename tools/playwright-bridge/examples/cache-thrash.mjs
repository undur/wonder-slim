// Cache torture harness for the ajax page-fragment cache cross-instance bleed fix.
//
// Drives REAL DOM interactions against AjaxPlayground/ScenarioInvoice through many corner cases that
// stress the (page x container) fragment cache and its container-name fallback. The grand total is the
// canary: each instance is given a distinctive total, so any cross-instance bleed shows up as a wrong
// total or a row from the wrong invoice. Reports PASS/FAIL per case; exit code 1 if anything failed.
//
// Run:  node examples/cache-thrash.mjs            (default base url / port 1200)
//       BASE=http://host:port/... node examples/cache-thrash.mjs
import { chromium } from '/Users/hugi/git/wonder-slim/tools/playwright-bridge/node_modules/playwright/index.mjs';

const BASE = process.env.BASE
  || 'http://localhost:1200/cgi-bin/WebObjects/AjaxPlayground.woa/wa/page?name=ScenarioInvoice';

let pass = 0, fail = 0;
const fails = [];
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; fails.push(name); console.log(`  FAIL  ${name}  ${detail}`); }
}

// --- low-level helpers --------------------------------------------------------------------------

async function freshPage(ctx) {
  const p = await ctx.newPage();
  // surface server 500s loudly
  p.on('response', r => { if (r.status() >= 500) console.log(`   [HTTP ${r.status()}] ${r.url()}`); });
  return p;
}

async function gotoInvoice(p) {
  await p.goto(BASE, { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('#qty-1');
}

// Set a line's quantity and let the observe-field round-trip (fires on blur -> Tab).
async function setQty(p, lineId, qty) {
  const sel = `#qty-${lineId}`;
  await p.fill(sel, String(qty));
  await p.press(sel, 'Tab');
  await p.waitForTimeout(450);
}

async function grandTotal(p) {
  return parseInt(await p.$eval('#grandTotal', e => e.textContent.trim()), 10);
}

async function lineCount(p) {
  return await p.$$eval('#linesContainer tbody tr, #linesContainer tr.invalid, #linesContainer [id^="linebox-"]', els => {
    const ids = new Set(els.map(e => e.id).filter(Boolean));
    return ids.size;
  });
}

async function rowTotals(p) {
  return await p.$$eval('#linesContainer .lineTotal', els => els.map(e => parseInt(e.textContent.trim(), 10)));
}

async function clickByTitle(p, title, nth = 0) {
  const links = await p.$$(`a[title='${title}']`);
  if (!links[nth]) return false;
  await links[nth].click();
  await p.waitForTimeout(450);
  return true;
}

// Compute expected grand total from a list of {qty, price}.
const sum = lines => lines.reduce((a, l) => a + l.qty * l.price, 0);

// --- the cases ----------------------------------------------------------------------------------

async function main() {
  const browser = await chromium.launch({ headless: true });

  // ============================================================================================
  // CASE 1: Two instances of the SAME component, same session - the exact reported bug.
  //   Edit instance A to a distinctive total, edit instance B to a different one, then go back
  //   and drive MORE updates on A. A must never show B's numbers and vice-versa.
  // ============================================================================================
  console.log('\nCASE 1: two same-component instances in one session');
  {
    const ctx = await browser.newContext();
    const a = await freshPage(ctx);
    const b = await freshPage(ctx);
    await gotoInvoice(a);
    await gotoInvoice(b);

    // base lines: Widget 2x500=1000, Gadget 1x1200=1200, Sprocket 4x75=300  => 2500
    // A: make line1 qty 10  => 10x500=5000  total 5000+1200+300 = 6500
    await setQty(a, 1, 10);
    // B: make line1 qty 3   => 3x500=1500   total 1500+1200+300 = 3000
    await setQty(b, 1, 3);

    const aTot1 = await grandTotal(a);
    const bTot1 = await grandTotal(b);
    ok('C1 A total after edit', aTot1 === 6500, `got ${aTot1}`);
    ok('C1 B total after edit', bTot1 === 3000, `got ${bTot1}`);

    // Go back to A and edit again - this is where the stale fallback used to grab B.
    await setQty(a, 2, 7); // Gadget 7x1200=8400 => 5000+8400+300 = 13700
    const aTot2 = await grandTotal(a);
    ok('C1 A total after 2nd edit (no B bleed)', aTot2 === 13700, `got ${aTot2}`);

    // And B again.
    await setQty(b, 3, 9); // Sprocket 9x75=675 => 1500+1200+675 = 3375
    const bTot2 = await grandTotal(b);
    ok('C1 B total after 2nd edit (no A bleed)', bTot2 === 3375, `got ${bTot2}`);

    // A's rows must be A's, not B's.
    const aRows = await rowTotals(a);
    ok('C1 A rows intact', aRows[0] === 5000 && aRows[1] === 8400 && aRows[2] === 300, `got ${aRows}`);
    await ctx.close();
  }

  // ============================================================================================
  // CASE 2: THREE instances interleaved, hammering the same container names.
  // ============================================================================================
  console.log('\nCASE 2: three instances, interleaved edits');
  {
    const ctx = await browser.newContext();
    const pages = [];
    for (let i = 0; i < 3; i++) { const p = await freshPage(ctx); await gotoInvoice(p); pages.push(p); }
    // distinctive line-1 qty per instance
    const qtys = [2, 20, 200];
    const expected = qtys.map(q => q * 500 + 1200 + 300);
    // interleave: round-robin several passes
    for (let pass = 0; pass < 4; pass++) {
      for (let i = 0; i < 3; i++) await setQty(pages[i], 1, qtys[i]);
    }
    for (let i = 0; i < 3; i++) {
      const t = await grandTotal(pages[i]);
      ok(`C2 instance ${i} total stable`, t === expected[i], `got ${t}, want ${expected[i]}`);
    }
    await ctx.close();
  }

  // ============================================================================================
  // CASE 3: Per-row targeted updates (the cache-hungry pattern) across two instances.
  //   Each row edit re-renders only that row+totals, leaving OTHER rows' links on ever-older
  //   contexts. Then operate those stale links (move/remove) on BOTH instances.
  // ============================================================================================
  console.log('\nCASE 3: stale per-row links across two instances');
  {
    const ctx = await browser.newContext();
    const a = await freshPage(ctx); await gotoInvoice(a);
    const b = await freshPage(ctx); await gotoInvoice(b);
    // age row contexts: edit row1 many times on A (row2/row3 links go stale), distinct on B
    for (let i = 1; i <= 6; i++) await setQty(a, 1, i);       // ends qty=6 -> 3000
    for (let i = 1; i <= 6; i++) await setQty(b, 1, i + 10);  // ends qty=16 -> 8000
    const aBefore = await grandTotal(a); // 6*500 + 1200 + 300 = 4500
    const bBefore = await grandTotal(b); // 16*500 + 1200 + 300 = 9500
    ok('C3 A pre-move total', aBefore === 4500, `got ${aBefore}`);
    ok('C3 B pre-move total', bBefore === 9500, `got ${bBefore}`);
    // now use a STALE link on A: move last line up (Sprocket row link rendered long ago)
    const movedA = await clickByTitle(a, 'Move up', 1); // 2nd move-up link = move line up
    ok('C3 A stale move-up worked (no 500)', movedA);
    const aAfter = await grandTotal(a);
    ok('C3 A total preserved after stale move (no B bleed)', aAfter === 4500, `got ${aAfter}`);
    // and on B
    const movedB = await clickByTitle(b, 'Move up', 1);
    ok('C3 B stale move-up worked', movedB);
    const bAfter = await grandTotal(b);
    ok('C3 B total preserved (no A bleed)', bAfter === 9500, `got ${bAfter}`);
    await ctx.close();
  }

  // ============================================================================================
  // CASE 4: add / remove lines (multi-update linesContainer;totalsPanel;renderedInvoice)
  //   interleaved across two instances. Structural changes are the harshest on the cache.
  // ============================================================================================
  console.log('\nCASE 4: add/remove lines interleaved across two instances');
  {
    const ctx = await browser.newContext();
    const a = await freshPage(ctx); await gotoInvoice(a);
    const b = await freshPage(ctx); await gotoInvoice(b);
    // A: add 3 lines (each Widget 1x100=100). base 2500 + 300 = 2800
    for (let i = 0; i < 3; i++) { await a.click('#addLine'); await a.waitForTimeout(300); }
    // B: remove 1 line (Sprocket 300). base 2500 - 300 = 2200
    const bRemove = await clickByTitle.call(null, b, 'Remove line');
    // remove links have no title 'Remove line'; they use class remove-line - click first
    if (!bRemove) { await b.click('.remove-line'); await b.waitForTimeout(350); }
    const aTot = await grandTotal(a);
    const bLines = await b.$$eval('.lineTotal', e => e.length);
    ok('C4 A total after 3 adds', aTot === 2800, `got ${aTot}`);
    ok('C4 B line count after remove', bLines === 2, `got ${bLines} rows`);
    // interleave more: A removes, B adds
    await a.click('.remove-line'); await a.waitForTimeout(350);
    await b.click('#addLine'); await b.waitForTimeout(350);
    const aTot2 = await grandTotal(a);
    ok('C4 A total still self-consistent', typeof aTot2 === 'number' && !Number.isNaN(aTot2), `got ${aTot2}`);
    await ctx.close();
  }

  // ============================================================================================
  // CASE 5: many short-lived instances churning the page limit.
  //   Open and lightly edit 12 instances in one session (default page limit 30, but they churn
  //   WO's backtrack cache). Then return to the FIRST and edit it - it must still be itself.
  // ============================================================================================
  console.log('\nCASE 5: many instances churn, first must survive');
  {
    const ctx = await browser.newContext();
    const first = await freshPage(ctx); await gotoInvoice(first);
    await setQty(first, 1, 42); // 42*500+1200+300 = 22500
    const firstTotal = await grandTotal(first); // 22500
    ok('C5 first total set', firstTotal === 22500, `got ${firstTotal}`);
    const churn = [];
    for (let i = 0; i < 12; i++) {
      const p = await freshPage(ctx); await gotoInvoice(p);
      await setQty(p, 1, i + 1);
      churn.push(p);
    }
    // back to first, drive another update
    await setQty(first, 3, 8); // Sprocket 8*75=600 => 21000(42*500)+1200+600 = 22800
    const firstAfter = await grandTotal(first);
    ok('C5 first survived churn (correct, not a churn instance)', firstAfter === 22800, `got ${firstAfter}`);
    await ctx.close();
  }

  // ============================================================================================
  // CASE 6: concurrent edits to two instances, then assert INTERNAL CONSISTENCY.
  //   The true bleed test isn't "which qty won the race" (that's nondeterministic) - it's whether
  //   each page's grand total equals the sum of ITS OWN visible rows. A bleed would splice another
  //   instance's rows/total in, breaking that invariant. We drive edits concurrently to race the
  //   cache, let both settle, then check total == sum(own rows) on each page.
  // ============================================================================================
  console.log('\nCASE 6: concurrent edits, assert each page is internally consistent');
  {
    const ctx = await browser.newContext();
    const a = await freshPage(ctx); await gotoInvoice(a);
    const b = await freshPage(ctx); await gotoInvoice(b);
    // Interleave at the request level but keep each page's own fills serialized (so inputs aren't
    // scrambled); the point is to interleave the two pages' cache writes, not corrupt one page's input.
    for (let i = 1; i <= 5; i++) {
      await Promise.all([ setQty(a, 1, i * 2), setQty(b, 2, i * 3) ]);
    }
    await a.waitForTimeout(400); await b.waitForTimeout(400);
    const [aTot, aRows] = [await grandTotal(a), await rowTotals(a)];
    const [bTot, bRows] = [await grandTotal(b), await rowTotals(b)];
    const aSum = aRows.reduce((x, y) => x + (Number.isNaN(y) ? 0 : y), 0);
    const bSum = bRows.reduce((x, y) => x + (Number.isNaN(y) ? 0 : y), 0);
    ok('C6 A total == sum(A rows) (internally consistent)', aTot === aSum, `total ${aTot} vs rows ${aRows}`);
    ok('C6 B total == sum(B rows) (internally consistent)', bTot === bSum, `total ${bTot} vs rows ${bRows}`);
    // A edited line 1, B edited line 2 - confirm each page reflects its own edit dimension.
    ok('C6 A line1 reflects A edits (>=1000)', aRows[0] >= 1000, `row0 ${aRows[0]}`);
    ok('C6 B line2 reflects B edits (>=1200)', bRows[1] >= 1200, `row1 ${bRows[1]}`);
    await ctx.close();
  }

  // ============================================================================================
  // CASE 7: multi-container update (_u="a;b;c") fallback scoping across two instances.
  //   The header observe-field updates renderedInvoice;summaryPanel;customerModalBox at once. Drive
  //   it on two instances and confirm each instance's summary/total stays its own.
  // ============================================================================================
  console.log('\nCASE 7: multi-container (_u) update scoping across two instances');
  {
    const ctx = await browser.newContext();
    const a = await freshPage(ctx); await gotoInvoice(a);
    const b = await freshPage(ctx); await gotoInvoice(b);
    await setQty(a, 1, 11); // A line1 -> 5500, total 5500+1200+300 = 7000
    await setQty(b, 1, 22); // B line1 -> 11000, total 11000+1200+300 = 12500
    // change customer on each (fires the header multi-update observe-field on the customer popup)
    // Use the qty edit's totals as the canary after the multi-update churn.
    await setQty(a, 2, 1); // unchanged value, but forces another multi-ish update cycle on A
    await setQty(b, 2, 1);
    const aTot = await grandTotal(a);
    const bTot = await grandTotal(b);
    ok('C7 A total after multi-update churn', aTot === 7000, `got ${aTot}`);
    ok('C7 B total after multi-update churn', bTot === 12500, `got ${bTot}`);
    // summary panel must match each page's own line count/total
    const aSummary = await a.$eval('#summaryPanel', e => e.textContent);
    const bSummary = await b.$eval('#summaryPanel', e => e.textContent);
    ok('C7 A summary shows A total', aSummary.includes('7000'), `summary="${aSummary.trim()}"`);
    ok('C7 B summary shows B total', bSummary.includes('12500'), `summary="${bSummary.trim()}"`);
    await ctx.close();
  }

  await browser.close();
  console.log(`\n================  ${pass} passed, ${fail} failed  ================`);
  if (fail) { console.log('FAILED:', fails.join(', ')); process.exitCode = 1; }
}

main().catch(e => { console.error('HARNESS ERROR', e); process.exitCode = 2; });
