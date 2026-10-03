# Route links

Design notes for routes that templates and Java code link to by type. A route is declared once as a Java type, links to
it are checked by the editor as they're written, and the application generates their URLs and invokes the route when
one is requested. Nothing here is built yet.

What it should give us:

1. **Links only to routes that exist**, with parameters checked against the route's, by name at least.
2. **Completion** of a route's parameter names in the editor.
3. **URL generation**, correct in its context: short URLs, the application's base path (#51), complete URLs where
   the context needs them (in an email).
4. **Invocation**: an incoming URL is matched to its route, and the route does its work.

## What exists today

- `RouteTable.map( pattern, handler )`, where a pattern is exact (`/shops/`) or a prefix (`/news/*`).
- Parameters are read by position: `ri.routeURL().getString( 1 )` is the first path element after the route's own.
- Routes have no names and no parameter names, so there's nothing to link *to*: a link to a route is a string typed
  by hand (`href="/articles/<slug>"`), and a typo, a renamed route or a missing parameter shows up as a 404, if at all.

## A route is a record

The route's parameters are the components of a record, its work is a method on it, and its URL pattern is an
annotation:

```java
@RoutePattern( "/search/{area}" )
public record Search( String area, String q, Boolean more ) implements Routable {

	@Override
	public WOActionResults invoke( RouteInvocation ri ) {
		...
	}
}
```

```html
<wo:link route="Search" :area="bork" :q="$someString" :more="$resultPicker">Search</wo:link>
```

```java
new Search( "bork", someString, true ).url()
```

- **Path and query parameters**: components named in the pattern (`{area}`) are path parameters, the rest are query
  parameters (`/search/bork?q=…&more=true`). Mapping the route checks the pattern against the components, and throws
  on a mismatch: a `{name}` with no component of that name.
- **Linking**: a template names the route by its class, as `pageName="ItemPage"` names a component. Java code builds the
  record and asks it for its URL, so links made in Java are checked by the compiler.
- **Invocation**: an incoming URL is matched against the pattern, its values converted to the components' types, and
  the record constructed (its compact constructor is a natural place for validation). Then `invoke` runs. A value that
  doesn't convert declines the URL (`RouteHandler.DECLINED`, #166), as `RouteURL.getInteger` answers its default for
  one that isn't a number (#169).
- **One routing system**: a mapped `Routable` is a `RouteHandler` in the route table's chain, so declining, the
  fallback and not found all work as they do now.
- **URLs in their context**: `url()` uses the current context, `url( context )` a given one. URLs are generated in the
  one place that already shapes short URLs, which is where the base path and complete URLs belong too.
- **The name**: `RouteAction` (the direct action handing requests to the route table) and `RouteTable.Route` (a mapped
  pattern and its handler) exist already, hence `Routable`: a record you can route to, as a `Runnable` is one you can
  run. The experiment below takes the pattern as an argument instead of the `@RoutePattern` annotation.

### Checking in the editor

This is what the record buys: Parslips knows a route **without running the application**. The `route` binding resolves
to a class the way a component name does, the record's components are the parameters to complete and check (names, and
types too: `$resultPicker` against `Boolean`), and the pattern is an annotation value, which is always a literal it can
read.

The `.apiext` format needs a way to say "the `:` attributes are the components of the route named by `route`", so the
editor knows to check them. That's an addition to the format (apiext-format).

### Template syntax: `:name`

Parsley's template parser accepts `?` as the start of a binding name, but not `:`: `parseBindings()` in ng-objects'
`NGTemplateParser` throws "Expected binding key" at `:id`. Accepting `:` the way `?` is accepted is a small change
there, then a Parsley release. ng-objects' own link element could adopt the same syntax.

## The experiment (route-links branch)

A first take on the mechanism, to see how it feels. The route is referenced by a key path, so the editor can resolve it
statically (`route="$routes.search"`), rather than by class name.

```java
public record Search( Area area, String q, Boolean more ) implements Routable {

	@Override
	public WOActionResults invoke( RouteInvocation invocation ) {
		...
	}
}

public final Endpoint<Search> search = Endpoint.of( "/typed/search/{area}", Search.class );
```

A record that's only data gets its action as a lambda instead, which is also how several routes share one parameter
record (a page and its JSON, say):

```java
public final Endpoint<ItemParameters> item = Endpoint.of( "/typed/item/{id}", ItemParameters.class, Endpoints::show );
```

The pattern is an argument rather than an annotation on the record: annotations are a last resort, and the pattern
then sits next to the route's name in the holder.

```html
<wo:route route="$routes.search" :area="books" :q="$query" :more="$true">Search books</wo:route>
```

