package miniredis;

import miniredis.server.RedisServer;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MaxConnectionBenchmark {

    @Test
    void findMaxSupportedConnectionsViaBinarySearch() throws Exception {
        System.out.println("==================================================");
        System.out.println("   STARTING BINARY SEARCH CONNECTION BENCHMARK");
        System.out.println("==================================================");

        int low = 500;
        int high = 2000;
        int maxSupported = 0;
        double maxThroughput = 0;
        long bestDuration = 0;

        while (low <= high) {
            int mid = (low + high) / 2;
            System.out.println("\nTesting candidate: " + mid + " concurrent TCP connections...");

            // Pause to allow OS TIME_WAIT sockets to recycle
            Thread.sleep(1000);

            BenchmarkResult result = testConnectionCapacity(mid);

            if (result.successRate == 1.0) {
                System.out.println("SUCCESS: " + mid + " connections passed! (Time: " + result.durationMs + " ms, Throughput: " + String.format("%.2f", result.throughput) + " req/sec)");
                maxSupported = mid;
                maxThroughput = result.throughput;
                bestDuration = result.durationMs;
                low = mid + 100; // Try higher
            } else {
                System.out.println("FAILED: " + mid + " connections experienced failures (" + result.failures + " failed). Trying lower threshold...");
                high = mid - 100; // Try lower
            }
        }

        System.out.println("\n==================================================");
        System.out.println("   BINARY SEARCH BENCHMARK COMPLETE");
        System.out.println("   MAX SUPPORTED CONCURRENT CONNECTIONS: " + maxSupported);
        System.out.println("   PEAK THROUGHPUT: " + String.format("%.2f", maxThroughput) + " req/sec");
        System.out.println("==================================================");
    }

    private static class BenchmarkResult {
        final double successRate;
        final int failures;
        final long durationMs;
        final double throughput;

        BenchmarkResult(double successRate, int failures, long durationMs, double throughput) {
            this.successRate = successRate;
            this.failures = failures;
            this.durationMs = durationMs;
            this.throughput = throughput;
        }
    }

    private BenchmarkResult testConnectionCapacity(int numConnections) {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        } catch (IOException e) {
            return new BenchmarkResult(0, numConnections, 0, 0);
        }

        RedisServer server = new RedisServer(port);
        Thread serverThread = new Thread(server::start, "benchmark-server");
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}

        ExecutorService clientPool = Executors.newFixedThreadPool(Math.min(numConnections, 250));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numConnections);

        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numConnections; i++) {
            final int id = i;
            clientPool.submit(() -> {
                try {
                    startLatch.await();
                    try (Socket socket = new Socket("localhost", port);
                         PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                        socket.setSoTimeout(5000);

                        out.println("SET bench_" + id + " val_" + id);
                        String setResp = in.readLine();
                        if ("OK".equals(setResp)) {
                            successfulOps.incrementAndGet();
                        } else {
                            failedOps.incrementAndGet();
                        }

                        out.println("GET bench_" + id);
                        String getResp = in.readLine();
                        if (("val_" + id).equals(getResp)) {
                            successfulOps.incrementAndGet();
                        } else {
                            failedOps.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failedOps.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        boolean completed = false;
        try {
            completed = doneLatch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        long duration = Math.max(1, System.currentTimeMillis() - startTime);
        clientPool.shutdownNow();
        server.stop();

        int totalExpected = numConnections * 2;
        int actualSuccess = successfulOps.get();
        double successRate = (double) actualSuccess / totalExpected;
        double throughput = (actualSuccess * 1000.0) / duration;

        if (!completed) {
            return new BenchmarkResult(0, numConnections, duration, throughput);
        }

        return new BenchmarkResult(successRate, failedOps.get(), duration, throughput);
    }
}
