package com.webobjects.foundation;

import java.util.LinkedHashMap;
import java.util.Map;

public class NSSizeLimitedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
  private static final long serialVersionUID = -5273353835281837352L;
  
  private final int _maxSize;
  
  private final NSDisposalDelegate _disposalDelegate;
  
  private static final float StandardMaxLoadFactor = 0.5F;
  
  public NSSizeLimitedLinkedHashMap(int maxSize, boolean isIdentity, NSDisposalDelegate disposalDelegate) {
    super((maxSize < 1) ? 4096 : maxSize, 0.5F, true);
    this._maxSize = (maxSize < 1) ? Integer.MAX_VALUE : maxSize;
    this._disposalDelegate = disposalDelegate;
  }
  
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    if (size() > this._maxSize) {
      if (this._disposalDelegate != null)
        this._disposalDelegate.valueRemoved(eldest.getValue()); 
      return true;
    } 
    return false;
  }
  
  public static interface NSDisposalDelegate {
    void valueRemoved(Object param1Object);
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERFoundation/1.0/ERFoundation-1.0.jar!/com/webobjects/foundation/NSSizeLimitedLinkedHashMap.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */