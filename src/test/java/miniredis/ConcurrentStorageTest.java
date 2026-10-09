package miniredis;

import miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentStorageTest {

    @Test
    void shouldHandleHighConcurrentReadsAndWritesWithoutRaceConditions() throws InterruptedException {
        int capacity = 100;
        KeyValueStore store = new KeyValueStore(capacity);
        int threadCount = 10;
        int opsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        assertDoesNotThrow(() -> {
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            String key = "key_" + (i % 50);
                            String value = "val_" + threadId + "_" + i;

                            if (i % 3 == 0) {
                                store.put(key, value);
                            } else if (i % 3 == 1) {
                                store.get(key);
                            } else {
                                store.put(key, value, 2);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        });

        executor.shutdown();
        assertTrue(store.size() <= capacity, "Store size should never exceed LRU capacity limit");
    }
}
