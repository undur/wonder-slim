# Routing

> **Experimental.** This describes the router in the ERRouting framework on the `route-links` branch (package
> `er.routing`), built beside the existing routes to replace them. Details will change. The design notes are in
> [ROUTE_LINKS.md](ROUTE_LINKS.md), and the work is tracked in #178.
>
> The example application [Bookclubs](../Bookclubs) uses everything described here, and most examples below are taken
> from it.

A route connects a URL to the code answering it. The router matches a request's path (and, if a route asks for it, its
host, method, scheme or headers) to a route, hands the route the values in the URL as parameters, and generates URLs
for links to the route, so templates and Java code link to routes rather than writing URLs by hand.

## Setting up

The application's constructor declares its routes:

```java
public Application() {
	ERXRouter.declare( routes -> {
		routes.map( "/", Main.class );
		routes.map( "/about", AboutPage.class );
	} );
}
```

That's a working application: `/` answers with `Main`, `/about` with `AboutPage`. A route something links to gets a
name, a constant, which the same call takes:

```java
public interface Routes {

	default Object routes() {                        // $routes.… in templates
		return RouteKeys.of( Routes.class );
	}

	PlainRoute home = Route.plain();
	PlainRoute book = Route.plain();
	Route<Search> search = Route.of( Search.class );
}
```

```java
ERXRouter.declare( routes -> {
	routes.map( "/", Routes.home, Main.class );
	routes.map( "/books/{book}", Routes.book, BookPage.class );
	routes.route( "/search", Routes.search, SearchPage.class );
} );
```

Pages implement `Routes`, and link to them:

```html
<wo:route to="$routes.book" :book="$book"><wo:str value="$book.title" /></wo:route>
```

An application imports from `er.routing` (the router, groups, routes), `er.routing.options` (what's passed to a route
or a group: conditions, policies, behaviors) and `er.routing.conversion` (converters).

`routes()` gathers the constants under one name in templates, clear of a page's own keys (a page's `book` is its
book, not the route), so `$routes.book` is the constant: key-value coding doesn't read static fields, and `RouteKeys`
answers a key with the constant of that name. Java code uses the constants directly:
`Routes.book.url( Map.of( "book", book ) )`.

Routes can be grouped by where they live, each group a nested interface with a key of its own:

```java
public interface Routes {

	default Object routes() {
		return RouteKeys.of( Routes.class );
	}

	RouteKeys web = RouteKeys.of( Web.class );     // $routes.web.news
	RouteKeys app = RouteKeys.of( App.class );     // $routes.app.receipts

	interface Web {
		PlainRoute news = Route.plain();
	}

	interface App {
		PlainRoute receipts = Route.plain();
	}
}
```

Java code uses `Routes.Web.news`. The groups are only names: where a route answers is its pattern's business.

A constant no declaration gives a pattern to stops the application's launch, naming it, and so does one given two. One a
declaration may leave without a pattern (a route mapped in development only) says so: `Route.plain().optional()`.

### Changing routes while the application runs

