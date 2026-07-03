// Differential probe for the component-action dispatch rewrite: drives the SAME matrix of
// happy-path and edge-case requests against a running app and prints one normalized line per case
// (status + page title + canary values). Run it twice - once against an app on the NEW handler
// (the default) and once with -Der.extensions.ERXComponentActionRequestHandler.enabled=false (the
// legacy handler) - and diff the outputs. Expected diffs are exactly the deliberate divergences
// documented in ERXComponentActionRequestHandler's javadoc (currently: the malformed-URL case).
//
//   BASE=http://localhost:<port> node dispatch-differential.mjs > new.txt   (default)
//   BASE=http://localhost:<port> node dispatch-differential.mjs > old.txt   (...enabled=false)
//   diff old.txt new.txt

const BASE = process.env.BASE || 'http://localhost:55432';
const APP = `${BASE}/cgi-bin/WebObjects/AjaxPlayground.woa`;

let cookie = '';

async function req(url, opts = {}) {
  const res = await fetch(url, { redirect: 'manual', ...opts, headers: { ...(opts.headers || {}), cookie: opts.cookie ?? cookie } });
  const sc = res.headers.get('set-cookie');
  if (sc && opts.cookie === undefined) cookie = sc.split(';')[0];
  const body = await res.text();
  return { status: res.status, body };
}

const title = html => html.match(/<title>([^<]*)<\/title>/)?.[1] ?? '(no title)';
const attr = (html, id, a) => (html.match(new RegExp(`<[^>]*id="${id}"[^>]*>`))?.[0].match(new RegExp(`${a}="([^"]*)"`)) || [])[1];
const count = (html, id) => html.match(new RegExp(`id="${id}"[^>]*>(\\d+)<`))?.[1] ?? '?';
const abs = u => u.startsWith('http') ? u : `${BASE}${u}`;

function report(name, r, extra = '') {
  console.log(`${name.padEnd(34)} ${String(r.status).padEnd(4)} ${title(r.body)}${extra ? '  ' + extra : ''}`);
}

async function main() {
  // Bootstrap a session and grab a live action link + form.
  let page = await req(`${APP}/wa/page?name=ScenarioReplay`);
  page = await req(`${APP}/wa/page?name=ScenarioReplay`);
  const sid = cookie.match(/wosid=([^;]+)/)?.[1];
  const link = attr(page.body, 'incrementLink', 'href');
  const formAction = page.body.match(/<form[^>]*action="([^"]*)"/)?.[1];
  const noteName = attr(page.body, 'note', 'name');
  const submitName = attr(page.body, 'submitBtn', 'name');

  // Happy paths first (these also mint fresh state the edge cases can lean on).
  let r = await req(abs(link));
  report('link click (happy)', r, `linkCount=${count(r.body, 'linkCount')}`);

  const body = new URLSearchParams({ [noteName]: 'hello', [submitName]: 'Submit' }).toString();
  r = await req(abs(formAction), { method: 'POST', body, headers: { 'content-type': 'application/x-www-form-urlencoded' } });
  report('form POST (happy)', r, `submitCount=${count(r.body, 'submitCount')}`);

  // Empty-sender action element: a live contextID with nothing after the dot.
  const freshLink = attr(r.body, 'incrementLink', 'href');
  const liveContext = freshLink.match(/\/wo\/(\d+)\./)?.[1] ?? '0';
  r = await req(`${APP}/wo/${liveContext}.`);
  report('empty senderID (/wo/<ctx>.)', r);

  // Edge matrix.
  report('bare /wo/', await req(`${APP}/wo/`));
  report('session only (/wo/<sid>)', await req(`${APP}/wo/${sid}`));
  report('unknown contextID (999999.5)', await req(`${APP}/wo/999999.5`));
  report('empty contextID (/wo/.5)', await req(`${APP}/wo/.5`));
  report('junk element (/wo/notanaction)', await req(`${APP}/wo/notanaction`));
  report('named page (/wo/Main.wo/1.5)', await req(`${APP}/wo/Main.wo/1.5`));
  report('sid in URL (/wo/<sid>/1.5)', await req(`${APP}/wo/${sid}/1.5`));
  report('expired session (bad cookie)', await req(abs(freshLink), { cookie: 'wosid=DEADBEEFDEADBEEF' }));
  report('no session anywhere', await req(`${APP}/wo/7.5`, { cookie: '' }));

  // Session still healthy afterwards? One more happy click proves the edge cases didn't poison it.
  page = await req(`${APP}/wa/page?name=ScenarioReplay`);
  const link2 = attr(page.body, 'incrementLink', 'href');
  r = await req(abs(link2));
  report('link click (after edge storm)', r, `linkCount=${count(r.body, 'linkCount')}`);
}

main().catch(e => { console.error(e); process.exit(1); });
