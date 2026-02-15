/**
 * Caching utilities
 * TECH DEBT: No TTL, cache grows infinitely until OOM
 */

package com.paymentplatform.commons;

import java.util.*;

public class CacheUtils {
    
    // TECH DEBT: In-memory cache with no eviction policy
    private static Map<String, Object> cache = new HashMap<>();
    
    // TECH DEBT: No size limit, will eventually cause OutOfMemoryError
    public static void put(String key, Object value) {
        cache.put(key, value);
        // TECH DEBT: Never expires, never evicts
    }
    
    public static Object get(String key) {
        return cache.get(key);
    }
    
    /**
     * TECH DEBT: Clear is never called, cache only grows
     */
    public static void clear() {
        cache.clear();
    }
    
    /**
     * TECH DEBT: Cache stampede - no locking, multiple threads compute same value
     */
    public static Object getOrCompute(String key, ComputeFunction func) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        
        // RACE CONDITION: Multiple threads might compute simultaneously
        Object value = func.compute();
        cache.put(key, value);
        return value;
    }
    
    // TECH DEBT: No cache stats, no monitoring, can't see hit rate
}

interface ComputeFunction {
    Object compute();
}