In development, routes are declared again when their classes change: the folders of the classes mapping routes (a
plugin's too), a constants interface's, or a route's record's. Change a pattern, add a route (a new constant included),
or add a component to a record, and the next request has it, with no restart. The constants follow the routes. Adding a
constant makes the hot swap run the constants interface's initializer again, so they're new objects, which templates and
`Routes.book` read fresh (a constant kept in a field of its own goes stale then). Changing an existing constant
(`.optional()`) doesn't: the hot swap leaves an initialized one alone, so that takes a restart. Every declaration runs
again, in the order they were first made (an application and its plugins), into a new router that replaces the current
one once they all succeed. Building is cheap (5,000 routes take a few milliseconds), and the check for changes reads the
class files in those folders at most once a second.

- A declaration that fails (two routes matching the same requests, say) leaves the previous routes in place, and routed
  requests answer with why until the routes are declared again, so the old routes aren't tested by mistake.
- The application's routes are changed only in a declaration. Outside one, mapping a route, reaching the application's
  routes or a table, registering a converter, adding a filter, naming a group, joining one or setting a `whenInvalid`
  throws, since declaring the routes again would lose it.
- A new class file's code may reach the running application a beat after it's written (the hot swap), so a declaration
  made within three seconds of a change is made once more after that.

`er.routing.reload` turns it on or off. It's on in development mode, and off otherwise, where routes are declared once.

### Beside the route table

The router is mapped into the existing route table as one route, on first use. What the router has no route for passes
on to the table's other routes, then its fallback and not found handling, so the router and existing routes work side by
side. The table's `hasRouteFor()` answers for the router's routes that answer any request at their path (whatever its
method), not for every URL: a route on a host, a scheme or a header doesn't claim a path for all requests.

`ERXRouter.defaultRouter().routes()` describes every route in precedence order (`RouteDescription`): its pattern,
conditions, trailing slash policy and table, the route itself, a typed route's record class, the sites it takes posts
from (`CrossSite`), and whether it reports fields that don't convert (`Fields.REPORTED`).

## Routes

A route has parameters and an answer. Its parameters are its pattern's (`map`), or a record's components, which add
query parameters and a form's fields, typed (`route`). Its answer is a page, or a handler for anything else:

| | A page answers | A handler answers |
|---|---|---|
| **The pattern's parameters** | `map( pattern, route, BookPage.class )` | `map( pattern, route, ri -> … )` |
| **A record's** | `route( pattern, route, BookListPage.class )` | `route( pattern, route, ( books, ri ) -> … )` |

The route's constant is `Route.plain()` for the pattern's parameters, `Route.of( Record.class )` for a record's. A
route nothing links to needs no constant: `map( pattern, ri -> … )` and `route( pattern, Record.class, … )` make one of
their own (an API's routes, a webhook).

### Page routes

```java
club.map( "/books/{book}", Routes.book, BookPage.class );
```

A new instance of the page answers, its route parameters set on it by name: a public field,
a setter (`setBook( Book )`) or a method taking the value (`book( Book )`, a page's fluent setter), converted to its
type. A page lacking one is refused when the route is declared. A parameter that doesn't convert, or names nothing,
declines the request. Query values aren't set, so a URL can't write a page's fields: a page reads those itself, or the
route is a record.

### Handlers

```java
club.map( "/rules/", Routes.rules, ri -> text( 200, "Rules of %s: read the book.".formatted( ri.parameter( "club" ) ) ), TrailingSlash.REDIRECT );
```

The handler gets a `RouteInvocation`, and answers with a response or a page (`ri.page( PageClass.class )` makes one).

- `ri.parameter( "id" )` is a route parameter's text, and `ri.parameter( "book", Book.class )` its value, with the
  router's converters (see [Parameter types](#parameter-types)). A value that doesn't convert, or names nothing,
  declines the request, without code in the route.
- `ri.query( "page", Integer.class )` is a query parameter's or form field's value, null when it's absent or empty. One
  that doesn't convert, or is given more than once, declines too.
- `ri.route()` is the route invoked: `ri.route() == Routes.books`, for a filter deciding by route or navigation marking
  the current page.
- Throwing `Declined` declines from anywhere inside a route, as returning `RouteHandler.DECLINED` does.

`redirect( pattern, route )` answers an old URL with a `308` to a route's (a browser and a search engine remember it,
and it keeps the method), its parameters the route's by name (checked once the routes are declared), the query string
kept. A value the route doesn't take declines:

```java
club.redirect( "/book/{book}", Routes.book );
```

A route whose first path element is a request handler's key (`/wa/…`, `/wo/…`) is refused when it's mapped: the
request handler would get every request for it.

### Patterns

| Pattern | Matches |
|---|---|
| `/books/new` | exactly that path |
| `/books/{id}` | one path element in place of `{id}` |
| `/books/{id}.json`, `/book-{id}` | a parameter within an element, the text around it literal |
| `/files/*` | `/files/` and everything beneath it, the rest available as `ri.parameter( "*" )` |
| `/files/{path*}` | the same, the rest named, so a record takes it and links give it |

- One parameter to an element. A parameter never matches an empty text, so `/books//edit` doesn't match
  `/books/{id}/edit`.
- Two parameters within literal text at the same place: the one with more literal text comes first (`{a}.min.json`
  before `{a}.json`). Literal text on opposite sides (`pre-{a}` and `{a}.json`, which both match
  `pre-7.json`) is refused as a conflict, since neither comes first.
- `/files` is the wildcard's other trailing slash form, which the route's policy decides: matched with nothing beneath
  (the default), redirected to `/files/`, or not matched (see [Trailing slashes](#trailing-slashes)).
- A link to a named wildcard gives its remainder (`:path="minutes/2026.txt"`), each element encoded.
- Path elements are decoded before they're handed over: `/books/a%2Fb` gives a parameter `a/b`, and `%2F` doesn't
  split the path. A wildcard doesn't match a path with an encoded `/` in its remainder, since the remainder would then
  read as more elements than the path had (`..%2F..%2Fetc`).
- Paths are case-sensitive.
- A path with a `.` or `..` segment (or its encoded form) matches no route. Browsers resolve them away, so such a path
  was written by hand, and a wildcard's remainder never carries one: a handler serving files from it is safe from `../`.
  Generating a link with `.` or `..` as a parameter's value is an error. A `%`, `\` or control character is encoded
  (`50%` is `50%25`), though some servers and adaptors refuse those in a path, the bundled Jetty adaptor among them
  (it answers `400` before the application sees the request): `er.routing.strictPathValues=true` makes generating such a
  link an error instead, for an application behind one.

### Which route answers

The order routes are mapped in doesn't matter. At the first path element where two patterns differ, a literal comes
first, then a parameter within literal text, then a whole-element parameter, then a wildcard. `/books/new` answers
`/books/new`, `/books/{id}.json` answers `/books/7.json`, and `/books/{id}` answers `/books/7`, whichever was mapped
first. A catch-all (`/*`) comes last.

### Declining

A route that has no answer for a URL declines, and the next matching route gets it. Bookclubs' club pages decline names
they don't have, and the club's catch-all answers with its own not found page:

```java
club.route( "/{name}", Routes.clubText, BookclubRoutes::clubText );   // declines a page the club doesn't have
club.map( "/*", ri -> /* the club's not found page */ );
```

When every route that matched declines, the URL passes on, and in development the not found page lists each of them
with why it passed it on (a parameter naming nothing, a value the record refused, its handler declining). Any route
handler can add a line with `RouteTable.explainDecline( request, … )`. A handler never returns null.

## Typed routes

A typed route's parameters are the components of a record. The record is what a link passes to the route and what the
route receives, each value converted to its component's type:

```java
public record Books( Club club, Sort sort, Integer page, List<String> author ) {}

Route<Books> books = Route.of( Books.class );                                        // Routes
club.route( "/books/", Routes.books, BookListPage.class, TrailingSlash.REDIRECT );   // the declaration
```

- **Path and query parameters:** components named in the pattern (`{club}`, `{id}`) are the path's, and must be
  there, a group's parameters included (see [Group parameters](#group-parameters)). The other components are query
  parameters (`?sort=author&page=2`), or a form's fields.
- **Types:** anything the router's converters convert (see [Parameter types](#parameter-types)). A query parameter
  can be absent, so it's a boxed type or an object, and null when absent or empty.
- **The answer:** a page gets the record's components set on it by name, as a page route gets its parameters: a public
  field, a setter or a method taking the value. One that's null (a query parameter absent) isn't set, so the page keeps
  its own default (`public Sort sort = Sort.title;`). A page lacking a member for a component is refused when the route
  is declared, apart from a group's parameter. A handler gets the record, for an answer of its own, and several routes
  can share a record (a page and its JSON):

  ```java
  public record DeleteBook( Club club, Book book ) {}

  club.route( "/books/{book}/delete", Routes.deleteBook, BookclubRoutes::deleteBook, Method.POST );
  ```

  A record is only parameters: what answers is named where the route is declared.

### Bad input

A route parameter that doesn't convert, or names an object that doesn't exist, declines the request: the
URL is wrong, and another route may answer it. Other bad input goes one of three ways:

- **Nothing set:** it declines too, so a route that never looks at its input's errors fails safe. A bookmark with a
  renamed enum value (`?sort=year`) is a 404.
- **`whenInvalid`:** the route answers it itself: a query parameter or field that doesn't convert, and values the
  record's constructor refuses (an `IllegalArgumentException`, or a `NullPointerException` from `Objects.requireNonNull`
  in the constructor, for a value it requires).

  ```java
  club.route( "/search", Routes.search, BookclubRoutes::search ).whenInvalid( ( invocation, reason ) -> … "What are you searching for?" … );
  ```

  The reason names what was wrong: `Objects.requireNonNull( q, "q" )` says so itself, and a `NullPointerException`
  without a message is given one naming the absent components (`Absent: [q]`). Only `Objects.requireNonNull` called by
  the constructor counts: a `NullPointerException` from anywhere else is a bug, and a `500`.
- **`Fields.REPORTED`:** the record is built anyway, a field that doesn't convert null, and the route hears about it in
  `invocation.conversionErrors()` with the text that was given, so it can show a form again with its errors. A group's
  `Fields.REPORTED` reaches its routes, and `Fields.DECLINED` (the default) puts a route back. A plain route reads its
  own fields, so it refuses the option.

### Parameter types

The router's converters turn a parameter's value into URL text and back. `String`, `Integer`/`int`, `Long`/`long`,
`Boolean`/`boolean`, `Double`, `LocalDate`, `Instant`, `UUID` and enums are built in, and a declaration registers the
application's own, before the routes taking them. A converter registered for a class or an interface converts its
subclasses and implementations.

```java
routes.converters().register( Club.class, Converter.of( id -> Library.club( id ).orElse( null ), Club::id ) );
```

`fromString` throws `IllegalArgumentException` for text that isn't a value of the type, and returns null for a value
that doesn't exist; either declines the request. A type that has no converter is an error when the route is declared.

The converters are the router's, shared by every declaration: the application's and its plugins'. A route sees those
registered before it's declared, and the application declares before the frameworks' plugins, so a framework registers
converters for its own types only (helium's for its inspection routes), never for a type the application's routes may
take, such as a superclass of its entities.

A converter can see the request it converts for (`Converter.scoped`): the request's other route parameters, converted
once each, and objects provided for the request. Bookclubs finds a book in the club the URL names, so another club's
book isn't there (`/clubs/kronan/books/2`, one of acme's, declines):

```java
routes.converters().register( Book.class, Converter.scoped( ( id, scope ) -> Library.book( Integer.parseInt( id ) )
		.filter( book -> scope.parameter( "club" ) == null || book.club().equals( scope.parameter( "club" ) ) )
		.orElse( null ), book -> String.valueOf( book.id() ) ) );
```

`scope.get( WOContext.class )` and `scope.get( WORequest.class )` are the request's, and the application provides more:
`routes.converters().provide( EOEditingContext.class, scope -> … )`, an editing context to fetch in. A provided object
is made once per request, and ending it (an editing context's disposal) is the application's, since the request's page
renders after the route has answered. Parameters are converted path first (a group's before its routes'), then query, so
a converter finds those it looks in. Without a request (a link's text), the scope is empty.

Parsing takes a type's common forms: `007` is 7, an ISO date may have milliseconds, a UUID may be upper case, and a
boolean is `true`, `false`, or `on` (what a checkbox without a `value` posts).

A route parameter has one URL per value: `/books/007` and `/books/+7` are answered with `308` to `/books/7`, keeping
the query string, so each object has one URL. A converter whose type is written more than one way says so
(`canonical()` is false, as for `Double`), and its values aren't redirected. Query parameters and fields aren't held to
one text.

### Repeated parameters

A query parameter or field given several values (`?author=Laxness&author=Undset`, a form's checkboxes) is a `List`
component of a type with a converter, as `Books.author` above. It has every value, in order, and is an empty list when
there are none (never null). An empty value (`?q=`, a field left empty) is no value, for text too. A value that doesn't
convert is bad input. A link repeats the parameter for each value: `:author="$authors"` takes a list (an `NSArray` too)
or one value. A route parameter has one value, so it can't be a `List`.

A component that isn't a `List` takes one value: given several (`?sort=title&sort=year`, two fields of the same name),
it's bad input rather than the first one silently. A checkbox doesn't need the hidden field some frameworks pair it
with: an absent `Boolean` is null, and a checked one posts `on`.

## Links

### In templates

`<wo:route>` links to a route. Each `:` attribute is one of its parameters:

```html
<wo:route to="$routes.book" :club="$club" :book="$book"><wo:str value="$book.title" /></wo:route>
<wo:route to="$routes.books" :club="$club" :sort="author">Sort by author</wo:route>
<wo:route to="$routes.admin" :club="$club" ?key="letmein">Admin</wo:route>
```

- A constant (`:sort="author"`) is checked against the parameter's type, so `author` must be one of the enum's values.
- Building a link's URL doesn't construct a typed route's record: only its route parameters are needed.
- A link gives every route parameter, a group's included. The values come from the page, as any binding's do, so a
  page rendered again (after a component action) links the same way.
- `?` attributes add query parameters the route doesn't declare. A typed route's own parameter as a `?` attribute is an
  error: bound as `:` it's checked.
- A text parameter takes a value the converters convert (a number, an object with a converter) as its text. Another
  object is an error, rather than its `toString()` in a URL.
- A parameter the route doesn't have, a missing route parameter, or a value of the wrong type is an error when the link
  renders, naming the route and its parameters.
- `<wo:route>` takes its URL from the route only, so it has no `href`, `action` or `pageName`. Other links are
  `<wo:link>`.

### From Java

```java
final String url = Routes.book.url( Map.of( "club", club, "book", book ) );
final String sorted = Routes.books.url( new Books( club, Sort.author, null, List.of() ) );
return Routes.book.redirect( Map.of( "club", club, "book", book ) );   // 303, post, redirect, get
```

A typed route takes its record, and any route takes values by name. They use the current request's context, or one given
(`url( values, context )`). Outside a request (a background job), a route's URL is its path in the application's URL
form, and a route on a host's is complete, which needs the public address. Values by name leave out the query parameters
a record would have as `null`. URLs are short (`/books/6`) when the application's short URLs are on.

### The public address

A complete URL needs the address people reach the application at, which the application can't always see. Set it:

```
er.routing.publicAddress=https://bookclubs.example.com
```

A scheme, a host, and a port if it isn't the scheme's. With it set:

- Complete URLs (a context generating them, an email's) have its scheme, host and port.
- `completeURL( record )`, or `completeURL( values )` on any route, makes a complete URL without a request, for a
  background job's email.

Without it, a complete URL has the request's host (or the machine's name), and `completeURL` outside a request fails,
naming the property. Relative
links don't change either way.

### Forms

`<wo:routeForm>` posts to a route. It takes `to` and `:` parameters as `<wo:route>` does, and a typed route's other
components are the form's fields:

```java
public record CreateBook( Club club, String title, String author, Integer year ) {}

club.route( "/books", Routes.createBook, BookclubRoutes::createBook, Method.POST, Fields.REPORTED );
```

```html
<wo:routeForm to="$routes.createBook">
	<input name="title"> <input name="author"> <input name="year">
</wo:routeForm>
```

The method is `post` unless the form binds another. A `method="get"` form's query parameters (`:sort="$sort"`) are
hidden fields, and its action is the path alone, since a browser replaces a get form's action query with its fields.
`href`, `action` and the direct action bindings aren't accepted: other forms are `<wo:form>`.

The route checks the form's fields, and shows the form again with what's wrong, a field that didn't convert included
(`invocation.conversionErrors()`, with `Fields.REPORTED`). Otherwise it answers with a redirect to the result.

### Posts from other sites

A route doesn't take a request that changes things (POST, PUT, PATCH, DELETE) from a page on another site: it's
answered with `403`, so a page elsewhere can't post a form to the application with the user's cookies. The browser says
where a request comes from (`Sec-Fetch-Site`, and `Origin`, compared with the request's host and the public address),
and a request with neither isn't from a browser page (curl, a server's webhook), so it's taken. For the same origin,
the browser's `Sec-Fetch-Site` decides when it's sent, since it accounts for the scheme.

A route or a group says which sites it takes them from:

- `CrossSite.SAME_ORIGIN`: its own origin only, the default.
- `CrossSite.OWN_HOSTS`: the application's other hosts too, for an application on several (see
  [Routing by host](#routing-by-host)).
- `CrossSite.ALLOWED`: any site.

A route's own setting wins over its group's. This covers routes: component actions and the route table's other routes
aren't checked.

### Other sites' scripts (CORS)

`CrossOrigin.allow( "https://partner.example" )` on a route or a group lets another site's scripts call it from a
browser: its requests are answered with `Access-Control-Allow-Origin`, and the browser's preflight (`OPTIONS` with
`Access-Control-Request-Method`) with the methods the routes at the path take and the headers it asked for.

```java
final RouteGroup api = club.group( "/api", TrailingSlash.STRICT, CrossSite.ALLOWED, CrossOrigin.allow( "https://partner.example" ) );
```

A named origin allowed may post to the route whatever its `CrossSite` level: allowing a partner's script to call a route
is allowing it to change things through it. `CrossOrigin.ANY` allows every origin's scripts to read, without
credentials, and doesn't waive `CrossSite`: a form posted from any site carries the user's cookies by the browser's
rules. `withCredentials()` lets named origins send the user's cookies. A route with named origins varies by `Origin` on
every answer, so a cache keeps one origin's answer from another.

## Conditions

A route can require more of a request than its path. Conditions are declared with the route, among its options (as is
its trailing slash policy):

```java
club.route( "/books/{book}/delete", Routes.deleteBook, BookclubRoutes::deleteBook, Method.POST );
```

A route has one condition of each type: a route can't add methods its group already has. Among routes with
the same pattern, one with conditions comes before one without, and a more specific condition before a less specific.

### Methods

A route accepts every method unless it declares the ones it accepts: `Method.GET`, `Method.POST`,
`Method.of( "PUT", "PATCH" )`. `HEAD` is accepted wherever `GET` is. Declare the methods of a route that changes
something, so that a link, a prefetch or a crawler can't trigger it.

When routes at a path don't accept the request's method, the answer is `405 Method Not Allowed`, with an `Allow` header
listing the methods they do accept. A less specific route, a catch-all say, doesn't get the request instead. An
`OPTIONS` request there is answered with `204` and the same `Allow`. The method is checked before a group's filters
run, so a route behind a login filter answers `405` to a wrong method before asking for the login.

### Schemes and headers

`Scheme.HTTPS` answers requests over https only, and `Header.of( "X-Api-Version", "2" )` (or `Header.present( name )`)
requests carrying a header. For other requests the route isn't there, and the next route answers:

```java
api.map( "/books", BookclubRoutes::apiBooksV2, Header.of( "X-Api-Version", "2" ) );
api.map( "/books", BookclubRoutes::apiBooks );
```

A condition of an application's own implements `RouteCondition`, and sees the request's method, host, path, scheme and
headers (`RouteRequest`). A route's host is a condition too: see [Routing by host](#routing-by-host).

### Beneath a path

An application served beneath a path of its own (`https://example.com/shop/…`) sets it in the framework (#51):
`er.extensions.ERXApplication.basePath=/shop`. Its URLs, the router's complete URLs included, start with it, and a
request's URL has it removed before routing, so routes are declared as if the application were at the root.

## Groups

A group is routes sharing a path prefix, conditions and wrapping:

```java
final RouteGroup club = routes.group( "/clubs/{club}" ).named( "club" );   // {club} reaches its routes
final RouteGroup api = club.group( "/api" );                               // /clubs/{club}/api/…
```

`group( prefix, body, options )` takes a body mapping the group's routes, and `group( prefix, options )` returns the
group for mapping them afterwards.

### Group parameters

A parameter in a group's prefix, which every route of the group has, is the group's:

```java
final RouteGroup club = routes.group( "/clubs/{club}" ).parameter( "club", Club.class ).named( "club" );
```

It's converted once, before the group's filters run, so one that isn't of the type or names nothing declines every
route in the group: `/clubs/nosuch/…` answers nothing, the group's plain routes included. The group's routes' records
have it as a component, as they have the path's other parameters, and a link gives it (`:club="$club"`). A page may
leave it out (a page with a member for it gets it).

Declaring it after the group has routes is an error: it's declared before they're mapped. A group's routes include its
prefix in their patterns (`/api` and `/books` give `/api/books`) and its conditions in theirs. Its options reach its
routes and nested groups unless they set their own: a trailing slash policy, `Fields`, `CrossSite`, `CrossOrigin`.

### Filters

`wrap( filter )` wraps every route of a group, those of its nested groups included, and those mapped before the call.
A filter answers the request itself, or passes it on:

```java
final RouteGroup adminGroup = club.group( "/admin" ).named( "admin" );
adminGroup.wrap( ( invocation, next ) -> {
	return "letmein".equals( invocation.request().stringFormValueForKey( "key" ) ) ? next.handle( invocation ) : text( 403, "Admins only" );
} );
```

A nested group's filters run inside its parent's: `/admin/danger/` runs the admin filter, then the danger filter. A
filter can decide by route (`invocation.route()`).

A route declining (`ri.parameter( name, Type )` naming nothing) or redirecting to a parameter's canonical text travels
out through the filters as an exception (`Declined`, `NotCanonical`), so a filter catching `RuntimeException` rethrows
those.

## Trailing slashes

A pattern declares its form, `/books/` or `/books`, and generated URLs use it. A request in the other form is handled by
a policy:

| Policy | A request in the other form |
|---|---|
| `TrailingSlash.IGNORE` (the default) | matches |
| `TrailingSlash.REDIRECT` | is answered with `308` to the declared form, keeping the method, body and query string |
| `TrailingSlash.STRICT` | doesn't match |

`new ERXRouter( TrailingSlash.REDIRECT )` sets the policy for every route. A group sets it for its routes, and a route
sets its own, both among their options:

```java
club.route( "/books/", Routes.books, BookListPage.class, TrailingSlash.REDIRECT );   // /books gets 308 to /books/
final RouteGroup api = club.group( "/api", TrailingSlash.STRICT );       // /api/books/ isn't /api/books
```

`/` has one form only, and a wildcard's other form is its prefix without the slash (`/files` for `/files/*`).

## Tables and plugins

A router has tables: the application's ranks first, and each plugin's after it, in the order they're declared
(dependency order). A plugin declares its routes as the application does, naming its own table, and its constants are
its own:

```java
public class GuestbookPlugin {

	public static final PlainRoute page = Route.plain();

	public static void declare( final RouteGroup routes ) {
		routes.join( "club", club -> club.map( "/guestbook", page, … ) );
		routes.join( "admin", admin -> admin.map( "/guestbook", … ) );    // behind the application's admin filter
	}
}

ERXRouter.declare( "guestbook", GuestbookPlugin::declare );     // the plugin's table
ERXRouter.declare( BookclubRoutes::declare );                  // the application's
```

A framework declares its routes itself, so an application including it has nothing to set up: its `ERXPlugin`
(listed in `META-INF/services/er.extensions.ERXPlugin`) declares them in `finishInitialization`, once the application
is constructed and before any request. helium's object pages are declared this way:

```java
public class HeliumPlugin implements ERXPlugin {

	@Override
	public void finishInitialization( final ERXApplication application ) {
		ERXRouter.declare( "helium", ObjectRoutes::declare );
	}
}
```

The application's routes still rank first, so it overrides a framework's route by mapping the same one.

- **Conflicts:** within one table, two routes matching the same requests (the same pattern, whatever the parameters are
  called, and overlapping conditions) are refused when the second is mapped.
- **Overrides:** between tables, the same route is an override. The application's route answers, and the override is
  logged: `The route /clubs/{club}/about (application) overrides /clubs/{club}/about (guestbook)`.
- **Joining the application's groups:** the application names a group (`named( "club" )`), and a plugin joins it from
  its own table. The plugin's routes then share the group's prefix, conditions, options and filters, and stay the
  plugin's routes for overrides. `join( name, body )` maps them once the group is named, or now if it is, so a plugin
  declared first works; `join( name )` joins a group that's named already. A group joined but never named fails the
  application's launch.
- **Specificity comes first:** a table's rank only decides between the same route. A plugin's `/guestbook` still answers
  `/guestbook` beside an application's catch-all.

## Routing by host

Most applications answer one host, and their routes never name it: a route without a host answers any. A route or a
group on a host is there for requests to that host only, and for others the router tries the next route. Bookclubs'
administrators have a host of their own:

```java
routes.map( "/", Routes.overview, ri -> …, Host.of( "admin.@" ) );
```

- **Matching:** `Host.of( "admin.example.com" )` answers one host, and `Host.of( "{tenant}.example.com" )` a host
  pattern. Hosts are compared without case and port, and a host pattern has no port. Among routes with the same path,
  one on a host comes before one without (`admin.localhost`'s `/` before the landing page's), and an exact host before
  a host pattern.
- **The application's domain:** a pattern ending in `@` is relative to it: the public address's host, or `localhost`
  without one. `Host.of( "admin.@" )` answers `admin.localhost:1300` in development (any name ending in `.localhost` is
  this machine, so there's no setup) and `admin.bookclubs.example.com` with
  `er.routing.publicAddress=https://bookclubs.example.com`. `@` is a whole label, and the last. Outside development, a
  relative host needs the public address: without one, declaring the route fails, naming the property, rather than
  answering `localhost` that no request has.
- **Host parameters:** a host pattern's parameters (`{tenant}.@`) are route parameters like the path's, and a group's
  (`routes.group( "", Host.of( "{tenant}.@" ) ).parameter( "tenant", Tenant.class )`) gives every route in it the
  tenant. Records may leave them out. A link leaving one out takes the request's, and a link to another host gives it,
  its URL complete, to that host. A host parameter's value is one host label. Its name keeps its case (`{tenantId}`),
  and its parameters convert before the path's.
- **Links to another host:** with a public address, they have its scheme and port, with the route's host
  (`https://admin.bookclubs.example.com/`), and without one, the request's.
- **Posts:** another subdomain is another site, so a page on `admin.localhost` doesn't post to `localhost`'s routes.
  `CrossSite.OWN_HOSTS` takes posts from the application's other hosts: those its routes' host patterns match, and the
  public address's. A pattern matches by its shape, so with `{tenant}.@` that's every subdomain of the domain, whether
  or not a tenant is there.
- **Behind a front end:** a route's host is the request's `Host` header. Whether a front end's forwarded host (and
  scheme) counts is decided for the whole framework in one place, with #67, rather than by the router.

## What a request gets

1. The routes whose path and conditions match, most specific first. The first that doesn't decline answers, unless:
   - it's a browser's preflight from an origin the route allows (`CrossOrigin`): `204` with the CORS headers;
   - the request changes things and comes from a site the route doesn't take those from: `403`;
   - a route parameter isn't in its canonical text (`/books/007`): `308` to the URL with it (`/books/7`).
2. `405` with `Allow`, if routes at the path don't accept the method (`204` with `Allow`, or a preflight's answer, for
   `OPTIONS`).
3. `308`, if the path matched a redirecting route only in its other trailing slash form.
4. Otherwise the router declines, and the route table's other routes, fallback and not found handling get the URL.

## Not there yet

The routing work's open items, in full, are in `docs/ROUTE_LINKS.md` (and #178). Those a user of the router meets:

- The editor: Parslips doesn't know what `$routes` answers, so `$routes.…` shows as unknown there though it works; and
  it doesn't complete or check a route's parameters (undur/parslips#12). Until then, link mistakes show when the link
  renders.
- `/docs` and `/docs/`, both strict, are refused as the same route, though no request matches both.
- An element rendering a route's bare URL (for a script), and route URLs for the Ajax elements: when they're needed.
- Converging with the existing route table (`er.extensions.routes`), and the same router in ng-objects.
