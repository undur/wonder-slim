// Ajax error-reporting harness. Proves a FAILED ajax request is surfaced to the user instead of
// silently injecting the server's error page into the target container.
//
// Two layers:
//   1. Real round-trip - drive a genuine server backtrack (a dead ajax context) and assert the server
//      returns a non-2xx status (ERXApplication.handlePageRestorationErrorInContext) AND the client
//      reacts: no error page morphed into the DOM, the 'ajaxslim:error' event fires, the banner shows.
//   2. Deterministic unit cases - mock window.fetch to return a 500 / reject / and verify an app can
//      preventDefault() the built-in banner. These don't depend on coaxing a specific server response.
//
// Run:  node examples/ajax-error-ux.mjs
import { chromium } from '/Users/hugi/git/wonder-slim/tools/playwright-bridge/node_modules/playwright/index.mjs';

const BASE = process.env.BASE
  || 'http://localhost:1200/cgi-bin/WebObjects/AjaxPlayground.woa/wa/page?name=ScenarioInvoice';
// A deliberately dead ajax action URL (impossible context id) - the server cannot restore its page,
// so it produces a "backtracked too far" page. With the framework fix that ships as a 500.
const DEAD_AJAX = '/cgi-bin/WebObjects/AjaxPlayground.woa/ajax/999999.5.3.1.0.1.19.1';

let pass = 0, fail = 0; const fails = [];
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; fails.push(name); console.log(`  FAIL  ${name}  ${detail}`); }
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext();
  const p = await ctx.newPage();
  await p.goto(BASE, { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('#linesContainer');

  // ============================================================================================
  // LAYER 1: real round-trip - server returns a non-2xx for a dead context, client surfaces it.
  // ============================================================================================
  console.log('\nLAYER 1: real backtrack round-trip (server status + client reaction)');
  {
    // (a) what status does a dead context return, in a live session?
    const http = await p.evaluate(async (dead) => {
      const r = await fetch(dead + '?_u=linesContainer;totalsPanel;renderedInvoice',
        { headers: { 'x-requested-with': 'XMLHttpRequest' }, credentials: 'same-origin' });
      const t = await r.text();
      return { status: r.status, ok: r.ok, isBacktrack: /backtrack|Missing Page/i.test(t) };
    }, DEAD_AJAX);
    ok('L1 server returns non-2xx for a dead ajax context', http.status >= 400, `status=${http.status}`);
    ok('L1 it is the backtrack/restoration error', http.isBacktrack, JSON.stringify(http));

    // (b) drive it through the real client path - banner must fire, container must NOT be polluted.
    await p.evaluate(() => { window.__err = null; document.addEventListener('ajaxslim:error', e => { window.__err = e.detail; }); });
    const before = await p.$eval('#linesContainer', e => e.innerText);
    await p.evaluate((dead) => {
      AjaxSlim.AUL.update('linesContainer;totalsPanel;renderedInvoice', dead, {});
    }, DEAD_AJAX);
    await p.waitForTimeout(1000);
    const after = await p.$eval('#linesContainer', e => e.innerText).catch(() => '(gone)');
    const errDetail = await p.evaluate(() => window.__err);
    const banner = await p.$('#ajaxslim-error-banner');
    ok('L1 error page NOT morphed into the container', before === after,
       'container rows changed - error HTML may have been injected');
    ok('L1 ajaxslim:error event fired', !!errDetail, JSON.stringify(errDetail));
    ok('L1 banner shown to the user', !!banner);
    // clean banner before layer 2
    await p.evaluate(() => { const b = document.getElementById('ajaxslim-error-banner'); if (b) b.remove(); });
  }

  // ============================================================================================
  // LAYER 2: deterministic client behaviour with a mocked fetch.
  // ============================================================================================
  console.log('\nLAYER 2: deterministic client error handling (mocked fetch)');
  {
    const r = await p.evaluate(async () => {
      const out = {};
      const realFetch = window.fetch;

      // 2a: HTTP 500 -> no morph, event fired with status 500, banner shown
      window.fetch = () => Promise.resolve(new Response(
        '<html><body>You backtracked too far.</body></html>',
        { status: 500, statusText: 'Internal Server Error', headers: { 'Content-Type': 'text/html' } }));
      let e1 = null; const h1 = e => { e1 = e.detail; };
      document.addEventListener('ajaxslim:error', h1);
      const before = document.getElementById('linesContainer').innerText;
      AjaxSlim.AUC.update('linesContainer');
      await new Promise(r => setTimeout(r, 250));
      out.h500_notMorphed = before === document.getElementById('linesContainer').innerText;
      out.h500_event = !!e1; out.h500_status = e1 && e1.status;
      out.h500_banner = !!document.getElementById('ajaxslim-error-banner');
      document.removeEventListener('ajaxslim:error', h1);
      let b = document.getElementById('ajaxslim-error-banner'); if (b) b.remove();

      // 2b: network error (fetch rejects) -> event fired with status 0, banner shown
      window.fetch = () => Promise.reject(new TypeError('Failed to fetch'));
      let e2 = null; const h2 = e => { e2 = e.detail; };
      document.addEventListener('ajaxslim:error', h2);
      AjaxSlim.AUC.update('linesContainer');
      await new Promise(r => setTimeout(r, 250));
      out.net_event = !!e2; out.net_status = e2 && e2.status;
      out.net_banner = !!document.getElementById('ajaxslim-error-banner');
      document.removeEventListener('ajaxslim:error', h2);
      b = document.getElementById('ajaxslim-error-banner'); if (b) b.remove();

      // 2c: an app can preventDefault() to suppress the built-in banner and own the UX
      window.fetch = () => Promise.resolve(new Response('err', { status: 503 }));
      const h3 = e => e.preventDefault();
      document.addEventListener('ajaxslim:error', h3);
      AjaxSlim.AUC.update('linesContainer');
      await new Promise(r => setTimeout(r, 250));
      out.prevented_noBanner = !document.getElementById('ajaxslim-error-banner');
      document.removeEventListener('ajaxslim:error', h3);

      window.fetch = realFetch;
      return out;
    });

    ok('L2a HTTP 500: error page not morphed', r.h500_notMorphed);
    ok('L2a HTTP 500: ajaxslim:error fired with status 500', r.h500_event && r.h500_status === 500, JSON.stringify(r));
    ok('L2a HTTP 500: banner shown', r.h500_banner);
    ok('L2b network error: event fired with status 0', r.net_event && r.net_status === 0, JSON.stringify(r));
    ok('L2b network error: banner shown', r.net_banner);
    ok('L2c preventDefault() suppresses the built-in banner', r.prevented_noBanner);
  }

  await browser.close();
  console.log(`\n================  ${pass} passed, ${fail} failed  ================`);
  if (fail) { console.log('FAILED:', fails.join(', ')); process.exitCode = 1; }
}
main().catch(e => { console.error('HARNESS ERROR', e); process.exitCode = 2; });
