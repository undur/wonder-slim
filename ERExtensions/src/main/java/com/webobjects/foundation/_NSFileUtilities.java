package com.webobjects.foundation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import org.apache.log4j.Logger;

public class _NSFileUtilities {
  protected static void freeProcessResources(Process p) {
    if (p != null)
      try {
        if (p.getInputStream() != null)
          p.getInputStream().close(); 
        if (p.getOutputStream() != null)
          p.getOutputStream().close(); 
        if (p.getErrorStream() != null)
          p.getErrorStream().close(); 
        p.destroy();
      } catch (IOException iOException) {} 
  }
  
  public static final Logger log = Logger.getLogger(_NSFileUtilities.class);
  
  public static File resolveLink(String path, String linkName) {
    int retry = NSProperties.intForKeyWithDefault("NSFileUtilities.resolveLinkRetryPeriod", 5000);
    int timeout = NSProperties.intForKeyWithDefault("NSFileUtilities.resolveLinkTimeoutPeriod", 60000);
    return resolveLink(path, linkName, retry, timeout);
  }
  
  public static File resolveLink(String path, String linkName, int retry, int timeout) {
    File resolvedPath;
    boolean debuggingEnabled = NSProperties.booleanForKeyWithDefault("NSFileUtilities.debugMissingCurrentLinks", false);
    if (debuggingEnabled)
      log.info("Resolving link (" + linkName + ") for: " + path); 
    try {
      File f = new File(path);
      boolean isNamedLink = f.getName().toLowerCase().equals(linkName.toLowerCase());
      long timeoutPoint = System.currentTimeMillis() + timeout;
      if (debuggingEnabled)
        log.info("Testing isNamedLink: " + f.getName() + " == " + linkName + " ==> " + (isNamedLink ? "true" : "false")); 
      while (true) {
        if (debuggingEnabled) {
          String output = "";
          String[] cmd = new String[3];
          cmd[0] = "stat";
          cmd[1] = "-F";
          cmd[2] = f.getPath();
          Process task = null;
          try {
            task = Runtime.getRuntime().exec(cmd);
            while (true) {
              try {
                task.waitFor();
                break;
              } catch (InterruptedException interruptedException) {}
            } 
            BufferedReader out = new BufferedReader(new InputStreamReader(task.getInputStream()));
            output = out.readLine();
            if (task.exitValue() != 0) {
              BufferedReader err = new BufferedReader(new InputStreamReader(task.getErrorStream()));
              output = String.valueOf(output) + "ERROR: " + err.readLine();
            } 
          } catch (Throwable t) {
            log.info("Failed to run stat with exception", t);
          } finally {
            freeProcessResources(task);
          } 
          log.info("debugMissingCurrentLinks: stat output: " + output);
        } 
        resolvedPath = f.getCanonicalFile();
        if (debuggingEnabled)
          log.info("Fetched resolved path: " + resolvedPath); 
        if (!isNamedLink || !resolvedPath.getName().toLowerCase().equals(linkName.toLowerCase())) {
          if (debuggingEnabled)
            log.info("resolveLink succeeded"); 
          break;
        } 
        if (debuggingEnabled)
          log.info("resolveLink failed"); 
        if (System.currentTimeMillis() + retry >= timeoutPoint)
          break; 
        Thread.sleep(retry);
      } 
    } catch (Exception e) {
      throw new RuntimeException("Failed to safely resolve current path: " + path, e);
    } 
    return resolvedPath;
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERFoundation/1.0/ERFoundation-1.0.jar!/com/webobjects/foundation/_NSFileUtilities.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */