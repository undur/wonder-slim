# Configuration in wonder-slim

Status: **how properties are loaded is being reworked** (#143). This document records
how it currently works, so each step of the rework starts from facts. It is descriptive, not
aspirational, in the manner of `LOGGING.md`.

## Where properties live

Every property ends up in the JVM's system properties. `ERXProperties` (and `NSProperties`) read
them from there by key, as strings; `ERXP` lists the keys ERExtensions reads. `ERXProperties`
keeps a cache of parsed values, cleared (along with `NSProperties`' own) whenever the configuration
writes the system properties.

`ERXConfigurationManager` composes the configuration from its sources and keeps the result: the
sources in order, and for each property the source its value came from.

## Startup, in sequence

1. **The JVM starts** with its own `-Dkey=value` options in the system properties. Before it calls
   `main()`, it initializes the application class and its superclasses, so their static
   initializers run first: `WOApplication`'s registers classes and packages, and `ERXApplication`'s
   creates its loggers, which initializes slf4j and log4j. Nothing here touches `NSBundle` or reads
   a properties file, so a static initializer in an application class that reads a property gets
   only what the JVM was started with. One of WO's own does: `WOApplication`'s static block reaches
   `EOEventCenter`, which reads `EOEventLoggingEnabled`, `EOEventLoggingOverflowDisplay`,
   `EOEventLoggingLimit` and `EOEventLoggingPassword` once, here. Those four take effect only as JVM
   `-D` options.

2. **`ERXApplication.main()`** starts logging (console output), finds and orders the plugins
   (`ERXPlugins.load()`: `ServiceLoader`, ordered by what each requires), then composes the
   configuration (`ERXConfigurationManager.compose()`):
   - It keeps a copy of the system properties as they are: the values from launch.
   - It puts the application's arguments into the system properties, so finding the files sees them.
   - It finds the files (below) through `NSBundle`, which initializes on first use. While
     initializing, `NSBundle` applies the bundles' `Properties` and `~/WebObjects.properties` to the
     system properties itself (existing values take precedence), then initializes each bundle's
     principal class (`NSPrincipalClass` in its Info.plist), in classpath order: it loads the class,
     so its static initializer runs, and creates no instance. wonder-slim's own frameworks have no
     principal class; they take part in startup as plugins.
   - It reads the sources, composes them and writes the result to the system properties. Since the
     properties that decide which files are read may be set by a file, it then finds the files again,
     and composes again if they changed (three rounds at most).

3. **Each plugin's `beforeApplicationConstruction()`** runs, in plugin order. Then **the application
   is constructed** (`WOApplication.main()`). WO applies the application's arguments again, with the
   same values. The constructors see the composed configuration.

4. **At the end of `ERXApplication`'s constructor**, reloading is set up and the startup report is
   printed: the sources in order, and every property in effect with the source that set it. (Logging
   was configured from the configuration as soon as it was composed, in step 2; see `LOGGING.md`.)

5. **Each plugin's `finishInitialization()`**, then the application's, runs once the application is
   constructed, before its adaptors start listening; **each plugin's `didFinishLaunching()`**, then
   the application's, once they are (see `ERXPlugin`).

## The sources, lowest precedence first

| # | Source | Condition |
|---|---|---|
| | the system properties at launch | the JVM's defaults, beneath everything |
| 1 | each framework's `Properties` | frameworks without a plugin in reverse `NSBundle.frameworkBundles()` order, then those with one in plugin order; from a directory, or read from the jar |
| 2 | each framework's `Properties.dev` | in development mode, frameworks in a directory |
| 3 | each framework's `Properties.<user.name>` | frameworks in a directory |
| 4 | the application's `Properties` | |
| 5 | `~/WebObjects.properties` | |
| 6 | each `er.extensions.ERXProperties.OptionalConfigurationFiles` entry | an absolute path, or a resource in the application |
| 7 | the application's `Properties.log4j`, `.database`, `.multilanguage`, `.migration`, `.<framework>`, each with a `.<user.name>` variant | `er.extensions.ERXProperties.loadOptionalProperties=true` |
| 8 | `/etc/WebObjects/Properties`, or failing that `/etc/WebObjects/<App>/Properties` | the directory is `er.extensions.ERXProperties.machinePropertiesPath` |
| 9 | the application's `Properties.dev` | in development mode; the name is `er.extensions.ERXProperties.devPropertiesName` |
| 10 | the application's `Properties.<user.name>` | |
| 11 | the JVM's `-D` options | read from the JVM's input arguments |
| 12 | the application's arguments | |
| 13 | properties set on the running instance | set in the admin console (`ERXConfigurationManager.setProperty()`), until unset or the instance stops |

Files that were looked for and aren't there are recorded too, where they would apply: the
application's `Properties`, `Properties.dev` and `Properties.<user>`, `~/WebObjects.properties`, the
machine's `Properties`, and each optional configuration file. The startup report and the admin
console list them as "no file present". The frameworks' variants are only listed when found.

## Reloading

In development, the files the configuration was read from are watched; in deployment, a touch file
(`er.extensions.ERXConfigurationManager.PropertiesTouchFile`) can be (see
`ERXConfigurationManager.watchForChanges()`). When one changes, `ERXConfigurationManager.reload()`
finds and reads the sources again and composes them. A property a source no longer sets gets its
value from launch back, or is removed if it had none. Logging is then configured again.

## Watching values

`ERXConfigurationManager.onChange()` registers a listener to changes in a property's value, or in
the values of a family of properties (`key -> key.startsWith( "log4j." )`):

```java
ERXConfigurationManager.onChange( "er.example.timeout", change -> {
	log.info( "{} changed from {} to {}", change.propertyName(), change.oldValue(), change.newValue() );
} );
```

A change is a difference in the value in effect after the configuration is reloaded, or a property
is set or unset on the running instance; a value is null where the property had none. Listeners run
on the thread that made the change, once it's fully applied, and a listener that throws doesn't stop
the others. `onChange()` returns the listener, to `remove()` it. A property set by code with
`System.setProperty()` isn't seen.

## Known limits

- **Precedence between frameworks without a plugin follows the classpath.** Those with one follow
  plugin order: a framework overrides the frameworks it requires.
- **WebObjects' principal classes run before the properties are composed.** They're initialized
  with `NSBundle`, which composing uses to find the files. Reading the files as classpath resources
  instead would let them run after (#151).
- **`NSBundle` still applies its own pass** of the bundles' `Properties` while initializing. The
  composed configuration then overrides every value it set.
- **The properties are strings**, read by key, with the type, default and meaning known only where
  they're read (#143).
- A system property set by code (`System.setProperty()`) isn't recorded by the configuration; a
  reload keeps it unless a source sets the same key. Properties set in the admin console or by
  `ERXAdminDirectAction`'s `systemPropertyAction` are, as a source of their own.
