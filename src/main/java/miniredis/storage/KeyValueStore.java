package miniredis.storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicLong;

public class KeyValueStore {
    private final Map<String, ValueEntry> store;
    private final DelayQueue<DelayedKey> delayQueue;
    private final Map<String, Long> activeLeases;
    private final AtomicLong tokenSequence;

    public KeyValueStore() {
        this(Long.MAX_VALUE);
    }

    public KeyValueStore(long capacity) {
        this.store = new LinkedHashMap<String, ValueEntry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ValueEntry> eldest) {
                return size() > capacity;
            }
        };
        this.delayQueue = new DelayQueue<>();
        this.activeLeases = new HashMap<>();
        this.tokenSequence = new AtomicLong(1);
    }

    public void put(String key, String value) {
        synchronized (this) {
            store.put(key, new ValueEntry(value));
        }
    }

    public void put(String key, String value, long ttlSeconds) {
        ValueEntry entry = new ValueEntry(value, ttlSeconds);
        synchronized (this) {
            store.put(key, entry);
        }
        if (entry.getExpiresAtMillis() > 0) {
            delayQueue.put(new DelayedKey(key, entry.getExpiresAtMillis()));
        }
    }

    public synchronized String get(String key) {
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

    public synchronized boolean delete(String key) {
        activeLeases.remove(key);
        return store.remove(key) != null;
    }

    public synchronized int deleteMultiple(String... keys) {
        int count = 0;
        for (String key : keys) {
            if (store.remove(key) != null) {
                count++;
            }
        }
        return count;
    }

    public boolean expire(String key, long ttlSeconds) {
        ValueEntry newEntry;
        synchronized (this) {
            ValueEntry entry = store.get(key);
            if (entry == null) {
                return false;
            }
            if (ttlSeconds <= 0) {
                store.remove(key);
                return true;
            }
            newEntry = new ValueEntry(entry.getValue(), ttlSeconds);
            store.put(key, newEntry);
        }
        if (newEntry.getExpiresAtMillis() > 0) {
            delayQueue.put(new DelayedKey(key, newEntry.getExpiresAtMillis()));
        }
        return true;
    }

    public synchronized long ttl(String key) {
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

    public DelayQueue<DelayedKey> getDelayQueue() {
        return delayQueue;
    }

    public synchronized void removeIfExpired(DelayedKey delayedKey) {
        if (delayedKey == null) return;
        ValueEntry current = store.get(delayedKey.getKey());
        // Lazy Tombstone Validation: check timestamp matches
        if (current != null && current.getExpiresAtMillis() == delayedKey.getExpiresAtMillis()) {
            store.remove(delayedKey.getKey());
        }
    }

    public void removeExpired() {
        // Non-blocking poll of all ready-to-expire keys in DelayQueue
        DelayedKey delayedKey;
        while ((delayedKey = delayQueue.poll()) != null) {
            removeIfExpired(delayedKey);
        }
    }

    public synchronized Set<String> keySet() {
        return new HashSet<>(store.keySet());
    }

    public synchronized int size() {
        return store.size();
    }

    public synchronized void clear() {
        synchronized (this) {
            store.clear();
        }
        delayQueue.clear();
    }

    public Long getWithLease(String key) {
        long token = tokenSequence.incrementAndGet();
        activeLeases.put(key, token);
        return token;
    }

    public synchronized boolean putWithLease(String key, String value, long leaseToken) {
        Long activeToken = activeLeases.get(key);

        if (activeToken != null && activeToken == leaseToken) {
            store.put(key, new ValueEntry(value));
            activeLeases.remove(key);
            return true;
        }
        return false;
    }
}
