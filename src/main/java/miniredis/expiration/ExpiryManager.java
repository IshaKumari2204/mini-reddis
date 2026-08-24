package miniredis.expiration;

import miniredis.storage.KeyValueStore;

import java.util.concurrent.atomic.AtomicBoolean;

public class ExpiryManager {
    private final KeyValueStore store;
    private final long intervalMillis;
    private final Thread cleanerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ExpiryManager(KeyValueStore store) {
        this(store, 1000L);
    }

    public ExpiryManager(KeyValueStore store, long intervalMillis) {
        this.store = store;
        this.intervalMillis = Math.max(50L, intervalMillis);
        this.cleanerThread = new Thread(this::runCleanupLoop, "mini-redis-expiry-cleaner");
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
                Thread.sleep(intervalMillis);
                store.removeExpired();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
