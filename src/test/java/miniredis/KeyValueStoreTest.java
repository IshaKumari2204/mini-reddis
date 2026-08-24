package miniredis;

import miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KeyValueStoreTest {

    @Test
    void shouldSetAndGetValue() {
        KeyValueStore store = new KeyValueStore();

        store.put("name", "alice");

        assertEquals("alice", store.get("name"));
    }

    @Test
    void shouldExpireValue() throws InterruptedException {
        KeyValueStore store = new KeyValueStore();
        store.put("token", "abc", 1);

        assertEquals("abc", store.get("token"));

        Thread.sleep(1200);

        assertNull(store.get("token"));
    }
}
