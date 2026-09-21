// Route dispatch under every URL shape a front end can hand the app. A multi-segment route
// (/ds/inc) must resolve whether the request is freestyle, carries the adaptor prefix, carries
// the prefix plus mod_WebObjects' instance number, or is marked as a route with the "route"
// request handler key; unknown paths must give the route table's 404, never a direct-action
// lookup error. A numeric path must be routed as a path wherever the URL grammar allows it: in
// every shape except the bare prefix, where WO reads it as an instance number (documented
// limitation; front ends use the marker). Regression guard for RouteRequestHandler.
//
//   PORT=<port> node route-url-shapes.mjs

const BASE = process.env.BASE || `http://127.0.0.1:${process.env.PORT || 2099}`;
const APP = '/Apps/WebObjects/AjaxPlayground.woa';
const BARE_PREFIX = APP; // the one shape where a numeric first segment is an instance number
const PREFIXES = ['', APP, `${APP}/1`, '/cgi-bin/WebObjects/AjaxPlayground.woa/-3', '/route', `${APP}/route`, `${APP}/1/route`, `${APP}/-1/route`];

const cases = [
  { path: '/ds/inc',       expect: 200, name: 'two-segment route' },
  { path: '/invoice',      expect: 200, name: 'one-segment route' },
  { path: '/',             expect: 200, name: 'root route' },
  { path: '/foo/bar/baz',  expect: 404, name: 'unknown three-segment path' },
  { path: '/foo/bar',      expect: 404, name: 'unknown two-segment path' },
  { path: '/12345',        expect: 404, name: 'numeric path is a path', skipFor: [BARE_PREFIX] },
  { path: '/res/AjaxSlim/wonder-select.css', expect: 200, name: 'resource handler key' },
  { path: '/wa/page?name=ScenarioReplay',    expect: 200, name: 'direct action handler key' },
  // routePath() must answer what was asked for, not the shape it arrived in - for routes and for handler requests
  { path: '/echo/route-path/a/b',      expect: 200, body: '/echo/route-path/a/b',  name: 'routePath of a route' },
  { path: '/echo/route-path/2/?x=1',   expect: 200, body: '/echo/route-path/2/',   name: 'routePath keeps slash, drops query' },
  { path: '/echo/route-path/1234',     expect: 200, body: '/echo/route-path/1234', name: 'routePath of a numeric segment' },
  { path: '/wa/routePath',             expect: 200, body: '/wa/routePath',         name: 'routePath of a direct action' },
  { path: '/wa/routePath?x=1',         expect: 200, body: '/wa/routePath',         name: 'routePath of a DA drops query' },
];

let failed = 0, passed = 0;
for (const prefix of PREFIXES) {
  for (const c of cases) {
    if (c.skipFor?.includes(prefix)) continue;
    const url = `${BASE}${prefix}${c.path === '/' && prefix ? '/' : c.path}`;
    const res = await fetch(url, { redirect: 'manual' });
    const body = await res.text();
    const lookupError = /NoSuchMethodException|Couldn't locate action class/.test(body);
    const bodyOk = c.body === undefined || body.trim() === c.body;
    const ok = res.status === c.expect && !lookupError && bodyOk;
    if (!ok) failed++; else passed++;
    console.log(`${ok ? '  ✓' : '  ✗'} ${(prefix || '(freestyle)').padEnd(46)} ${c.name.padEnd(28)} ${res.status}${lookupError ? '  DIRECT ACTION LOOKUP ERROR' : ''}${bodyOk ? '' : `  BODY: ${body.trim().slice(0, 80)}`}`);
  }
}
console.log(failed ? `\n${failed} case(s) failed.` : `\nAll ${passed} cases passed.`);
process.exit(failed ? 1 : 0);
