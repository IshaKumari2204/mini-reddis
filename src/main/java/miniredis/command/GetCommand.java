package miniredis.command;

import miniredis.storage.KeyValueStore;

public class GetCommand implements Command {
    @Override
    public String execute(KeyValueStore store, String[] args) {
        if (args == null || args.length != 1) {
            return "ERR wrong number of arguments for 'get' command";
        }

        String value = store.get(args[0]);
        if (value == null) {
            return "(nil)";
        }
        return value;
    }
}
