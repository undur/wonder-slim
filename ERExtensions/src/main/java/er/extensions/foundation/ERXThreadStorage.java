/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.
 */
package er.extensions.foundation;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides a way to store objects for a particular thread. This can be especially handy for storing objects
 * like the current actor or the current form name within the scope of a thread handling a particular request.
 */

public class ERXThreadStorage {

    /**
     * Holds the single instance of the thread map.
     */
    private static ThreadLocal<Map> threadMap = new ThreadLocal<>();

    /**
     * Holds the default initialization value of the hash map.
     */
    private static int DefaultHashMapSize = 10;

    /**
     * Sets a value for a particular key for a particular thread.
     */
    public static void takeValueForKey(Object object, String key) {
    	Map map = storageMap(true);
    	map.put(key, object);
    }

    /**
     * Removes the value in the map for a given key.
     * 
     * @param key key to be removed from the map.
     * @return the object corresponding to the key that was removed, null if nothing is found.
     */
    public static Object removeValueForKey(String key) {
        Map map = storageMap(false);
        return map != null ? map.remove(key) : null;
    }

    /**
     * Gets the object associated with the key in the storage map off of the current thread.
     * 
     * @param key key to be used to retrieve value from map.
     * @return the value stored in the map for the given key.
     */
    public static Object valueForKey(String key) {
		Map map = storageMap(false);
		Object result = null;
		if (map != null) {
			result = map.get(key);
		}
		return result;
	}
    
    /**
     * Gets the storage map from the current thread.
     * At the moment this Map is syncronized for thread
     * safety. This might not be necessary in which case
     * users of this method would need to make sure that
     * they take the appropriate precautions.
     * 
     * @return Map object associated with this particular thread.
     */
    public static Map map() {
        return storageMap(true);
    }

    /**
     * Removes all of the keys from the current Map.
     */
    public static void reset() {
        Map map = storageMap(false);

        if (map != null) {
        	map.clear();
        }
    }

    /**
     * Gets the {@link Map} from the thread map. Has the option to
     * to create the map if it hasn't been created yet for this thread.
     * Only used internally.
     * 
     * @param create should create the map storage if it isn't found.
     * @return the map for the current thread or null
     */
    private static Map storageMap(boolean create) {
        Map map = threadMap.get();
        if (map == null && create) {
            map = new HashMap(DefaultHashMapSize);
            threadMap.set(map);
        }
        return map;
    }
}