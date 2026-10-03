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

Two pieces: the routes, as constants, and a declaration giving them their patterns.

```java
public interface Routes {

	Routes routes = new Routes() {};                 // $routes.… in templates

	PlainRoute home = Route.plain();
	PlainRoute book = Route.plain();
	Route<Search> search = Route.of( Search.class );
}
```

```java
public Application() {
	ERXRouter.declare( router -> {
		final RouteGroup routes = router.application();
		routes.map( "/", Routes.home, Main.class );
		routes.map( "/books/{book}", Routes.book, BookPage.class );
		routes.route( "/search", Routes.search );
	} );
}
```

Pages implement `Routes`, and link to them:

```html
<wo:route to="$routes.book" :book="$book"><wo:str value="$book.title" /></wo:route>
```

A template's key path reaches a static member of the component's class or its interfaces when the component has no
member of its own by the name (#172), so `$routes.book` is the constant. `routes` gathers them under one name, clear of
a page's own keys (a page's `book` is its book, not the route); `$book` would reach the constant too where nothing
clashes. Java code uses the constants directly: `Routes.book.url( Map.of( "book", book ) )`.

A constant no declaration gives a pattern to stops the application's launch, naming it, and so does one given two.

### Changing routes while the application runs

In development, routes are declared again when their classes change: the declaring class's folder, a constants
interface's, or a route's record's. Change a pattern, add a route (a new constant included), or add a component to a
record, and the next request has it, with no restart. The constants follow the routes. A change to the constants'
interface makes them new objects (the hot swap runs its initializer again), which templates and `Routes.book` read
fresh; a constant kept in a field of its own goes stale then. Every
declaration runs again, in the order they were first made (an application and its plugins), into a new router that
replaces the current one once they all succeed. Building is cheap (5,000 routes take a few milliseconds), and the check
for changes reads the class files in those folders at most once a second.

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

A route does one of three things, and the lightest that fits is the one to use:

| | Declared | For |
|---|---|---|
| A page | `map( pattern, route, PageClass.class )` | a page taking its route's parameters |
| A handler | `map( pattern, route, ri -> … )` | a small answer: a redirect, JSON, a text |
| A record | `route( pattern, route )` | a route with query parameters or a form's fields, as typed components |

