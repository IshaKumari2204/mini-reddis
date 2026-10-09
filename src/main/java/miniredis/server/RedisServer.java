package miniredis.server;

import miniredis.command.CommandProcessor;
import miniredis.storage.KeyValueStore;
import miniredis.expiration.ExpiryManager;

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
    private final ExpiryManager expiryManager;

    public RedisServer(int port) {
        this.port = port;
        this.store = new KeyValueStore();
        this.commandProcessor = new CommandProcessor();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        this.expiryManager = new ExpiryManager(this.store);
    }

    public void start() {
        boolean started = false;
        try {
            serverSocket = new ServerSocket(port, 1024);
            running = true;
            started = true;
            System.out.println("mini-redis server listening on port " + port);
            expiryManager.start();

            while (running) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(new ClientHandler(clientSocket, store, commandProcessor));
            }
        } catch (IOException e) {
            if (!started) {
                System.err.println("Failed to start server on port " + port + ": " + e.getMessage());
                System.exit(1);
            } else if (running) {
                throw new RuntimeException("Server error", e);
            }
        }
    }

    public void stop() {
        running = false;
        expiryManager.stop();
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
