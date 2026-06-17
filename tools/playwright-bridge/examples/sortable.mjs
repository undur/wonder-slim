// Drag-to-reorder harness for AjaxSlim.Sortable, against AjaxPlayground/ScenarioInvoice.
// Drives a real pointer drag (mousedown on the handle -> mousemove -> mouseup) - NOT HTML5 DnD, which
// the pointer-based sortable does not use - and asserts the server reordered the list and the morph
// reflected it. Rows are identified by their stable qty-<id> field id, which follows a line across
// the reorder morph.
//
// Run:  node examples/sortable.mjs
import { chromium } from '/Users/hugi/git/wonder-slim/tools/playwright-bridge/node_modules/playwright/index.mjs';

const BASE = process.env.BASE
  || 'http://localhost:1200/cgi-bin/WebObjects/AjaxPlayground.woa/wa/page?name=ScenarioInvoice';

let pass = 0, fail = 0; const fails = [];
function ok(name, cond, detail = '') {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; fails.push(name); console.log(`  FAIL  ${name}  ${detail}`); }
}

const rowIds = page => page.$$eval('#linesContainer tbody tr', rows =>
  rows.map(r => { const q = r.querySelector('[id^="qty-"]'); return q ? q.id : '?'; }));

// Drag the row at fromIdx so it lands at/after toIdx, via manual pointer events on its handle.
async function dragRow(page, fromIdx, toIdx) {
  const rows = page.locator('#linesContainer tbody tr');
  const handle = rows.nth(fromIdx).locator('.drag-handle');
  const fb = await handle.boundingBox();
  const tb = await rows.nth(toIdx).boundingBox();
  if (!fb || !tb) throw new Error('no bounding box for drag');
  const targetY = tb.y + tb.height * (toIdx > fromIdx ? 0.8 : 0.2);
  await page.mouse.move(fb.x + fb.width / 2, fb.y + fb.height / 2);
  await page.mouse.down();
  for (let s = 1; s <= 8; s++) {
    await page.mouse.move(fb.x + fb.width / 2, fb.y + (targetY - fb.y) * (s / 8), { steps: 3 });
    await page.waitForTimeout(45);
  }
  await page.mouse.up();
  await page.waitForTimeout(800); // reorder action + morph
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const page = await (await browser.newContext()).newPage();
  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('response', r => { if (r.status() >= 500) errors.push('HTTP ' + r.status() + ' ' + r.url()); });
  await page.goto(BASE, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#linesContainer tbody tr .drag-handle');

  console.log('\nSortable: drag-to-reorder invoice lines');
  const before = await rowIds(page);
  ok('S0 three rows present', before.length === 3, before.join(','));

  // Drag row 0 (qty-1) to the bottom.
  await dragRow(page, 0, 2);
  const afterDown = await rowIds(page);
  ok('S1 top row dragged to bottom', JSON.stringify(afterDown) === JSON.stringify(['qty-2', 'qty-3', 'qty-1']),
     'got ' + afterDown.join(','));

  // Drag it back to the top (drag last row up to index 0).
  await dragRow(page, 2, 0);
  const afterUp = await rowIds(page);
  ok('S2 bottom row dragged back to top', afterUp[0] === 'qty-1', 'got ' + afterUp.join(','));

  ok('S3 no console errors / 500s during drags', errors.length === 0, errors.join(' | '));

  await browser.close();
  console.log(`\n================  ${pass} passed, ${fail} failed  ================`);
  if (fail) { console.log('FAILED:', fails.join(', ')); process.exitCode = 1; }
}
main().catch(e => { console.error('HARNESS ERROR', e); process.exitCode = 2; });
