package er.extensions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ERXConstant {

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