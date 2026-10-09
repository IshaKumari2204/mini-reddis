package miniredis;

import miniredis.expiration.ExpiryManager;
import miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyValueStoreTest {

    @Test
    void shouldSetAndGetValue() {
        KeyValueStore store = new KeyValueStore();

        store.put("name", "alice");

        assertEquals("alice", store.get("name"));
    }

    @Test
    void shouldExpireValueOnGet() throws InterruptedException {
        KeyValueStore store = new KeyValueStore();
        store.put("token", "abc", 1);

        assertEquals("abc", store.get("token"));

        Thread.sleep(1100);

        assertNull(store.get("token"));
    }

    @Test
    void shouldEvictLeastRecentlyUsedKeyWhenCapacityExceeded() {
        // Capacity = 2
        KeyValueStore store = new KeyValueStore(2);

        store.put("k1", "v1");
        store.put("k2", "v2");

        // Access k1 to promote it to most recently used
        assertEquals("v1", store.get("k1"));

        // Put k3 -> should evict k2 (least recently used)
        store.put("k3", "v3");

        assertEquals("v1", store.get("k1"));
        assertNull(store.get("k2"), "k2 should have been LRU evicted");
        assertEquals("v3", store.get("k3"));
        assertEquals(2, store.size());
    }

    @Test
    void shouldHandleDeleteAndMultipleDelete() {
        KeyValueStore store = new KeyValueStore();

        store.put("a", "1");
        store.put("b", "2");
        store.put("c", "3");

        assertTrue(store.delete("a"));
        assertNull(store.get("a"));
        assertFalse(store.delete("a")); // already deleted

        assertEquals(2, store.deleteMultiple("b", "c", "nonexistent"));
        assertEquals(0, store.size());
    }

    @Test
    void shouldHandleTtlAndExpireMethods() {
        KeyValueStore store = new KeyValueStore();

        // Key doesn't exist
        assertEquals(-2L, store.ttl("missing"));

        // Key exists without TTL
        store.put("k", "v");
        assertEquals(-1L, store.ttl("k"));

        // Set TTL
        assertTrue(store.expire("k", 10));
        assertTrue(store.ttl("k") > 0 && store.ttl("k") <= 10);

        // Expire with <= 0 ttl should delete immediately
        assertTrue(store.expire("k", 0));
        assertNull(store.get("k"));
        assertEquals(-2L, store.ttl("k"));
    }

    @Test
    void shouldBackgroundPurgeExpiredKeysViaExpiryManager() throws InterruptedException {
        KeyValueStore store = new KeyValueStore();
        ExpiryManager expiryManager = new ExpiryManager(store);
        expiryManager.start();

        try {
            store.put("temp1", "val1", 1);
            store.put("temp2", "val2", 1);

            assertEquals(2, store.size());

            // Wait for DelayQueue and ExpiryManager to purge
            Thread.sleep(1300);

            assertEquals(0, store.size(), "Expired keys should have been purged automatically by ExpiryManager");
        } finally {
            expiryManager.stop();
        }
    }

    @Test
    void shouldHandleTombstoneValidationWhenKeyOverwritten() throws InterruptedException {
        KeyValueStore store = new KeyValueStore();
        ExpiryManager expiryManager = new ExpiryManager(store);
        expiryManager.start();

        try {
            // Set k1 with 1s TTL
            store.put("k1", "v1_short", 1);

            // Overwrite k1 with NO TTL immediately
            store.put("k1", "v1_permanent");

            // Sleep past the 1s expiry of the first entry
            Thread.sleep(1300);

            // The tombstone in DelayQueue should NOT delete the new permanent entry
            assertEquals("v1_permanent", store.get("k1"));
            assertEquals(1, store.size());
        } finally {
            expiryManager.stop();
        }
    }
}
