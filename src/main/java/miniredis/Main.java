package miniredis;

import miniredis.server.RedisServer;

public class Main {
    public static void main(String[] args) {
        int port = 6379;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                System.err.println("Invalid port, defaulting to 6379");
            }
        }

        RedisServer server = new RedisServer(port);
        server.start();
    }
}
