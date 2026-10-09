# mini-redis — Full Project Workflow & Documentation

> A lightweight, Redis-compatible in-memory key-value server written in **Java 17**, built from scratch using only the JDK standard library (no external runtime dependencies).

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Project Structure](#2-project-structure)
3. [Architecture Diagram](#3-architecture-diagram)
4. [Package-by-Package Breakdown](#4-package-by-package-breakdown)
   - [Entry Point — `Main.java`](#entry-point--mainjava)
   - [Server Layer — `server/`](#server-layer--server)
   - [Command Layer — `command/`](#command-layer--command)
   - [Storage Layer — `storage/`](#storage-layer--storage)
   - [Expiration — `expiration/`](#expiration--expiration)
   - [Eviction — `eviction/`](#eviction--eviction)
5. [End-to-End Request Workflow](#5-end-to-end-request-workflow)
6. [Supported Commands](#6-supported-commands)
7. [Expiry Mechanics](#7-expiry-mechanics)
8. [Concurrency Model](#8-concurrency-model)
9. [LRU Cache (Standalone Utility)](#9-lru-cache-standalone-utility)
10. [Build & Run](#10-build--run)
11. [Design Decisions & Trade-offs](#11-design-decisions--trade-offs)

---

## 1. Project Overview

**mini-redis** is an educational, single-process Redis-like server that:

- Listens on a TCP socket (default port **6379** — same as real Redis).
- Accepts plain-text line-based commands (one command per line).
- Supports a subset of Redis commands: `SET`, `GET`, `DEL`, `EXPIRE`, `TTL`.
- Handles multiple clients simultaneously using a cached thread pool.
- Automatically purges expired keys in the background every second.
- Ships with an LRU eviction structure (currently standalone/utility, not yet wired into the main store).

**Tech stack:**
| Aspect | Choice |
|---|---|
| Language | Java 17 |
| Build tool | Maven 3 |
| Runtime dependencies | None (pure JDK) |
| Test framework | JUnit Jupiter 5.10.2 |
| Protocol | Plain-text, line-delimited (simplified Redis-like) |

---

## 2. Project Structure

```
mini-reddis/
├── pom.xml                          ← Maven build descriptor
├── README.md
└── src/
    └── main/
        └── java/
            └── miniredis/
                ├── Main.java                        ← Entry point
                ├── server/
                │   ├── RedisServer.java             ← TCP listener & thread pool
                │   └── ClientHandler.java           ← Per-client I/O loop (Runnable)
                ├── command/
                │   ├── Command.java                 ← Command interface
                │   ├── CommandProcessor.java        ← Command registry & dispatcher
                │   ├── SetCommand.java              ← SET [EX seconds]
                │   ├── GetCommand.java              ← GET
                │   ├── DeleteCommand.java           ← DEL (multi-key)
                │   ├── ExpireCommand.java           ← EXPIRE
                │   └── TtlCommand.java              ← TTL
                ├── storage/
                │   ├── KeyValueStore.java           ← Thread-safe in-memory store
                │   └── ValueEntry.java              ← Value wrapper with TTL metadata
                ├── expiration/
                │   └── ExpiryManager.java           ← Background cleanup daemon
                └── eviction/
                    └── LRUCache.java                ← Generic LRU cache utility
```

---

## 3. Architecture Diagram

```
                        ┌─────────────────────────────┐
                        │          Main.java           │
                        │  Reads port → new RedisServer│
                        └──────────────┬──────────────┘
                                       │ server.start()
                                       ▼
                        ┌─────────────────────────────┐
                        │        RedisServer           │
                        │  ServerSocket on port 6379   │
                        │  ExecutorService (cached)    │
                        └────────────┬────────────────┘
                    accept() loop    │   one thread per client
              ┌──────────────────────┤
              ▼                      ▼
  ┌──────────────────┐    ┌──────────────────┐
  │  ClientHandler   │    │  ClientHandler   │  ... (N clients)
  │  (Runnable)      │    │  (Runnable)      │
  │  BufferedReader  │    │  BufferedReader  │
  │  PrintWriter     │    │  PrintWriter     │
  └────────┬─────────┘    └────────┬─────────┘
           │ process(line, store)  │
           ▼                       ▼
  ┌──────────────────────────────────────────┐
  │           CommandProcessor               │
  │  HashMap<String, Command>               │
  │  SET → SetCommand                        │
  │  GET → GetCommand                        │
  │  DEL → DeleteCommand                     │
  │  EXPIRE → ExpireCommand                  │
  │  TTL → TtlCommand                        │
  └────────────────────┬─────────────────────┘
                       │ command.execute(store, args)
                       ▼
  ┌──────────────────────────────────────────┐
  │            KeyValueStore                 │
  │  ConcurrentHashMap<String, ValueEntry>  │
  │  put / get / delete / expire / ttl      │
  └───────────────────────┬──────────────────┘
                          │ isExpired() check (lazy)
                          ▼
  ┌──────────────────────────────────────────┐
  │             ValueEntry                   │
  │  value: String                           │
  │  expiresAtMillis: long (-1 = no expiry)  │
  │  isExpired() / getRemainingTtlMillis()   │
  └──────────────────────────────────────────┘

  Background daemon (ExpiryManager) — every 1 second:
  ┌──────────────────────────────────────────┐
  │         ExpiryManager                    │
  │  Thread: "mini-redis-expiry-cleaner"     │
  │  Calls store.removeExpired() periodically│
  └──────────────────────────────────────────┘
```

---

## 4. Package-by-Package Breakdown

### Entry Point — `Main.java`

```java
public static void main(String[] args) {
    int port = 6379;                   // Default Redis port
    // Optional: first CLI arg overrides port
    RedisServer server = new RedisServer(port);
    server.start();                    // Blocks forever (accept loop)
}
```

**Responsibilities:**
- Reads an optional port number from the command-line argument.
- Falls back to port **6379** on invalid input.
- Instantiates `RedisServer` and starts it (blocking call).

---

### Server Layer — `server/`

#### `RedisServer.java`

The core TCP server. Owns three shared, long-lived objects:

| Field | Type | Role |
|---|---|---|
| `store` | `KeyValueStore` | Shared in-memory database (one instance for all clients) |
| `commandProcessor` | `CommandProcessor` | Shared, stateless command registry |
| `executorService` | `ExecutorService` | `newCachedThreadPool()` — spawns a thread per client |

**`start()` flow:**
1. Opens a `ServerSocket` on the configured port.
2. Enters a blocking `accept()` loop.
3. For every incoming `Socket`, wraps it in a `ClientHandler` and submits to the thread pool.
4. The server thread itself stays in the accept loop — never handles I/O directly.

**`stop()` flow:**
1. Sets `running = false`.
2. Shuts down the executor service immediately (`shutdownNow()`).
3. Closes the `ServerSocket`, which unblocks the `accept()` call.

#### `ClientHandler.java`

Implements `Runnable`. One instance per connected client.

**`run()` flow:**
1. Wraps the client socket's streams in a `BufferedReader` (UTF-8 input) and a `PrintWriter` (UTF-8 output, auto-flush).
2. Enters a read loop: `readLine()` → blocks until the client sends a line.
3. Passes each line to `CommandProcessor.process(line, store)`.
4. Writes the returned response string back with `println()`.
5. Exits when the client disconnects (`readLine()` returns `null`) or on `IOException`.
6. Always closes the socket in `finally`.

> **Protocol:** One command per line (whitespace-delimited tokens). Responses are single lines. This is a simplified version of the real Redis Serialization Protocol (RESP).

---

### Command Layer — `command/`

#### `Command.java` — Interface

```java
public interface Command {
    String execute(KeyValueStore store, String[] args);
}
```

A single-method interface. Every command receives the shared store and the parsed argument tokens, and returns a string response.

#### `CommandProcessor.java` — Registry & Dispatcher

- Maintains a `HashMap<String, Command>` — the command registry.
- On construction, registers all five built-in commands.
- Exposes `register(name, command)` for extensibility (add new commands at runtime).
- `process(input, store)`:
  1. Trims and splits the input line by whitespace.
  2. Uppercases the first token as the command name.
  3. Looks up the command in the map.
  4. Returns `"ERR unknown command"` if not found.
  5. Slices off `args[1..]` and calls `command.execute(store, args)`.

#### `SetCommand.java`

Syntax: `SET <key> <value> [EX <seconds>]`

- Validates at least 2 arguments (key + value).
- Iterates optional flag pairs (`EX <n>`):
  - Parses `EX` to extract a TTL in seconds.
  - Returns `"ERR syntax error"` on odd remaining args.
  - Returns `"ERR unsupported option"` for unknown flags.
- Calls `store.put(key, value)` or `store.put(key, value, ttlSeconds)`.
- Returns `"OK"` on success.

#### `GetCommand.java`

Syntax: `GET <key>`

- Validates exactly 1 argument.
- Calls `store.get(key)`.
- Returns the string value, or `"(nil)"` if key doesn't exist or is expired.

#### `DeleteCommand.java`

Syntax: `DEL <key> [<key2> ...]`

- Validates at least 1 argument.
- Calls `store.deleteMultiple(args)`.
- Returns the **count** of keys actually deleted (as a string integer).

#### `ExpireCommand.java`

Syntax: `EXPIRE <key> <seconds>`

- Validates exactly 2 arguments.
- Calls `store.expire(key, ttlSeconds)`.
- Returns `"1"` if the expiry was set successfully, `"0"` if the key doesn't exist.

#### `TtlCommand.java`

Syntax: `TTL <key>`

- Validates exactly 1 argument.
- Calls `store.ttl(key)`.
- Returns:
  - `"-2"` — key does not exist or is already expired.
  - `"-1"` — key exists but has no expiry.
  - `"N"` — remaining TTL in whole seconds (N ≥ 0).

---

### Storage Layer — `storage/`

#### `ValueEntry.java`

An **immutable** wrapper around a stored value.

| Field | Type | Meaning |
|---|---|---|
| `value` | `String` | The stored string |
| `expiresAtMillis` | `long` | Absolute expiry timestamp in ms epoch; `-1` = no expiry |

**Key methods:**

```java
boolean isExpired()
// → true if expiresAtMillis > 0 AND System.currentTimeMillis() >= expiresAtMillis

long getRemainingTtlMillis()
// → max(0, expiresAtMillis - now)  or  -1 if no expiry
```

Since `ValueEntry` is immutable, updating a key's TTL always creates a **new** `ValueEntry` object.

#### `KeyValueStore.java`

The central, thread-safe in-memory store. Backed by a `ConcurrentHashMap<String, ValueEntry>`.

| Method | Behaviour |
|---|---|
| `put(key, value)` | Stores key with no expiry |
| `put(key, value, ttlSeconds)` | Stores key with TTL |
| `get(key)` | Returns value or `null`; performs **lazy expiry** — removes key in-place if expired |
| `delete(key)` | Removes single key, returns `true` if existed |
| `deleteMultiple(keys...)` | Removes multiple keys, returns count removed |
| `expire(key, ttl)` | Updates TTL on existing key; removes key if `ttl <= 0`; returns `false` if key missing |
| `ttl(key)` | Returns remaining seconds, or `-1` (no expiry), or `-2` (missing/expired) |
| `removeExpired()` | **Active sweep** — iterates all entries and removes expired ones (called by `ExpiryManager`) |
| `keySet()` | Snapshot of current keys |
| `size()` | Current entry count |
| `clear()` | Wipes all data |

**Expiry is handled with a dual strategy:**
- **Lazy expiry:** Every `get()` and `ttl()` call checks `isExpired()` and removes on the spot.
- **Active expiry:** `ExpiryManager` periodically sweeps the entire map to evict keys that were never accessed after expiry.

---

### Expiration — `expiration/`

#### `ExpiryManager.java`

A background daemon thread that periodically calls `store.removeExpired()`.

```
ExpiryManager
  └─ Thread: "mini-redis-expiry-cleaner"
       └─ loop: sleep(intervalMillis) → store.removeExpired()
```

| Parameter | Default |
|---|---|
| `intervalMillis` | 1000 ms (1 second); minimum clamped to 50 ms |

**Lifecycle:**
- `start()` — uses `AtomicBoolean` to ensure the cleaner thread is only started once (idempotent).
- `stop()` — sets running flag to `false` and interrupts the thread, which catches `InterruptedException` and exits cleanly.

> **Note:** `ExpiryManager` is implemented and ready but is not wired into `RedisServer` in the current codebase. It must be explicitly started if desired. The store still works correctly without it via lazy expiry — the `ExpiryManager` just ensures memory is reclaimed for keys that are never accessed after expiry.

---

### Eviction — `eviction/`

#### `LRUCache.java`

A generic **Least Recently Used** cache built on top of Java's `LinkedHashMap` in access-order mode.

```java
new LinkedHashMap<K, V>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;  // auto-evict oldest on overflow
    }
}
```

| Method | Description |
|---|---|
| `get(key)` | Returns value; promotes key to "most recently used" |
| `put(key, value)` | Inserts/updates; auto-evicts LRU entry if over capacity |
| `remove(key)` | Explicit removal |
| `containsKey(key)` | Membership check |
| `size()` | Current count |
| `keySet()` | Set of all keys |
| `clear()` | Wipe all |

> **Note:** `LRUCache` is a standalone utility class. It is **not thread-safe** (no synchronization). It is not currently connected to `KeyValueStore` — it exists as a building block for adding a maximum-capacity eviction policy in a future iteration.

---

## 5. End-to-End Request Workflow

Below is a step-by-step trace of what happens when a client sends `SET foo bar EX 60`:

```
1. CLIENT ──────────────────────────────────────────────────────────────────
   Sends TCP data: "SET foo bar EX 60\n"

2. ClientHandler.run()
   BufferedReader.readLine() → "SET foo bar EX 60"

3. CommandProcessor.process("SET foo bar EX 60", store)
   a. Trim + split by whitespace → ["SET", "foo", "bar", "EX", "60"]
   b. commandName = "SET"
   c. args = ["foo", "bar", "EX", "60"]
   d. command = commands.get("SET") → SetCommand instance

4. SetCommand.execute(store, ["foo", "bar", "EX", "60"])
   a. key = "foo", value = "bar", ttlSeconds = -1
   b. Loop i=2: option = "EX", ttlSeconds = 60
   c. ttlSeconds > 0 → store.put("foo", "bar", 60)

5. KeyValueStore.put("foo", "bar", 60)
   a. new ValueEntry("bar", 60)
      → expiresAtMillis = System.currentTimeMillis() + 60_000
   b. store.put("foo", entry)  [ConcurrentHashMap]

6. SetCommand returns "OK"
   CommandProcessor returns "OK"

7. ClientHandler
   PrintWriter.println("OK") → TCP response to client

8. CLIENT ──────────────────────────────────────────────────────────────────
   Receives: "OK\n"
```

---

Later, the client sends `GET foo` before expiry:

```
GetCommand.execute(store, ["foo"])
  → store.get("foo")
    → entry = store.get("foo")            // ConcurrentHashMap.get
    → entry.isExpired() → false           // 60s haven't passed
    → return entry.getValue() → "bar"
  → response: "bar"
```

After 60 seconds, `GET foo` returns `"(nil)"`:

```
store.get("foo")
  → entry.isExpired() → true             // currentTimeMillis >= expiresAtMillis
  → store.remove("foo")                  // lazy eviction
  → return null
→ GetCommand returns "(nil)"
```

---

## 6. Supported Commands

| Command | Syntax | Returns | Notes |
|---|---|---|---|
| `SET` | `SET key value [EX seconds]` | `OK` or `ERR ...` | `EX` option sets TTL in seconds |
| `GET` | `GET key` | `value` or `(nil)` | Returns `(nil)` for missing/expired keys |
| `DEL` | `DEL key [key2 ...]` | Integer count | Number of keys actually deleted |
| `EXPIRE` | `EXPIRE key seconds` | `1` or `0` | `1` = success, `0` = key not found |
| `TTL` | `TTL key` | Integer | `-2`=missing, `-1`=no expiry, else seconds remaining |

**Error responses:**
| Condition | Response |
|---|---|
| Unknown command | `ERR unknown command` |
| Wrong arg count | `ERR wrong number of arguments for '<cmd>' command` |
| Non-integer TTL | `ERR value is not an integer or out of range` |
| Bad option in SET | `ERR unsupported option` |
| Syntax error in SET | `ERR syntax error` |

---

## 7. Expiry Mechanics

The system uses a **dual-strategy expiry** pattern, identical to how Redis itself works:

### Lazy Expiry (Passive)
- Triggered on every `get()` and `ttl()` call.
- No background work needed; expired key is removed on first access attempt.
- Downside: orphaned memory for keys that are set to expire but never accessed again.

### Active Expiry (Background Sweep)
- `ExpiryManager` runs a dedicated daemon thread (`mini-redis-expiry-cleaner`).
- Every `intervalMillis` (default 1 second), it calls `KeyValueStore.removeExpired()`.
- `removeExpired()` iterates the entire `ConcurrentHashMap` using its iterator and removes expired entries safely.
- Mitigates the memory orphan problem from lazy-only expiry.

```
Time:   0s      1s      2s      3s ...
        │       │       │       │
SET x 5s│       │       │       │
        │  sweep│  sweep│  sweep│  sweep  ← ExpiryManager
        │       │       │       │
GET x   │ ✓ val │ ✓ val │ ✓ val │ ← lazy check: still alive if < 5s
        │       │       │       │
[5s expires]
GET x   → (nil)  ← lazy check removes it
                 ← next sweep also removes it if lazy check missed it
```

---

## 8. Concurrency Model

| Component | Thread(s) | Safety mechanism |
|---|---|---|
| `RedisServer.start()` | 1 main thread (accept loop) | Single-threaded accept |
| `ClientHandler.run()` | 1 thread per client (from pool) | Independent per-client state |
| `KeyValueStore` | Multiple client threads + expiry cleaner | `ConcurrentHashMap` — lock-striped, thread-safe |
| `ExpiryManager` | 1 background daemon thread | `AtomicBoolean` for start/stop; iterator-safe removal |
| `CommandProcessor` | Shared across all client threads | Stateless after construction — read-only map |

The `ExecutorService.newCachedThreadPool()` creates threads on demand and reuses idle ones, making it suitable for bursty workloads but unbounded under sustained high concurrency.

---

## 9. LRU Cache (Standalone Utility)

`LRUCache<K, V>` provides a fixed-capacity, access-ordered eviction structure:

- Built on `LinkedHashMap` with `accessOrder = true`.
- Overrides `removeEldestEntry()` to auto-evict the least recently used entry when `size > capacity`.
- Generic — can be used for any key/value types.
- **Not thread-safe** — callers must synchronize externally if used concurrently.

**Example use case (future integration):**
```java
// Could be used to cap KeyValueStore at N entries:
LRUCache<String, ValueEntry> lruStore = new LRUCache<>(10_000);
// When capacity is exceeded, the least recently accessed key is dropped automatically.
```

---

## 10. Build & Run

### Prerequisites
- Java 17+
- Maven 3.6+

### Build

```bash
# From project root
mvn clean package
```

Produces: `target/mini-redis-1.0.0.jar`

### Run

```bash
# Default port 6379
java -cp target/mini-redis-1.0.0.jar miniredis.Main

# Custom port
java -cp target/mini-redis-1.0.0.jar miniredis.Main 7379
```

### Test via Telnet or Netcat

```bash
# Linux/macOS
nc localhost 6379

# Windows (PowerShell)
telnet localhost 6379
```

Then type commands:
```
SET name Alice EX 30
OK
GET name
Alice
TTL name
29
EXPIRE name 5
1
DEL name
1
GET name
(nil)
```

### Run Tests

```bash
mvn test
```

---

## 11. Design Decisions & Trade-offs

| Decision | Rationale | Trade-off |
|---|---|---|
| Plain-text protocol (not RESP) | Simpler to implement and test with basic tools | Not compatible with `redis-cli` or standard Redis clients |
| `ConcurrentHashMap` for store | Lock-free reads, fine-grained locking for writes | Unbounded memory — no max-size enforcement without LRU integration |
| `newCachedThreadPool()` | Zero-latency thread creation for new clients | Thread count unbounded under load; should use bounded pool in production |
| Immutable `ValueEntry` | Avoids data races on TTL updates; simple reasoning | Creates a new object on every `EXPIRE` call |
| Dual expiry strategy | Combines speed (lazy) with completeness (active sweep) | Active sweep iterates full map — O(N); acceptable for small datasets |
| `LRUCache` not integrated | Decouples eviction concern from current implementation | Memory is unbounded; keys are never evicted for capacity reasons |
| No persistence | Keeps implementation simple and educational | All data is lost on restart (no RDB/AOF equivalent) |
| No authentication/TLS | Out of scope for educational project | Not suitable for production or internet-facing use |
