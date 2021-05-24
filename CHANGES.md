# Changelog

## 2021-05-24

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

## 2021-05-23

- **Deleted ERXGracefulShutdownHook**
  It's been disabled by default for a while. It used `sun.misc.Signal` and `Signalhandler` whose usage is not recommended. Use ERXShutdownHook instead.

- **Deleted `ERXApplication._startRequest()` and `ERXApplication._endRequest()`**

  If you need to do stuff before and after requests, override `dispatchRequest()`.

- **Moved the ERXExtensions.initApp(...) methods to new class ERXAppRunner**
  ERXExtensions should serve only as the ERExtensions framework's principal/initialization class

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

## 2021-05-21

- **Deleted ERXDelayedRequestHandler**
  Cool idea, but reading the mailing list it seems to have it's problems. I'd prefer a mechanism that allows the programmer to consciously decide to use long responses when desired, not something that alters the global behaviour of the application.
- **Moved ERXBrowser and it's companions to a separate package; er.extensions.browser**
  The er.extensions.appserver package is pretty full as is
- **Deleted ERXTimestampUtilities**
  Modern java uses the classes from java.time.
- **Deleted ERXSelectorUtilities**
