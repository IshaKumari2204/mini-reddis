package miniredis.command;

import miniredis.storage.KeyValueStore;

public class ExpireCommand implements Command {
    @Override
    public String execute(KeyValueStore store, String[] args) {
        if (args == null || args.length != 2) {
            return "ERR wrong number of arguments for 'expire' command";
        }

        String key = args[0];
        try {
            long ttlSeconds = Long.parseLong(args[1]);
            return store.expire(key, ttlSeconds) ? "1" : "0";
        } catch (NumberFormatException e) {
            return "ERR value is not an integer or out of range";
        }
    }
}
