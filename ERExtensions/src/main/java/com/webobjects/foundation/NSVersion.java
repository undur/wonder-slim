package com.webobjects.foundation;

public interface NSVersion extends Comparable<NSVersion> {
  public static final String First = "1.0.0";
  
  public static final String Ultimate = "9999.9999.9999";
  
  Integer version();
  
  Integer revision();
  
  Integer fix();
  
  Integer build();
  
  String versionString();
  
  String releaseString();
  
  public static class DefaultImplementation {
    public static boolean before(NSVersion target, NSVersion object) {
      return (target.compareTo(object) < 0);
    }
    
    public static boolean after(NSVersion target, NSVersion object) {
      return (target.compareTo(object) > 0);
    }
    
    public static int compareTo(NSVersion target, NSVersion object) throws ClassCastException {
      if (object == null)
        throw new NullPointerException(); 
      int result = target.version().intValue() - object.version().intValue();
      if (result == 0) {
        result = target.revision().intValue() - object.revision().intValue();
        if (result == 0)
          result = target.fix().intValue() - object.fix().intValue(); 
      } 
      return result;
    }
    
    public static boolean equals(NSVersion target, Object object) {
      if (!(object instanceof NSVersion))
        return false; 
      return (target.compareTo((NSVersion)object) == 0);
    }
    
    public static String toString(NSVersion target) {
      StringBuilder aLog = new StringBuilder();
      aLog.append(target.version());
      aLog.append(".");
      aLog.append(target.revision());
      aLog.append(".");
      aLog.append(target.fix());
      if (target.releaseString() != null && target.releaseString().length() > 0)
        aLog.append(target.releaseString()); 
      return aLog.toString();
    }
    
    public static String toName(NSVersion target) {
      StringBuilder aLog = new StringBuilder();
      aLog.append(target.version());
      aLog.append("_");
      aLog.append(target.revision());
      aLog.append("_");
      aLog.append(target.fix());
      if (target.releaseString() != null && target.releaseString().length() > 0)
        aLog.append(target.releaseString()); 
      return aLog.toString();
    }
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERFoundation/1.0/ERFoundation-1.0.jar!/com/webobjects/foundation/NSVersion.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */