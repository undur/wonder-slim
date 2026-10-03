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
public record Search( String area, String q, Boolean more ) implements RouteAction {

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
- **One routing system**: a mapped `RouteAction` is a `RouteHandler` in the route table's chain, so declining, the
  fallback and not found all work as they do now.
- **URLs in their context**: `url()` uses the current context, `url( context )` a given one. URLs are generated in the
  one place that already shapes short URLs, which is where the base path and complete URLs belong too.
- **The name**: `RouteTable.Route` exists already (a mapped pattern and its handler), hence `RouteAction` and
  `@RoutePattern` above. Names aren't settled.

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

## A possible order

1. Named parameters in patterns (`/items/{id}`). `RouteTable` matches exact paths and `*` prefixes only today, and
   ng-objects has the same gap (ng-objects #16–#19). The plain-Java core (matching a pattern, building a record from
   a URL, building a URL from a record) could be shared with ng-objects.
2. `RouteAction` records: `map`, invocation and `url()`.
3. `wo:link route=… :area=…` at run time, with errors for unknown routes and parameters. Usable with checks at render
   only.
4. `:` in the template parser (ng-objects, then Parsley).
5. The `.apiext` addition, and the checks and completion in Parslips.
