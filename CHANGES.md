# Changelog

## 2021-05-23

- Deleted ERXGracefulShutdownHook
  It's been disabled by default for a while. It used `sun.misc.Signal` and `Signalhandler` whose usage is not recommended. Use ERXShutdownHook instead.

- Deleted `ERXApplication._startRequest()` and `ERXApplication._endRequest()`

  If you need to do stuff before and after requests, override `dispatchRequest()`.

  
