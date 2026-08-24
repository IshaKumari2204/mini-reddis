package miniredis.server;

import miniredis.command.CommandProcessor;
import miniredis.storage.KeyValueStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final KeyValueStore store;
    private final CommandProcessor commandProcessor;

    public ClientHandler(Socket socket, KeyValueStore store, CommandProcessor commandProcessor) {
        this.socket = socket;
        this.store = store;
        this.commandProcessor = commandProcessor;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String response = commandProcessor.process(line, store);
                writer.println(response);
            }
        } catch (IOException e) {
            System.err.println("Client disconnected: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // no-op
            }
        }
    }
}