```java
routes.search.url( new Search( Area.films, "noir", null ) )   // "/typed/search/films?q=noir"
```

- The experiment is kept apart from the existing routes: it lives in its own package, `er.extensions.experimental.routing`
  (ERExtensions), and the routes package is unchanged. "Endpoint" is a working name that keeps the concept separate
  while experimenting. Converging with the existing routes would most likely make it the `Route`, refactoring the
  existing code where needed.
- `Endpoint<P extends Record>`: a `RouteHandler`. `endpoint.mapInto( routes )` maps it under the part of its pattern
  before the first parameter (`/typed/search/*`), since the route table matches exact paths and prefixes, and the
  endpoint declines a URL that doesn't match its whole pattern. It builds URLs, builds its parameter record from
  values by name, and invokes the record's `invoke` (`Routable`), or the action it was given. A URL whose values don't
  convert, or that the record's constructor refuses, is declined.
- `ERXRouteHyperlink` subclasses `ERXWOHyperlink`, registered as its own tag, `<wo:route>`, so `<wo:link>` is untouched
  and the experiment stays apart. `route` is required, and its `route` and `:` bindings become a computed `href`. `?`
  bindings are still added to the query. `href`, `action`, `pageName` and the direct action bindings aren't accepted:
  the URL comes from the endpoint. (`<wo:route route="…">` repeats itself; a binding named `to` would read better.)
- KVC doesn't read static fields, so templates reach routes through an instance: the playground's base page has
  `routes()`, returning the object whose fields are the routes. Every step of `$routes.search` has a declared type, so
  the editor can follow it to `Endpoint<Search>` and from there to the record's components.
- The template parser accepts `:` binding keys on ng-objects' master (not yet released). ERExtensions depends on
  that parser (0.1.4-SNAPSHOT) directly on this branch.
- The playground's `/typed` page links to two routes and shows what a route received when it rendered the page.

Checked in the playground:

- Links render the route's URL, short, with path values encoded as path segments and query values as query values.
  Absent query parameters are left out.
- Invocation converts values to the components' types: strings, ints, booleans and an enum. Wrong values decline
  (404), and so does a value the record's compact constructor refuses.
- An unknown parameter, a missing path parameter, a value of the wrong type, `route` together with `href`, and `:`
  bindings without `route` are each an error when the link renders, naming the route and its parameters.
- Ordinary links, component actions included, work through the new element.

Found along the way:

- Parslips reads `:id` as `id`, so a link with both `:id` and an HTML `id` is reported as a duplicate binding.
- `WOHyperlink` doesn't escape `&` in `href`, so a route URL with two query parameters renders a raw `&` in the
  attribute. Browsers accept it, but it isn't strictly valid HTML.
- Removing the `route` key from short URLs happens in `Endpoint` for now. It belongs in `ERXShortURLs`, as the reverse
  of `canonicalize()`.

## Other approaches considered

Before the record, three ways for the editor to learn the routes were on the table:

- **`Route` constants** in Java (`public static final Route ITEM = Route.of( "/items/{id}" )`), named in templates by a
  key path. The editor would have to read the pattern from the field's initializer, so it must be a literal right there.
- **Annotations** naming a route (`@Route( name = "item", path = "/items/{id}" )`) on a page class or a handler
  method, named in templates by a string.
- **A routes file** in the project (`item  /items/{id}  ItemPage`), as Play does: simple for every tool, but a second
  language beside Java, with handlers named by strings.

The record keeps what was good in each: routes are plain Java and refactoring-safe, found by type, and the pattern is
a literal the editor can always read. Parameter wrappers in the record (`QueryParameter<String>`,
`PathParameter<String>`) were also considered; the pattern already says which parameters are path ones, and plain
components keep construction from Java natural.

Prior art worth a look: Ktor's type-safe Resources (an annotated class is the route and builds its own link), and
Phoenix's verified routes (links checked at compile time).

## Open questions

1. **The template prefixes.** The route knows its path parameters from its query parameters, so `:` and `?` in the
   template say nothing it doesn't. Either `:` for every declared parameter, with `?` staying free and unchecked as on
   `WOHyperlink` today (a parameter then moves between path and query without touching templates), or both prefixes,
   as a check.
2. **Required and optional.** Path parameters are always required. Query parameters could be optional by being boxed
   (null when absent), or primitives with defaults.
3. **Registration.** An explicit `routes.map( Search.class )`, or found by scanning. Either way, a link to a route
   that isn't mapped is an error at render, not a dead URL.
4. **Types.** Which component types convert (strings, numbers, booleans, enums, dates), and whether there's a way to add
   more.
5. **Routes beyond pages**: a JSON endpoint or a redirect is linked to the same way, presumably.
6. **Which elements take `route`**: `wo:link`, `wo:form` (a GET form to a route, its fields named after the
   components), an element rendering a bare URL for scripts, the Ajax elements' URLs.

## Compared with other frameworks

From memory, not checked against each framework's current documentation.

- **Ktor Resources** is nearly the same design: a class is the route (`@Resource("/search/{area}") class Search(…)`),
  its properties are the parameters, `{name}` segments are path parameters and the rest query parameters, and a link
  is built by constructing the class (`href(Search("books"))`). The pattern is an annotation, handlers are registered
  apart (`get<Search> { }`), conversion goes through a serialization library, and resources nest through a parent
  property.
- **TanStack Router** is the closest on the linking side: `<Link to="/posts/$postId" params={…} search={…}>`, path
  and query ("search") parameters apart, query values validated by a schema, and every link checked by the TypeScript
  compiler.
- **Play** declares routes in a routes file and generates a typed reverse router (`@routes.Clients.show(42)`),
  checked at compile time. **Phoenix**'s verified routes (`~p"/users/#{user}"`) are checked at compile time too,
  paths only.
- **ASP.NET Core**'s `<a asp-action="Details" asp-route-id="5">` marks route parameters with an attribute prefix, as
  `:id` does here, checked at run time (and by Rider in the editor).
- **Rails** (`user_path(@user)`) and **Django** (`{% url 'item' id %}`) find a bad link at run time. **Spring**'s
  template URLs are strings, and its `fromMethodCall(…)` is typed in Java only. **Wicket** links to page classes, with
  string parameters.

Where this is strong: one declaration in plain Java (no routes file, code generation or annotation), one conversion
for both directions so `url()` and invocation can't disagree, validation in the record's constructor for both
directions, and links from Java checked by the compiler.

Where it's behind: template links aren't checked in the editor yet (parslips#12), templates reach routes through an
instance, there's no HTTP method handling, no composition, a fixed list of parameter types, and matching is a prefix
plus "decline what isn't mine", with no precedence rules or conflict detection.

## The router API (proposal)

Covering #173 (matching), #179 (preconditions), #174 (conflicts), #176 (groups) and #177 (methods) in one shape, so the
parts built later slot in. Nothing here is built yet. Decisions still open are marked.

### Scopes

A scope is a value: a path prefix, preconditions (a host, methods), a trailing slash policy and wrappers. The router is
the root scope, and every scope can make narrower ones and map routes:

```java
final Router routes = new Router();

routes.map( "/", Main.class );                                   // any host, any method (today's behaviour)
routes.map( "/items/{id}", ri -> ItemPage.page( ri, ri.parameter( "id" ) ) );
routes.map( "/news/*", news );                                   // wildcard: everything beneath /news/

routes.methods( POST ).map( "/hooks/github", hooks::github );    // other methods: 405
routes.host( "{tenant}.example.com" ).map( "/", TenantHome.class );

routes.host( "admin.example.com" ).group( "/manage", manage -> {
	manage.wrap( requireLogin );                                 // wraps every route in the group
	manage.map( "/users", Users.class );
	manage.map( "/users/{id}", User.class );
} );

routes.trailingSlash( TrailingSlash.REDIRECT ).map( "/docs/", Docs.class );   // per route; the default is IGNORE
```

- **Parameters by name:** `invocation.parameter( "id" )`, and `invocation.parameters()` for all of them. Host parameters
  (`{tenant}`) are included. A host parameter and a path parameter with the same name are refused when mapped.
- **Wrapping:** a `RouteFilter` gets the invocation and the next handler, and either answers itself (a redirect to a
  login page, a 403) or passes on: `( invocation, next ) -> loggedIn( invocation ) ? next.handle( invocation ) : login()`.
  A nested group's filters run inside its parent's.
- **Endpoints** are mapped like any route, and their records get the parameters by name, host parameters included.

### Outcomes

For a request (method, host, path), in this order:

1. **Match:** the candidates are the routes whose path and host match, in precedence order: a literal segment before a
   parameter before a wildcard, then, for the same path shape, a route with preconditions before one without. The
   first that answers wins. One that declines (`RouteHandler.DECLINED`) passes the URL to the next candidate.
2. **405:** a path matched, but no route there accepts the method. The answer is `405` with an `Allow` header. `HEAD` is
   accepted wherever `GET` is.
3. **308:** under the redirect policy, the path matched only in its other trailing-slash form.
4. **Declined:** nothing matched, or every candidate declined. The router declines, and what comes after it (the
   fallback, not found) gets the URL.

### Decided

- Trailing slashes: a route's pattern declares its form, generated URLs use it, and the policy (**ignore** by default,
  **redirect** or **strict**) is set for the router and per route. `/` never changes form.
- Paths are case-sensitive, hosts aren't. Segments are percent-decoded after splitting, so `%2F` stays in its segment.
  A parameter never matches an empty segment.
- `/x` is exact, and `/x/*` matches `/x/` and anything beneath it, but not `/x`, as today.
- The router's core takes plain strings (method, host, path) and returns plain values: no WebObjects types, so
  ng-objects could use the same code.

### Open

1. **Conflicts and declining.** #174 refuses two routes with the same shape, but declining exists so the next candidate
   can have the URL. Under #174 the next candidate can only be a less specific route. Either same-shape routes are
   refused, and alternatives at one shape become one route whose handler tries several
   (`RouteHandler.firstOf( pages, products )`), or they're allowed and the mapping order breaks ties.
2. **Where the router lives.** Beside `RouteTable` in the experimental package, mapped into the existing table as one
   handler that declines when nothing matches, so `er.extensions.routes` stays as it is until convergence. Or replacing
   `RouteTable`'s matching now.
3. **Endpoints in groups.** An endpoint's URL depends on its group's prefix and host. Either:
   - (a) endpoint patterns are absolute, and a group checks that they start with its prefix
   - (b) they're relative, completed when mapped, and `url()` before mapping is an error
   - (c) endpoints are declared from a scope (`shop.endpoint( "/items/{id}", ShopItem.class )`), so the full pattern
     and preconditions are known from the start. A group's parameters (`/shops/{shop}`) are then components of the
     record, checked when it's declared. An endpoint with a host pattern generates a complete URL to that host, its
     host parameters taken from the record.
4. **Which host.** The `Host` header, read in one place. `x-forwarded-host` only once forwarded headers are restricted to
   trusted front ends (#67).

## Next steps

Tracked in #178. The whole API is designed in the first round, host patterns, methods and groups included, so later
features slot into an established shape instead of redesigning it. Built one at a time.

1. **Static members in KVC** (#172). Our own association class can invoke a static method or read a static field when the
   key names one, as ng-objects' KVC already does for methods (`NGKeyValueCoding.locateMethod`). Routes can then be
   static: constants on an interface the pages implement reach templates as `$search`, with no holder object.
   ```java
   public interface PlaygroundRoutes {
   	Endpoint<Search> search = Endpoint.of( "/typed/search/{area}", Search.class );
   }
   ```
2. **Matching** (#173). A real router: path segments matched as a tree, a literal before a parameter before a
   wildcard, so the result doesn't depend on the order of `map()` calls, with parameters by name. Declining works as
   now: the next candidate in precedence order, then the fallback, then not found. Existing patterns keep their
   meaning (`/x` exact, `/x/*` everything beneath it). Trailing slashes: a route's pattern declares its form, and
   generated URLs use it. A request in the other form is handled by a policy, for the application and per route:
   **ignore** (the default, both forms match), **redirect** (`308` to the declared form, keeping method and body) or
   **strict**. `/` never changes form. Paths are case-sensitive, segments are percent-decoded after splitting, and a
   parameter never matches an empty segment. ng-objects has the same gaps (ng-objects #16–#19), so the router is a
   candidate for plain Java shared by both.
3. **Preconditions** (#179): a route can require a host, exact or a pattern (`{tenant}.example.com`, its parameters
   reaching the route like path parameters), or a set of HTTP methods. Failing a host precondition means the route
   isn't there for this request. Failing a method means the path is there, so with no route at the path accepting the
   method the answer is `405` with an `Allow` header. Among routes with the same path shape, one with preconditions
   comes first. Host routing (#59) and declared methods (#177) are its use cases.
4. **Conflict checks at startup** (#174). With the router, a route whose shape another route already has (the same literals
   and parameters in the same places) is refused when it's mapped.
5. **Objects as parameters** (#175). Converters, both ways (a value to URL text, and back), registered per type, so an
   application can register its own: `record ItemPage( Item item )` with `{item}` gets an `Item`, and a link passes
   `:item="$item"`. An object that isn't found declines, so a missing record is a 404.
6. **Composition** (#176). Groups of routes sharing a prefix (`/admin`), preconditions (a host) and whatever wraps every route in the group: access
   control, cache headers, logging. Open: whether a group's parameters (`/shops/{shop}/items/{id}`) are handed to its
   routes, and how that looks as records.
7. **HTTP methods** (#177): a route declares the methods it accepts, `GET` and `HEAD` by default, through the
   preconditions (#179). No dispatch beyond that is needed for it.

Built so far: named parameters in patterns, endpoints with `map`, invocation and `url()`, `<wo:route>` checked at
render, and `:` in the template parser. Still to do beyond the list: the `.apiext` addition, and the checks and
completion in Parslips.
