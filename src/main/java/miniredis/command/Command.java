package miniredis.command;

import miniredis.storage.KeyValueStore;

public interface Command {
    String execute(KeyValueStore store, String[] args);
}
