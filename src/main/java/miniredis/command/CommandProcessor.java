package miniredis.command;

import miniredis.storage.KeyValueStore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CommandProcessor {
    private final Map<String, Command> commands = new HashMap<>();

    public CommandProcessor() {
        register("SET", new SetCommand());
        register("GET", new GetCommand());
        register("DEL", new DeleteCommand());
        register("EXPIRE", new ExpireCommand());
        register("TTL", new TtlCommand());
    }

    public void register(String commandName, Command command) {
        commands.put(commandName.toUpperCase(Locale.ROOT), command);
    }

    public String process(String input, KeyValueStore store) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        String[] parts = input.trim().split("\\s+");
        String commandName = parts[0].toUpperCase(Locale.ROOT);
        Command command = commands.get(commandName);
        if (command == null) {
            return "ERR unknown command";
        }

        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        return command.execute(store, args);
    }
}
