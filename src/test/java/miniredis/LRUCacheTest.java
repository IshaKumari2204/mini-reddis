package miniredis;

import miniredis.eviction.LRUCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LRUCacheTest {

    @Test
    void shouldEvictLeastRecentlyUsedEntry() {
        LRUCache<String, String> cache = new LRUCache<>(2);

        cache.put("a", "A");
        cache.put("b", "B");
        cache.get("a");
        cache.put("c", "C");

        assertTrue(cache.containsKey("a"));
        assertFalse(cache.containsKey("b"));
    }
}
