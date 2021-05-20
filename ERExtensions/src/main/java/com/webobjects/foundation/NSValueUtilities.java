package com.webobjects.foundation;

import java.math.BigDecimal;

public class NSValueUtilities {
  public static boolean booleanValue(Object obj) {
    return booleanValueWithDefault(obj, false);
  }
  
  public static boolean booleanValueWithDefault(Object obj, boolean def) {
    return (obj == null) ? def : BooleanValueWithDefault(obj, Boolean.valueOf(def)).booleanValue();
  }
  
  public static Boolean BooleanValueWithDefault(Object obj, Boolean def) {
    Boolean flag = def;
    if (obj != null)
      if (obj instanceof Number) {
        if (((Number)obj).intValue() == 0) {
          flag = Boolean.FALSE;
        } else {
          flag = Boolean.TRUE;
        } 
      } else if (obj instanceof String) {
        String strValue = ((String)obj).trim();
        if (strValue.length() > 0)
          if (strValue.equalsIgnoreCase("no") || strValue.equalsIgnoreCase("false") || strValue.equalsIgnoreCase("n")) {
            flag = Boolean.FALSE;
          } else if (strValue.equalsIgnoreCase("yes") || strValue.equalsIgnoreCase("true") || strValue.equalsIgnoreCase("y")) {
            flag = Boolean.TRUE;
          } else {
            try {
              if (Integer.parseInt(strValue) == 0) {
                flag = Boolean.FALSE;
              } else {
                flag = Boolean.TRUE;
              } 
            } catch (NumberFormatException numberformatexception) {
              throw new IllegalArgumentException("Failed to parse a boolean from the value '" + strValue + "'.");
            } 
          }  
      } else if (obj instanceof Boolean) {
        flag = (Boolean)obj;
      } else {
        throw new IllegalArgumentException("Failed to parse a boolean from the value '" + obj + "'.");
      }  
    return flag;
  }
  
  public static int intValue(Object obj) {
    return intValueWithDefault(obj, 0);
  }
  
  public static int intValueWithDefault(Object obj, int def) {
    return (obj == null) ? def : IntegerValueWithDefault(obj, Integer.valueOf(def)).intValue();
  }
  
  public static Integer IntegerValueWithDefault(Object obj, Integer def) {
    Integer value = def;
    if (obj != null) {
      if (obj instanceof Integer) {
        value = Integer.valueOf(((Integer)obj).intValue());
      } else if (obj instanceof Number) {
        value = Integer.valueOf(((Number)obj).intValue());
      } else if (obj instanceof String) {
        try {
          String strValue = ((String)obj).trim();
          if (strValue.length() > 0)
            value = Integer.valueOf(strValue); 
        } catch (NumberFormatException numberformatexception) {
          throw new IllegalArgumentException("Failed to parse an integer from the value '" + obj + "'.", numberformatexception);
        } 
      } else if (obj instanceof Boolean) {
        value = ((Boolean)obj).booleanValue() ? Integer.valueOf(1) : def;
      } 
    } else {
      value = def;
    } 
    return value;
  }
  
  public static float floatValue(Object obj) {
    return floatValueWithDefault(obj, 0.0F);
  }
  
  public static float floatValueWithDefault(Object obj, float def) {
    return (obj == null) ? def : FloatValueWithDefault(obj, Float.valueOf(def)).floatValue();
  }
  
  public static Float FloatValueWithDefault(Object obj, Float def) {
    Float value = def;
    if (obj != null) {
      if (obj instanceof Float) {
        value = (Float)obj;
      } else if (obj instanceof Number) {
        value = Float.valueOf(((Number)obj).floatValue());
      } else if (obj instanceof String) {
        try {
          String strValue = ((String)obj).trim();
          if (strValue.length() > 0)
            value = Float.valueOf(strValue); 
        } catch (NumberFormatException numberformatexception) {
          throw new IllegalArgumentException("Failed to parse a float from the value '" + obj + "'.", numberformatexception);
        } 
      } else if (obj instanceof Boolean) {
        value = ((Boolean)obj).booleanValue() ? Float.valueOf(1.0F) : def;
      } 
    } else {
      value = def;
    } 
    return value;
  }
  
  public static double doubleValue(Object obj) {
    return doubleValueWithDefault(obj, 0.0D);
  }
  
  public static double doubleValueWithDefault(Object obj, double def) {
    return (obj == null) ? def : DoubleValueWithDefault(obj, Double.valueOf(def)).doubleValue();
  }
  
  public static Double DoubleValueWithDefault(Object obj, Double def) {
    Double value = def;
    if (obj != null) {
      if (obj instanceof Double) {
        value = (Double)obj;
      } else if (obj instanceof Number) {
        value = Double.valueOf(((Number)obj).doubleValue());
      } else if (obj instanceof String) {
        try {
          String strValue = ((String)obj).trim();
          if (strValue.length() > 0)
            value = Double.valueOf(strValue); 
        } catch (NumberFormatException numberformatexception) {
          throw new IllegalArgumentException("Failed to parse a double from the value '" + obj + "'.", numberformatexception);
        } 
      } else if (obj instanceof Boolean) {
        value = ((Boolean)obj).booleanValue() ? Double.valueOf(1.0D) : def;
      } 
    } else {
      value = def;
    } 
    return value;
  }
  
  public static long longValue(Object obj) {
    return longValueWithDefault(obj, 0L);
  }
  
  public static long longValueWithDefault(Object obj, long def) {
    return (obj == null) ? def : LongValueWithDefault(obj, Long.valueOf(def)).longValue();
  }
  
