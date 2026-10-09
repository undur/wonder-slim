# Changelog

## Unreleased

- **Routes are declared again when the application's own classes change**
  In development, a change to the class that declares the application's routes declares them again, as it did
  before 8.1.1. In 8.1.1 only a change to a class holding a route's constant or its parameters' record did.

## 2026-10-07 (8.1.1)

- **One verb declares every route**
  A typed route is mapped with `map`, as a plain one is: `club.map( "/books/{book}", Routes.book, BookPage.class )`,
  whether `Routes.book` is `Route.plain()` or `Route.of( Book.class )`. The route's constant says which kind it is.

  Upgrading: `route( … )` is `map( … )`, with the same arguments.

- **The application's routes have a type of their own**
  `ERXRouter.declare` hands the application `ApplicationRoutes`, which has `fallback` and `notFound`: a group's or a
  plugin's routes no longer have them, so the compiler says what an exception said before. (#205)

  Upgrading: a declaration in a method of its own that sets a fallback or a not found handler takes `ApplicationRoutes`
  instead of `RouteGroup`.

- **A converter's type has one owner**
  The application, or the plugin that registered it first: another registering the type stops the launch, naming both,
  and only the application replaces a built-in converter. A plugin's converters can no longer change how the
  application's routes convert. (#199)

- **An optional last path parameter**
  `/search/{text?}` stands for `/search` and `/search/{text}`, one route where two were mapped before. Absent, the
  parameter is null (a record's component, `ri.parameter`, converted or not), a page keeps its own value, and a link
  leaves it out when its value is null; a value naming nothing declines, as any parameter's does. It ranks and
  conflicts as the two patterns it stands for. (#204)

- **A plainer 404, deployed**
  A URL nothing answers gets a small HTML page (the path asked for, and a link to the front page) instead of a line of
  text. In development, the development pages still answer.

- **Faster links**
  With short URLs, a route's link is its path, composed directly rather than composed under the route key and shortened,
  and shortening a generated URL no longer compiles a regular expression for each. A page of thousands of links renders
  noticeably faster.

- **A page of the application's routes**
  ERControl shows how the application routes as a whole (trailing slashes, bad input, the fallback and the not found
  handler), then every route in the order they're tried. Each opens to what applies to it, in words: what it matches,
  what it answers with, its parameters, conditions and linking, with the routing guide's section for each.

- **Options for every route of a declaration**
  `ERXRouter.declare( routes -> … , TrailingSlash.REDIRECT )` applies the options to every route the declaration
  declares, as a group's apply to its routes. A plugin's declaration takes them too. (#202)

- **A handler knows its route's pattern**
  `ri.route().pattern()` is the whole pattern the route is mapped under, its group's prefix included. (#203)

- **The period form of stamped resource URLs is gone**
  Only the `@` form (`css/site@3f9c1e07ab.css`) is a stamped URL now: `css/site.3f9c1e07ab.css` names a file of that
  name. Every deployed instance has generated the `@` form since 8.0.16. (#168)

- **Parsley 1.6.4**
  Parsley no longer brings in WebObjects as a dependency of its own.

## 2026-10-04 (8.1.0)

- **A router replaces the route table**
  Routes are declared, with named parameters, and links are generated from them, checked when they render:
  `ERXRouter.declare( routes -> routes.map( "/books/{book}", Routes.book, BookPage.class ) )`, linked to with
  `<wo:route to="$routes.book" :book="$book">`. The most specific route answers, whatever order they're declared in,
  and a route can decline, passing the request on. Parameters are typed through converters, alone or as a record's
  components; routes take conditions (methods, hosts) and form groups sharing a prefix, options and filters; a
  framework declares its routes in a table of its own, which the application's override; posts from other sites are
  refused unless a route takes them; and in development, routes are declared again when their classes change, without
  a restart. The development 404 lists the routes and says why those that matched passed the URL on. See
  `docs/ROUTING.md`. (#178)

  Upgrading: `RouteTable`, its `RouteHandler` and `RouteInvocation`, `RouteURL` and `RouteClaims` are gone.
  - Routes mapped with `RouteTable.defaultRouteTable().map( … )` are declared:
    `ERXRouter.declare( routes -> routes.map( "/about", AboutPage.class ) )`. A pattern's `*` is a wildcard as before.
  - A handler implements `er.extensions.routing.RouteHandler`, and reads named parameters
    (`invocation.parameter( "id" )`, from `/things/{id}`) instead of positions (`routeURL().getString( 1 )`).
  - `setFallbackRouteHandler( new ERXPublicResources() )` is `routes.fallback( new ERXPublicResources() )`, and
    `setNotFoundRouteHandler( … )` is `routes.notFound( … )`, in the application's declaration.
  - A route taking posts from other sites (an API, a webhook) says so: `CrossSite.ALLOWED`.

- **ERRouting is the routing core, a plain Java library**
  Path patterns, matching, conditions and converters (`er.routing.matching`, `er.routing.options`,
  `er.routing.conversion`), with no dependencies, for Java 21. ERExtensions depends on it.

- **Parsley 1.6.3**
  With ng-objects' template parser 0.1.4, which reads `:` attributes (`:book="$book"`) for route parameters.

## 2026-10-04 (8.0.17)

- **An application can be served beneath a path of its own**
  `er.extensions.ERXApplication.basePath=/shop` serves the application at `https://example.com/shop/…`
  behind a front end that passes paths on as they arrive: its generated URLs start with the base path,
  and a request's URL has it removed before anything else reads it. It needs short URLs. (#51)

- **The development not found page says why a route passed a URL on**
  A route handler that declines a URL can give its reason (`RouteTable.explainDecline( request, … )`),
  and the not found page in development lists what each handler that matched said, answering "why is
  this a 404" when a route matched. (#196)

- **A component created while the application runs renders in development**
  A new component's template is found at its first use, as is a template added to a component already
  in use: when a component's template is on disk but not yet in the project's resource index, the
  projects are rescanned and the component looked up again. (#171)

## 2026-10-02 (8.0.16)

- **Stamped resource URLs mark the stamp with an `@`**
  A resource's URL carries its content stamp as `css/site@3f9c1e07ab.css` instead of
  `css/site.3f9c1e07ab.css`, so the stamp stands apart from the name. The period form is still served,
  for pages rendered by an instance not yet updated during a deploy, as is the marker encoded (`%40`).
  (#168)

- **`RouteURL.getInteger` answers its default for a segment that isn't an integer**
  A URL is user input, so a segment that isn't an integer, or is too large for one, gets the default
  (null unless another is given), as a missing segment does. A route reading an id from `/team/abc`
  can then answer "not found", or decline the URL. (#169)

- **Parsley 1.6.2**
  Inline error display keeps what was rendered before the failing element (the doctype, the head,
  the stylesheets) instead of losing it and cascading into further errors. Recorded problems name the
  template location and tag (`Main.html:14:9 <wo:str>`), unknown-key messages explain collection
  operators and keys that can't be set, and stack traces point at template locations readably.

- **The resource cache's size is configurable**
  `er.extensions.ERXAppBasedResourceRequestHandler.cacheMegabytes` sets how many megabytes of
  resource content the resource request handler holds in memory in production, 64 by default. 0 holds
  none, reading every resource from its bundle for each request.

## 2026-10-01 (8.0.15)

- **A welcome page at `/`, and a helpful 404, in development**
  An application that hasn't mapped `/` shows a page there in development that says what's running and
  how to map a page of its own, instead of a 404. Any other URL no route claims shows the URL, the
  mapped routes and how to map one. Deployed, both stay the plain 404 they were. (#162)

- **Routing is a chain: routes, fallback, not found**
  A URL goes to the routes whose pattern matches it, then to the route table's fallback, if one is set,
  and then to its not found handler: the plain 404 by default, the pages above in development, or one
  of your own (`RouteTable.setNotFoundRouteHandler()`). Each handler answers, or declines by returning
  `RouteHandler.DECLINED`, and the next one gets the URL; a route can now match a pattern and still
  leave the URL to the routes after it. A URL every handler declines is passed on to the next handler
  in the server, for an application sharing its server with ng-objects, whose not found handler is
  `RouteTable.PassOnRouteHandler`; the plain 404 no longer passes it on, so an application on
  wo-adaptor-jetty answers with its own 404. Public resources are a fallback:
  `ERXApplication.setServesPublicResources(true)` is now
  `RouteTable.defaultRouteTable().setFallbackRouteHandler( new ERXPublicResources() )`.
  `RouteTable.notFoundRouteHandler()` is per table rather than static, and `RouteTable.routes()` lists
  the mapped routes.

- **The console logger understands log4j's named date formats**
  `%d{ISO8601}`, `%d{ABSOLUTE}` and `%d{DATE}` in `er.logging.pattern` write the dates they write with
  reload4j, instead of stopping the application while logging is set up.

- **ERLoggingLogback uses logback 1.6.5** (was 1.5.38).

## 2026-09-30 (8.0.14)

- **Stack traces written the same way with every logging backend**
  The console logger, reload4j and logback now write a logged throwable's stack trace through
  `ERXStackTraces`: a throwable that only wraps another (`NSForwardException`,
  `InvocationTargetException`) is printed as the one it wraps (`er.logging.stackTrace.unwrap`,
  default true), and optionally only the innermost cause (`er.logging.stackTrace.innermostOnly`).
  With reload4j it applies to every appender, not only `ERXConsoleAppender`. Skipping frames that
  are plumbing is opt-in (`er.logging.stackTrace.skipFrames`), with a new built-in list for today's
  stack (reflection, key-value coding, the element tree, Parsley, the request's dispatch, the
  server's threads) that takes a component action's trace from 40 lines to about 12, or patterns of
  your own (`er.logging.stackTrace.skipPatterns`). ERLoggingReload4j's `StackTraceSkipPatterns`
  files are gone, and the `er.extensions.stackTrace.*` keys are reported as obsolete, naming their
  replacements. An application's own `logback.xml` opts in with a `<conversionRule>` for `ex` (see
  docs/LOGGING.md). (#161)

- **Project bundles from build.properties only when `NSProjectBundleEnabled` is set**
  ERProjectLayout's bundle factory now follows the same rule as WebObjects' own project bundles, so a
  built application is never taken for a project, even when run from inside its project folder.
  wonder-slim and Project Wonder applications set the flag when run from their project; a plain
  WebObjects application passes `-DNSProjectBundleEnabled=true` in its development launch.

- **A layout key declared without a value stops the application**
  A line like `dir.woresources=` left in `build.properties` now stops startup with an error naming
  the key, instead of pointing at the project folder itself. Remove the line to use the default.

- **A warning when WebObjects' JavaXML is on the classpath**
  JavaWebObjects brings JavaXML along unless the pom excludes it, and it replaces the JDK's XML
  parsers and transformers for the whole application with old Xerces and Xalan, besides carrying
  its own log4j 1.2 and servlet API. Nothing in wonder-slim needs it. An application that has it now
  says so at startup, naming the jar and showing the exclusion to add to its pom. (#163)

- **More obsolete properties reported at startup**
  ERJavaMail's `er.javamail.*` settings, `er.extensions.ERXNSLogLog4jBridge` (NSLog always writes to slf4j,
  under the logger `NSLog`), and a few more EOF settings: `er.extensions.ERXOpenEditingContextLocksPassword`
  and the MySQL and PostgreSQL plug-ins' `com.webobjects.jdbcadaptor.*` options. All were set in applications
  started from the Project Wonder templates.

- **The current call stack read with `StackWalker`**
  `ERXExceptionUtilities.stackTrace()` and reload4j's `%@` conversion (the call stack of the logging
  call) read the stack with `StackWalker` instead of printing an exception and cutting up the text.
  `%@` now also leaves out the slf4j bridge's frames, and no longer drops the stack's last frame.

## 2026-09-30 (8.0.13)

This release is about project layout. A project's `build.properties` now says where its components
and resources are, and every project running from its folder in development is found from it,
instead of being guessed from its Eclipse natures.

- **A project can declare its layout in build.properties**
  `dir.components`, `dir.woresources` and `dir.webserverResources` in `build.properties` tell
  the application where to find its components and resources when it runs from its project folder,
  so a project laid out the "Fluffy Bunny" way works under Maven without a built bundle. Every
  project (a folder whose `build.properties` has `project.name` and `project.type`) is now found this
  way, instead of by its Eclipse natures: undeclared keys take the Maven-layout defaults, and only
  the declared folders are searched. A project that keeps WebObjects resources elsewhere, a model or
  `Properties` in `src/main/resources` or a Fluffy Bunny layout relying on having no m2e nature,
  must now move them or declare its folders. This comes from the new ERProjectLayout module, which
  `ERXApplication` registers before NSBundle is loaded. It depends only on ERFoundation, so it
  works with Project Wonder too. vermilingua 1.1.11 packages from the same folders. (#164)

- **The exception page shows the source of any project in the workspace**
  A stack frame counts as your own code, with its source shown, for any project running from its
  folder, not only one that ERFoundation loaded as an `NSMavenProjectBundle`. The source is looked
  for under `src/main/java`, then `Sources`, in the frame's own project, where before the main
  bundle's layout was applied to every project. (#165)

- **ERLoggingReload4j carries its own stack trace settings**
  `er.extensions.stackTrace.cleanup` and `er.extensions.stackTrace.skipPatternsFile`, with the
  `StackTraceSkipPatterns` files, moved from ERExtensions to ERLoggingReload4j: only its
  `ERXConsoleAppender` reads them. (#161)

## 2026-09-29 (8.0.12)

This release reworks how an application starts, is configured and logs. The configuration is
composed from all its sources before the application is constructed, frameworks take part in
startup as plugins, and the control panel shows where each value came from. Logging is configured
the same way whichever logging module is used, levels can be changed on a running instance, and
neither a logging module nor a log4j API is required any more.

- **The configuration is composed once, before the application is constructed**
  `ERXApplication.main()` now composes the configuration from all its sources before the application
  exists, so its constructor sees the properties it runs with. Before, the constructor saw only
  WebObjects' own pass: a property read there (the port, caching, `shortURLs`, `publicHost` and the
  like) set in `Properties.dev`, `Properties.<user>`, an optional configuration file or
  `/etc/WebObjects` showed in the startup report but had no effect. A JVM `-D` option now takes
  precedence over properties files throughout, where before it did in the constructor and not
  afterwards. A reload gives a property no file sets any more its value from launch back, instead of
  keeping it. The startup report lists every source, framework jars, JVM options and arguments
  included, and each property with the source that set it. `ERXConfigurationManager` now composes
  the configuration and holds its sources and where each value came from
  (`ERXConfigurationManager.current()`), and checks its files for changes on a thread of its own
  rather than on each request, replacing `ERXFileNotificationCenter`. Its old instance API
  (`defaultManager()`, the argument and loading methods) and
  `ERXProperties.pathsForUserAndBundleProperties()` and `applyConfiguration()` are gone.
  `ERXConfigurationManager.onChange()` calls a listener when a property's value changes (or any of a
  family of properties'), whether by a reload or by setting it on the running instance. A touch file
  (`er.extensions.ERXConfigurationManager.PropertiesTouchFile`) now works without
  `er.extensions.ERXFileNotificationCenter.CheckFilesPeriod`, which nothing reads any more. (#144)

- **Frameworks take part in startup as plugins, found through `ServiceLoader`**
  A framework or application module implements `ERXPlugin` and lists it in
  `META-INF/services/er.extensions.ERXPlugin`. Plugins are ordered by what they require
  (`requires()`), never by classpath order, with class name breaking ties. A missing required plugin
  or a cycle stops the launch with a message naming them. Each runs, in that order, at
  `beforeApplicationConstruction()` (the configuration composed, no application object yet),
  `finishInitialization()` (fully constructed, the application's own constructor included, and no
  request can arrive yet) and `didFinishLaunching()` (the adaptors are listening, and requests may
  be arriving concurrently). The application's own `finishInitialization()` and
  `didFinishLaunching()` run at the same points, once all plugins have run theirs. The plugins' and
  the application's `finishInitialization()` now run before
  `ApplicationWillFinishLaunchingNotification` is posted, rather than as observers of it, in no
  defined order among the others. A framework with a plugin has its `Properties` applied in plugin
  order, so it overrides the frameworks it requires. ERExtensions' principal, `ERXExtensions`, is
  now a plugin. (#33)

- **`ERXFrameworkPrincipal` removed; Ajax, AjaxSlim and ERControl are plugins**
  A framework joins startup by implementing `ERXPlugin` and listing it in
  `META-INF/services/er.extensions.ERXPlugin`, instead of subclassing `ERXFrameworkPrincipal`,
  registering itself from a static initializer and naming itself as `NSPrincipalClass`. The Ajax
  frameworks register their request handlers and response delegate once, when the application is
  constructed (before, on two notifications). ERControl maps its routes then too, after the
  application's own constructor, so a route the application maps at `/wonder/admin` wins as
  documented. `er.extensions.ERXFrameworkPrincipal.logLifecycle` is reported as obsolete. A
  framework still subclassing `ERXFrameworkPrincipal` fails at launch with a `NoClassDefFoundError`.
  (#33)

- **Logging works without a logging module**
  Without ERLoggingLogback or ERLoggingReload4j, slf4j found no provider and discarded everything.
  ERExtensions now logs to the console itself in that case (`ERXConsoleLoggingBackend`), with the
  same levels as the modules (`er.logging.level.*`, Project Wonder style `log4j.logger.*`, levels
  set on the running instance) and lines laid out by `er.logging.pattern`, and the control panel's
  Logging page works with it. A logging module stays the way to get files, appenders and its own
  configuration. It's named as slf4j's provider only when no logging module is on the classpath, so
  it never competes with one. (#157)

- **WebObjects needs no log4j API**
  WebObjects' form value encoder (`WOCGIFormValues$Encoder`) logs through the log4j 1.x API, so
  without one on the classpath the first URL with query parameters failed with a
  `NoClassDefFoundError`. `ERXApplication.main()` now rewrites the encoder to log through slf4j
  before WebObjects first uses it (`ERXLog4jRewrite`), with the JDK's class-file API and without an
  agent. If the encoder uses log4j in a way the rewrite doesn't handle, or is loaded already, it's
  left as it is, with a warning. (#156)

- **ERLoggingLogback: logback as the logging backend, and logging configuration every backend understands**
  A second logging module, ERLoggingLogback, puts logback behind slf4j, in place of
  ERLoggingReload4j (one or the other). An application whose own code calls the log4j API adds
  `log4j-over-slf4j` alongside it. Logging is configured in layers, lowest first: Project Wonder
  style `log4j.logger.*` and `log4j.rootLogger` levels (for logback; its appenders and layouts are
  named in a warning); `er.logging.level.<logger>` and `er.logging.pattern`, which every backend
  understands; the backend's own configuration (`log4j.*` for reload4j, as before, `logback.xml` for
  logback), which wins where it names a logger; and levels set on the running instance, which win
  over everything. With reload4j, console output without `log4j.*` configuration now uses
  `er.logging.pattern`'s layout (by default `%d{MMM dd HH:mm:ss} %-5p %c - %m%n`) instead of log4j's
  own. Literal parentheses in the layout, as in `(%F:%L)`, stay literal with logback, which
  otherwise reads them as grouping. The defaults, root at INFO and that layout, are set in
  ERExtensions' own `Properties`, so the Configuration page and the startup report show where they
  come from, and an application changes them by setting the same keys. (#44)

- **Logging is configured as soon as the configuration is composed**
  Logging used to be configured from the configuration only when `ERXApplication`'s constructor
  finished; until then, everything went to a console appender in a fixed layout. Now it's configured
  in `ERXApplication.main()`, right after the configuration is composed, so everything the plugins
  and the application's construction log reaches the configured log. After that, logging is
  configured again when a logging property changes (`er.logging.*`, `log4j.*`,
  `logback.configurationFile`), from a watched file or set on the running instance, instead of on
  every reload of the configuration. (#44)

- **What's written before `WOOutputPath` takes effect goes to the start of its file**
  WebObjects redirects the console to the `WOOutputPath` file while the application is constructed,
  so what was written before (logging while the configuration is composed, plugins before
  construction, WebObjects' own startup messages) went to the process's original output, which
  wotaskd doesn't keep. It's now kept from `ERXApplication`'s static initializer, while still
  written to the console, and written at the start of the file once WebObjects has redirected,
  between two marker lines (`ERXEarlyOutput`). WebObjects' rotation of the previous file is
  unchanged. (#158)

- **A password passed as an argument is masked in `sun.java.command` too**
  The startup report masks a property whose key names a secret, but the JVM keeps the whole command
  line in `sun.java.command`, so a password given as an argument was printed as part of that value.
  Now the value of every secret property is masked wherever it appears
  (`ERXConfigurationManager.maskedValue()`), in the startup report, the admin console and
  `ERXAdminDirectAction`'s property listing, which also escapes what it writes now. (#145)

- **ERControl: a Configuration page, with the plugins, the sources and where each value came from**
  The admin console's Properties section becomes Configuration (`/wonder/admin/configuration`). It
  lists the plugins in the order they run, with what each requires and provides; the sources in the
  order they're applied, with how many of each one's properties are in effect or overridden; and
  every property with the source it came from and the sources it overrides, or one source's
  properties with their own values. A value changed on the running instance is marked. A property
  set in the console is a source of its own, "Set on this instance", above every other source, the
  application's arguments included; it survives a reload of the configuration, and can be unset,
  giving the property back the value the other sources give it
  (`ERXConfigurationManager.setProperty()` and `unsetProperty()`). The files an application or
  machine usually configures with are listed even when they aren't there (the application's
  `Properties` files, `~/WebObjects.properties`, `/etc/WebObjects/Properties` and
  `/etc/WebObjects/<App>/Properties`, each optional configuration file), marked "no file present",
  here and in the startup report. (#146)

- **ERControl: a Logging page**
  The control panel shows the logging backend and its own configuration, and every logger with a
  level set: the level, the layer and source that set it (`er.logging.level.*` with the source it
  came from, `log4j.logger.*`, the backend's own configuration, or set on this instance) and what it
  overrides; or every logger the backend knows, filtered by name or to the application's own
  package, each with its own or inherited level. Clicking a level sets `er.logging.level.<logger>`
  on the running instance, so it survives a reload of the configuration; clicking it again unsets
  it. ERExtensions registers the page. A logging backend now also reports its name, its own
  configuration and the levels it set, and the loggers it knows (`ERXLoggingBackend`).
  ERLoggingReload4j's `ERXLog4JConfiguration` page is removed, with `ERXLog4jAction` and
  `ERXRadioButtonMatrix`: it worked with reload4j only, and its changes were lost the next time
  logging was configured. (#155)

- **Pages for the control panel are registered from ERExtensions, in categories**
  `ERXControlPages.register()` adds a page to ERControl's control panel: a category, a name (its
  path beneath `/wonder/admin`), a title, a description and the component rendering its content.
  Plugins, the application or any other code can register pages without depending on ERControl; when
  ERControl is present, it shows each one in its layout, behind its login, and lists it in its
  navigation under its category. The framework's own pages are registered the same way, in the first
  category, `wonder-slim`, with their paths unchanged. A name registered twice throws. (#154)

- **`ERXProperties`' readers are bridges to `NSProperties`**
  ERFoundation's `NSProperties` reads and converts property values exactly as `ERXProperties` did,
  so `stringForKey()`, `booleanForKey()`, `intForKey()`, `longForKeyWithDefault()`, `arrayForKey()`
  and their variants now call `NSProperties`' own, and either can be used. Values are no longer
  cached, so a value changed with `System.setProperty()` is read as it is (before, the value cached
  earlier was). A value spelled `-undefined-` reads as the string it is, or as no value for a
  boolean, number or array, instead of null or an exception. A test compares the two over a matrix
  of values, and another pins what each reader returns. `NSProperties.cacheEnabled` (a JVM option,
  `-DNSProperties.cacheEnabled=true`) must stay off; a warning says so at startup if it's on. (#148)

- **`ERXProperties` is typed reading only**
  The loading plumbing and the handling of secrets moved to `ERXConfigurationManager`:
  `isSecretKey()`, `maskedValue()` and `logString()` are `ERXConfigurationManager`'s now, and
  `propertiesFromArgv()` and the reading of properties files are private to it.
  `transferPropertiesFromSourceToDest()` and `explicitlySetKeys()` are removed. `ERXProperties`
  keeps what applications use it for: `stringForKey()`, `booleanForKeyWithDefault()` and the other
  typed readers. (#147)

- **WebObjects' NSLog output goes to slf4j, whichever logging backend is used**
  `ERXNSLogBridge`, in ERExtensions, replaces ERLoggingReload4j's `ERXNSLogLog4jBridge`: NSLog's
  out, err and debug go to slf4j's `NSLog` logger at INFO, WARN and DEBUG, from the first line of
  `main()` rather than once the application is constructed. Startup output WebObjects writes through
  NSLog therefore appears in the log's format. It caps NSLog's debug level at
  `er.extensions.NSLog.debugLevel` itself, replacing the separate logger `main()` installed, and
  stays an `NSLog.PrintStreamLogger`, which WebObjects requires where `WOOutputPath` is set.
  `er.extensions.ERXNSLogLog4jBridge.ignoreNSLogSettings` keeps its name. (#44)

- **The logging backend is found through `ServiceLoader`**
  A logging module lists its backend (`ERXLoggingBackend`) in
  `META-INF/services/er.extensions.logging.ERXLoggingBackend`; ERLoggingReload4j's is
  `ERXReload4jLoggingBackend`. It replaces the reflective `ERXTemporaryLoggingBridge`. At most one
  backend may be on the classpath: two stop the launch, naming both. (#44)

- **The logging classes are in `er.extensions.logging`**
  `ERXLoggingSupport` moved there from `er.extensions`, alongside the new `ERXLoggingBackend`,
  `ERXLoggingConfiguration` and `ERXNSLogBridge` and the logging modules' own classes. (#44)

- **Console logging for programs that don't start through `ERXApplication`**
  A stand-alone tool, a batch job or an application's unit tests get the console logger without a
  logging module by naming it as slf4j's provider:
  `-Dslf4j.provider=er.extensions.logging.ERXConsoleServiceProvider`. It configures itself from the
  system properties when slf4j starts it, so `-Der.logging.level.<logger>=DEBUG` and
  `er.logging.pattern` work there too. (#159)

## 2026-09-28 (8.0.11)

- **Resource URLs carry a stamp of the resource's content, and are cached for good**
  In production, resource URLs the framework generates carry a stamp of the resource's content in
  the file name: `/res/AjaxSlim/ajaxslim.0dc9602958.js`. A deploy that changes a resource changes
  its URL, so a stamped URL is served with `Cache-Control: public, max-age=31536000, immutable` and
  browsers never ask for it again. A request whose stamp isn't the resource's current one (from a
  page an instance not yet updated served during a deploy, say) is served with `no-cache`. The
  plain URL, `/res/AjaxSlim/ajaxslim.js`, keeps working as before. Not in development, where files
  change while the application runs. (#30)

- **Resources are validated with ETags instead of kept for a fixed hour**
  Every resource is served with an `ETag`, its content stamp. A resource requested by an unstamped
  URL (a public resource such as `/robots.txt`, or a `/res/…` URL written by hand) is served with
  `Cache-Control: no-cache` instead of `max-age=3600`: the browser keeps it but asks first, and a
  request naming the current `ETag` (`If-None-Match`) gets `304 Not Modified` without a body. So a
  deploy is picked up immediately, and an unchanged resource costs a small request. `If-Range` is
  answered with the range when it names the current `ETag`. Stamped URLs are still cached for
  good. (#138)

- **Resources are served in ranges, so video and audio play and seek properly**
  The resource request handler answers a request for part of a resource (`Range: bytes=…`, as
  browsers send for media) with `206 Partial Content` and just that part, a range past the end with
  `416`, and says `Accept-Ranges: bytes` on every resource. Before, a `<video>` got the whole file
  for every request, seeking was degraded, and browsers tended not to cache it. Public resources
  are served the same way. (#137)

- **The resource cache is bounded, and large resources are streamed**
  In production, the resource request handler kept every resource it had served in memory, for
  good. Resources over 1 MB (media, large documents) are now streamed from their bundle for each
  request, ranges included, rather than held in memory, and the rest are cached up to 64 MB in
  total, forgetting the least recently requested past it. (#139)

- **Public resources: files served at the root of the application's URL space**
  An application that calls `setServesPublicResources(true)` in its constructor serves the files in
  the `public` folder of its web server resources (`src/main/webserver-resources/public`) at the
  root, by path: `public/robots.txt` answers `/robots.txt`, `public/.well-known/security.txt`
  answers that path. For `favicon.ico`, `robots.txt`, `sitemap.xml` and other files asked for at a
  site's root. A URL is looked up only once no request handler or route has claimed it, so a route
  always wins over a file. The folder is indexed on first use, so a URL that isn't one of its files
  costs one set lookup; in development it's indexed again on a miss, so new files are served
  without a restart. The files are served by the resource request handler, as at their resource
  URLs. Off by default. (#32)

- **An Ajax request tells the client when the session has expired**
  WebObjects answered it with the session expiry page and status 200, or with whatever an
  application's `handleSessionRestorationErrorInContext` returns, often a redirect the browser
  follows without the client knowing. Now the response to an Ajax request whose session couldn't
  be restored gets status 403 and an `x-session-expired` header, whatever the application answered.
  A redirect's location moves to `x-session-expired-location`, and the application's cookies are
  kept, so a session it created to log the user back in survives. AjaxSlim shows "Your session has
  expired" with a Continue button that goes where the application sent the user, or reloads the
  page. Requests that aren't Ajax requests are answered as before. (#123)

- **AjaxSlim's Ajax requests are handled like component actions**
  `AjaxRequestHandler` (the `ajax` key) now extends `ERXComponentActionRequestHandler` instead of
  WebObjects' own component request handler, so Ajax requests follow the same rules as component
  actions. A request without a session no longer creates one, the session is checked back in
  exactly once however the request ends, and the page is restored through the session's page
  cache. (#130)

- **A partial submit takes the value of a field whose name is bound explicitly**
  An `AjaxObserveField` submitting only the changed field dropped its value when the field had a
  `name` binding (`<wo:textfield name="email" …/>`): the client named the field by its `name`, the
  server compared element IDs. Such a field now also renders its element ID (`data-element-id`),
  which the client sends instead. Text fields, text areas, password and hidden fields, and pop-up
  buttons. (#142)

- **Correct content types for icons, JSON and current web formats**
  Resources are served with `image/x-icon` for `.ico` (WebObjects says `text/plain`, so a
  `favicon.ico` went out as text), `application/json` for `.json` and `.map`, `audio/mpeg` for
  `.mp3`, and the types WebObjects' table doesn't know: `application/wasm`, `text/javascript` for
  `.mjs`, `application/manifest+json` for `.webmanifest`, `image/webp`, `image/avif` and
  `video/webm`. Also added: the Office Open XML and OpenDocument formats (`docx`, `xlsx`, `pptx`,
  `odt`, `ods`, `odp`), audio and video (`m4a`, `m4v`, `aac`, `flac`, `oga`, `opus`, `ogv`, with
  `ogg` as `audio/ogg`), `heic`, `md`, `vcf`, `rss`, `jsonld`, `epub`, `7z`, and `gz` as
  `application/gzip`. An unknown extension still gets `text/plain`. (#136)

- **The admin actions for the event pages work**
  `/wa/ERXAdminDirectAction/events` and `/eventsSetup` answered with a 500: they returned what the
  page's password form action returns, which for the event pages is null (show the same page
  again). They now return the page. Their password is `EOEventLoggingPassword`, not the statistics
  password. (#135)

- **A locale for one request: `ERXWOContext.setLocale(Locale)`**
  A page or route that knows its locale without a session to carry it (a stateless page rendering
  a customer's document in the customer's locale, say) sets it on the context. It comes before the
  session's and the application's locale, and lives only as long as the request. (#63)

- **Complete URLs from `completeURLWithRequestHandlerKey` are short**
  With short URLs on, `WOContext.completeURLWithRequestHandlerKey(…)` returned the long form, the only
  way of generating a URL that did. An absolute link built with it (in an email, say) is now short
  like every other URL. A URL asked for with an instance number keeps the long form, since a short
  URL can't carry one. (#128)

- **Regular resources in a folder named `WebServerResources` aren't served as web server resources**
  A resource counts as a web server resource only when it lies in its bundle's web server
  resources folder. (#133)

- **The resource cache no longer grows with every missing resource requested**
  In production, the resource request handler cached every response by path, a 404 included, so
  each new URL requested under `/res/` stayed in memory for good. Now only resources found are
  cached, and paths known to be missing go into a set of at most 10,000, forgetting the least
  recently requested. A repeated request for a missing resource still skips the lookup, which
  searches every bundle. (#133)

- **A resource URL that names no resource is a 404**
  `/res/foo` or `/res/` (no framework and resource name) answered with a 500. It's now a 404, and,
  not being a resource, isn't kept in the resource cache. (#133)

- **`AjaxFileUpload` no longer loads an error into its hidden iframe**
  The iframe's initial document was a direct action removed years ago, so every page with an upload
  loaded a 500 into it and logged an exception. It now starts as `about:blank`. Uploads work as
  before. (#124)

- **`ERXWOImage` renamed `ERXSVGImage`**
  The element behind `<wo:svg>` is named for what it's for: an `<img>` for an image resource or URL
  that doesn't read the image to work out its size, so it suits SVG images. Templates using
  `<wo:svg>` are unaffected; a template naming the class directly uses the new name.

- **`_WOJExtensionsUtil` removed, and `WOCollapsibleComponentContent`'s arrow has alt text**
  The components inherited from JavaWOExtensions read their bindings with the stock
  `valueForBinding()`. `_WOJExtensionsUtil.valueForBindingOrNull()` only differed for a binding
  written as the literal `$null`, which arrives as `false`. `WOCollapsibleComponentContent`'s arrow
  image now has the alt text it was always meant to have ("Click to collapse" / "Click to expand"),
  which was looked up as a binding name and so was always empty. (#134)

## 2026-09-28 (8.0.10)

- **Sticky sessions behind mod_proxy_balancer work again**
  The route cookie (`routeid_<app>`) has its leading dot again: `.app_2001`. mod_proxy_balancer reads
  the route after the first dot of the sticky value, so without it, requests weren't kept on the
  instance holding their session. This had been broken since 8.0.0. The setup the cookie pairs with
  is documented in `ERXProxyBalancerConfig`. (#40)

- **Handler URLs always reach their handler**
  A wildcard route matching a handler key's URLs, such as a catch-all `/*`, took over component
  actions, direct actions and resources. Now a URL whose first segment is a registered request
  handler key always goes to that handler, as `RouteTable` already required of mapped routes.
  `ERXShortURLs.canonicalize()` no longer takes a route predicate. (#112)

- **The development endpoints refuse forwarded requests**
  A proxy or web server adaptor on the same machine makes every request it forwards come in on a
  loopback connection. `/eval`, `/log` and `/problems` now also refuse requests carrying the headers
  such forwarders add (`x-forwarded-for`, `forwarded`, `x-real-ip`, `x-webobjects-remote-addr`,
  `remote_addr`, `remote_host`). (#110)

- **A request on port 443 is secure**
  `ERXRequest.isRequestSecure()` (and so `isSecure()`) again treats a request whose server port
  header is `443` as secure, as WebObjects' own `isSecure()` does, alongside the `https: on` and
  `x-forwarded-proto` headers. (#107)

- **Cookie expiry dates as WebObjects writes them, without the overflow**
  A cookie with a timeout gets an `expires` date along with `max-age` again. An explicitly set
  expiry date is kept rather than replaced by one computed from the timeout, and timeouts over 24
  days no longer overflow into a date in the past. (#109)

- **`SessionDidRestoreNotification` is posted once, and only on restore**
  `ERXSession.awake()` no longer posts it in addition to WebObjects, which already does when it
  restores a session. A new session gets WebObjects' `SessionDidCreateNotification` only.
  Observers of new sessions should observe that one. (#108)

- **`ERXNumberFormatter` factor patterns parse at full precision**
  Parsing with a multiplying pattern and no explicit scale, like `(*1000=)0`, now divides at full
  precision, so a displayed value parses back to what it was (`"12"` → 0.012, where it gave 0). (#111)

- **Every patch to WebObjects is explained**
  Each class that patches or replaces a part of WebObjects (the app server, the elements, and the
  rest) now says in its documentation what it changes and why, so it's clear what an application
  gets that plain WebObjects wouldn't do. (#116)

- **`ERXActionLogging` removed, and with it the `WOActiveImage` and `WOSubmitButton` patches**
  `ERXWOHyperlink`, `ERXWOActiveImage` and `ERXWOSubmitButton` wrote the invoked element into the
  session under `ERXActionLogging`, which nothing read. `ERXWOActiveImage` and `ERXWOSubmitButton`
  existed only for that, so `WOActiveImage` and `WOSubmitButton` are now WebObjects' own again.
  (#117)

- **`ERXUnitAwareDecimalFormat` removed, `ERXUtilities.formatByteCount(long)` added**
  Byte counts are formatted by one method: "123 B", "1.5 KB", "1.2 GB", in steps of 1000, with one
  decimal, the number formatted in the current locale. Log patterns using `%V` (JVM memory) and the
  statistics pages show sizes this way now, where they were in steps of 1024 with two decimals. An
  application using `new ERXUnitAwareDecimalFormat(ERXUnitAwareDecimalFormat.BYTE).format(n)` calls
  `ERXUtilities.formatByteCount(n)` instead. (#127)

- **`ERXMutableURL` removed**
  The framework's few uses of it (building the development stop URL, encoding a redirect's query
  parameters, appending a parameter to an Ajax URL) are handled by two small helpers,
  `ERXUtilities.appendQueryParameter(url, key, value)` and `ERXUtilities.queryString(dictionary)`,
  and `java.net.URI`. The URLs they produce are unchanged. (#126)

- **`ERXSimpleTemplateParser` removed**
  Its only use was formatting `ERXPatternLayout`'s `%W` (application info) and `%V` (JVM memory)
  conversions, which now fill their few placeholders themselves. Their output is unchanged.
  (#125)

- **`ERXKeepAliveResponse` delivers what's pushed, intact**
  The stream behind Ajax's push handler returned bytes signed, corrupting non-ASCII content (and
  ending the stream at a 0xFF byte). It held back data queued while the previous item was being
  written, so the message part of every push waited for the next push. A spurious wakeup also ended
  the stream. `reset()`, used when a push response is stopped, now ends the stream. (#105)

- **`ERXWOHyperlink` passes on only actions inside it**
  A link hands an action to its children only when the sender's element ID is inside the link's
  (`1.2.…`), no longer when it merely starts with the same characters (`1.21.0`). (#106)

- **The monitor server answers with a status**
  `ERXMonitorServer` now answers a missing or wrong `monitor-service-password` with 401 and an
  unknown operation with 404, where it dropped the connection (after a `NullPointerException` when
  the header was missing). The password is compared in constant time. (#114)

- **The monitor server's thread dump has complete stacks**
  `/monitor/jstack` wrote each thread with `ThreadInfo.toString()`, which stops after eight frames.
  Every thread is now written with its complete stack, the lock it's waiting on and the monitors it
  holds. (#115)

- **`WOTextField` ignores content, as WebObjects' does**
  Anything written inside a text field element was rendered after the `<input>`. It's now ignored,
  as in WebObjects' own `WOTextField`. (#120)

- **A component redirect without a page cache fails with an explanation**
  With `WOPageCacheSize=0`, `ERXRedirect` redirected to a component instance with the page's name
  in the URL, WebObjects' form for recreating a page without a cache. Those URLs aren't served, and
  a recreated page wouldn't be the instance redirected to, so the redirect now throws, naming the
  alternatives: a direct action or a URL. (#122)

- **The resource URL prefix properties are reported as obsolete**
  `er.extensions.ERXResourceManager.resourceUrlPrefix` and `secureResourceUrlPrefix` (a CDN host
  for resource URLs, in Project Wonder) are no longer applied. Setting one only kept resource URLs
  from being completed. They're now listed in the startup report of obsolete properties. (#113)

- **`ERXErrorPage` documented, and its session-expiry helper fixed**
  `ERXErrorPage` is a general-purpose error page for applications:
  `ERXErrorPage.errorWithMessageAndStatusCode(message, context, status)` shows a message (HTML) on
  the framework's error page layout, with a button back to the application.
  `handleSessionRestorationErrorInContext(context)`, for an application's own override, no longer
  links to a hardcoded `/Apps` path; the page's button leads back instead. The layout,
  `ERXErrorLayout`, is documented for error pages of your own, with a new `.api` and `.apiext`. (#119)

- **The dead "all bundles loaded" startup path is gone**
  `ERXExtensions` observed `NSBundleAllBundlesLoaded` to load the configuration, a notification
  nothing posts any more, so that code never ran. The configuration is loaded, as it has been all
  along, when the application has been created (`ERXExtensions.finishInitialization()`).
  `ERXNotification.AllBundlesLoadedNotification` and `ERXConfigurationManager.initialize()` are
  removed. (#118)

- **`ERXWOForm` no longer publishes its `enctype`**
  The form put its `enctype` into `ERXWOContext.contextDictionary()` while it rendered, for a file
  upload element that has since been removed. Nothing reads it. The `enctype` attribute is rendered
  as before. (#121)

## 2026-09-27 (8.0.9)

- **`dateformat` formats java.time values and takes `DateTimeFormatter` patterns**
  `ERXWOString`'s `dateformat` now formats `LocalDate`, `LocalDateTime`, `ZonedDateTime`, `Instant`
  and every other java.time value. The binding takes either syntax: an `NSTimestampFormatter` pattern
  (`%d.%m.%Y`, recognised by a `%` outside quoted text) is translated to its `DateTimeFormatter`
  equivalent, covering all of `NSTimestampFormatter`'s conversions including the locale's `%x`, `%X`
  and `%c`, and renders exactly as `NSTimestampFormatter` would, so templates keep their patterns when
  a model moves to java.time. A `DateTimeFormatter` pattern (`dd.MM.yyyy`) is used as is. `formatter` also accepts a
  `DateTimeFormatter`. The formatters are cached and shared, since they're thread-safe. A pattern that
  doesn't fit the value (a time on a `LocalDate`) throws, as does an unknown conversion, which
  `NSTimestampFormatter` silently renders as `%`. `NSTimestamp` values format exactly as before.
  The logic lives in `ERXDateTimeFormatters`. (#86)

- **Concurrent request handling is on by default**
  ERExtensions' Properties now set `WOAllowsConcurrentRequestHandling=true`. With WebObjects' own
  default (`false`) and a multithreaded adaptor such as Jetty, an instance handles one component,
  direct action or route request at a time, for all users. An application that relies on requests
  being serialised sets the property to `false` at launch or in its Properties. Requests for the
  same session are still serialised while their session is checked out. (#91)

- **`ERXStyleSheet` is a dynamic element**
  Templates are unchanged: `filename`/`framework` or `href`, and `media` and `inline`, work as before,
  and the `<link>` still goes in the head (or inline in Ajax responses), once per page. Other
  bindings are now passed through as attributes of the tag, such as `integrity` and `crossorigin`
  for a CDN `href`. The rarely used mode that rendered the element's content into a stylesheet
  cached in the session (the `key` binding) is gone. A template using it throws when parsed, with a
  message naming the replacement: a stylesheet file or a `<style>` tag. The older binding names
  `styleSheetName`, `styleSheetFrameworkName` and `styleSheetUrl` still work, deprecated, each used
  only when `filename`, `framework` or `href` isn't bound. The element's bindings are documented in
  a new `ERXStyleSheet.apiext`. (#92)

- **`ERXJavaScript` references one script, like `ERXStyleSheet`**
  `filename` (a resource, or a complete URL) and `framework` work as before, rendered in place, and
  other bindings are still passed through as attributes. A bound `type` (such as `module`) now
  replaces the default `text/javascript` instead of adding a second `type`. The content modes are
  gone: inline content, `scriptString`, `scriptFile`, `hideInComment` and the session-cached
  `scriptKey` (with its direct action). A template using them throws when parsed, with a message
  naming the replacement: a script file or a `<script>` tag. The
  `er.extensions.ERXJavaScript.hideInComment` property is reported as obsolete. The older names
  `scriptSource` and `scriptFramework` still work, deprecated, each used only when `filename` or
  `framework` isn't bound. The bindings are documented in a new `ERXJavaScript.apiext`. (#93)

- **The development endpoints only answer requests from this machine**
  `/eval`, `/log` and `/problems` (development mode only) check the address of the connection the
  request came in on and refuse anything but a loopback address with a 403. Request headers aren't
  consulted, since the client sets them, and when the address isn't known the request is refused.
  `/eval` previously decided from headers the adaptor in use doesn't set, and `/log` and `/problems`
  had no check.

- **Obsolete properties are reported, not refused**
  Properties that configure removed features no longer stop the launch. They're listed with the
  reason they're no longer read in an `OBSOLETE PROPERTIES` section of the startup banner, with a
  warning in the log, so an older application can be tried on the framework as it is and cleaned up
  afterwards. (#77)
  The list covers the properties Project Wonder's ERExtensions, JavaWOExtensions, WOOgnl and Ajax
  read that wonder-slim no longer does, each key listed explicitly, about 260 entries: `ERXLocalizer`,
  the `replaceApplicationPath` pair, EOF, JDBC, model and synchronizer settings, the SSL adaptor,
  crypto, the administrative direct-action passwords, ERXPatcher, WOOgnl and more. Where a
  replacement exists, the message names it. The table and the report live in a class of their own,
  `ERXObsoleteProperties`. (#79)

- **The element packages, reorganized**
  Every element the framework offers now lives in a package that says how it relates to WO's
  elements:
  - `er.extensions.components.patches`: elements that extend the WO element they replace.
    `ERXWOHyperlink`, and the former nested classes of `ERXDynamicElementsPatches`, now
    top-level classes: `ERXWOSubmitButton`, `ERXWOActiveImage`, `ERXWOText`, `ERXWOHiddenField`,
    `ERXWOPasswordField`, `ERXWOPopUpButton`, `ERXWOBrowser` and `ERXWOCheckBoxList`. (#97)
  - `er.extensions.components.replacements`: our own implementations, installed in place of a WO
    element. `ERXWOConditional`, `ERXWOForm`, `ERXWORepetition`, `ERXWOString`,
    `ERXWOSwitchComponent` and `ERXWOTextField`. (#98)
  - `er.extensions.components.additions`: our own elements, ones WO doesn't have. `ERXStyleSheet`,
    `ERXJavaScript`, `ERXWOImage`, `ERXWOComponentContent`, `ERXWOComponentInstance`, and from the
    former `er.extensions.components.conditionals`: `ERXElse`, `ERXWOSwitch`, `ERXWOCase` and
    `ERXWOTemplate`. (#99)

  `er.extensions.components` keeps the Java-only classes: the component and element base classes and
  `ERXComponentUtilities`, which now also holds the list elements' `list`/`selections` handling as
  `ERXComponentUtilities.InputLists`. Templates are unaffected, since elements are found through
  the tag aliases and by their simple names. A class referring to one of the moved classes updates
  its import. Property names that included a class's name are unchanged.

- **Every element ERExtensions exposes has an `.api` and an `.apiext`**
  Bindings with types, documentation, constraints between bindings, content and attribute policy,
  and deprecated older names, for the elements installed in place of WO's (and what they change),
  wonder-slim's own elements, and the components carried over from JavaWOExtensions. Several
  existing `.api` files were corrected along the way. (#96)

- **Response compression fixes**
  Responses worth compressing now carry `Vary: Accept-Encoding`, so caches don't hand gzipped
  content to clients that can't read it. Responses already encoded in any coding are left alone. A
  content stream that fails to compress becomes a logged 500 instead of an empty 200. Content types
  are matched without their parameters, so `application/json; charset=utf-8` is compressed. The
  default extra types are now `application/javascript`, `application/json`, `application/xml` and
  `image/svg+xml`. `Accept-Encoding` quality values are honoured, and responses under 1 KB aren't
  compressed. The copied-in byte buffer class and compression helpers are replaced with a single
  method built on the JDK's streams, which no longer holds a buffer the size of the uncompressed
  response, and reads content in place and within its range (content that's a slice of a larger
  array was compressed from the start of the array). Compression is still off by default (`er.extensions.ERXApplication.responseCompressionEnabled`). (#83)

- **ERXP lists every configuration parameter ERExtensions reads**
  Each property key ERExtensions reads is a constant in the `ERXP` enum, documented with what
  the property does and its default, and grouped by the class that reads it. Code references
  the constant instead of a key written out as a string, so the enum is the one place to find out
  what can be configured. Nothing is read differently. The public `ERXStats.STATS_ENABLED_KEY` and
  `STATS_TRACE_COLLECTING_ENABLED_KEY` constants are replaced by `ERXP.STATS_ENABLED` and
  `ERXP.STATS_TRACE_COLLECTING_ENABLED`. (#81)

- **`ERXRequest.remoteAddress(WORequest)`, one way to get the client's address**
  Checks the address a WO adaptor passes on (`x-webobjects-remote-addr`, `remote_addr`,
  `remote_host`, `pc-remote-addr`), then the first address in `x-forwarded-for`, then the
  connection's address, else null. `remoteHostAddress()` returns the same, or `"UNKNOWN"`. It
  no longer prefers the connection's address under direct connect (behind a proxy that's the
  proxy's) or returns the whole `x-forwarded-for` list, and no longer reads `remote_user`, which is
  a user name. `ERXHTTPUtilities` is removed: use `ERXRequest.remoteAddress(request)` in place of
  `ERXHTTPUtilities.ipAddressFromRequest(request)`. (#102)

- **A null `dateformat` uses the default date format**
  When `ERXWOString`'s `dateformat` is bound but evaluates to null, a timestamp is now rendered with
  the default format, the full timestamp (`2026-09-27 12:00:00 Etc/GMT`) in the default time zone,
  as `numberformat` already did for numbers. A typo had kept this from ever happening, so these
  values were rendered with `toString()`. Unbound and constant formats are unaffected. (#85)

- **The legacy component request handler is gone**
  `ERXComponentActionRequestHandler`, the default since July, is now the only component-action
  handler. `ERXComponentRequestHandler`, the patched copy of WebObjects' stock handler, and the
  `er.extensions.ERXComponentActionRequestHandler.enabled` property that switched back to it are
  removed. (#75)

- **The frameworks' own Properties files carry only active properties**
  Entries nothing reads any more (`hasLocalization`, the `load.Properties.framework` markers, and
  commented-out documentation for removed features) are gone, so a stock application starts
  without an obsolete-properties report, and the files document only what can actually be
  configured. (#80)

- **`ERXNumberFormatter` factor patterns round only as the pattern says**
  A pattern dividing by a factor without an explicit scale, like `(/1024=)0.00 KB`, now divides at
  full precision and leaves the rounding to the pattern. Previously the division rounded to the
  value's own scale (or 4 digits for whole numbers), so 1500.5 displayed as `1.50 KB` rather than
  `1.47 KB`. (#103)

- **`bindingNamed()` removed**
  `ERXComponentUtilities.bindingNamed(name, associations)` and `ERXDynamicElement.bindingNamed(name)`
  were second names for a dictionary lookup. Use `associations.objectForKey(name)`, or
  `associations().objectForKey(name)` in a dynamic element. (#84)

- **`ERXExpiringCache` deleted**
  Its only users were the session caches of `ERXStyleSheet`'s and `ERXJavaScript`'s content modes,
  which are gone. The `er.extensions.ERXExpiringCache.reaperFrequency` property is reported as
  obsolete. (#94)

- **`WOBatchNavigationBar` deleted**
  Its Java class went in 2021 along with the other `WODisplayGroup` code, but the template and `.api`
  stayed behind. The template relied on the deleted class and on `WODisplayGroup`, so the component
  couldn't render. (#96)

- **`ERXStats.logStatisticsForOperation()` sorts by its operation again**
  The logged entries are ordered by the given operation ("sum", "count", "min", "max", "avg" or
  "key"), ascending. An unknown operation throws. (#104)

## 2026-09-26 (8.0.8)

- **URL handling as its own layer**
  The request canonicalisation, short URLs and the route request handler moved out of
  `ERXApplication` and `ERXWOContext` into two classes in the inheritance chain,
  `ERXRoutingApplication` and `ERXRoutingContext` (`WOApplication <- ERXRoutingApplication <-
  ERXAjaxApplication <- ERXApplication`, and likewise for the context). `ERXShortURLs` moved to
  `er.extensions.routes` with them. `createRequest()` is now final on the routing layer: an
  application that constructs a request class of its own overrides `newRequest()` instead.

- **Removed, unused or superseded**
  - `ERXRequest.remoteHostName()`: it returned the host the request was addressed to, not the
    client's; the lookup lives on privately behind `_serverName()`.
  - `ERXRequest`'s `wodata` override, the receiving half of a path-style resource URL form nothing
    generates any more. Dynamic data (`<wo:img data="..." />`) is unaffected: it uses the query
    form, which WebObjects reads itself.
  - `ERXSession.didBacktrack()` and `lastActionWasDA`, a context-ID heuristic that nothing called
    and that misreported under Ajax traffic. The repeated-request guard of the page cache is
    unaffected.
  - `ERXWOContext`'s static `directActionUrl(...)` helpers and its three-argument
    `directActionURLForActionNamed(name, query, includeSessionID)`; use WebObjects' own
    `directActionURLForActionNamed(name, query, secure, port, includeSessionID)`.
  - `WOAdaptorPlain`, an experimental adaptor, and the deprecated
    `WOExceptionPage.reportException()` (the page is used automatically).

- **Smaller changes**
  - `ERXWOContext` no longer keeps its own flag for complete-URL generation: `WOContext` tracks
    the mode itself, including through the internal `_generateCompleteURLs()`, which the flag
    missed and then misreported.
  - `ERXNotification.addObserver(Consumer)` returns a `Registration` whose `remove()` unregisters
    the observer. Lambda observers used to stay registered, and retained, forever.
  - `ERXStatisticsStore`'s listener and request description types are nested in the class
    (`ERXStatisticsStore.Listener`, `ERXStatisticsStore.RequestDescription`);
    `er.extensions.statistics.store` is gone. A custom listener implements `slowRequest` instead
    of `log`.
  - The session cookie settings (`er.extensions.ERXSession.useSecureSessionCookies`,
    `useHttpOnlySessionCookies`) are read once, like the SameSite setting;
    `useHttpOnlySessionCookies()` is no longer static, so both can be overridden as documented.
  - `ERXExceptionManager` records the component hierarchy itself; `ERXWOContext.componentPath()`
    moved there.
  - Dependencies: ng-core 0.1.3, wo-adaptor-jetty 0.12.1, slf4j 2.0.20.

- **WebObjects' own `browserLanguages()`**
  `ERXRequest` no longer overrides `browserLanguages()`; WebObjects' implementation is used. It
  takes the `Accept-Language` languages in the order the header lists them (current browsers list
  them by weight, so the first language is the same), maps regional tags to the base language
  (`en-US` is `English`, not `English_US`), and no longer appends `Nonlocalized` and a default
  language to the list. Components and resources in `Nonlocalized.lproj` (or outside any `.lproj`)
  are found as before, whatever the list says: WebObjects always falls back to them. It matters
  only to an application with language-specific `.lproj` folders, where the default language was
  searched last; such an application sets the session's languages itself. (#64)

- **User-agent detection reduced to what still holds**
  `ERXUserAgent.of(request)` (or `ERXUserAgent.parse(header)`) tells you the browser family
  (Chrome, Safari, Firefox, Edge, Opera, other) and its major version, the operating system
  (macOS, Windows, iOS, Android, Linux, other), whether it's a mobile device, and whether it's a
  bot, matched against a short list of markers covering current crawlers, AI fetchers and scripted
  clients (`ERXUserAgent.addBotMarkers` extends it). Browsers freeze operating system versions and
  minor browser versions in the user-agent string, so those are no longer offered.
  `er.extensions.browser` (`ERXBrowser`, `ERXBasicBrowser`, `ERXBrowserFactory`) and its
  463-pattern `robots.txt` are removed, as are `ERXRequest.browser()` and `ERXSession.browser()`.

- **Locale-aware formatting replaces ERXLocalizer**
  Numbers and dates formatted and parsed by WOString and WOTextField (`numberformat`,
  `dateformat`) can follow a locale of the application's choosing: the one it gave a session
  (`ERXSession.setLocale`), or the application's (`ERXLocale.setApplicationLocale`, typically
  called once in the application's constructor). Patterns are written in the usual form (`#,##0.00`); the locale
  decides separators and month and day names - for parsing as well as output, so in an Icelandic
  locale a text field reads `12.50` as 1250. With no locale configured, formatting and parsing are
  exactly as before, whatever the JVM's default locale. The locale a request asks for in its
  `Accept-Language` header is available as `ERXRequest.requestedLocale()`, a hint the application
  may act on; the framework never formats in it by itself. See `er.extensions.appserver.ERXLocale`.

  Formatters are no longer shared between requests; one is created per use, which costs a
  microsecond or two and removes the thread-safety problem of the shared instances. The
  `setNumberFormatterForPattern`, `setDateFormatterForPattern` and `sharedInstance` methods went
  with the shared repositories.

  `ERXLocalizer` is removed, together with string lookup from `.strings` files, the per-session
  localizer (`ERXSession.localizer()`, `language()`, `setLanguage(String)`,
  `availableLanguagesForTheApplication()`, `availableLanguagesForThisSession()`) and
  `ERXComponent.localizer()`. Any `er.extensions.ERXLocalizer.*` property now stops the launch
  with a pointer to the replacement.

## 2026-09-22 (8.0.7)

- **ERControl: the framework's control panel**
  A new framework, the counterpart of ng-objects' ng-control, holding the diagnostic and
  administrative pages for a running application behind one gate and one set of routes beneath
  `/wonder/admin`. Overview (uptime, sessions, memory, instance facts; collect garbage, stop),
  statistics (what the statistics store has counted, and the ERXStats summary), events (which
  event classes are recorded, and the recorded events as a tree), exceptions, sessions and
  caches, threads, the log, properties (everything in effect, explicitly set keys marked, secrets
  masked, set a property on the running instance) and bundles. A request is let in in
  development mode, or once its session has logged in with `WOMonitorServicePassword` - the
  same property ERXMonitorServer reads, so one secret opens both; unset means closed. A logged-in
  session ends after ten minutes without activity. Optional: an application that does not depend
  on the framework has no administrative surface at all.

  Everything only the control panel uses moves there from ERExtensions, package names
  unchanged: WOStatsPage, the WOEvent pages, ERXExceptionManagementPage, the session cache
  overview pages, ERXStatsSummary, the WX outline components, and the resources only they use.
  The data they show stays in ERExtensions. The admin direct actions keep working where the
  framework is present.

- **One canonical URL form for every inbound request**
  `ERXApplication.createRequest()` turns every inbound URL into the canonical WebObjects URL for it
  before WO parses it (`ERXShortURLs.canonicalize`), so nothing downstream depends on the shape the
  front end delivered. A URL whose first path segment names a registered request handler becomes
  `<prefix>[/N]/<key>/...`; everything else is a route and becomes `<prefix>[/N]/route/<path>`.
  That holds for freestyle URLs (`/a/b`), URLs carrying an adaptor prefix (`/Apps/WebObjects/App.woa/a/b`,
  whatever adaptor path the front end uses), URLs carrying the instance number mod_WebObjects adds
  (`/App.woa/1/a/b`), and URLs already in the canonical form. The prefix a request carried is kept;
  one that carried none gets the application's own. URLs naming another application pass through.

  `route` is a registered request handler key, served by `RouteRequestHandler`, which hands the
  request to the route table. It is internal: generated URLs never contain it, and public URLs are
  identical in development and deployment. `RouteRequestHandler` is also the default request handler,
  as a safety net for a request that arrives uncanonicalized. Since WO only ever parses well-formed
  URLs now, the `WODynamicURL` subclass that disabled its validity check is gone.

  A front end forwarding paths to a WebObjects adaptor should forward the canonical form itself:
  `/Apps/WebObjects/App.woa/route/<path>`, for every path. Behind the key the URL is an ordinary WO
  handler URL the adaptor passes through untouched, so a numeric path such as `/1234` or a trailing
  slash survive - in the bare-prefix form WO's URL grammar, and the adaptor, read `1234` as an
  instance number. The application sorts handler-key URLs (`/route/res/...`) from routes itself, so
  the front end needs no list of handler keys.

  `RouteRequestHandler.routePath(WORequest)` is public: the path a request asked for - the route
  path of a route, the short path (`/wa/default`) of a handler request - for applications
  describing the current page (canonical URLs and the like) instead of reading `request.uri()`. Mapping a route whose first segment is a registered request handler key fails at
  mapping time, since such a route could never be matched. `tools/playwright-bridge/examples/
  route-url-shapes.mjs` probes the URL-shape matrix.

- **`com.webobjects.woextensions` is gone**
  The components inherited from JavaWOExtensions now live in packages of the framework's own:
  the error pages in `er.extensions.components.errorpages`, the rest (WOCollapsibleComponentContent,
  WOIFrame, WOKeyValueConditional, WOLongResponsePage, WOMetaRefresh, and in ERControl the
  statistics and event pages) in `er.extensions.components.woextensions`. Templates are
  unaffected; an application importing `ERXErrorPage` or `WOExceptionPage` by class updates the
  import.

- **Parsley 1.6.1**
  The `/problems` development endpoint now reports from Parsley's recording store, so it lists the
  runtime problems the application rendered into its pages.

- **ERXURLRewriter is gone**
  The pattern-and-replacement applied to generated URLs
  (`er.extensions.ERXApplication.replaceApplicationPath.pattern` / `.replace`) has been removed.
  It rewrote outbound only, leaving the inbound half to the front end, and with short URLs on it
  never saw the long form its pattern was written to match. An application that still sets either
  property refuses to launch and says why, rather than ignoring the configuration.

- **Describing the application to the deployment stack**
  `/wa/ERXDirectAction/describe` answers `{"framework":"wonder-slim"}`, so wotaskd can tell a
  wonder-slim application from a Project Wonder or plain WebObjects one. Temporary, until
  ng-objects and wonder-slim share a response structure for this.

- **Admin action password check**
  `ERXAdminDirectAction` reads the statistics store's password itself; `ERXPrivateKVC` is gone.

## 2026-09-20 (8.0.6)

- **Legible startup output**
  The properties report is two banner sections: the Properties files in load order (including
  framework Properties inside jars, which the old report skipped), and every property in effect,
  alphabetically, with the keys a Properties file or the command line set marked with `*` - so the
  application's own configuration stands out while WebObjects' and the JVM's defaults stay
  available. Keys that look like secrets (passwords, API keys, tokens, credentials) are masked; the
  classpath is printed one entry per line. WO's own NSLog.debug chatter - the dump of every WO
  default, "Application project found", "Waiting for requests..." - is capped at DebugLevelCritical
  by default (`-Der.extensions.NSLog.debugLevel=2` restores it). The banner ends with an
  application section: the name WebObjects resolved (with the bundle name alongside when the two
  differ), the pid, and the direct connect URLs - in every mode, and last, since it is what one
  reaches for first. The ERXFrameworkPrincipal lifecycle trace is opt-in
  (`er.extensions.ERXFrameworkPrincipal.logLifecycle`).

- **Logging works from the first line**
  A console appender is installed as `main()`'s first act, so nothing logged during WO's and the
  application's own initialization is dropped any more and log4j's "No appenders could be found"
  is gone. That was the root cause of constructor-time log calls silently vanishing: log4j was only
  configured once the bundles had loaded, which is during the WOApplication constructor.
  `docs/LOGGING.md` documents the stack, the timeline and the remaining traps.

- **Generated URLs name the application on every request**
  A root request (`/`, or a front end's rewrite of it that omits the application name) parses to an
  empty name, and a context created by a handler a route delegates to never saw the fixup RouteAction
  applied for its own - every URL such a context generated read `/cgi-bin/WebObjects/.woa/wo/…`,
  which WO accepts but short URLs could not recognise as the application's own. The context's URL
  base is now normalised in ERXWOContext for every context.

- **User-facing error pages**
  WOSessionRestorationError, WOPageRestorationError, WOSessionCreationError and ERXErrorPage share
  one calm layout - a centered card with an icon, a heading, a plain-language explanation, one
  action back to the application, and the application's name - self-contained (inline CSS, no web
  resources, no scripts), since an error page must render when something has already gone wrong.
  The session page tells the user the timeout. The 2000-era tables and exclamation.gif are gone.

- **Browser pool and reference counting removed**
  ERXBrowserFactory kept shared ERXBrowser objects in a manually reference-counted pool, released
  from ERXSession.terminate() and ERXRequest.finalize(). The counting never freed anything - the
  factory also cached every browser by user-agent string in a static, unbounded map, which was the
  actual leak (one entry per distinct user-agent ever seen). Pool, counters, retainBrowser /
  releaseBrowser and the finalizer are gone; the user-agent cache is a bounded LRU.

- **WOConditional resolves to ERXWOConditional**
  The full element name now maps to ERXWOConditional like the `if` / `conditional` / `condition`
  shortcuts already did, so every conditional tracks its state and `<wo:else>` works after any of
  them. The `else` shortcut moved into ERExtensions' own tag aliases, next to the element it names.

- **Streams of unknown length are never compressed**
  An open-ended response (server-sent events, say) has no end to read to; gzipping it would block
  forever. ERXResponseCompression leaves such responses alone.

- **Housekeeping**
  ng-core 0.1.2 released, so the dev-mode classes are a regular dependency again; vermilingua 1.1.10;
  a note in RouteAction on what it would need to stand alone if routing were split out. AjaxPlayground
  gained server-sent-events and Datastar-over-SSE scenario pages (on the released wo-adaptor-jetty).


## 2026-09-15 (8.0.5)

- **Short URLs, on by default**
  An application now accepts `/wa/…`, `/wo/…` and `/res/…` as if the adaptor prefix were present, and
  generates its URLs without it - the same URLs in development and deployment. The long form keeps
  working and explicit routes take precedence. Both directions are pure functions of the application's
  URL prefix and its registered handler keys (ERXShortURLs), hooked in at `createRequest()` inbound and
  `_urlWithRequestHandlerKey()` / `_newLocationForRequest()` outbound; the prefix removed outbound is the
  one the request was built from, so a front end rewriting into `/Apps/WebObjects/App.woa/…` is answered
  in kind. Opt out: `er.extensions.ERXApplication.shortURLs=false`.

- **Routes match beneath any adaptor prefix**
  `/`, `/cgi-bin/WebObjects/App.woa/` and `/Apps/WebObjects/App.woa/` all route as `/`, so an
  application no longer registers its routes once per prefix in circulation.

- **URL rewriting reduced to the one escape hatch**
  ERXURLRewriter is now only `er.extensions.ERXApplication.replaceApplicationPath.pattern` /
  `.replace` (the unprefixed `er.extensions.replaceApplicationPath.*` spelling is gone). The
  development-only `rewriteDirectConnect` is removed - it never produced a parseable URL - and the
  direct connect URL is shortened but never passed through the front-end rewriter.

- **ERXWOComponentInstance: embed a constructed component instance**
  `<wo:ERXWOComponentInstance instance="$inspector" selectedObject="$object" />` embeds an instance the
  page constructed and configured itself - bindings, wrapped content, page caching and awake/sleep all as
  for a normally embedded component - so a page can hold a typed handle to its subcomponent and call
  methods on it instead of passing everything through bindings. The `instance` binding is re-evaluated
  every phase; a different instance replaces the embedded one and the old one leaves the page. Null,
  stateless (pooled) and already-embedded-elsewhere instances are refused with an exception. Construct
  with `ERXComponentUtilities.instantiate(MyComponent.class, context())`, which - unlike
  `pageWithName()` - neither awakens the instance nor flags it as a page. Replaces the experimental
  ERXSwitchComponentInstance (nothing switched; the binding was `componentInstance`).

- **handleActionRequestError() rewritten to its minimal honest form**
  The override that routed direct-action errors into `handleException()` shed its WO 5.2-era baggage
  (the InstantiationError/InvocationError session check-in that 5.4.3's own finally blocks made
  redundant) but stays: answering WO's empty hook ourselves is what keeps action-request error handling
  independent of `WODisplayExceptionPages` - in stock WO that property does not hide exception pages, it
  skips `handleException()` for action errors entirely. An explicit `WODisplayExceptionPages=false` now
  draws a startup warning saying so. To hide stack traces from end users, override `handleException()`.

- **AjaxSlim: Idiomorph 0.7.4 -> 0.8.0**
  Drop-in update of the vendored morph engine: fixes the focus-restoration TypeError on elements
  without text selection, namespace preservation for recreated SVG elements, `beforeAttributeUpdated`
  firing for unchanged attributes, and CSS-special characters in ids; adds a console warning for
  duplicate ids. The full playground bridge suite passes before and after.

- **Housekeeping**
  slf4j 2.0.19; `docs/LOGGING.md` documents the logging setup and its known traps (notably that
  `log.*` from the application constructor is silently dropped - log from `didFinishLaunching()`).


## 2026-09-04 (8.0.4)

- **Page cache reuse measurement**
  Every cache hit records the restored instance's idle time and instance-LRU depth into app-wide
  histograms, shown on the session cache overview with a reverse-cumulative "broken by bound" column
  that reads directly as "a TTL/cap at this bound would have broken N restores". Restores broken by
  SESSION EXPIRY - invisible to in-cache counters, since the session dies before any cache is
  consulted - are counted at the `restoreSessionWithID` choke point (deliberately not in
  `handleSessionRestorationErrorInContext`, which applications override for their own expiry UX).
  A notable-reaches list names the pages users actually reach back for.

- **ERXPageCachePressureValve: caches trim themselves under real memory pressure**
  Instead of an always-on TTL (paying with broken deep reaches even when memory is plentiful),
  eviction now happens only when memory is actually short: when a GC completes with old gen still
  above `threshold` (default 85%), every session is trimmed to `trimToFraction` (default 50%) of the
  page cache cap, LRU instances first; above `aggressiveThreshold` (default 90%), to
  `aggressiveTrimToFraction` (default 25%). A session is never trimmed below its most recently used
  instance. Push-based (the old-gen pool's collection-usage notification - no polling, no forced
  GCs), throttled, logged loudly, surfaced on the overview page and in the startup banner. The valve
  also subscribes to ERXLowMemoryHandler's LowMemory/StarvedMemory notifications. Opt out:
  `er.extensions.ERXPageCachePressureValve.enabled=false`. Underneath, cache accesses now
  synchronize on the cache map, properly fixing the overview page's cross-session iteration race.

- **AjaxSlim: AjaxExpansion**
  The disclosure-section element, rebuilt on native `<details>/<summary>`: four bindings (label,
  expanded, id, class), instant client-side toggling, and open state that survives morphs of
  enclosing containers - every toggle pings the server, so re-renders always carry the correct
  open attribute. The legacy effect bindings and lazy content loading are gone.

- **AjaxSlim: modal light dismiss + pre-adoption hardening**
  AjaxModalContainer closes on a backdrop click (clicks inside the dialog's box, and drags that
  merely end outside, never dismiss). The pre-adoption review's fix list landed: an open modal
  survives morphs of enclosing regions (data-morph-ignore while open); a full-document ajax
  response - the expired-session shape - is diverted to the error contract instead of being
  swallowed or morphed into a container; AjaxSelfUpdatingContainer.action no longer fires when the
  container is merely another trigger's render target; field observers no longer fire into a real
  form submission; quoting and content-policy fixes throughout. Focus is preserved when tabbing out
  of a field that triggers a morph, including multi-container updates.

- **Development mode: /eval and /problems endpoints**
  A JShell REPL inside the running app (`…/App.woa/eval?snippet=…`, loopback-only) and the rendered
  binding-error boxes as JSON (`…/App.woa/problems`), aligned with ng-objects' dev endpoints. The
  dev-loop machinery is consolidated under `er.extensions.dev`; apps report runtime and pid when
  registering with the dev server; the launch banner prints external direct-connect URLs.

- **Elements & housekeeping**
  Full `.apiext` adoption across AjaxSlim (typed constraints, defaults, deprecations,
  unknown-attribute and content policies); the empty-update 500 explains itself; experimental
  SVG-aware ERXWOImage (opt-in); ERXWOTextField cleanups; RouteTable names its unhandled-response
  userInfo key; json 20260719, junit 6.1.3, vermilingua 1.1.7.


## 2026-07-03

- **New default: ERXComponentActionRequestHandler - component-action dispatch rewritten as owned code**
  A from-requirements rewrite of the /wo/ handler, replacing the patched-stock ERXComponentRequestHandler
  as the DEFAULT. Same behavior, verified by the full playground suite, the cache/replay/expired-session
  harnesses, and a differential URL-matrix probe (dispatch-differential.mjs: happy paths plus every
  malformed-URL/expired-session edge, run against both handlers and diffed - one intended divergence),
  with deliberate exceptions: a malformed action URL (no contextID)
  gets a clean page-restoration error instead of the old handler's exception page; awake/sleep and session
  check-in are correctly paired on all paths including exceptions; errors log through slf4j; the
  page-recreation branch (WO's cacheless component-action mode, pageCacheSize=0) is gone - that mode has
  been dead in this lineage since named-page parsing was removed, in both handlers alike. The complete
  divergence list is in the class javadoc. Escape hatch back to the legacy handler:
  `er.extensions.ERXComponentActionRequestHandler.enabled=false`.

- **Unified the page cache: ERXAjaxSession's cache is now THE page cache, WO's private caches are never fed**
  One session-side `contextID -> live page instance` map now serves every restore — back button, component
  actions and ajax updates alike. `savePage` never calls super, so the storage routing decision (which cache
  does this page go in) is gone: an ajax update is just another alias for the same instance and can never
  evict the foreground page. Bounded by distinct page instances via WO's own knob, `WOPageCacheSize` /
  `WOApplication.pageCacheSize()` (note: that now counts retained live page trees, not backtrack steps —
  prefer small values). The repeated-request guard WO's cache used to provide moved with it: entries
  record request provenance and `ERXComponentRequestHandler` consults
  `ERXAjaxSession.contextIDForRepeatedRequest` instead of `_contextIDMatchingIDs` — gated on
  `isPageRefreshOnBacktrackEnabled()` exactly like stock WO (flag off = an identical re-submit
  re-executes the action, as always; flag on = the repeat re-renders the stored result, because that
  mode makes the browser re-issue requests during history navigation). The frames-era permanent page
  cache is obsolete and fails loudly: its machinery is deleted (`er.extensions.overridePrivateCache`
  and the storage itself, which had a leak in its dormant path) and `savePageInPermanentCache` now
  throws `UnsupportedOperationException` — instance-LRU eviction removed the context churn that
  permanent pages needed protection from, and silently downgrading a pinning request to LRU semantics
  would be a behavior change with no error. Also deleted the dead `original_context_id` header write.
  `er.extensions.maxPageReplacementCacheSize` is no longer used (startup WARN if set);
  per-store cache logging renamed to `er.extensions.appserver.ajax.ERXAjaxSession.logPageCache`.
  See `docs/UNIFIED_PAGE_CACHE.md`.

---

*Everything below this line is a retroactive reconstruction. wonder-slim had no releases before 8.0.0 —
the 0.x versions were assigned to the natural waves in the commit history, long after the fact
(in September 2026). The dates and changes are real; only the version numbers are invented.
A few sections absorb changelog notes that were actually written at the time.*

---

## 2026-03-13 (0.25.0)

- **Routing handles URLs at the application root**
  Routed URLs no longer need a request-handler prefix: RouteRequestHandler dispatches them straight
  from the app root, with query strings stripped before matching and lenient suffix handling for
  generic URLs.
- **Routing API tightened**
  RouteAction now extends WODirectAction, ComponentClassRouteHandler became a record,
  BiFunctionRouteHandler was deleted, and the deprecated `RouteTable.urlForDevelopment()` was
  removed before it could become public API.
- **Resource URLs without a context now throw**
  Generating a resource URL with no WOContext used to fail quietly and wrongly; now it's an
  IllegalStateException.
- **`type` can be overridden on ERXWOTextField / `wo:textfield`**
- **Documented reality: targets JDK 25, runs fine up to and including JDK 26**

## 2025-12-28 (0.24.0)

- **Deleted the SSL direct-connect adaptor stack**
  ERXDefaultAdaptor and ERXSecureDefaultAdaptor are gone. TLS is the web server's job.
- **Experimental pure-Java HTTP adaptor**
  WOAdaptorPlain, built on the JDK's own HttpServer: streams request content properly, guards
  against chunked transfer encoding, populates remote/local addresses on the WORequest and reports
  the actually-bound port back into WOPort. Additive and self-contained — the classic adaptor
  remains the default.
- **Startup measured and slimmed**
  The app now reports time from JVM launch to first request accepted. `loadOptionalProperties` is
  off by default. WOTimer usage replaced with `CompletableFuture.delayedExecutor()`.
- **Added ERXErrorPage; direct component access disabled for good**
  Everything goes through the component request handler now.
- **Parsley 1.3.0** (off snapshot)

## 2025-10-31 (0.23.0)

- **Deleted ERXResourceManager and ERXStaticResourceRequestHandler**
  The single largest deletion of the era (−714 lines). The application-served resource pipeline
  introduced in 0.20.0 is now the only path.
- **JDK 25**
  Wanted: writing code before invoking the super constructor. All modules migrated.
- **ERXApplication sheds its config**
  Proxy-balancer support extracted to ERXProxyBalancerConfig, URL rewriting to ERXURLRewriteConfig;
  `installPatches()` and the binding-debugging extensions deleted; ERXPrivateer renamed to
  ERXPrivateKVC and moved to a `.hacks` package where it belongs.
- **Started the ERXP enum**
  One place for every property key the framework reads, instead of strings scattered everywhere.

## 2025-10-24 (0.22.0)

- **The great core cleanout**
  Nine days, roughly ninety commits, almost all of them removing methods from ERXApplication,
  ERXWOContext and ERXRequest: `userInfo()`/`mutableUserInfo()`, the browser form-value-encoding
  override machinery, `instantiatePage()`, `rawName()`, ERXRetainer, ERXDate and a long tail of
  accreted conveniences. This is where the "slim" gets earned.
- **Lambdas as notification listeners**
  Observer registration takes a lambda, retains it, and does so thread-safely.
- **Initialization made comprehensible**
  `ERXApp.setup()` merged into `main()`; ERXFrameworkPrincipal initialization reworked.

## 2025-10-15 (0.21.0)

- **URL routing**
  New `er.extensions.routes` package: RouteTable, RouteAction, RouteURL, RouteHandler — and the
  project's first unit tests.
- **Typed notifications**
  ERXNotification replaces string-keyed NSNotificationCenter usage with an enum and nicer syntax
  for registering observers and posting.
- **JDK 21 formally required** via maven-enforcer; **Parsley moves to the released 1.2.0**.
- **Deleted AjaxRemoteLogging and ERXAppRunner**

## 2025-10-07 (0.20.0)

- **The application serves its own webserver resources**
  Instead of relying on an external web server for /WebServerResources: an in-memory resource
  cache, client-side cache headers in production, corrected mimetypes (`.js` is `text/javascript`,
  real font types) and user-defined types via `AdditionalMimeTypes.plist`. On by default.
- **Deleted the resource version manager and `isDeployedAsServlet()`**
- **vermilingua 1.0.5**

## 2025-09-25 (0.19.0)

- **ERXExceptionManager**
  The application keeps a log of thrown exceptions, browsable on the new
  ERXExceptionManagementPage: stack traces, most-recent-first, filterable.
- **ERXComponentRequestHandler no longer creates sessions**
  A component action URL with no session gets an error, not a fresh session.
- **Java arrays work in `list` bindings**
  For WOPopUpButton and every other WOInputList-based element — and unknown bound types now throw
  instead of silently rendering an empty popup.
- **Build modernization**
  JDK version via `<maven.compiler.release>` (21), Maven ≥ 3.9 enforced, UTF-8 encoding pinned
  across all modules, vermilingua on the released 1.0.4.
- **Deleted WOMethodInvocation**; README rewritten to describe the current shape of the project.

## 2025-07-26 (0.18.0)

- **Parsley replaces the in-tree template parser**
  The `er.extensions.bettertemplates` package — the WOOgnl parser absorbed back in 0.8.0, seventeen
  WOHelperFunction classes, 2,400 lines — deleted, supplanted by the external Parsley template
  engine. Template parsing is no longer this framework's problem.

## 2025-06-27 (0.17.0)

- **ERXPatcher's XHTML machinery torn down**
  `processResponse()`, `cleanupXHTML()` and the response-rewriting layer deleted; the surviving
  element patches (ERXWOForm, ERXWORepetition, ERXWOString, ERXWOTextField) promoted to real
  classes and always enabled, localization or not.
- **Deleted ERXWOBrowser** (370 lines).
- **ERXWORepetition thinks in Lists**
  Internal `Context` renamed to `ListWrapper`, NSArray special-casing dropped.
- **A KVC reflection hack for modern JDKs**
  ERXKVCReflectionHack — self-describedly "absolutely horrifying" — keeps key-value coding working
  against Java's ever-more-private internals. Enabled globally.

## 2025-06-08 (0.16.0)

- **Run development mode straight from a Maven project**
  A `build.properties` file marks a project as under development; `src/main/woresources/Properties`
  is found where Maven puts it; NSProjectBundleEnabled set automatically.
- **Deleted ERXLoader**
  845 lines of classpath munging, refactored for a week, then proved unnecessary and deleted
  outright ("YOLO"). Replaced by a 49-line classpath validation at startup that fails loudly when
  the order is actually wrong.
- **Added the ERXDate element**; ERXApplication's constructor restructured into something readable.

## 2025-04-28 (0.15.0)

- **JDK 24 readiness**
  `sun.security.action.GetPropertyAction` was removed from the JDK, so WebObjects' internals need a
  stand-in: vendored.
- **Added ERXSwitchComponentInstance**
  An experimental switch component taking component instances rather than names.
- **Small things**: the development-mode flag is cached, WOHostUtilities obtains localhost IPs
  automatically, slf4j 2.0.17, reload4j 1.2.26.

## 2024-12-20 (0.14.0)

- **ERXMonitorServer**
  A small HTTP monitoring service wired into ERXApplication, gated on a
  `WOMonitorServicePassword` property.
- **The old template parser becomes opt-out**
  Better-templates initialization moved behind a `useBetterTemplates()` override instead of an
  unconditional call — the first step toward its removal in 0.18.0.
- **Assorted**: statistics package modernized, exception pages show class names in stack traces,
  `SameSite=Lax` on the proxy-balancer route cookie, development apps are terminated by port and
  given an honest amount of time to die.

## 2024-05-02 (0.13.0)

- **Exception IDs**
  Every exception gets an ID, surfaced on WOExceptionPage — grep the log for the ID a user reports.
- **Reproducible build**: vermilingua-maven-plugin moves off SNAPSHOT.
- README corrected to match reality; slf4j bumps.

## 2023-09-29 (0.12.0)

- **JDK 21**
  Source/target 17 → 21 across all modules; first JDK-21-era API usage (`Locale.of()`).

## 2023-07-22 (0.11.0)

- **Deleted the legacy jar checker**
  ERXJarChecker — classpath scanning from another age — extracted, examined, and removed.
  ERXLoader trimmed and de-instanced in the process.
- **First seed of a Prototype-free Ajax**
  A plain-JavaScript field observation experiment lands in AjaxSlim's webserver resources.

## 2023-04-21 (0.10.1)

- **Stack traces name their JARs**
  WOExceptionPage identifies which JAR a class came from when it isn't inside a bundle, and
  presents Maven bundles more readably. slf4j and reload4j bumps.

## 2022-12-20 (0.10.0)

- **AjaxSlim is born**
  The Ajax framework forked wholesale into a new AjaxSlim module (both remain in the build), and
  then the fork put on a diet: some 35 components deleted in a single day — AjaxAutoComplete,
  AjaxTree, AjaxTabbedPanel, AjaxSlider, AjaxDatePicker, the file uploads, drag and drop, progress
  bars, rico.js — 228 files and 21,600 lines gone.
- **The first Prototype-free update container**
  A proof-of-concept AjaxUpdateLink/AjaxUpdateContainer on plain asynchronous XMLHttpRequest.
  Parked for now ("Don't use the new scripts"), but the direction is set.
- **AjaxComponent deleted**; AjaxUtils gutted of everything ERXResponseRewriter already does.
- **Nicer URLs for webserver resources inside JARs** in development mode.

## 2022-10-15 (0.9.0)

- **Deleted wondaculous.js**
  13,381 lines of concatenated JavaScript, gone in one commit — the first real cut into Ajax's
  script payload.
- **Publishing plumbing**
  The WOCommunity Maven repository configured and hoisted to the parent pom.
- **Logging setup moved out of ERXApplication** into ERXLoggingSupport.
- **Deleted the testapp** — the repo is frameworks only now.
- **NSArray/NSDictionary begin their walk to List/Map**, starting with WOExceptionPage.
- **Error pages link into the IDE**: a click on a stack trace line opens the Java editor via the
  WOLips server.

## 2022-03-22 (0.8.0)

- **OGNL removed**
  WOOgnl forked into WONoOgnl with the OGNL machinery stripped out, the survivor absorbed into
  ERExtensions as `er.extensions.bettertemplates`, and both WOOgnl and WONoOgnl deleted as
  modules. The template syntax stays; the expression language goes.
- **Inline bindings enabled by default.**
- **ERLoggingReload4j module created**
  The log4j-specific code moves out of ERExtensions into its own small framework, behind a logging
  facade.
- **EOF dependency dropped**
  JavaEOAccess removed from the pom — this time it sticks.
- **Deleted ERXMessageEncoding**; serialVersionUIDs obliterated; Localizable.strings deleted;
  admin direct actions always allowed in development mode; Java 17 Eclipse settings; Xerces pulled
  in as an explicit dependency.
- **Added an updateContainerID binding to AjaxUpdateTrigger**

## 2022-01-22 (0.7.0)

- **log4j → slf4j, completed; reload4j as the backend**
  The migration begun in December finishes across all frameworks, with reload4j replacing the
  ancient log4j 1.2.17.
- **The validation subsystem eliminated**
  ERXValidationFactory, ERXValidationException, the delegates, the template strings — some 680
  lines of machinery for a thing applications do better themselves.
- **ERXProperties put in its place**
  No longer inherits from `Properties`, can't be instantiated, KVC implementation removed, cache
  made concurrent — roughly 25 consecutive deletion commits. ERXSystem deleted alongside.
- **ERXDirectAction reshaped**
  The old kitchen-sink class renamed to ERXAdminDirectAction; a new, thin ERXDirectAction takes
  its name.
- **ERXLocalizer pruned** (plurification helpers, ERXNonPluralFormLocalizer); the ancient
  Safari-on-Leopard workaround removed; `Loader` renamed to `ERXLoader`.

## 2021-11-20 (0.6.0)

- **Java 17** (from 11).
- **Deleted the forked `com.webobjects.foundation` package**
  NSSet and friends, 4,285 lines — the framework now lives with the Foundation it's given.
- **Deleted ERXResponse**
  Plain WOResponse everywhere.
- **ThreadLocals are no longer cloned** — ERXCloneableThreadLocal deleted.
- **Direct component access disallowed; plain WOApplication no longer supported**
  An ERXApplication subclass and the component request handler are the supported path.
- **ERXApplication decomposition begins**: the Loader promoted to its own file, AppClassLoader
  deleted, session-store deadlock detection removed, ERXDictionaryUtilities and ERXValidation
  deleted. The Japanese-javadoc purge, running since May, completes.

## 2021-07-14 (0.5.0)

- **Build plugin: wolifecycle → vermilingua**
  Small diff, big decision — the build now runs on the maintained plugin.
- **The utility classes dissolve**
  A systematic method applied to ERXFileUtilities and ERXStringUtilities: move each method to its
  single use site, then delete the empty husk. ERXFileUtilities dies here; ERXStringUtilities is
  deprecated and takes another eighteen months to stay dead.
- **`.wo` templates read as UTF-8** instead of platform encoding — a real bug fix hiding in the
  cleanup.
- **Component pruning continues post-absorption**: WOTabPanel, WOCheckboxMatrix,
  WORadioButtonMatrix, WOTable, the old WOExceptionPage; ERXExceptionPage takes the WOExceptionPage
  name; packages reorganized into `stats` and `error`.
- **`handleException()` returns a proper 500**; startup log noise reduced.

## 2021-05-31 (0.4.0)

- **Third-party cords cut**
  The Apache commons-lang and EOControl dependencies removed (EOAccess may still pull EOControl in
  transitively — pragmatism won).
- **JavaWOExtensions absorbed**
  Moved into ERExtensions wholesale and deleted as a module; the reactor drops to three frameworks.
- **Legacy web resources purged**: every gif, clippy.swf, dhtml.js, date-picker.js and their
  friends.
- **This changelog introduced.** The entries below this one, from May 2021, are the original notes
  written at the time.
- **Deleted ERXArrayUtilities, ERXConstant, ERXSubmitButton**
- **Moved response compression from dispatchRequest() to new class ERXResponseCompression**
  Makes the code easier on the eyes. Still considering full removal of response compression since that tends to be handled by the web server in most environments I know of.
- **Moved ERXCompressionUtilities class into ERXResponseCompression and made it private.**
  Slim is not a generic compression framework, so it's reasonable that the only user of the code keeps it.
- **Renamed ERXHyperlink to ERXWOHyperlink**
  Naming conventions are good.
- **Renamed ERXSwitchComponent to ERXWOSwitchComponent**
  Naming conventions are good.
- **Removed ERXSession.javascriptEnabled**
  If you need this sort of functionality, do it yourself
- **Deleted ERXDirectAction.browser()**
  ERXRequest already holds a browser object and a direct action holds a request.
- **Deleted ERXGracefulShutdownHook**
  It's been disabled by default for a while. It used `sun.misc.Signal` and `Signalhandler` whose usage is not recommended. Use ERXShutdownHook instead.
- **Deleted `ERXApplication._startRequest()` and `ERXApplication._endRequest()`**
  If you need to do stuff before and after requests, override `dispatchRequest()`.
- **Moved the ERXExtensions.initApp(...) methods to new class ERXAppRunner**
  ERXExtensions should serve only as the ERExtensions framework's principal/initialization class
- **Removed threadInterrupt stuff from ERXRuntimeUtilities**
  Logic not actually used by any code inside the frameworks.
- **Deleted ERXActiveImage**
  Doesn't seem to serve any purpose
- **Deleted ERXWOPasswordField**
  The improvements offered by it are negligible in the age of ubiquitous https
- **Deleted userInfo() stuff from ERXResponse**
  It seems to have been there mostly to keep comptibility with older WO versions.
- **Removed pushContent(), popContent(), __setContent() etc. from ERXWOContext**
  Looks like the vestiges of a 13 year old experiment by mschrag.
- **Deleted ERXDelayedRequestHandler**
  Cool idea, but reading the mailing list it seems to have it's problems. I'd prefer a mechanism that allows the programmer to consciously decide to use long responses when desired, not something that alters the global behaviour of the application.
- **Moved ERXBrowser and it's companions to a separate package; er.extensions.browser**
  The er.extensions.appserver package is pretty full as is
- **Deleted ERXTimestampUtilities**
  Modern java uses the classes from java.time.
- **Deleted ERXSelectorUtilities**

## 2021-04-05 (0.3.0)

- **The component museum deleted**
  Roughly two hundred legacy components in two days: the checkbox and radio matrices, tab panels,
  batch navigation, grouping tables, ERXFlashMovie, ERXClippy, ERXLoremIpsum, the RSS and podcast
  pages, ERXRemoteShell, ERXDatabaseConsole and their kin. ERExtensions' `.wo` bundle count falls
  from 239 to 26.
- **Utility classes hollowed out**
  Most of ERXArrayUtilities, ERXDictionaryUtilities, ERXValueUtilities, ERXStringUtilities,
  ERXFileUtilities; ERXMutableDictionary and ERXMutableArray replaced with plain collections; the
  crypting package (BCrypt included) deleted.
- **A real reactor build**
  A proper parent pom (`undur-parent`) replaces per-module boilerplate.
- **The Ajax framework comes home**
  Exiled to its own repo in October 2020, re-imported (327 files, 62k lines) and added to the
  build.
- **Pragmatic walk-backs**: JavaEOControl and JavaEOAccess re-added as compile-time dependencies to
  keep things building; `ERXStringUtilities.safeIdentifierName()` re-added after its deletion was
  regretted in use.

## 2021-03-29 (0.2.0)

- **EOF removed from ERExtensions**
  The defining change of the fork. `er.extensions.eof`, the qualifiers, the EOControl package,
  display groups, partials, migrations, the JDBC utilities and the EOAccess patches — deleted in a
  day and a half. ERExtensions goes from 525 Java classes to 223, from a database framework to a
  web framework.
- **Servlet support deleted**: ServletAdaptor, ERXWOServletContext, ERXServletApplication and the
  servlet-api dependency.
- **The ERXJS component family deleted.**
- **Java 11** (from 1.8); **groupId becomes `undur`**.
- **Dependencies shed**: joda-time, commons-codec (→ `java.util.Base64`), icu, commons-httpclient,
  junit, commons-lang's CharEncoding (→ `StandardCharsets`).
- **A test application added** — the first in-repo way to actually run the thing.

## 2020-10-15 (0.1.0)

- **The fork**
  Project Wonder cut down to the frameworks actually in use: the Applications, Examples, Archives,
  Utilities and Tests directories and some 380 frameworks deleted — 13,500 files, 1.2 million
  lines — leaving ERExtensions, JavaWOExtensions and WOOgnl at the repo root.
- **The Ajax framework exiled** to its own repository (it returns in 0.3.0).
- **ant is dead; the build is Maven now**
  The ant build deleted, all three frameworks restructured to Maven layout ("Ye'r a maven project
  now, laddie").
- **The version is set to 8.0.0-SNAPSHOT** — on day one, choosing the number that would finally
  ship five and a half years later.
