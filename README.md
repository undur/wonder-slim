# wonder-slim

*Before you start reading, I should mention that this is not my own project. It's merely an organization of over two decades of work of dozens, even hundreds of people. To emphasize this, I've chosen to go the way of cloning the existing Wonder repository and keep attribution and history of the source files in question as much as possible. Much love and respect for past contributors.*

## Motivation

Project Wonder is an old and storied collection of frameworks that's had many contributors with differing goals. Over the years, it's accumulated a *lot* of cruft and it's been a while since it's seen something like an overarching design philosophy.

This is an experimental project to see how it can be improved, not by adding stuff but by removing stuff.

Below I enumerate a few primary goals of wonder-slim (from here on called just "Slim"), they all share the same **ultimate goal**, to make Wonder manageable and maintainable, lean and mean and a lot more readable and understandable. The derived goal of this is to make Wonder maintainable.

## Primary goals

### Separate concerns and only include the basics

Project Wonder has a multitude of frameworks. This project focuses on the minimal subset that's required to create and run a modern basic WebObjects web application, nothing more, nothing less.

For this reason, the project picked only four frameworks from Project Wonder to work on, `ERExtensions`, `JavaWOExtensions`, `Ajax` and `WOOGNL`. I've also chosen the route of combining `JavaWOExtensions` and `ERExtensions`, since they both currently serve pretty much the same purpose.

The the project therefore currently only contains three frameworks.

### **Loosen the ties between WOF and EOF**

This is really an extensions of "separate concerns", but since EOF is a huge part of many users' projects, it merits a separate mention.

A Web framework and a persistence frameworks really are separate things. As mentioned previously, this project focuses only on the "Web" part of WO, and removes everything related to EOF. Note that although EOF is not *part* of this project, that does not mean EOF cannot *integrate* well with the project. But EOF integration is not a priority at this stage.

Slim's ERExtensions no longer uses any code from JavaEOControl or JavaEOAccess, although a WO application will still have to import these frameworks (due to JavaWebObjects referencing some classes there).

### Reduce the number of external dependencies

`ERExtensions` only pulls in two external dependencies `slf4j-api` and `log4j`.  The other two frameworks are unchanged from wonder; `WOOGNL ` pulls in `ognl` and `Ajax` pulls in `jabsorb`.

### Reduce the amount of code used from closed frameworks

Although not using WO is not realistic, I'd like to reduce the amount of usage of WO apis as much as possible. This includes not using NS* collection classes, avoiding property list serialization.

This makes it easier to integrate with the java ecosystem, i.e. use code and contribute code from it. And it even allows for some exciting development in the future, who knows.

### **Standardize**

One of WO's strengths used to be convention over configuration. Over the years a lot of things has changed and changes have often been made "easier" by Wonder, by keeping old code around and activating it conditionally by looking at the project or by setting a property. This "old" code is being removed and with it goes the option of doing things "the old way". It's been 13 years since we've had a WO release, we should be ready to standardize on a set of practices.

### Use Maven

The frameworks are only tested on maven and assume a maven project layout.

### **Run on modern java**

Slim targets Java 11 and will not run on older JDKs.

## FAQ

*Ok "FAQ" might be something of an overstatement, I haven't received a single question about this. But "potential questions" just sounds stupid.*

### So, are you just leaving Project Wonder behind?

No. While this is a usable framework in and off itself, it's also meant to serve as an exercise to learn to navigate the Wonder Sources and WO's architecture, what's actually used, what can be improved etc. Hopefully, some of the refacorings and cleanups can be backported to Wonder itself, so everyone benefits. 

In a perfect world, this effort could replace the Frameworks we're working on in Wonder. But that would require a huge effort, due to the highly interconnected nature of the current Project Wonder.

### Do you use it?

Yes. I now use Slim in all of my active projects instead of Project Wonder.

### Can I use it?

Sure. But be aware that the real question is probably "should I use it". I'm moving fast and breaking things at the moment and I'm not doing any releases, stable or otherwise. If there's interest in the project I might consider changing that.
