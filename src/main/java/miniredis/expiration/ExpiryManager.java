package miniredis.expiration;

import miniredis.storage.DelayedKey;
import miniredis.storage.KeyValueStore;

import java.util.concurrent.atomic.AtomicBoolean;

public class ExpiryManager {
    private final KeyValueStore store;
    private final Thread cleanerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ExpiryManager(KeyValueStore store) {
        this.store = store;
        this.cleanerThread = new Thread(this::runCleanupLoop, "mini-redis-expiry-cleaner");
        this.cleanerThread.setDaemon(true);
    }

    public ExpiryManager(KeyValueStore store, long intervalMillis) {
        this(store);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            cleanerThread.start();
        }
    }

    public void stop() {
        running.set(false);
        cleanerThread.interrupt();
    }

    private void runCleanupLoop() {
        while (running.get()) {
            try {
                // Blocks efficiently until the next key expires! Zero CPU polling!
                DelayedKey delayedKey = store.getDelayQueue().take();
                store.removeIfExpired(delayedKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
