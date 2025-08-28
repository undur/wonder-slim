# wonder-slim

A slimmed down version of Project Wonder, containing only the required basics to create and run a modern WO application on a modern JDK.

## Installation

*To use this project your machine must be set up for WO development using maven. If you haven't done that, see the "Let's set it up" part [here]( https://gist.github.com/hugithordarson/d2ba6da9e4942f4ece95d7a721159cd1). And no fear, this project has different maven artifact coordinates from the original Project Wonder and thus will not interfere with any other Project Wonder projects or installations*

1. Clone the repository
2. Run `mvn clean install` in the cloned repository's root

If you only plan on using this from within Eclipse, you don't need to perform an installation, just import slim's projects into your Eclipse workspace (using `Import existing projects into workspace`).

## Usage

First, [here's a sample project](https://github.com/undur/wonder-slim-sample) that demonstrates the most basic `pom.xml` that includes ERExtensions, Ajax and logging.
 
To use in an existing project:

 * Change the version for `ERExtensions` (and `Ajax`, if present) to `8.0.0.slim-SNAPSHOT` in your `pom.xml`.
 * You'll probably want `ERLoggingReload4j` framework as a dependency as well if you want logging. It's currently the only implementation of our "logging bridge".
 * Remove `JavaWOExtensions` and `WOOgnl` if present (`JavaWOExtensions` is now a part of `ERExtensions` and `WOOgnl` has been replaced by Parsley)

_Do not use the `AjaxSlim` framework. It's work in progress that really should have gotten it's own repository_

Since this project only includes a fraction of the original Wonder frameworks and code, compatibility with existing projects **will** be hit and miss. And if you're using *any* frameworks from Project Wonder other than those we've adopted, don't expect them to work.

## Motivation

Project Wonder is a huge collection of frameworks that's had many contributors and has accumulated a lot of cruft over the years. This project aims to improve and simplify Wonder not by adding stuff but by *removing* stuff, cleaning house and understanding and documenting what's left.

Below I enumerate a few primary goals of wonder-slim (from here on called just "slim"), they all share the same **ultimate goal**, to have a small, understandable, standardized, manageable and maintainable way to run WO applications in a modern environment and enabling faster development and delivery of features and improvements.

## Primary goals

### Separation of concerns and focus on basics

The original Project Wonder consists of a multitude of features and frameworks. This project is about the minimal subset that's required to create and run a modern basic WO application, nothing more, nothing less.

For this reason, the project picks four frameworks from Project Wonder as baseline, `ERExtensions`, `JavaWOExtensions`, `Ajax` and `WOOgnl`.

* `JavaWOExtensions` has been combined with `ERExtensions` since there's no point in keeping them separate.
* `WOOgnl` has been refactored and moved to the new framework `Parsley`. `ERExtensions` depends on `Parsley`, enables it by default and uses it's syntax (WOOgnl's syntax, mostly) for it's templates. It's also lost it's dependency on OGNL, meaning it no longer supports OGNL expressions, just regular keyPaths and other standard associations supported by WO itself.

### Standardize on `WOOgnl`/`Parsley` template syntax

Most modern WO apps that use inline bindings probably use `WOOgnl`'s syntax. As mentioned above, we've refactored the `WOOgnl` template parser into the new parser `Parsley` and enable it by default. This shouldn't change anything for applications that only use WO's old style folder/multi-file templates since they're supported as well. It just enables inline bindings by default, allowing us to use them in the frameworks' templates.

### Run on modern java

Slim's sources target JDK 21 and it runs fine up to and including JDK 24. We do not support older JDKs.

### Reduce the number of external dependencies

* `ERExtensions`  pulls in two external dependencies `slf4j-api` and `xercesImpl` (to replace `JavaXML.framework`.
* `ERLoggingReload4J` pulls in `reload4j` (as a replacement for log4j 1.x).
* `Ajax` pulls in `jabsorb`.

Note that `JavaXML.framework` is excluded everywhere by the project's poms so it won't pull it in to your project.

### Reduce usage of code and APIs in closed frameworks

This might sound ironic since Slim is based on a closed framework. But we still reduce the usage of closed WO APIs as much as possible. This includes not using NS* collection classes, foundation utility classes etc.

This makes it easier to integrate with the java ecosystem when using and contributing code and also makes our code more reusable and future proof. That applies to both framework code and the resulting application code.

### Standardize development conventions

Over the years a lot of things have changed in Wonder and WO. To keep compatibility between releases, newer releases often allow changes to be hidden, for example by keeping deprecated code around and activating it conditionally by looking at projects to see if they're "old" or by setting a property. This makes the codebase larger, harder to understand and harder to maintain. So to simplify development, "old" code is being removed and with it the option of doing things "the old way", whatever that may be.

### Maven only

To keep the project simple, Slim is built using maven and officially only supports maven-style projects.

### **Loosen the ties between WOF and EOF**

This is really just an extension of "separate concerns", but since EOF forms a large part of many projects it merits a separate mention.

A web framework and a persistence framework are separate things and this project focuses on the "web" part. Therefore, everything EOF-related has been removed. Note that this does not preclude EOF usage, because although EOF is not *part* of *this* project it doesn't mean EOF can't *integrate* well with it. But I consider that a separate effort. EOF is by far the largest and most complex part of the entire WO stack, I don't use it myself so testing with it is hard, and this makes maintenance and development much easier.

So. Slim's frameworks (notably `ERExtensions`) no longer do anything EOF-related nor does it use any code from `JavaEOControl` or `JavaEOAccess`.

## FAQ

*Ok "FAQ" might be something of an overstatement, I haven't received a single question about this. But "potential questions" just sounds stupid.*

### This just leaves the original Project Wonder behind?

For development purposes, yes it does. I no longer have any projects that use the original Project Wonder, making contributing to it somewhat difficult. But the code and knowledge gained can be cherry-picked and backported to the original Wonder if anyone wants it. It's all there for the taking and I'm happy to help.

This was originally started as a cleanup of the orginal Project Wonder and was meant to somehow end up there. But I didn't feel there was a lot of interest in that and as of now I think that would require a huge effort, since I've refactored *a lot* and there's a lot of highly interdependent code and functionality in Wonder's frameworks. And seeing the state of Wonder development in the past few years, it's hard to imagine a lot happening there in the future.

### Do you use it?

Yes. I've used Slim in all of my projects instead of Project Wonder for half a decade now. It's extremely nice to base one's projects on an understandable, easily modifiable and (comparably) small set of framework code with a limited set of dependencies.

### Can I use it?

Absolutely. It's made to be used. The real question is probably "should I use it". If you want to stay with EOF and Project Wonder's tight integration with it, it might not be for you. I'm also not doing any stable releases at the moment, but if anyone decides to use Slim, I'll change that.

Also note that I have removed a lot of stuff that _I don't use_ so I may have removed something you need. But stuff that's been removed can always be re-added if considered important.

### Can I change it?

Yes! Please! I'd love both feedback and contributions.

## Attribution

Finally, I should mention that this isn't really my own project. It's merely an organization of almost three decades of work by dozens, even hundreds of people, and the conserved history of the source files will reflect this. I have much love and respect for past contributors, from whose work I've massively benefited through the years, and many of whom are friends and colleagues.
