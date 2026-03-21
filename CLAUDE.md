# CLAUDE.md — AI Assistant Guide for kafka-connect-azure-blob-storage

This file provides context for AI assistants (Claude Code and similar tools) working on this repository.

---

## Project Overview

**kafka-connect-azure-blob-storage** is a Kafka Connect sink connector that streams records from Kafka topics into Azure Blob Storage. It supports multiple file formats (Parquet, Avro, JSON, ByteArray), configurable partitioning strategies, and compression codecs.

- **GroupId:** `io.coffeebeans.kafka.connect`
- **ArtifactId:** `kafka-connect-azure-blob-storage`
- **Current Version:** `1.0.1-SNAPSHOT`
- **Java Version:** 11
- **Build Tool:** Maven (use included wrapper `./mvnw`)

---

## Repository Structure

```
.
├── src/
│   ├── main/java/io/coffeebeans/connect/azure/blob/sink/
│   │   ├── AzureBlobSinkConnector.java       # Connector entry point
│   │   ├── AzureBlobSinkTask.java             # Sink task implementation
│   │   ├── AzureBlobSinkConnectorContext.java # Builder-based config holder
│   │   ├── TopicPartitionWriter.java          # Batching & rotation logic
│   │   ├── config/                            # Configuration definitions & validators
│   │   ├── exception/                         # Custom exceptions
│   │   ├── format/                            # Pluggable format writers
│   │   │   ├── parquet/
│   │   │   ├── avro/
│   │   │   ├── json/
│   │   │   └── bytearray/
│   │   ├── partitioner/                       # Partitioning strategies
│   │   │   ├── field/
│   │   │   └── time/
│   │   ├── storage/                           # Azure Blob Storage interaction
│   │   └── util/
│   ├── test/java/                             # Unit & integration tests
│   └── assembly/                              # Maven assembly descriptors
├── checkstyle/checkstyle.xml                  # Google Style + 120 char limit
├── docs/                                      # Docusaurus documentation site
├── quickstart/                                # Example configs and schemas
├── docker-compose.yml                         # Local dev environment
├── Dockerfile                                 # Connector Docker image
└── pom.xml
```

---

## Essential Commands

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run unit tests
./mvnw test

# Run with checkstyle validation
./mvnw verify -DskipTests

# Run integration tests (requires external infra)
./mvnw verify -P integration-test

# Build standalone assembly
./mvnw package -P standalone

# Start local dev environment (Kafka + Schema Registry + Azurite)
docker-compose up -d

# Run checkstyle only
./mvnw checkstyle:check
```

---

## Architecture & Key Design Patterns

### Core Flow

1. **`AzureBlobSinkConnector`** — Validates config and instantiates tasks.
2. **`AzureBlobSinkTask`** — Receives `SinkRecord` batches; delegates to `TopicPartitionWriter`.
3. **`TopicPartitionWriter`** — Buffers records per topic-partition; triggers rotation on flush size, record count, or time interval.
4. **`RecordWriterProvider`** — Selected based on `format` config; creates a `RecordWriter` for the target blob.
5. **`Partitioner`** — Determines the blob path prefix (Default, Time-based, Field-based).
6. **`AzureBlobStorageManager`** — Wraps Azure SDK calls using Project Reactor async primitives (`Mono`/`Flux`).

### Design Patterns in Use

| Pattern | Location |
|---|---|
| Provider / Factory | `RecordWriterProvider` implementations |
| Strategy | `Partitioner`, `TimestampExtractor` |
| Builder | `AzureBlobSinkConnectorContext` |
| Template Method | Abstract base classes in `format/` |

### Supported Formats

| Format | Class | File Extension |
|---|---|---|
| Parquet | `ParquetRecordWriter` | `.parquet` |
| Avro | `AvroRecordWriter` | `.avro` |
| JSON | `JsonRecordWriter` | `.json` |
| ByteArray | `ByteArrayRecordWriter` | `.bin` |

### Partitioning Strategies

| Strategy | Class | Path Example |
|---|---|---|
| DEFAULT | `DefaultPartitioner` | `topics/<topic>/<partition>/` |
| TIME | `TimePartitioner` | `topics/<topic>/year=YYYY/month=MM/day=DD/hour=HH/` |
| FIELD | `FieldPartitioner` | `topics/<topic>/<field-value>/` |

---

## Configuration Reference

Configuration is defined in `AzureBlobSinkConfig.java`. All properties use typed `ConfigDef` entries with validators.

Key configuration groups:

| Group | Prefix | Examples |
|---|---|---|
| Azure connection | `connection.string` | Azure Storage connection string |
| Container | `azblob.container.name` | Target blob container |
| Format | `format.class`, `format.bytearray.extension` | Output format |
| Compression | `az.compression.type` | `none`, `gzip`, `snappy` |
| Partitioner | `partitioner.class`, `path.format`, `timezone` | Partition strategy |
| Rotation | `flush.size`, `rotate.interval.ms`, `rotate.schedule.interval.ms` | Rotation triggers |
| Retry | `retry.type`, `retries`, `retry.backoff.ms` | Retry behaviour |
| Null values | `behavior.on.null.values` | `IGNORE` or `FAIL` |

---

## Code Conventions

### Style Enforcement

- **Checkstyle** is enforced at build time using Google Java Style (configured in `checkstyle/checkstyle.xml`).
- **Line length limit:** 120 characters.
- **No wildcard imports** — all imports must be explicit.
- **Import order:** Static imports first, then lexicographic order.

### Logging

```java
private static final Logger log = LoggerFactory.getLogger(ClassName.class);
```

Use SLF4J. Do not use `System.out.println`.

### Null Handling

Use explicit null checks. Null value behaviour is configurable via `NullValueBehavior` enum (`IGNORE` / `FAIL`).

### Exceptions

Throw from the `exception/` package:
- `BlobStorageException` — Azure storage failures
- `PartitionException` — Partitioning errors
- `SchemaParseException` — Schema parsing failures
- `UnsupportedOperationException` — Unsupported operations

### Adding a New Format

1. Create a package under `format/<format-name>/`.
2. Implement `RecordWriter` and `RecordWriterProvider`.
3. Add the format to the `Format` enum.
4. Add corresponding codec validators in `config/validators/format/` if needed.
5. Register the provider in `AzureBlobSinkTask`.
6. Add unit tests in `src/test/java/.../format/<format-name>/`.

### Adding a New Partitioner

1. Create a class under `partitioner/` implementing the `Partitioner` interface.
2. Add the strategy to the `PartitionStrategy` enum.
3. Add any new config properties and validators to `AzureBlobSinkConfig`.
4. Register in `AzureBlobSinkTask` or `AzureBlobSinkConnectorContext`.
5. Add unit tests.

---

## Testing

### Test Stack

- **JUnit 5** — `@Test`, `@DisplayName`, `@ParameterizedTest`
- **Mockito** — Mocking dependencies
- **AssertJ** — Fluent assertions
- **Project Reactor Test** — Async operation testing

### Test Layout

```
src/test/java/io/coffeebeans/connect/azure/blob/sink/
├── AzureBlobSinkConnectorTest.java
├── AzureBlobSinkTaskTest.java
├── TopicPartitionWriterTest.java
├── config/
├── format/
│   ├── parquet/
│   ├── avro/
│   ├── json/
│   └── bytearray/
├── partitioner/
├── storage/
└── util/
    ├── EmbeddedConnectUtils.java    # Integration test helpers
    ├── AzureBlobStorageUtils.java
    └── HttpUrlStreamHandler.java
