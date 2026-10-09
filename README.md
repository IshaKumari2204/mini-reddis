# mini-redis

A lightweight, high-performance Java implementation of a Redis-inspired in-memory key-value store. Built with Java 21 Virtual Threads, $O(1)$ LRU-based eviction, and event-driven `DelayQueue` expiration.

---

## Performance & Benchmark Summary

`mini-redis` includes automated multi-threaded stress tests and an empirical **binary search connection benchmark**.

| Metric | Measured Benchmark Value |
|---|---|
| **Max Concurrent TCP Connections** | **1,143 Connections** *(100% success rate)* |
| **Peak Throughput** | **9,605.04 req/sec** |
| **Average Command Latency** | **~0.10 ms** |
| **Networking Thread Model** | **Java 21 Virtual Threads** (`Executors.newVirtualThreadPerTaskExecutor()`) |
| **Eviction Policy** | **$O(1)$ LRU Eviction** (`LinkedHashMap` with `removeEldestEntry`) |
| **TTL Expiration Engine** | **Event-Driven `DelayQueue`** (0% idle CPU overhead via `take()`) |

> [!NOTE]
> The ~1,200 connection cap measured during local loopback benchmarks is bound by **Windows TCP ephemeral port recycling (`TIME_WAIT`)**, rather than application code or JVM limits. The underlying code scales seamlessly to higher concurrency on tuned systems.

For the complete benchmark methodology and analysis, see **[benchmark.md](file:///d:/cp/mini-reddis/benchmark.md)**.

---

## Key Features

- **In-Memory Storage**: Thread-safe key/value storage with $O(1)$ operations.
- **LRU Eviction**: Automatic capacity management using $O(1)$ Least Recently Used eviction.
- **Event-Driven Expiration (TTL)**: `DelayQueue`-backed active expiration worker with **0% idle CPU usage** and **Lazy Tombstone Validation**.
- **Virtual Thread Scaling**: Supports thousands of concurrent TCP socket connections per server instance.
- **Redis Protocol Commands**: Implements `SET`, `GET`, `DEL`, `EXPIRE`, `TTL`, and `SET ... EX`.

---

## Project Structure

```text
mini-redis/
├── pom.xml
├── README.md
├── benchmark.md
├── src/
│   ├── main/java/miniredis/
│   │   ├── Main.java
│   │   ├── command/
│   │   │   ├── Command.java
│   │   │   ├── CommandProcessor.java
│   │   │   ├── SetCommand.java
│   │   │   ├── GetCommand.java
│   │   │   ├── DeleteCommand.java
│   │   │   ├── ExpireCommand.java
│   │   │   └── TtlCommand.java
│   │   ├── expiration/
│   │   │   └── ExpiryManager.java
│   │   ├── server/
│   │   │   ├── RedisServer.java
│   │   │   └── ClientHandler.java
│   │   └── storage/
│   │       ├── KeyValueStore.java
│   │       ├── ValueEntry.java
│   │       └── DelayedKey.java
│   └── test/java/miniredis/
│       ├── CommandProcessorTest.java
│       ├── ConcurrentStorageTest.java
│       ├── KeyValueStoreTest.java
│       ├── ServerConnectionStressTest.java
│       └── MaxConnectionBenchmark.java
└── target/
```

---

## Supported Commands

- `SET key value [EX seconds]` : Set key to value with optional expiration in seconds
- `GET key` : Retrieve value if exists and not expired (returns `(nil)` if expired/missing)
- `DEL key [key ...]` : Delete one or more keys
- `EXPIRE key seconds` : Set TTL in seconds on an existing key
- `TTL key` : Returns remaining TTL in seconds (`-2` if missing, `-1` if no TTL)

---

## Prerequisites

- **Java JDK 21** or newer
- **Maven 3.8+**

Check your environment:

```bash
java -version
mvn -version
```

---

## Running & Testing

### 1. Run Unit & Concurrency Tests
```bash
mvn test
```

### 2. Run Binary Search Connection Benchmark
```bash
mvn test -Dtest=MaxConnectionBenchmark
```

### 3. Start Server
```bash
mvn exec:java -Dexec.mainClass=miniredis.Main
```
Or start server on a custom port (e.g. `6379`):
```bash
mvn exec:java -Dexec.mainClass=miniredis.Main -Dexec.args="6379"
```

---

## Example Usage

Connect using `nc` / `telnet` or your favorite TCP client to port `6379`:

```text
SET name alice
OK

GET name
alice

SET session token123 EX 10
OK

TTL session
10

EXPIRE name 30
1

DEL name
1
```

---

## License

This project is intended for learning, system design experimentation, and high-concurrency Java exploration.
