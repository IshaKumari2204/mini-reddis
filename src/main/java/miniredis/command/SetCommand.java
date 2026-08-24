package miniredis.command;

import miniredis.storage.KeyValueStore;

public class SetCommand implements Command {
    @Override
    public String execute(KeyValueStore store, String[] args) {
        if (args == null || args.length < 2) {
            return "ERR wrong number of arguments for 'set' command";
        }

        String key = args[0];
        String value = args[1];
        long ttlSeconds = -1L;

        for (int i = 2; i < args.length; i += 2) {
            if (i + 1 >= args.length) {
                return "ERR syntax error";
            }
            String option = args[i].toUpperCase();
            try {
                if ("EX".equals(option)) {
                    ttlSeconds = Long.parseLong(args[i + 1]);
                } else {
                    return "ERR unsupported option";
                }
            } catch (NumberFormatException e) {
                return "ERR value is not an integer or out of range";
            }
        }

        if (ttlSeconds > 0) {
            store.put(key, value, ttlSeconds);
        } else {
            store.put(key, value);
        }
        return "OK";
    }
}
