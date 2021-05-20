package com.webobjects.appserver._private;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation._NSUtilities;
import java.io.File;

public class WOBundle {
  public static Class classForComponentNamedInBundle(String aClassName, NSBundle aBundle) {
    return aBundle._classWithName(aClassName);
  }
  
  public static boolean hasWOComponents(NSBundle aBundle) {
    boolean hasWOComponents = false;
    if (aBundle._infoDictionary() != null) {
      Object value = aBundle._infoDictionary().objectForKey("Has_WOComponents");
      if (value instanceof Boolean) {
        hasWOComponents = ((Boolean)value).booleanValue();
      } else if (value != null) {
        hasWOComponents = NSPropertyListSerialization.booleanForString(value.toString());
      } 
    } 
    if (!hasWOComponents) {
      NSArray components = aBundle.resourcePathsForResources(null, null);
      int count = components.count();
      for (int i = 0; i < count; i++) {
        String nextResource = (String)components.objectAtIndex(i);
        if (nextResource.indexOf(".wo" + File.separator) != -1) {
          hasWOComponents = true;
          break;
        } 
      } 
    } 
    return hasWOComponents;
  }
  
  public static Class lookForClassInAllBundles(String aClassName) {
    Class<NSBundle> aBundleClass = NSBundle.class;
    return _NSUtilities.classWithName(aClassName);
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERWebObjects/1.0/ERWebObjects-1.0.jar!/com/webobjects/appserver/_private/WOBundle.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */