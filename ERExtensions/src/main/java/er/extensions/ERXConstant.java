package er.extensions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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