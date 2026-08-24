package miniredis.eviction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class LRUCache<K, V> {
    private final int capacity;
    private final Map<K, V> entries;

    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.entries = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public V get(K key) {
        return entries.get(key);
    }

    public void put(K key, V value) {
        entries.put(key, value);
    }

    public V remove(K key) {
        return entries.remove(key);
    }

    public boolean containsKey(K key) {
        return entries.containsKey(key);
    }

    public int size() {
        return entries.size();
    }

    public Set<K> keySet() {
        return entries.keySet();
    }

    public void clear() {
        entries.clear();
    }
}
