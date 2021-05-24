# Changelog

## 2021-05-24

- **Moved response compression from dispatchRequest() to new class ERXResponseCompression**
  Makes the code easier on the eyes. Still considering full removal of response compression since that tends to be handled by the web server in most environments I know of.
- 

## 2021-05-23

- **Deleted ERXGracefulShutdownHook**
  It's been disabled by default for a while. It used `sun.misc.Signal` and `Signalhandler` whose usage is not recommended. Use ERXShutdownHook instead.

- **Deleted `ERXApplication._startRequest()` and `ERXApplication._endRequest()`**

  If you need to do stuff before and after requests, override `dispatchRequest()`.

- **Moved the ERExtensions.initApp(...) methods to new class ERXAppRunner**
  ERExtensions should serve only as ERExtensions' principal class

- **Removed threadInterrupt stuff from ERXRuntimeUtilities**
  Logic not actually used by any code inside the frameworks.

- **Deleted ERXArrayUtilities**

- **Deleted ERXActiveImage**
  Doesn't seem to serve any purpose

- **Deleted ERXWOPasswordField**
  The improvements offered by it are negligible in the age of ubiquitous https

- **Deleted userInfo() stuff from ERXResponse**
  It seems to have been there mostly to keep comptibility with older WO versions.

- **Removed pushContent(), popContent(), __setContent() etc. from ERXWOContext**
  Looks like the vestiges of a 13 year old experiment by mschrag.

- 
