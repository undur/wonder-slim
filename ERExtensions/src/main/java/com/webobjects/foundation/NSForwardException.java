package com.webobjects.foundation;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;

public class NSForwardException extends RuntimeException {
  private static final long serialVersionUID = 7417321001012630866L;
  
  public static final Class<?> _CLASS = _NSUtilitiesExtra._classWithFullySpecifiedNamePrime("com.webobjects.foundation.NSForwardException");
  
  public static RuntimeException _runtimeExceptionForThrowable(Throwable throwable) {
    if (throwable == null)
      return null; 
    if (throwable instanceof RuntimeException)
      return (RuntimeException)throwable; 
    if (throwable instanceof InvocationTargetException) {
      Throwable targetException = ((InvocationTargetException)throwable).getTargetException();
      if (targetException instanceof RuntimeException)
        return (RuntimeException)targetException; 
      return new NSForwardException(targetException);
    } 
    return new NSForwardException(throwable);
  }
  
  public static Throwable _originalThrowable(Throwable throwable) {
    Throwable originalThrowable = throwable;
    Throwable currentThrowable = throwable;
    while (currentThrowable != null) {
      originalThrowable = currentThrowable;
      currentThrowable = originalThrowable.getCause();
    } 
    return originalThrowable;
  }
  
  public NSForwardException(Throwable wrapped, String extraMessage) {
    super(extraMessage, _originalThrowable(wrapped));
  }
  
  public NSForwardException(String message, Throwable cause) {
    super(message, _originalThrowable(cause));
  }
  
  public NSForwardException(Throwable wrapped) {
    super(wrapped);
  }
  
  public Throwable originalException() {
    return getCause();
  }
  
  public String stackTrace() {
    StringWriter writer = new StringWriter();
    getCause().printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }
  
  public String toString() {
    return String.valueOf(getClass().getName()) + " [" + getCause().getClass().getName() + "] " + getCause().getMessage() + ":" + getMessage();
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERFoundation/1.0/ERFoundation-1.0.jar!/com/webobjects/foundation/NSForwardException.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */