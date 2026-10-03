# Routing

> **Experimental.** This describes the router in the ERRouting framework on the `route-links` branch (package
> `er.routing`), built beside the existing routes to replace them. Details will change. The design notes are in
> [ROUTE_LINKS.md](ROUTE_LINKS.md), and the work is tracked in #178.
>
> The example application [Bookclubs](../Bookclubs) uses everything described here, and most examples below are taken
> from it.

A route connects a URL to the code answering it. The router matches a request's path (and, if a route asks for it, its
host and method) to a route, hands the route the values in the URL as parameters, and generates URLs for links to the
route, so templates and Java code link to routes rather than writing URLs by hand.

## Setting up

The application's routes are declared in a class of their own, whose constructor maps them in the router's
application table. The application declares it at startup, and the instance holds the routes, for links:

```java
public class AppRoutes {

	private static Declared<AppRoutes> _routes;

	public final PlainRoute home;

	private AppRoutes( final ERXRouter router ) {
		final RouteGroup routes = router.application();
		home = routes.map( "/", Main.class );
		routes.map( "/items/{id}", ri -> ri.page( ItemPage.class ).item( ri.parameter( "id", Item.class ) ) );
	}

	public static void declare() {
		_routes = ERXRouter.declare( AppRoutes::new );     // in the Application's constructor
	}

	public static AppRoutes instance() {
		return _routes.get();
	}
}
```

### Changing routes while the application runs

In development, routes are declared again when their classes change: the declaring class (and the classes in its
folder) or a route's record. Change a route, add one, or add a component to a record, and the next request has it, with
no restart. Every declaration runs again, in the order they were first made (an application and its plugins), into a
new router that replaces the current one once they all succeed. Building is cheap (5,000 routes take a few
milliseconds), and the check for changes looks at the class files in those folders only.

- A declaration that fails (two routes matching the same requests, say) leaves the previous routes in place, and routed
  requests answer with why until the routes are declared again, so the old routes aren't tested by mistake.
- The holder is made again, so `instance()` is read each time, never kept. State that should outlive a declaration (a
  plugin's data) is kept elsewhere.
- A route mapped outside a declaration (`ERXRouter.defaultRouter().application().map( … )` in the application's
  constructor) would be lost, so then the routes aren't declared again, and the log says which route stopped it.
- A new class file's code may reach the running application a beat after it's written (the hot swap), so a declaration
  made within three seconds of a change is made once more after that.

`er.routing.reload` turns it on or off. It's on in development mode, and off otherwise, where routes are declared once.

The default router is created on first use and mapped into the existing route table then, as one route. What the
router has no route for passes on to the table's other routes, then its fallback and not found handling, so the router
and existing routes work side by side. The table's `hasRouteFor()` answers for the router's routes that answer any host,
not for every URL: a route for one host doesn't claim a path for all of them. (An admin UI mapped under a host isn't
seen by the welcome page's check, then.)

`ERXRouter.defaultRouter().routes()` describes every route in precedence order (`RouteDescription`): its pattern,
conditions, trailing slash policy and table, the route itself for linking, and a typed route's record class.

## Routes

`map( pattern, handler )` maps a route. The handler gets a `RouteInvocation`, and answers with a response or a page:

```java
club.map( "/files/*", ri -> text( 200, "The file %s of %s".formatted( ri.parameter( "*" ), ri.parameter( "club" ) ) ) );
```

`map( pattern, PageClass.class )` maps a route to a page. Both return the route, a `PlainRoute`, for links, forms and
redirects to it (see [Links](#links)):

```java
rules = club.map( "/rules/", ri -> text( 200, "Rules of %s: read the book.".formatted( ri.parameter( "club" ) ) ), TrailingSlash.REDIRECT );
```

A route whose first path element is a request handler's key (`/wa/…`, `/wo/…`) is refused when it's mapped: the
request handler would get every request for it.

A parameter is text, `ri.parameter( "id" )`, or converted to a type, `ri.parameter( "book", Book.class )`, with the
router's converters (see [Parameter types](#parameter-types)). A value that doesn't convert, or names an object that
doesn't exist, declines the request, without code in the route:

```java
private static WOActionResults apiBook( final RouteInvocation invocation ) {
	final Book book = invocation.parameter( "book", Book.class );
	…
}
```

Throwing `Declined` declines from anywhere inside a route, as returning `RouteHandler.DECLINED` does from its handler.

### Patterns

| Pattern | Matches |
|---|---|
| `/books/new` | exactly that path |
| `/books/{id}` | one path element in place of `{id}`, available as `ri.parameter( "id" )` |
| `/files/*` | `/files/` and everything beneath it (but not `/files`), the rest available as `ri.parameter( "*" )` |

- A parameter or the wildcard is a whole path element: `/books/book-{id}` isn't a pattern.
- A parameter never matches an empty element, so `/books//edit` doesn't match `/books/{id}/edit`.
- Path elements are decoded before they're handed over: `/files/a%2Fb` gives `a/b`, and `%2F` doesn't split the
  path.
- Paths are case-sensitive.
- A path with a `.` or `..` segment (or its encoded form) matches no route. Browsers resolve them away, so such a path
  was written by hand, and a wildcard's remainder never carries one: a handler serving files from it is safe from `../`.
  Generating a link with `.` or `..` as a parameter's value is an error, and so is one containing `%` or `\`: servers
  refuse an encoded `%` or `\` in a path, so the link wouldn't reach the application.

### Which route answers

The order routes are mapped in doesn't matter. At the first path element where two patterns differ, a literal comes
before a parameter, and a parameter before a wildcard. `/books/new` answers `/books/new`, and `/books/{id}` answers
`/books/42`, whichever was mapped first. A catch-all (`/*`) comes last.

### Declining

A route that has no answer for a URL returns `RouteHandler.DECLINED`, and the next matching route gets it. Bookclubs'
club pages decline names they don't have, and the club's catch-all answers with its own not found page:

```java
club.route( "/{name}", ClubText.class );        // declines a page the club doesn't have
club.map( "/*", ri -> /* the club's not found page */ );
```

A handler never returns null.

## Typed routes

A typed route is a route whose parameters are the components of a record. The record is what a link passes to the route
and what the route receives, with each value converted to its component's type:

```java
public record Books( Club club, Sort sort, Integer page ) implements Routable {

	@Override
	public WOActionResults invoke( final RouteInvocation invocation ) {
		…
	}
}

books = club.route( "/books/", Books.class, TrailingSlash.REDIRECT );
```

- **Path, host and query parameters:** components named in the pattern (`{id}`) or in a host pattern (`{club}`) come
  from the URL's path and host. The others are query parameters (`?sort=author&page=2`), or a form's fields.
- **Types:** anything the router's converters convert (see [Parameter types](#parameter-types)). A query parameter
  can be absent, so it's a boxed type or an object, and null when absent.
- **Values that don't convert:** a route parameter (path or host) that doesn't convert, or names an object that doesn't
  exist, declines the request: the URL is wrong. So does a query parameter or a form's field, by default, so a route
  that never looks at its input's errors fails safe. A route taking a form declares `Fields.REPORTED`: a field that
  doesn't convert is then null, and the route hears about it in `invocation.conversionErrors()`, with the text that was
  given, so it can show the form again with its errors.

  ```java
  createBook = club.route( "/books", CreateBook.class, BookclubRoutes::createBook, Method.POST, Fields.REPORTED );
  ```

  A group's `Fields.REPORTED` reaches its typed routes. A plain route reads its own fields, so it refuses the option.
  Declining is strict for old URLs too: once an enum value is renamed, a bookmark with the old one (`?sort=year`) is a
  404 on a route without `Fields.REPORTED`.
- **Validation:** the record's constructor checks what makes the route's own parameters valid, and a value it refuses
  (an `IllegalArgumentException`, or a `NullPointerException` from `Objects.requireNonNull` for a value it requires)
  declines the request. A route that answers such a request itself says so with `whenInvalid`:

  ```java
  search = club.route( "/search", Search.class ).whenInvalid( ( invocation, reason ) -> … "What are you searching for?" … );
  ```

  The reason names what was wrong: `Objects.requireNonNull( q, "q" )` says so itself, and a `NullPointerException`
  without a message is given one naming the absent components (`Absent: [q]`). A form's fields are checked by the
  route, which can show the form again with what's wrong.
- **Bad input, three ways:** with nothing set, bad input declines the request. With `whenInvalid`, the route answers
  any bad input itself: a query parameter or field that doesn't convert, and values its record's constructor refuses.
  With `Fields.REPORTED`, the record is built anyway, a field that doesn't convert null and reported in
  `conversionErrors()`. A route parameter that doesn't convert always declines: the URL is wrong, and another route may
  answer it. Each decline is logged at debug level (`er.routing.Route`, `er.routing.ERXRouter`),
  naming the route and why: the answer to "why is this a 404".
- **What the route does:** a record implementing `Routable` does the route's work in `invoke`. A record that's only
  data gets an action instead:

  ```java
  public record DeleteBook( Club club, Book book ) {}

  deleteBook = club.route( "/books/{book}/delete", DeleteBook.class, BookclubRoutes::deleteBook, Method.POST );
  ```

  Several routes can share a record that way (a page and its JSON, say).

### Parameter types

The router's converters turn a parameter's value into URL text and back. `String`, `Integer`/`int`, `Long`/`long`,
`Boolean`/`boolean`, `Double`, `LocalDate`, `Instant`, `UUID` and enums are built in, and an application registers its
own types, before declaring the routes taking them. A converter registered for a class or an interface converts its
subclasses and implementations.

```java
router.converters().register( Club.class, Converter.of( id -> Library.club( id ).orElse( null ), Club::id ) );
router.converters().register( Book.class, Converter.of( id -> Library.book( Integer.parseInt( id ) ).orElse( null ), book -> String.valueOf( book.id() ) ) );

public record BookView( Club club, Book book ) implements Routable { … }

book = club.route( "/books/{book}", BookView.class );
```

The `{club}` of the club's host is a `Club` too: a host parameter converts as a path parameter does, so an unknown
club's host declines before any of the club's routes is invoked.

A link passes the object (`:book="$book"`), and its URL has the book's id (`/books/2`). A request's id becomes the
book. An id that isn't a number (`/books/abc`) or isn't a book's (`/books/999`) declines the request before the route
is invoked: `fromString` throws `IllegalArgumentException` for text that isn't a value of the type, and returns null for
a value that doesn't exist. A type that has no converter is an error when the route is declared.

Parsing takes a type's common forms: `007` is 7, an ISO date may have milliseconds, a UUID may be upper case, and a
boolean is `true`, `false`, or `on` (what a checkbox without a `value` posts).

A route parameter has one URL per value: `/books/007` and `/books/+7` are answered with `308` to `/books/7`, keeping
the query string, so each object has one URL. A converter whose type is written more than one way says so
(`canonical()` is false, as for `Double`), and its values aren't redirected. Query parameters and fields aren't held to
one text. The redirect comes while the parameters are converted, before the route decides anything, so a URL the route
would decline (another club's book) is redirected first, and then declined.

### Repeated parameters

A query parameter or field given several values (`?author=Laxness&author=Undset`, a form's checkboxes) is a `List`
component of a type with a converter:

```java
public record Books( Club club, Sort sort, Integer page, List<String> author ) implements Routable { … }
```

It has every value, in order, and is an empty list when there are none (never null). An empty value (`?q=`, a field
left empty) is no value, for text too: a `String` component is null then, not `""`. A value that doesn't convert is bad
input, as for any query parameter (declined, `whenInvalid`, or reported and left out with `Fields.REPORTED`). A link
repeats the parameter for each value: `:author="$authors"` takes a list (an `NSArray` too) or one value. A route
parameter has one value, so a path or host parameter can't be a `List`.

A component that isn't a `List` takes one value: given several (`?sort=title&sort=year`, two fields of the same name),
it's bad input rather than the first one silently. A checkbox doesn't need the hidden field some frameworks pair it
with: an absent `Boolean` is null, and a checked one posts `on`.

### Reaching typed routes from templates

Templates reach typed routes through a key path, which the editor can follow to each typed route's record. Bookclubs keeps its
typed routes as fields of one class, and its pages reach it as `$routes`:

```java
public class BookclubRoutes {
	public final Route<Books> books;
	public final Route<BookView> book;
	…
}

public abstract class BaseComponent extends ERXComponent {

	public BookclubRoutes routes() {
		return BookclubRoutes.instance();
	}
}
```

(Key paths can't reach static fields yet: #172.)

## Links

### In templates

`<wo:route>` links to a route, typed or plain. Each `:` attribute is one of its parameters:

```html
<wo:route route="$routes.book" :book="$book"><wo:str value="$book.title" /></wo:route>
<wo:route route="$routes.books" :sort="author">Sort by author</wo:route>
<wo:route route="$routes.admin" ?key="letmein">Admin</wo:route>
<wo:route route="$routes.clubHome" :club="$current">Visit</wo:route>    <!-- from localhost, to a club's host -->
<wo:route route="$routes.rules">Rules</wo:route>                         <!-- a plain route -->
```

- A constant (`:sort="author"`) is checked against the parameter's type, so `author` must be one of the enum's values.
- Building a link's URL doesn't construct the typed route's record: only its route parameters are needed.
- A plain route's `:` parameters are its pattern's and its host's; another name is an error, as on a typed route. Query
  parameters the route doesn't declare are `?` attributes.
- A text parameter takes a value the converters convert (a number, an object with a converter) as its text. Another
  object is an error, rather than its `toString()` in a URL.
- A host parameter the link leaves out is the current request's: on `acme.localhost`, links to the club's routes don't
  repeat `:club`. A link from another host (the landing page on `localhost`) gives it.
- `?` attributes add query parameters the typed route doesn't declare, as on any link.
- A parameter a typed route doesn't have, a missing route parameter, or a value of the wrong type is an error when the
  link renders, naming the route and its parameters.
- A host parameter's value must be one host label (letters, digits and hyphens): a dot or a slash would make the link
  go to another host.
- `<wo:route>` takes its URL from the route only, so it has no `href`, `action` or `pageName`. Other links are
  `<wo:link>`.

### From Java

```java
final String url = routes.book.url( new BookView( club, book ) );
final String rules = routes.rules.url( context );
final String sorted = routes.books.url( Map.of( "sort", Sort.author ), context );
```

`route.redirect( … )` answers with a `303` to a route, taking what `url( … )` takes, and `invocation.page( PageClass.class )`
makes a page in the invocation's context:

```java
return instance().books.redirect( new Books( delete.club(), null, null, List.of() ), invocation.context() );
return invocation.page( ClubPage.class ).club( club );
```

A typed route takes its record, and any route takes values by name (`url( values, context )`), host parameters taken
from the request as links do. URLs are short (`/books/6`) when the application's short URLs are on, and a link to
another host is complete, to that host, in a context generating complete URLs (an email) too.

### The public address

A complete URL needs the address people reach the application at, which the application can't see: behind a front
end, the request it gets is `http` on its own port, and its own host name is the machine's. Set it:

```
er.routing.publicAddress=https://bookclubs.example.com
```

A scheme, a host, and a port if it isn't the scheme's: a path is refused (a base path is #51's). With it set:

- Complete URLs (a context generating them, an email's) have its scheme, host and port.
- A link to a route on another host has its scheme and port, with the route's host: `https://kronan.bookclubs.example.com/`.
- `route.completeURL( record )`, or `completeURL( values )` on any route, makes a complete URL without a request, for
  a background job's email. Host parameters are given then, since there's no request to take them from.

Without it, URLs take the request's scheme, host and port, and `completeURL` outside a request fails, naming the
property. Relative links don't change either way.

### Forms

`<wo:routeForm>` posts to a typed route. It takes `route` and `:` parameters as `<wo:route>` does, and the record's
other components are the form's fields. A `method="get"` form's query parameters (`:sort="$sort"`) are rendered as
hidden fields, since a browser replaces a get form's action query with its fields.

```java
public record CreateBook( Club club, String title, String author, Integer year ) {}

createBook = club.route( "/books", CreateBook.class, BookclubRoutes::createBook, Method.POST );
```

```html
<wo:routeForm route="$routes.createBook">
	<input name="title"> <input name="author">
</wo:routeForm>

<wo:routeForm route="$routes.deleteBook" :book="$book">
	<button type="submit">Remove</button>
</wo:routeForm>
```

The method is `post` unless the form binds another. `href`, `action` and the direct action bindings aren't accepted:
other forms are `<wo:form>`.

The route checks the form's fields, and shows the form again with what's wrong, a field that didn't convert included.
Otherwise it answers with a redirect to the result (post, redirect, get):

```java
private static WOActionResults createBook( final CreateBook form, final RouteInvocation invocation ) {

	if( invocation.conversionErrors().containsKey( "year" ) ) {
		… the form again: "The year is a number, not 'abc'"
	}

	final Book book = Library.book( form.club().id(), form.title(), form.author(), form.year() );
	return instance().book.redirect( new BookView( form.club(), book ), invocation.context() );
}
```

### Posts from other sites

A route doesn't take a request that changes things (POST, PUT, PATCH, DELETE) from a page on another site: it's
answered with `403`, so a page elsewhere can't post a form to the application with the user's cookies. The browser says
where a request comes from (`Sec-Fetch-Site`, or `Origin`, compared with the request's host and the public address), and
a request with neither isn't from a browser page (curl, a server's webhook), so it's taken. Another subdomain is another
site: a page on `kronan.localhost` doesn't post to `acme.localhost`.

A route or a group says which sites it takes them from:

- `CrossSite.SAME_ORIGIN`: its own origin only, the default.
- `CrossSite.OWN_HOSTS`: the application's other hosts too, those its routes' host patterns match and the public
  address's (a form on the landing page posting to a club).
- `CrossSite.ALLOWED`: any site.

```java
final RouteGroup api = club.group( "/api", TrailingSlash.STRICT, CrossSite.ALLOWED );
```

A route's own setting wins over its group's, so a route in that group can be `CrossSite.SAME_ORIGIN` again. The same
goes for fields: `Fields.DECLINED` is the default that `Fields.REPORTED` replaces. `routes()` says each route's level.

This covers routes: component actions and the route table's other routes aren't checked.

## Conditions

A route can require more of a request than its path. Conditions are declared with the route, among its options (as is
its trailing slash policy):

```java
createBook = club.route( "/books", CreateBook.class, BookclubRoutes::createBook, Method.POST );
home = routes.route( "/", Home.class, Host.of( "@" ) );
```

### Methods

A route accepts every method unless it declares the ones it accepts: `Method.GET`, `Method.POST`,
`Method.of( "PUT", "PATCH" )`. `HEAD` is accepted wherever `GET` is. Declare the methods of a route that changes
something, so that a link, a prefetch or a crawler can't trigger it.

When routes at a path don't accept the request's method, the answer is `405 Method Not Allowed`, with an `Allow` header
listing the methods they do accept. A less specific route, a catch-all say, doesn't get the request instead. An
`OPTIONS` request there is answered with `204` and the same `Allow`.

The method is checked before a group's filters run, so a route behind a login filter answers `405` to a wrong method
before asking for the login.

Routes with the same pattern and different methods are separate routes:

```java
api.map( "/books/{id}", BookclubRoutes::apiBook, Method.GET );
api.map( "/books/{id}", BookclubRoutes::apiDeleteBook, Method.DELETE );
```

### Hosts

`Host.of( "admin.example.com" )` answers one host, and `Host.of( "{club}.example.com" )` a host pattern, its
parameters available like path parameters. Hosts are compared without case and port. For a request to another host,
the route isn't there: the router tries the next route.

Among routes with the same pattern, one with conditions comes before one without, and an exact host before a host
pattern.

A typed route with a host pattern takes the host's parameters as components (`ClubHome( Club club )`), and links to it
from another host are complete URLs: `http://acme.localhost:1300/`. Host parameter names keep their case
(`{tenantId}`). A host pattern has no port (hosts are compared without one). A route has one condition of each type: a route can't add a host or methods its group already has.

A pattern ending in `@` is relative to the application's domain: the public address's host, or `localhost` without
one. Bookclubs' clubs are `Host.of( "{club}.@" )` and its landing page `Host.of( "@" )`, so the same code answers
`acme.localhost:1300` in development (any name ending in `.localhost` is this machine, so there's no setup) and
`acme.bookclubs.example.com` with `er.routing.publicAddress=https://bookclubs.example.com`. `@` is a whole label, and
the last.

The host is the request's `Host` header. Whether a front end's `x-forwarded-host` counts is to be decided with #67.

## Groups

A group is routes sharing a path prefix, conditions and wrapping:

```java
final RouteGroup club = routes.group( "", CLUB_HOST );          // by host alone
final RouteGroup api = club.group( "/api" );                    // /api/…, on the club's host
final RouteGroup shop = routes.group( "/shops/{shop}" );        // {shop} reaches its routes' records
```

`group( prefix, body, conditions )` takes a body mapping the group's routes, and `group( prefix, conditions )` returns
the group for mapping them afterwards. A group's routes include its prefix in their patterns (`/api` and `/books` give
`/api/books`) and its conditions in theirs. Its typed routes take its path parameters as components.

### Filters

`wrap( filter )` wraps every route of a group, those of its nested groups included, and those mapped before the call.
A filter answers the request itself, or passes it on:

```java
final RouteGroup adminGroup = club.group( "/admin" );
adminGroup.wrap( ( invocation, next ) -> {
	return "letmein".equals( invocation.request().stringFormValueForKey( "key" ) ) ? next.handle( invocation ) : text( 403, "Admins only" );
} );
```

A nested group's filters run inside its parent's: `/admin/danger/` runs the admin filter, then the danger filter.

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
books = club.route( "/books/", Books.class, TrailingSlash.REDIRECT );        // /books gets 308 to /books/
final RouteGroup api = club.group( "/api", TrailingSlash.STRICT );          // /api/books/ isn't /api/books
```

`/` has one form only.

## Tables and overrides

A router has tables: the application's (`application()`) ranks first, whenever it's created, and each plugin's
(`table( name )`) after it, in the order they're created (dependency order). A plugin maps its routes in its own table:

```java
final RouteGroup routes = router.application();                 // the application's
…
guestbook = new GuestbookPlugin( router.table( "guestbook" ) ); // a plugin's
```

- **Conflicts:** within one table, two routes matching the same requests (the same pattern, whatever the parameters are
  called, and overlapping conditions) are refused when the second is mapped.
- **Overrides:** between tables, the same route is an override. The application's route answers, and the override is
  logged at startup:
  `The route /about [Host {club}.localhost] (application) overrides /about [Host {club}.localhost] (guestbook)`.
- **Joining the application's groups:** the application names a group, and a plugin joins it from its own table. The
  plugin's routes then share the group's prefix, conditions, trailing slash policy and filters, and stay the plugin's
  routes for overrides:

  ```java
  routes.join( "club", club -> _page = club.map( "/guestbook", … ) );             // the plugin, set up first
  routes.join( "admin", admin -> admin.map( "/guestbook", … ) );                  // behind the application's admin filter

  final RouteGroup club = routes.group( "", CLUB_HOST ).named( "club" );          // the application, later
  final RouteGroup admin = club.group( "/admin" ).named( "admin" );
  ```

  A plugin starts before the application declares its groups, so `join( name, body )` maps its routes once the group is
  named, or now if it is. A route the plugin links to is kept in a field the body sets, which is null until then: the
  one cost of joining late. `join( name )` joins a group that's named already. A group joined but never named fails the
  application's startup, before it listens for requests.
- **Specificity comes first:** a table's rank only decides between the same route. A plugin's `/guestbook` still answers
  `/guestbook` beside an application's catch-all.

## What a request gets

1. The routes whose path and conditions match, most specific first. The first that doesn't decline answers.
2. `405` with `Allow`, if routes at the path don't accept the method (`204` with `Allow`, for `OPTIONS`).
3. `308`, if the path matched a redirecting route only in its other trailing slash form.
4. Otherwise the router declines, and the route table's other routes, fallback and not found handling get the URL.

## Not there yet

- Static fields in key paths (#172): templates reach routes through an instance.
- Completing and checking a route's parameters in the editor (undur/parslips#12), and a form's fields against its
  record. Until then, link mistakes show when the link renders.
- Without the public address, a link to another host assumes the request's scheme and port, and complete URLs of
  routes without a host have the machine's name (`http://my-macbook.local:1300/…`).
- Wildcards in typed routes, and a redirect from `/files` to a wildcard's `/files/`.
- `/docs` and `/docs/`, both strict, are refused as the same route, though no request matches both.
