package miniredis.storage;

public class ValueEntry {
    private final String value;
    private final long expiresAtMillis;

    public ValueEntry(String value) {
        this(value, -1L);
    }

    public ValueEntry(String value, long ttlSeconds) {
        this.value = value;
        if (ttlSeconds > 0) {
            this.expiresAtMillis = System.currentTimeMillis() + (ttlSeconds * 1000L);
        } else {
            this.expiresAtMillis = -1L;
        }
    }

    public String getValue() {
        return value;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired() {
        return expiresAtMillis > 0 && System.currentTimeMillis() >= expiresAtMillis;
    }

    public long getRemainingTtlMillis() {
        if (expiresAtMillis < 0) {
            return -1L;
        }
        return Math.max(0L, expiresAtMillis - System.currentTimeMillis());
    }
}
