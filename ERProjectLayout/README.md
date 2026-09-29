# ERProjectLayout

Lets a WebObjects project declare where its components and resources live, in its `build.properties`:

```properties
dir.components=Components
dir.woresources=Resources
dir.webserverResources=WebServerResources
```

When an application runs from its project folder (in development, without a built bundle), every project it runs from, the application and any frameworks open in the workspace, finds its resources in those folders. The layout is what `build.properties` says, never guessed from the project's Eclipse natures, so a "Fluffy Bunny" project built with Maven works the same as one using the Maven layout.

A project is a folder whose `build.properties` has `project.name` and `project.type`. Paths are relative to the project folder. A key that isn't declared takes the Maven-layout default:

| Key | Default |
|---|---|
| `dir.components` | `src/main/components` |
| `dir.woresources` | `src/main/woresources` |
| `dir.webserverResources` | `src/main/webserver-resources` |

Only these folders are searched, the same ones vermilingua packages from. A resource kept anywhere else, `src/main/resources` included, isn't found in development: if a project keeps its resources somewhere else, `build.properties` has to say so.

Jars and built bundles are left to ERFoundation, as are folders that aren't projects.

[vermilingua](https://github.com/undur/vermilingua-maven-plugin) (1.1.11+) reads the same keys when packaging, so development and the built application agree.

## Requirements

`ERFoundation` (`wonder.core:ERFoundation`), which provides the project bundle support this builds on. Java 11+. It doesn't depend on ERExtensions, so it works with Project Wonder too.

## Registering

The bundle factory has to be registered before anything touches `NSBundle`, since `NSBundle` reads its factories once, when it's initialized.

**wonder-slim** does this for you: ERExtensions includes ERProjectLayout, and `ERXApplication` registers it.

**Anywhere else**, either call it first thing, from a static block in your application class:

```java
public class Application extends ERXApplication {

	static {
		ERXProjectLayout.register();
	}
	...
```

or name the factory on the command line (in your Eclipse launch configuration):

```
-DNSBundleFactories=er.extensions.projectlayout.ERXProjectLayoutBundleFactory
```

`register()` keeps any factories already named in `NSBundleFactories`, and they're asked before this one.

## Maven

```xml
<dependency>
	<groupId>is.rebbi.slim</groupId>
	<artifactId>ERProjectLayout</artifactId>
	<version>8.0.13</version>
</dependency>
```
