package miniredis.command;

import miniredis.storage.KeyValueStore;

public class DeleteCommand implements Command {
    @Override
    public String execute(KeyValueStore store, String[] args) {
        if (args == null || args.length == 0) {
            return "ERR wrong number of arguments for 'del' command";
        }
        return String.valueOf(store.deleteMultiple(args));
    }
}