The route's constant is `Route.plain()` for a page or a handler, `Route.of( Record.class )` for a record. A route
nothing links to needs no constant: `map( pattern, ri -> … )` and `route( pattern, Record.class )` make one of their own
(an API's routes, a webhook).

### Page routes

```java
club.map( "/books/{book}", Routes.book, BookPage.class );
```

A new instance of the page answers, its route parameters (the path's and the host's) set on it by name: a public field,
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
and it keeps the method), its parameters the route's by name, the query string kept:

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
- `/files` is the wildcard's other trailing slash form, which the route's policy decides: matched with nothing beneath
  (the default), redirected to `/files/`, or not matched (see [Trailing slashes](#trailing-slashes)).
- A link to a named wildcard gives its remainder (`:path="minutes/2026.txt"`), each element encoded.
- Path elements are decoded before they're handed over: `/books/a%2Fb` gives a parameter `a/b`, and `%2F` doesn't
  split the path. A wildcard doesn't match a path with an encoded `/` in its remainder, since the remainder would then
  read as more elements than the path had (`..%2F..%2Fetc`).
- Paths are case-sensitive.
- A path with a `.` or `..` segment (or its encoded form) matches no route. Browsers resolve them away, so such a path
  was written by hand, and a wildcard's remainder never carries one: a handler serving files from it is safe from `../`.
  Generating a link with `.` or `..` as a parameter's value is an error, and so is one containing `%`, `\` or a control
  character: servers refuse those in a path, encoded, so the link wouldn't reach the application.

### Which route answers

The order routes are mapped in doesn't matter. At the first path element where two patterns differ, a literal comes
first, then a parameter within literal text, then a whole-element parameter, then a wildcard. `/books/new` answers
`/books/new`, `/books/{id}.json` answers `/books/7.json`, and `/books/{id}` answers `/books/7`, whichever was mapped
first. A catch-all (`/*`) comes last.

### Declining

A route that has no answer for a URL declines, and the next matching route gets it. Bookclubs' club pages decline names
they don't have, and the club's catch-all answers with its own not found page:

```java
club.route( "/{name}", Routes.clubText );       // declines a page the club doesn't have
club.map( "/*", ri -> /* the club's not found page */ );
```

When every route that matched declines, the URL passes on, and in development the not found page lists each of them
with why it passed it on (a parameter naming nothing, a value the record refused, its handler declining). Any route
handler can add a line with `RouteTable.explainDecline( request, … )`. A handler never returns null.

## Typed routes

A typed route's parameters are the components of a record. The record is what a link passes to the route and what the
route receives, each value converted to its component's type:

```java
public record Books( Sort sort, Integer page, List<String> author ) implements Routable {

	@Override
	public WOActionResults invoke( final RouteInvocation invocation ) {
		…
	}
}

Route<Books> books = Route.of( Books.class );                    // Routes
club.route( "/books/", Routes.books, TrailingSlash.REDIRECT );   // the declaration
```

- **Path, host and query parameters:** components named in the pattern (`{id}`) are the path's, and must be there.
  Components named in a host pattern (`{club}`) are the host's, and may be left out: the tenant every route of a group
  has is read from the invocation (`ri.parameter( "club", Club.class )`), and a link takes it from the request. The
  other components are query parameters (`?sort=author&page=2`), or a form's fields.
- **Types:** anything the router's converters convert (see [Parameter types](#parameter-types)). A query parameter
  can be absent, so it's a boxed type or an object, and null when absent or empty.
- **What the route does:** a record implementing `Routable` does its work in `invoke`. A record that's only data gets an
  action, and several routes can share a record that way (a page and its JSON):

  ```java
  public record DeleteBook( Book book ) {}

  club.route( "/books/{book}/delete", Routes.deleteBook, BookclubRoutes::deleteBook, Method.POST );
  ```

### Bad input

A route parameter (path or host) that doesn't convert, or names an object that doesn't exist, declines the request: the
URL is wrong, and another route may answer it. Other bad input goes one of three ways:

- **Nothing set:** it declines too, so a route that never looks at its input's errors fails safe. A bookmark with a
  renamed enum value (`?sort=year`) is a 404.
- **`whenInvalid`:** the route answers it itself: a query parameter or field that doesn't convert, and values the
  record's constructor refuses (an `IllegalArgumentException`, or a `NullPointerException` from `Objects.requireNonNull`
  in the constructor, for a value it requires).

  ```java
  club.route( "/search", Routes.search ).whenInvalid( ( invocation, reason ) -> … "What are you searching for?" … );
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
router.converters().register( Club.class, Converter.of( id -> Library.club( id ).orElse( null ), Club::id ) );
```

`fromString` throws `IllegalArgumentException` for text that isn't a value of the type, and returns null for a value
that doesn't exist; either declines the request. A type that has no converter is an error when the route is declared.

A converter can see the request it converts for (`Converter.scoped`): the request's other route parameters, converted
once each, and objects provided for the request. Bookclubs finds a book in the club the host names, so another club's
book isn't there:

```java
router.converters().register( Book.class, Converter.scoped( ( id, scope ) -> Library.book( Integer.parseInt( id ) )
		.filter( book -> scope.parameter( "club" ) == null || book.club().equals( scope.parameter( "club" ) ) )
		.orElse( null ), book -> String.valueOf( book.id() ) ) );
```

`scope.get( WOContext.class )` and `scope.get( WORequest.class )` are the request's, and the application provides more:
`router.converters().provide( EOEditingContext.class, scope -> … )`, an editing context to fetch in. Parameters are
converted host first, then path, then query, so a converter finds those it looks in. Without a request (a link's text),
the scope is empty.

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
or one value. A route parameter has one value, so a path or host parameter can't be a `List`.

A component that isn't a `List` takes one value: given several (`?sort=title&sort=year`, two fields of the same name),
it's bad input rather than the first one silently. A checkbox doesn't need the hidden field some frameworks pair it
with: an absent `Boolean` is null, and a checked one posts `on`.

## Links

### In templates

`<wo:route>` links to a route. Each `:` attribute is one of its parameters:

```html
<wo:route to="$routes.book" :book="$book"><wo:str value="$book.title" /></wo:route>
<wo:route to="$routes.books" :sort="author">Sort by author</wo:route>
<wo:route to="$routes.admin" ?key="letmein">Admin</wo:route>
<wo:route to="$routes.clubHome" :club="$current">Visit</wo:route>    <!-- from localhost, to a club's host -->
```

- A constant (`:sort="author"`) is checked against the parameter's type, so `author` must be one of the enum's values.
- Building a link's URL doesn't construct a typed route's record: only its route parameters are needed.
- A host parameter the link leaves out is the current request's: on `acme.localhost`, links to the club's routes don't
  repeat `:club`. A link from another host (the landing page on `localhost`) gives it, and its URL is complete, to that
  host.
- `?` attributes add query parameters the route doesn't declare. A typed route's own parameter as a `?` attribute is an
  error: bound as `:` it's checked.
- A text parameter takes a value the converters convert (a number, an object with a converter) as its text. Another
  object is an error, rather than its `toString()` in a URL.
- A parameter the route doesn't have, a missing route parameter, or a value of the wrong type is an error when the link
  renders, naming the route and its parameters. A host parameter's value must be one host label.
- `<wo:route>` takes its URL from the route only, so it has no `href`, `action` or `pageName`. Other links are
  `<wo:link>`.

### From Java

```java
final String url = Routes.book.url( Map.of( "book", book ) );
final String sorted = Routes.books.url( new Books( Sort.author, null, List.of() ) );
return Routes.book.redirect( Map.of( "book", book ), invocation.context() );   // 303, post, redirect, get
```

A typed route takes its record, and any route takes values by name (`url( values, context )`), host parameters taken
from the request as links do. Values by name leave out the query parameters a record would have as `null`. URLs are
short (`/books/6`) when the application's short URLs are on.

### The public address

A complete URL needs the address people reach the application at, which the application can't always see. Set it:

```
er.routing.publicAddress=https://bookclubs.example.com
```

A scheme, a host, and a port if it isn't the scheme's. With it set:

- Complete URLs (a context generating them, an email's) have its scheme, host and port.
- A link to a route on another host has its scheme and port, with the route's host: `https://kronan.bookclubs.example.com/`.
- `completeURL( record )`, or `completeURL( values )` on any route, makes a complete URL without a request, for a
  background job's email. Host parameters are given then, since there's no request to take them from.

Without it, a link to another host takes the request's scheme and port, a complete URL of a route without a host has
the request's host (or the machine's name), and `completeURL` outside a request fails, naming the property. Relative
links don't change either way.

### Forms

`<wo:routeForm>` posts to a route. It takes `to` and `:` parameters as `<wo:route>` does, and a typed route's other
components are the form's fields:

```java
public record CreateBook( String title, String author, Integer year ) {}

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
the browser's `Sec-Fetch-Site` decides when it's sent, since it accounts for the scheme. Another subdomain is another
site: a page on `kronan.localhost` doesn't post to `acme.localhost`.

A route or a group says which sites it takes them from:

- `CrossSite.SAME_ORIGIN`: its own origin only, the default.
- `CrossSite.OWN_HOSTS`: the application's other hosts too, those its routes' host patterns match and the public
  address's (a form on the landing page posting to a club). A pattern matches by its shape, so with `{club}.@` that's
  every subdomain of the domain, whether or not a club is there.
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

An origin allowed may post to the route whatever its `CrossSite` level: allowing another site's script to call a route
is allowing it to change things through it. `CrossOrigin.ANY` allows every origin, without credentials, and
`withCredentials()` lets named origins send the user's cookies.

## Conditions

A route can require more of a request than its path. Conditions are declared with the route, among its options (as is
its trailing slash policy):

```java
club.route( "/books/{book}/delete", Routes.deleteBook, BookclubRoutes::deleteBook, Method.POST );
routes.map( "/", Routes.home, Main.class, Host.of( "@" ) );
```

A route has one condition of each type: a route can't add a host or methods its group already has. Among routes with
the same pattern, one with conditions comes before one without, and a more specific condition before a less specific.

### Methods

A route accepts every method unless it declares the ones it accepts: `Method.GET`, `Method.POST`,
`Method.of( "PUT", "PATCH" )`. `HEAD` is accepted wherever `GET` is. Declare the methods of a route that changes
something, so that a link, a prefetch or a crawler can't trigger it.

When routes at a path don't accept the request's method, the answer is `405 Method Not Allowed`, with an `Allow` header
listing the methods they do accept. A less specific route, a catch-all say, doesn't get the request instead. An
`OPTIONS` request there is answered with `204` and the same `Allow`. The method is checked before a group's filters
run, so a route behind a login filter answers `405` to a wrong method before asking for the login.

### Hosts

`Host.of( "admin.example.com" )` answers one host, and `Host.of( "{club}.example.com" )` a host pattern, its parameters
available like path parameters. Hosts are compared without case and port. For a request to another host, the route
isn't there: the router tries the next route. An exact host comes before a host pattern. Host parameter names keep their
case (`{tenantId}`), and a host pattern has no port.

A pattern ending in `@` is relative to the application's domain: the public address's host, or `localhost` without
one. Bookclubs' clubs are `Host.of( "{club}.@" )` and its landing page `Host.of( "@" )`, so the same code answers
`acme.localhost:1300` in development (any name ending in `.localhost` is this machine, so there's no setup) and
`acme.bookclubs.example.com` with `er.routing.publicAddress=https://bookclubs.example.com`. `@` is a whole label, and
the last. Outside development, a relative host needs the public address: without one, declaring the route fails,
naming the property, rather than answering `localhost` that no request has.

### Schemes and headers

`Scheme.HTTPS` answers requests over https only, and `Header.of( "X-Api-Version", "2" )` (or `Header.present( name )`)
requests carrying a header. For other requests the route isn't there, and the next route answers:

```java
api.map( "/books", BookclubRoutes::apiBooksV2, Header.of( "X-Api-Version", "2" ) );
api.map( "/books", BookclubRoutes::apiBooks );
```

A condition of an application's own implements `RouteCondition`, and sees the request's method, host, path, scheme and
headers (`RouteRequest`).

### Where a request came from

A route's host is the request's `Host` header. Whether a front end's forwarded host (and scheme) counts is decided for
the whole framework in one place, with #67, rather than by the router.

An application served beneath a path of its own (`https://example.com/shop/…`) sets it in the framework (#51):
`er.extensions.ERXApplication.basePath=/shop`. Its URLs, the router's complete URLs included, start with it, and a
request's URL has it removed before routing, so routes are declared as if the application were at the root.

## Groups

A group is routes sharing a path prefix, conditions and wrapping:

```java
final RouteGroup club = routes.group( "", Host.of( "{club}.@" ) ).named( "club" );   // by host alone
final RouteGroup api = club.group( "/api" );                                         // /api/…, on the club's host
final RouteGroup shop = routes.group( "/shops/{shop}" );                             // {shop} reaches its routes
```

`group( prefix, body, options )` takes a body mapping the group's routes, and `group( prefix, options )` returns the
group for mapping them afterwards. A group's routes include its prefix in their patterns (`/api` and `/books` give
`/api/books`) and its conditions in theirs. Its options reach its routes and nested groups unless they set their own:
a trailing slash policy, `Fields`, `CrossSite`, `CrossOrigin`.

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
club.route( "/books/", Routes.books, TrailingSlash.REDIRECT );          // /books gets 308 to /books/
final RouteGroup api = club.group( "/api", TrailingSlash.STRICT );       // /api/books/ isn't /api/books
```

`/` has one form only, and a wildcard's other form is its prefix without the slash (`/files` for `/files/*`).

## Tables and plugins

A router has tables: the application's (`application()`) ranks first, whenever it's created, and each plugin's
(`table( name )`) after it, in the order they're created (dependency order). A plugin declares its routes as the
application does, in its own table, and its constants are its own:

```java
public class GuestbookPlugin {

	public static final PlainRoute page = Route.plain();

	public static void declare( final ERXRouter router ) {
		final RouteGroup routes = router.table( "guestbook" );
		routes.join( "club", club -> club.map( "/guestbook", page, … ) );
		routes.join( "admin", admin -> admin.map( "/guestbook", … ) );    // behind the application's admin filter
	}
}

ERXRouter.declare( GuestbookPlugin::declare );     // the Application's constructor, before its own
ERXRouter.declare( BookclubRoutes::declare );
```

- **Conflicts:** within one table, two routes matching the same requests (the same pattern, whatever the parameters are
  called, and overlapping conditions) are refused when the second is mapped.
- **Overrides:** between tables, the same route is an override. The application's route answers, and the override is
  logged: `The route /about [Host {club}.localhost] (application) overrides /about [Host {club}.localhost] (guestbook)`.
- **Joining the application's groups:** the application names a group (`named( "club" )`), and a plugin joins it from
  its own table. The plugin's routes then share the group's prefix, conditions, options and filters, and stay the
  plugin's routes for overrides. `join( name, body )` maps them once the group is named, or now if it is, so a plugin
  declared first works; `join( name )` joins a group that's named already. A group joined but never named fails the
  application's launch.
- **Specificity comes first:** a table's rank only decides between the same route. A plugin's `/guestbook` still answers
  `/guestbook` beside an application's catch-all.

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

- The editor: Parslips doesn't yet resolve a key path to a static member (#172), so `$routes.…` shows as unknown there
  though it works; and it doesn't complete or check a route's parameters (undur/parslips#12). Until then, link mistakes
  show when the link renders.
- `/docs` and `/docs/`, both strict, are refused as the same route, though no request matches both.
- An element rendering a route's bare URL (for a script), and route URLs for the Ajax elements: when they're needed.
- Converging with the existing route table (`er.extensions.routes`), and the same router in ng-objects.
