# wonder-slim and ng-objects: convergence

Written 2026-10-02, comparing wonder-slim 8.0.16 with ng-objects master as of that day.

ng-objects is behind wonder-slim in six areas: routing, static resources, logging, configuration, the admin console and Ajax morphing. The quickest wins are routing and resources: the HTTP side of serving resources (ETags, ranges, stamps, a bounded cache) and the routing chain port with little change. Several pieces could stop being ported at all and live once, in a library both frameworks use, as the development tools in ng-core already do.

ng-objects is ahead in places too. Its Ajax renders once and only appends the targeted containers, its page cache locks per page and answers 503 under contention, and its URLs have no adaptor prefix to begin with. Convergence runs both ways.

## Feature by feature

Effort is to bring the wonder-slim behaviour into ng-objects; "Share" says whether the implementation itself could be common code.

| Area | wonder-slim | ng-objects today | Effort | Share |
| --- | --- | --- | --- | --- |
| Routing chain | Routes, then a fallback, then a not found handler; any handler can decline (`RouteHandler.DECLINED`); passing on to the next server handler | First match wins; no declining (a null response throws); public resources hard-wired as the no-route case | Small | The chain logic, yes |
| Route parameters | Positional (`RouteURL`); named parameters designed (`route-links` branch) | Positional (`NGParsedURI`); `:name` advertised in the javadoc but not implemented; typed routes designed (`docs/routing.md`) | Small | Yes: one URL class, one design |
| Development 404 and welcome page | Welcome page at an unmapped `/`, a 404 page listing the mapped routes, development only | A static welcome page at `/` in every mode, production included; a plain-text 404 | Small | The design; the pages are framework components |
| Resource responses | ETag and `304`, single ranges (`206`/`416`/`If-Range`), files over 1 MB streamed | Whole file read into memory, `max-age=3600` for everything, no ETag, no ranges | Small | Yes: plain HTTP logic over bytes and streams |
| Resource cache | LRU of 64 MB (configurable), missing paths remembered, bounded | Unbounded map in production, missing paths remembered | Small | Yes |
| Content stamps | `site@3f9c1e07ab.css`, cached `immutable` for a year | None | Small | Yes: `ERXResourceStamps` is plain Java |
| Public resources | The fallback, indexed, re-indexed on a miss in development | Experimental, the `app` namespace hard-coded | Small | Mostly |
| Logging | Console logger, reload4j or logback; `er.logging.*` levels and pattern; log capture at the appender | slf4j API only (slf4j-simple in tests); stdout tee for `/ng/dev/log` | Medium | Yes, the backends are slf4j-level (#153) |
| Stack traces | One formatter for every backend: wrappers unwrapped, plumbing frames skippable | `printStackTrace()` | Small | Yes: `ERXStackTraces` is plain Java |
| Configuration | Layers composed once with each value's origin, change watching, secret masking, key constants (ERXP), obsolete properties reported, plugin lifecycle | `NGProperties` layers with `get`/`getInteger`, a FIXME list covering typing, defaults, redaction and watching; `NGPlugin` lifecycle | Medium | One model for both (#143 meant to bring NGProperties' model to wonder-slim) |
| Project layout | `build.properties` `dir.*` keys, any layout runs from its folder | Maven classpath folders only | Small | The `build.properties` reading, yes |
| Exception page | Java source and the template location | Java source only; the parser's source ranges unused | Medium | Partly |
| Development endpoints | `/log`, `/eval`, `/problems`, dev server registration | The same | Done | Already shared (ng-core) |
| Admin console | Nine sections at `/wonder/admin` with a page registry | `/control`: one page (page cache, sessions, properties) | Large | The data gathering (thread dump, statistics) |
| Ajax | Morphing (Idiomorph), wonder-select, session-expiry banner | `innerHTML` replacement, single-pass rendering of the targeted containers | Medium | The client script, largely |
| Page cache | One cache per session, size bound, memory-pressure valve | Count-bounded (100 pages), locks per page with `503` under contention | Medium | Ideas both ways |
| Locale | `ERXLocale` for formatting and parsing, never the JVM default | None | Medium | Possibly |

## Shareable code

The pattern already works. ng-core and ng-template-parser have no dependencies, and both frameworks use them:

- **ng-core:** wonder-slim's `/eval` and `/problems` endpoints run on `NGEvalSession`, `NGRuntimeProblems` and `NGDevJson`, and Parsley uses its KVC.
- **ng-template-parser:** Parsley parses every WebObjects template with it.

These wonder-slim pieces could join them. Each is plain Java already, or close to it:

| Piece | wonder-slim class | What separating it takes |
| --- | --- | --- |
| Content stamps | `ERXResourceStamps` | Nothing: no framework types |
| Stack trace formatting | `ERXStackTraces` | Its settings read through ERXP; pass them in instead |
| Conditional and range responses | inside `ERXAppBasedResourceRequestHandler` | Lift the `If-None-Match`, `Range` and `If-Range` logic out of `WOResponse` into a neutral result (status, headers, byte range) |
| Bounded resource cache | `ResourceCache`, `BoundedPathSet` | Nothing beyond the resource type |
| Route URLs and matching | `RouteURL`, the matching in `RouteTable` | The URL class is neutral; the chain needs a neutral handler type, or stays per framework |
| Console logging | `ERXConsoleLayout`, the `er.logging.*` reading | The layout is neutral; the keys need one name both frameworks read (#153) |
| Project layout | `ERXProjectLayout` | The `build.properties` reading is neutral; the bundle factory is WebObjects-only |
| Ajax client | AjaxSlim's script, Idiomorph, wonder-select | The page side is the same; the requests and responses differ, so an adapter per framework |

The common elements design (one element body, a holder per framework) is the larger version of the same idea, for dynamic elements. It's waiting on ng's render redesign (`NGOutput`, `NGRenderContext`).

## Where to start

Value for convergence against effort to bring into ng-objects, by quadrant (zones only, not exact positions):

- **Start here** (high value, little effort): resource responses and stamps; the routing chain and development pages; the stack trace formatter.
- **Plan for** (high value, more effort): logging and configuration; Ajax morphing and wonder-select; common elements; locale for formatting and parsing.
- **When convenient** (less value, little effort): public resources; project layout from `build.properties`.
- **Later, if at all** (less value, more effort): admin console sections; the page cache pressure valve.

The top left is mostly plain HTTP and plain Java, already written and tested in wonder-slim, and routing is what ng-objects' own migration notes call known-bad. Logging and configuration are worth more but need the design questions below settled first.

## Open questions

1. **Where shared code lives.** In ng-core, which has no dependencies and which wonder-slim already uses, or in a new neutral module beside it.
2. **Port or extract.** Copying wonder-slim's code into ng doubles the maintenance; extracting it first and having both use it doesn't, but costs a step now.
3. **One routing design.** ng's typed routes (`docs/routing.md`) and wonder-slim's route links (`route-links` branch) are being designed separately, for the same thing.
4. **One configuration model.** Whether both frameworks read properties the same way, and whose model it is (#143).
5. **One set of logging keys.** `er.logging.*`, or a name that isn't wonder-slim's, for both (#153).
6. **URL prefixes.** ng serves resources at `/nr/`, wonder-slim at `/res/`; component actions at `/no/` and `/wo/`. Whether they should match.
7. **ng's welcome page in production.** It's served at `/` in every mode today; wonder-slim's is development only.
