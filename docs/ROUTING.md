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

Create a router, give it a table for the application's routes, and map the router into the existing route table:

```java
public class Application extends ERXApplication {

	public Application() {
		final ERXRouter router = new ERXRouter();
		final RouteGroup routes = router.table( "application" );

		routes.map( "/", Main.class );
		routes.map( "/items/{id}", ri -> ItemPage.page( ri, ri.parameter( "id" ) ) );

		router.mapInto( RouteTable.defaultRouteTable() );
	}
}
```

`mapInto` puts the router into the route table as one route. What the router has no route for passes on to the
table's other routes, then its fallback and not found handling, so the router and existing routes work side by side.

## Routes

`map( pattern, handler )` maps a route. The handler gets a `RouteInvocation`, and answers with a response or a page:

```java
club.map( "/files/*", ri -> text( 200, "The file %s of %s".formatted( ri.parameter( "*" ), ri.parameter( "club" ) ) ) );
```

`map( pattern, PageClass.class )` maps a route to a page.

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
public record Books( String club, Sort sort, Integer page ) implements Routable {

	public Books {
		if( page != null && page < 1 ) {
			throw new IllegalArgumentException( "Pages start at 1" );
		}
	}

	@Override
	public WOActionResults invoke( final RouteInvocation invocation ) {
		…
	}
}

books = club.route( "/books/", Books.class );
```

- **Path, host and query parameters:** components named in the pattern (`{id}`) or in a host pattern (`{club}`) come
  from the URL's path and host. The others are query parameters (`?sort=author&page=2`), or a form's fields.
- **Types:** anything the router's converters convert (see [Parameter types](#parameter-types)). A query parameter
  can be absent, so it's a boxed type or an object, and null when absent.
- **Validation:** the record's constructor is the place for it. A value that doesn't convert (`?page=abc`), or that the
  constructor refuses (`?page=0`), declines the URL.
- **What the route does:** a record implementing `Routable` does the route's work in `invoke`. A record that's only
  data gets an action instead:

  ```java
  public record DeleteBook( String club, int id ) {}

  deleteBook = club.route( "/books/{id}/delete", DeleteBook.class, BookclubRoutes::deleteBook, Method.POST );
  ```

  Several routes can share a record that way (a page and its JSON, say).

### Parameter types

The router's converters turn a parameter's value into URL text and back. `String`, `Integer`/`int`, `Long`/`long`,
`Boolean`/`boolean`, `LocalDate` and enums are built in, and an application registers its own types, before declaring
the routes taking them:

```java
router.converters().register( Book.class, Converter.of( id -> Library.book( Integer.parseInt( id ) ).orElse( null ), book -> String.valueOf( book.id() ) ) );

public record BookView( String club, Book book ) implements Routable { … }

book = club.route( "/books/{book}", BookView.class );
```

A link passes the object (`:book="$book"`), and its URL has the book's id (`/books/2`). A request's id becomes the
book. An id that isn't a number (`/books/abc`) or isn't a book's (`/books/999`) declines the request before the route
is invoked: `fromString` throws `IllegalArgumentException` for text that isn't a value of the type, and returns null for
a value that doesn't exist. A type that has no converter is an error when the route is declared.

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

`<wo:route>` links to a typed route. Each `:` attribute is one of its parameters:

```html
<wo:route route="$routes.book" :book="$book"><wo:str value="$book.title" /></wo:route>
<wo:route route="$routes.books" :sort="author">Sort by author</wo:route>
<wo:route route="$routes.admin" ?key="letmein">Admin</wo:route>
<wo:route route="$routes.clubHome" :club="$current.id">Visit</wo:route>    <!-- from localhost, to a club's host -->
```

- A constant (`:sort="author"`) is converted to the parameter's type, so `author` becomes the enum value.
- A host parameter the link leaves out is the current request's: on `acme.localhost`, links to the club's routes don't
  repeat `:club`. A link from another host (the landing page on `localhost`) gives it.
- `?` attributes add query parameters the typed route doesn't declare, as on any link.
- A parameter the typed route doesn't have, a missing path parameter, or a value of the wrong type is an error when the
  link renders, naming the typed route and its parameters.
- `<wo:route>` takes its URL from the typed route only, so it has no `href`, `action` or `pageName`. Other links are
  `<wo:link>`.

### From Java

```java
final String url = routes.book.url( new BookView( club, book.id() ) );
```

`url()` generates the URL in the current context, `url( parameters, context )` in a given one. URLs are short
(`/books/6`) when the application's short URLs are on.

### Forms

`<wo:routeForm>` posts to a typed route. It takes `route` and `:` parameters as `<wo:route>` does, and the record's
other components are the form's fields:

```java
public record CreateBook( String club, String title, String author, Integer year ) {}

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

After a form's post, answer with a redirect to the result (post, redirect, get):

```java
final Book book = Library.book( form.club(), form.title(), form.author(), form.year() );
return seeOther( routes.book.url( new BookView( form.club(), book.id() ), invocation.context() ) );
```

## Conditions

A route can require more of a request than its path. Conditions are declared with the route, among its options (as is
its trailing slash policy):

```java
createBook = club.route( "/books", CreateBook.class, BookclubRoutes::createBook, Method.POST );
home = routes.route( "/", Home.class, Host.of( "localhost" ) );
```

### Methods

A route accepts every method unless it declares the ones it accepts: `Method.GET`, `Method.POST`,
`Method.of( "PUT", "PATCH" )`. `HEAD` is accepted wherever `GET` is. Declare the methods of a route that changes
something, so that a link, a prefetch or a crawler can't trigger it.

When routes at a path don't accept the request's method, the answer is `405 Method Not Allowed`, with an `Allow` header
listing the methods they do accept. A less specific route, a catch-all say, doesn't get the request instead.

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

A typed route with a host pattern takes the host's parameters as components (`ClubHome( String club )`), and links to it
from another host are complete URLs: `http://acme.localhost:1300/`.

In development, any name ending in `.localhost` is this machine, so host routes need no setup: Bookclubs' clubs are at
`acme.localhost:1300` and `kronan.localhost:1300`.

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

A router has tables, ranked in the order they're created: the application's first, then each plugin's, in dependency
order. A plugin maps its routes in its own table:

```java
final RouteGroup routes = router.table( "application" );
…
GuestbookPlugin.register( router.table( "guestbook" ) );
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
  final RouteGroup club = routes.group( "", CLUB_HOST ).named( "club" );       // the application
  final RouteGroup admin = club.group( "/admin" ).named( "admin" );

  routes.join( "club" ).map( "/guestbook", … );                              // the plugin
  routes.join( "admin" ).map( "/guestbook", … );                             // behind the application's admin filter
  ```

  A group is named before a plugin joins it.
- **Specificity comes first:** a table's rank only decides between the same route. A plugin's `/guestbook` still answers
  `/guestbook` beside an application's catch-all.

## What a request gets

1. The routes whose path and conditions match, most specific first. The first that doesn't decline answers.
2. `405` with `Allow`, if routes at the path don't accept the method.
3. `308`, if the path matched a redirecting route only in its other trailing slash form.
4. Otherwise the router declines, and the route table's other routes, fallback and not found handling get the URL.

## Not there yet

- Static fields in key paths (#172): templates reach typed routes through an instance.
- Completing and checking a typed route's parameters in the editor (undur/parslips#12). Until then, link mistakes show
  when the link renders.
- Reading a table's routes again on each request in development.
- A typed route's link to another host assumes the request's scheme and port.
