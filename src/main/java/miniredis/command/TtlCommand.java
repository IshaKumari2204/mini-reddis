package miniredis.command;

import miniredis.storage.KeyValueStore;

public class TtlCommand implements Command {
    @Override
    public String execute(KeyValueStore store, String[] args) {
        if (args == null || args.length != 1) {
            return "ERR wrong number of arguments for 'ttl' command";
        }

        return String.valueOf(store.ttl(args[0]));
    }
}
