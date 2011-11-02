/*
 * Copyright (C) 2011 Pixmob (http://github.com/pixmob)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pixmob.droidlink.util;

import static org.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static org.pixmob.droidlink.Constants.TAG;

import java.util.WeakHashMap;

import android.util.Log;

/**
 * Time limited cache. This cache retains values for a limited time. If the
 * Garbage Collector is requesting for more memory, stored values may be freed.
 * @author Pixmob
 */
public class Cache<K, V> {
    private final int maxItemAge;
    private final InternalWeakHashMap values = new InternalWeakHashMap();
    
    public Cache(final int maxItemAge) {
        this.maxItemAge = maxItemAge;
    }
    
    /**
     * This is method is called when an entry is removed, either because the
     * Garbage Collector is under pressure, or the value is too old.
     */
    protected void entryRemoved(K key, V value) {
    }
    
    /**
     * This method is called when an entry is missing.
     */
    protected V createEntry(K key) {
        return null;
    }
    
    /**
     * Get a value from the cache. If the value does not exist in the cache, the
     * method {@link #createEntry(Object)} is called to get a new instance.
     */
    public final V get(K key) {
        V value = null;
        
        // Get the cache item.
        final CacheItem<V> item = values.get(key);
        final long now = System.currentTimeMillis();
        
        // The cache item may be null if the key was garbage collected.
        // If we got a value from the cache, check if the value is still valid.
        if (item != null && now - item.timestamp < maxItemAge) {
            value = item.value;
        }
        
        if (value == null) {
            value = createEntry(key);
            if (value == null) {
                // Make sure the cache item is removed if the value is not
                // valid.
                values.remove(key);
                return null;
            }
            put(key, value);
        }
        
        if (DEVELOPER_MODE) {
            Log.d(TAG, "Get value from cache: " + key + "=" + value);
        }
        
        return value;
    }
    
    /**
     * Store a value in the cache.
     */
    public final void put(K key, V value) {
        CacheItem<V> item = values.get(key);
        if (item == null) {
            item = new CacheItem<V>();
        }
        item.timestamp = System.currentTimeMillis();
        item.value = value;
        
        if (DEVELOPER_MODE) {
            Log.d(TAG, "Put value in cache: " + key + "=" + value);
        }
        
        values.put(key, item);
    }
    
    /**
     * Internal cache item for storing an entry.
     * @author Pixmob
     */
    private static class CacheItem<V> {
        long timestamp;
        V value;
    }
    
    /**
     * Internal {@link WeakHashMap} implementation.
     * @author Pixmob
     */
    private class InternalWeakHashMap extends WeakHashMap<K, CacheItem<V>> {
        public InternalWeakHashMap() {
            super(8);
        }
        
        @SuppressWarnings("unchecked")
        @Override
        public CacheItem<V> remove(Object key) {
            final CacheItem<V> item = super.remove(key);
            if (key != null && item != null) {
                entryRemoved((K) key, item.value);
            }
            return item;
        }
    }
}