```

### Integration Tests

- Annotated with `@IntegrationTest` (custom marker).
- Activated via Maven profile: `./mvnw verify -P integration-test`.
- Require a running Kafka, Schema Registry, and Azurite (Azure Blob emulator).
- Use `docker-compose.yml` to spin up the environment.

### Test Naming Convention

```java
@Test
@DisplayName("Should <expected behaviour> when <condition>")
void should_<expectedBehaviour>_when_<condition>() { ... }
```

---

## Local Development Setup

```bash
# Start all dependencies
docker-compose up -d
# Zookeeper: localhost:2181
# Kafka:     localhost:9092
# Schema Registry: localhost:8081

# Build and run connector
./mvnw clean package -DskipTests
# Deploy the connector JAR to your Kafka Connect worker
```

The `quickstart/` directory contains sample connector configurations and Avro schemas.

---

## Dependencies & Versions (Key)

| Dependency | Version |
|---|---|
| Apache Kafka Connect | 3.2.1 |
| Azure Blob Storage SDK | 12.18.0 |
| Parquet Avro | 1.12.3 |
| Confluent Avro Data | 7.2.1 |
| Hadoop Common | 3.3.2 |
| Jackson | 2.13.3 |
| Project Reactor | 3.4.21 |
| JUnit 5 | 5.9.0 |
| Mockito | 4.6.1 |
| AssertJ | 3.23.1 |

---

## Packaging & Release

- The connector is packaged as a ZIP via the Maven Assembly Plugin.
- Profiles: `standalone` (self-contained), `development`, default package.
- Version management via `maven-release-plugin`.
- Docker image based on `confluentinc/cp-kafka-connect:7.2.0`.

```bash
# Standard connector package
./mvnw clean package

# Standalone fat package
./mvnw clean package -P standalone
```

---

## Important Files to Know

| File | Purpose |
|---|---|
| `AzureBlobSinkConfig.java` | All config properties, defaults, validators |
| `AzureBlobSinkTask.java` | Main task orchestration |
| `TopicPartitionWriter.java` | Buffer, rotate, flush logic |
| `AzureBlobStorageManager.java` | Azure SDK integration (async) |
| `pom.xml` | Build config, versions, profiles |
| `checkstyle/checkstyle.xml` | Code style rules |
| `docker-compose.yml` | Local dev stack |
| `docs/configuration/config-properties.md` | User-facing config docs |

---

## Pull Request Guidelines

From `.github/pull_request_template.md`:

- All new code must have unit tests.
- Checkstyle must pass (`./mvnw checkstyle:check`).
- Document new configuration properties in `docs/configuration/config-properties.md`.
- Keep commits focused and descriptive.
- Integration tests are required for Azure storage interactions.
