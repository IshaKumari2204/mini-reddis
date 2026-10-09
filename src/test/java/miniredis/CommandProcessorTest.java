package miniredis;

import miniredis.command.CommandProcessor;
import miniredis.storage.KeyValueStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandProcessorTest {

    @Test
    void shouldProcessSetGetDeleteAndExpireCommands() {
        KeyValueStore store = new KeyValueStore();
        CommandProcessor processor = new CommandProcessor();

        assertEquals("OK", processor.process("SET name alice", store));
        assertEquals("alice", processor.process("GET name", store));
        assertEquals("1", processor.process("DEL name", store));
        assertEquals("(nil)", processor.process("GET name", store));

        assertEquals("OK", processor.process("SET session token EX 10", store));
        String ttlValue = processor.process("TTL session", store);
        assertTrue(Long.parseLong(ttlValue) > 0);
        assertEquals("1", processor.process("EXPIRE session 5", store));
    }

    @Test
    void shouldHandleCommandErrors() {
        KeyValueStore store = new KeyValueStore();
        CommandProcessor processor = new CommandProcessor();

        assertEquals("ERR unknown command", processor.process("UNKNOWN key", store));
        assertEquals("ERR wrong number of arguments for 'set' command", processor.process("SET keyOnly", store));
        assertEquals("ERR wrong number of arguments for 'get' command", processor.process("GET", store));
        assertEquals("ERR syntax error", processor.process("SET key val EX", store));
        assertEquals("ERR value is not an integer or out of range", processor.process("EXPIRE key abc", store));
    }
}
