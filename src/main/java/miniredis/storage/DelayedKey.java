package miniredis.storage;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayedKey implements Delayed{
    private final String key;
    private final long expiresAtMillis;

    public DelayedKey(String key, long expiresAtMillis) {
        this.key = key;
        this.expiresAtMillis = expiresAtMillis;
    }
    public String getKey() {
        return key;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }
    
    @Override
    public long getDelay(TimeUnit unit) {
        long diff = expiresAtMillis - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if(this==o) return 0;
        if (o instanceof DelayedKey) {
            DelayedKey other = (DelayedKey) o;
            return Long.compare(this.expiresAtMillis, other.expiresAtMillis);
        }
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }
}
