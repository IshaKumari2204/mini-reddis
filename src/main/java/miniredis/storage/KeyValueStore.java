package miniredis.storage;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {
    private final Map<String, ValueEntry> store = new ConcurrentHashMap<>();

    public void put(String key, String value) {
        store.put(key, new ValueEntry(value));
    }

    public void put(String key, String value, long ttlSeconds) {
        store.put(key, new ValueEntry(value, ttlSeconds));
    }

    public String get(String key) {
        ValueEntry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            store.remove(key);
            return null;
        }
        return entry.getValue();
    }

    public boolean delete(String key) {
        return store.remove(key) != null;
    }

    public int deleteMultiple(String... keys) {
        int count = 0;
        for (String key : keys) {
            if (store.remove(key) != null) {
                count++;
            }
        }
        return count;
    }

    public boolean expire(String key, long ttlSeconds) {
        ValueEntry entry = store.get(key);
        if (entry == null) {
            return false;
        }
        if (ttlSeconds <= 0) {
            store.remove(key);
            return true;
        }
        store.put(key, new ValueEntry(entry.getValue(), ttlSeconds));
        return true;
    }

    public long ttl(String key) {
        ValueEntry entry = store.get(key);
        if (entry == null) {
            return -2L;
        }
        if (entry.isExpired()) {
            store.remove(key);
            return -2L;
        }
        if (entry.getExpiresAtMillis() < 0) {
            return -1L;
        }
        return Math.max(0L, entry.getRemainingTtlMillis() / 1000L);
    }

    public void removeExpired() {
        Iterator<Map.Entry<String, ValueEntry>> iterator = store.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ValueEntry> entry = iterator.next();
            if (entry.getValue() != null && entry.getValue().isExpired()) {
                iterator.remove();
            }
        }
    }

    public Set<String> keySet() {
        return new HashSet<>(store.keySet());
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }
}
