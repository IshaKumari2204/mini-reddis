package miniredis.server;

import miniredis.command.CommandProcessor;
import miniredis.storage.KeyValueStore;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisServer {
    private final int port;
    private final KeyValueStore store;
    private final CommandProcessor commandProcessor;
    private final ExecutorService executorService;
    private ServerSocket serverSocket;
    private boolean running;

    public RedisServer(int port) {
        this.port = port;
        this.store = new KeyValueStore();
        this.commandProcessor = new CommandProcessor();
        this.executorService = Executors.newCachedThreadPool();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("mini-redis server listening on port " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(new ClientHandler(clientSocket, store, commandProcessor));
            }
        } catch (IOException e) {
            if (running) {
                throw new RuntimeException("Failed to start Redis server", e);
            }
        }
    }

    public void stop() {
        running = false;
        executorService.shutdownNow();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // no-op
        }
    }
}
