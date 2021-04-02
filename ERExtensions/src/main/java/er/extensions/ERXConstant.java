package er.extensions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSMutableArray;

import er.extensions.foundation.ERXArrayUtilities;

/**
 * FIXME: Everything here is stupid.
 * 
 * This class was introduced temporarily to serve as container for the public variables previously used from here, until they get replaced on-site. 
 */

public class ERXConstant {

	public static final Class[] NotificationClassArray = { com.webobjects.foundation.NSNotification.class };
    public static final Integer OneInteger = 1;
    public static final Integer ZeroInteger = 0;
    public static final Class[] StringClassArray = new Class[] { String.class };
    public static final Object[] EmptyObjectArray = new Object[] {};
    public static final Class[] EmptyClassArray = new Class[0];
    public static final Class[] ObjectClassArray = { Object.class };

    public static final int MAX_INT=2500;

    protected static Integer[] INTEGERS=new Integer[MAX_INT];

    static {
        for (int i=0; i<MAX_INT; i++) INTEGERS[i]=Integer.valueOf(i);
    }

    /**
     * Returns an Integer for a given int
     * @return potentially cache Integer for a given int
     */
    public static Integer integerForInt(int i) {
        return (i>=0 && i<MAX_INT) ? INTEGERS[i] : Integer.valueOf(i);
    }
    
    public static interface Constant {
		public int sortOrder();
		public String name();
		public Object value();
	}
    
	/**
	 * Holds the value store, grouped by class name.
	 */
	private static final Map _store = new HashMap();
	
    /**
     * Retrieves the constant for the given class name and value. Null is returned
     * if either class or value isn't found.
     * @param value
     * @param clazzName
     */
    public static Constant constantForClassNamed(Object value, String clazzName) {
        synchronized (_store) {
            Map classMap = keyMap(clazzName, false);
            Constant result = (Constant) classMap.get(value);
            return result;
        }
    }

	/**
	 * Retrieves the key map for the class name.
	 * @param name
	 * @param create
	 */
	private static Map keyMap(String name, boolean create) {
		Map map = (Map) _store.get(name);
		if(map == null) {
			if(create) {
				map = new HashMap();
				_store.put(name, map);
				name = name.replace('$', '.');
				_store.put(name, map);
			} else {
				map = Collections.EMPTY_MAP;
			}
		}
		return map;
	}
}