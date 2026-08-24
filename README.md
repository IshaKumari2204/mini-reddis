# mini-redis

A lightweight Java implementation of a Redis-inspired in-memory key-value store. This project demonstrates core Redis-style behaviors such as setting and getting values, deleting keys, expiration, TTL checks, and LRU-based eviction.

## Overview

`mini-redis` is a small educational project designed to simulate key parts of Redis in a minimal form:

- In-memory key/value storage
- Command parsing and execution
- Expiration tracking
- Time-to-live (TTL) queries
- LRU cache eviction for memory control
- Lightweight network server with client handling

The project is organized as a Maven Java application and includes JUnit tests covering the storage, cache, and command behavior.

## Project Structure

```text
mini-redis/
├── pom.xml
├── README.md
├── .gitignore
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
│   │   ├── eviction/
│   │   │   └── LRUCache.java
│   │   ├── expiration/
│   │   │   └── ExpiryManager.java
│   │   ├── server/
│   │   │   ├── RedisServer.java
│   │   │   └── ClientHandler.java
│   │   └── storage/
│   │       ├── KeyValueStore.java
│   │       └── ValueEntry.java
│   └── test/java/miniredis/
│       ├── CommandProcessorTest.java
│       ├── KeyValueStoreTest.java
│       └── LRUCacheTest.java
└── target/
```

## Main Components

### Storage
- `KeyValueStore`: manages key-value entries and underlying metadata
- `ValueEntry`: stores a value along with expiration and metadata information

### Commands
- `SetCommand`: assigns a key to a value
- `GetCommand`: retrieves a value if it exists and is not expired
- `DeleteCommand`: removes a key from storage
- `ExpireCommand`: sets a time-to-live on an existing key
- `TtlCommand`: returns remaining time until expiry
- `CommandProcessor`: dispatches commands from parsed input

### Expiration and Eviction
- `ExpiryManager`: tracks keys that should expire and removes expired entries
- `LRUCache`: provides least-recently-used eviction for memory pressure scenarios

### Server
- `RedisServer`: starts the service and listens for incoming client connections
- `ClientHandler`: handles per-client command communication

## Supported Behavior

The project is intended to support Redis-like operations such as:

- `SET key value`
- `GET key`
- `DEL key`
- `EXPIRE key seconds`
- `TTL key`

Behavior typically follows these patterns:

- expired keys are not returned by `GET`
- `TTL` reports the remaining lifetime of a key
- deleted keys are removed from storage
- cache entries are evicted using an LRU policy when needed

## Prerequisites

Make sure your machine has:

- Java JDK 17 or newer
- Maven installed and available on your `PATH`

Check installation:

```bash
java -version
mvn -version
```

## Running the Project

From the project root:

```bash
mvn clean test
```

To run the main application:

```bash
mvn exec:java -Dexec.mainClass=miniredis.Main
```

If the project has not defined the `exec-maven-plugin` yet, you may need to add it to `pom.xml` or run the application via your IDE.

## Development Notes

This project is a good starting point for learning:

- Java project structure and package organization
- command design with a processor pattern
- in-memory expiration logic
- cache eviction strategies
- test-driven development with JUnit

## Example Usage

Once the server or application is started, you may interact with commands similar to:

```text
SET name alice
GET name
TTL name
EXPIRE name 30
DEL name
```

Example output:

```text
OK
alice
-1
1
1
```

Exact output depends on the implementation details in the command classes and test cases.

## Troubleshooting

If you see issues while building:

1. Ensure Java is installed correctly.
2. Ensure Maven is installed and on your `PATH`.
3. Run:

```bash
mvn clean test
```

4. Check for Java version compatibility in `pom.xml`.

## License

This project is intended for learning and experimentation.
