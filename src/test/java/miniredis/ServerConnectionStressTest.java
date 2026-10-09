package miniredis;

import miniredis.server.RedisServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConnectionStressTest {

    private RedisServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        // Pick an available ephemeral port
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        server = new RedisServer(port);
        // Start server in a background thread so test can connect to it
        Thread serverThread = new Thread(() -> server.start(), "test-redis-server");
        serverThread.setDaemon(true);
        serverThread.start();

        // Wait brief moment for server socket to bind
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void shouldHandleConcurrentClientConnectionsAndRequests() throws InterruptedException {
        int totalClients = 1000; // 1,000 concurrent TCP client connections
        ExecutorService executor = Executors.newFixedThreadPool(200);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(totalClients);

        AtomicInteger successfulReqs = new AtomicInteger(0);
        AtomicInteger failedReqs = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try {
                    startSignal.await(); // Synchronize all clients to start simultaneously

                    try (Socket socket = new Socket("localhost", port);
                         PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                        String key = "stress_key_" + clientId;
                        String val = "stress_val_" + clientId;

                        // 1. Send SET
                        out.println("SET " + key + " " + val);
                        String setResp = in.readLine();
                        if ("OK".equals(setResp)) {
                            successfulReqs.incrementAndGet();
                        } else {
                            failedReqs.incrementAndGet();
                        }

                        // 2. Send GET
                        out.println("GET " + key);
                        String getResp = in.readLine();
                        if (val.equals(getResp)) {
                            successfulReqs.incrementAndGet();
                        } else {
                            failedReqs.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failedReqs.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Release all client threads simultaneously
        startSignal.countDown();
        doneSignal.await();
        long duration = System.currentTimeMillis() - startTime;

        executor.shutdown();

        int expectedSuccess = totalClients * 2;
        System.out.println("==================================================");
        System.out.println("STRESS TEST RESULTS:");
        System.out.println("Total Concurrent TCP Connections: " + totalClients);
        System.out.println("Total Commands Executed:          " + (totalClients * 2));
        System.out.println("Successful Operations:            " + successfulReqs.get() + " / " + expectedSuccess);
        System.out.println("Failed Operations:                " + failedReqs.get());
        System.out.println("Total Time:                       " + duration + " ms");
        System.out.println("Throughput:                       " + String.format("%.2f", (expectedSuccess * 1000.0) / duration) + " req/sec");
        System.out.println("==================================================");

        assertEquals(0, failedReqs.get(), "No client requests should fail under concurrent load");
        assertEquals(expectedSuccess, successfulReqs.get());
        assertTrue(duration < 10000, "200 concurrent connections should complete in under 10 seconds");
    }
}