  public static Long LongValueWithDefault(Object obj, Long def) {
    Long value = def;
    if (obj != null) {
      if (obj instanceof Long) {
        value = (Long)obj;
      } else if (obj instanceof Number) {
        value = Long.valueOf(((Number)obj).longValue());
      } else if (obj instanceof String) {
        try {
          String strValue = ((String)obj).trim();
          if (strValue.length() > 0)
            value = Long.valueOf(strValue); 
        } catch (NumberFormatException numberformatexception) {
          throw new IllegalArgumentException("Failed to parse a long from the value '" + obj + "'.", numberformatexception);
        } 
      } else if (obj instanceof Boolean) {
        value = ((Boolean)obj).booleanValue() ? Long.valueOf(1L) : def;
      } 
    } else {
      value = def;
    } 
    return value;
  }
  
  public static NSArray arrayValue(Object obj) {
    return arrayValueWithDefault(obj, null);
  }
  
  public static NSArray arrayValueWithDefault(Object obj, NSArray def) {
    NSArray value = def;
    if (obj != null)
      if (obj instanceof NSArray) {
        value = (NSArray)obj;
      } else if (obj instanceof String) {
        String strValue = ((String)obj).trim();
        if (strValue.length() > 0) {
          if (strValue.charAt(0) != '(')
            strValue = "(" + strValue + ")"; 
          value = (NSArray)NSPropertyListSerialization.propertyListFromString(strValue);
          if (value == null)
            throw new IllegalArgumentException("Failed to parse an array from the value '" + obj + "'."); 
        } 
      } else {
        throw new IllegalArgumentException("Failed to parse an array from the value '" + obj + "'.");
      }  
    return value;
  }
  
  public static NSSet setValue(Object obj) {
    return setValueWithDefault(obj, null);
  }
  
  public static NSSet setValueWithDefault(Object obj, NSSet def) {
    NSSet value = def;
    if (obj != null)
      if (obj instanceof NSSet) {
        value = (NSSet)obj;
      } else if (obj instanceof NSArray) {
        value = new NSSet((NSArray)obj);
      } else if (obj instanceof String) {
        NSArray array = arrayValueWithDefault(obj, null);
        if (array != null)
          value = new NSSet(array); 
      } else {
        throw new IllegalArgumentException("Failed to parse a set from the value '" + obj + "'.");
      }  
    return value;
  }
  
  public static NSDictionary dictionaryValue(Object obj) {
    return dictionaryValueWithDefault(obj, null);
  }
  
  public static NSDictionary dictionaryValueWithDefault(Object obj, NSDictionary def) {
    NSDictionary value = def;
    if (obj != null)
      if (obj instanceof NSDictionary) {
        value = (NSDictionary)obj;
      } else if (obj instanceof String) {
        String strValue = ((String)obj).trim();
        if (strValue.length() > 0) {
          Object objValue = NSPropertyListSerialization.propertyListFromString((String)obj);
          if (objValue == null || !(objValue instanceof NSDictionary))
            throw new IllegalArgumentException("Failed to parse a dictionary from the value '" + obj + "'."); 
          value = (NSDictionary)objValue;
        } 
      } else {
        throw new IllegalArgumentException("Failed to parse a dictionary from the value '" + obj + "'.");
      }  
    return value;
  }
  
  public static NSData dataValue(Object obj) {
    return dataValueWithDefault(obj, null);
  }
  
  public static NSData dataValueWithDefault(Object obj, NSData def) {
    NSData value = def;
    if (obj != null)
      if (obj instanceof NSData) {
        value = (NSData)obj;
      } else if (obj instanceof byte[]) {
        byte[] byteValue = (byte[])obj;
        value = new NSData(byteValue, new NSRange(0, byteValue.length), true);
      } else if (obj instanceof String) {
        String strValue = ((String)obj).trim();
        if (strValue.length() > 0) {
          Object objValue = NSPropertyListSerialization.propertyListFromString(strValue);
          if (objValue == null || !(objValue instanceof NSData))
            throw new IllegalArgumentException("Failed to parse data from the value '" + obj + "'."); 
          value = (NSData)objValue;
        } 
      } else {
        throw new IllegalArgumentException("Failed to parse data from the value '" + obj + "'.");
      }  
    return value;
  }
  
  public static BigDecimal bigDecimalValue(Object obj) {
    return bigDecimalValueWithDefault(obj, null);
  }
  
  public static BigDecimal bigDecimalValueWithDefault(Object obj, BigDecimal def) {
    BigDecimal value = def;
    if (obj != null)
      if (obj instanceof BigDecimal) {
        value = (BigDecimal)obj;
      } else if (obj instanceof String) {
        String strValue = ((String)obj).trim();
        if (strValue.length() > 0)
          value = new BigDecimal(strValue); 
      } else if (obj instanceof Integer) {
        value = new BigDecimal(((Integer)obj).intValue());
      } else if (obj instanceof Long) {
        value = new BigDecimal(((Long)obj).longValue());
      } else if (obj instanceof Float) {
        value = new BigDecimal(((Float)obj).floatValue());
      } else if (obj instanceof Double) {
        value = new BigDecimal(((Double)obj).doubleValue());
      } else if (obj instanceof Number) {
        value = new BigDecimal(((Number)obj).doubleValue());
      } else if (obj instanceof Boolean) {
        value = new BigDecimal(((Boolean)obj).booleanValue() ? 1 : 0);
      } else {
        throw new IllegalArgumentException("Failed to parse a BigDecimal from the value '" + obj + "'.");
      }  
    return value;
  }
  
  public static int compare(int int1, int int2) {
    return (int1 > int2) ? 1 : ((int1 < int2) ? -1 : 0);
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERFoundation/1.0/ERFoundation-1.0.jar!/com/webobjects/foundation/NSValueUtilities.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */