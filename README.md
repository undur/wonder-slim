# wonder-slim

The basics required to create and run a modern WO application. A playground for experimenting with Project Wonder improvements.

## Installation

*To use this project your machine must be set up for WO development using maven. If you haven't done that, see the "Let's set it up" part [here]( https://gist.github.com/hugith/d2ba6da9e4942f4ece95d7a721159cd1). Don't fear, this project has different versioning from the real Project Wonder and will not interfere with other Project Wonder installations*

1. Clone the repository
2. Run `mvn clean install` in the cloned repository's root **or**
3. …alternatively, Import the projects into your Eclipse workspace (using `Import existing projects into workspace`).

## Usage

If you just want to play around, you can import the "testapp" in the repository's root into Eclipse and run it.

To use in an existing project you can just change the version for `ERExtensions`, `Ajax`  and `WOOGNL` in your own project to 8.0.0.slim-SNAPSHOT in your `pom.xml` file. Note that these are the only frameworks included in Slim and if you're using other Wonder frameworks or EOF, compatibility will be hit and miss.

## Motivation

*Before you start reading, I should mention that this is not my own project. It's merely an organization of over two decades of work of dozens, even hundreds of people. To emphasize this, I've chosen to go the way of cloning the existing Wonder repository and keep attribution and history of the source files in question as much as possible. I have much love and respect for past contributors, many of whom are friends and colleagues.*

Project Wonder is an old and storied collection of frameworks that's had many contributors. Over the years, it's accumulated a *lot* of cruft.

This is an experimental project to see how Wonder can be improved, not by adding stuff but by *removing* stuff.

Below I enumerate a few primary goals of wonder-slim (from here on called just "Slim"), they all share the same **ultimate goal**, to make Wonder smaller, more manageable and maintainable, enabling faster development and delivery of features and improvements.

## Primary goals

### Separation of concerns and focus on basics

Project Wonder has a multitude of features and frameworks. This project is about the minimal subset that's required to create and run a modern basic WO application, nothing more, nothing less.

For this reason, the project picks only four frameworks from Project Wonder as baseline for the work, `ERExtensions`, `JavaWOExtensions`, `Ajax` and `WOOGNL`. I've also combined `JavaWOExtensions` and `ERExtensions`, since they serve similar purposes in many ways.

### **Loosen the ties between WOF and EOF**

This is really just an extension of "separate concerns", but since EOF is a huge part of many projects it merits a separate mention.

A web framework and a persistence framework are separate things and this project focuses only on the "web" part. Therefore, everything EOF-related has been removed. Note that this does not preclude EOF usage, because although EOF is not *part* of *this* project it doesn't mean EOF can't *integrate* well with it. But I consider that a separate effort.

For this reason, Slim's frameworks (notably ERExtensions) no longer does anything EOF-related nor does it use any code from JavaEOControl or JavaEOAccess. A WO application using it will still have to import these frameworks (due to JavaWebObjects referencing some classes there, such as WOEvent inheriting from EOEvent, WOSession having an EOEditingContext etc.)

### Reduce the number of external dependencies

`ERExtensions` only pulls in two external dependencies `slf4j-api` and `log4j`.  The other two frameworks are unchanged from wonder; `WOOGNL ` pulls in `ognl` and `Ajax` pulls in `jabsorb`.

### Reduce usage of code and APIs in closed frameworks

This might sound ironic since Slim is based on a closed framework. But we still reduce the usage of closed WO APIs as much as possible. This includes not using NS* collection classes, foundation utility classes etc.

This makes it easier to integrate with the java ecosystem when using and contributing code and also makes our code more reusable and future proof. That applies to both framework code and the resulting application code.

### Standardize development conventions

Over the years a lot of things have changed in Wonder and WO. To keep compatibility, newer releases often allow changes to be hidden, for example by keeping deprecated code around and activating it conditionally by looking at projects to see if they're "old" or by setting a property. This makes the codebase larger, harder to understand and harder to maintain. So to simplify development, "old" code is being removed and with it the option of doing things "the old way", whatever that may be.

### Use Maven only

To keep the build simple, Slim is only built using maven.

### Run on (relatively) modern java

Slim targets Java 11 and does not support older JDKs. This is a part of the simplification of the development and environment through standardization.

## FAQ

*Ok "FAQ" might be something of an overstatement, I haven't received a single question about this. But "potential questions" just sounds stupid.*

### So, this just leaves Project Wonder behind?

No. While this is a usable framework in and off itself, it's also something of an exercise to just learn to navigate the Wonder Sources and WO's architecture, understand what's there, what's actually used, what can be improved etc. Hopefully, some of the refactorings and cleanups can be backported to Wonder itself, so everyone benefits. 

In a perfect world, this effort might work it's way directly into Wonder itself. But that would require a huge effort, due to the huge amount of (highly interconnected) code in current Project Wonder.

### Do you use it?

Yes. I've used Slim in all of my active projects instead of Project Wonder for a while now. It's very nice to have an understandable, easily modifiable and (comparably) small base of framework code with a limited set of dependencies.

### Can I use it?

Sure. But be aware that the real question is probably "should I use it". I'm moving fast and breaking things at the moment and I'm not doing any releases, stable or otherwise. If there's interest in the project I might consider changing that.

### Can I change it?

Yes! Please! I'd love to get both feedback and contributions.
