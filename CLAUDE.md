# CLAUDE.md

This file provides guidance for AI assistants working with the kafka-connect-azure-blob-storage codebase.

## Project Overview

A Kafka Connect **sink connector** that streams data from Apache Kafka topics into Azure Blob Storage. It supports multiple output formats (Avro, Parquet, JSON, ByteArray), flexible partitioning strategies, and configurable compression. Built by CoffeeBeans Consulting under Apache License 2.0.

- **Group/Artifact:** `io.coffeebeans.kafka.connect:kafka-connect-azure-blob-storage`
- **Version:** 1.0.1-SNAPSHOT
- **Java:** 11
- **Kafka Connect API:** 3.2.1
- **Azure Storage Blob SDK:** 12.18.0

## Build Commands

```bash
# Build the connector JAR (includes unit tests)
mvn clean package

# Run unit tests only
mvn clean test

# Run integration tests (requires Azure/Azurite setup)
mvn clean test -P integration-test

# Generate checkstyle violation report (produces checkstyle.html)
mvn site

# Use Maven wrapper (no local Maven install required)
./mvnw clean package
```

Integration tests are skipped by default (`skip.integration.tests=true` in pom.xml). Unit tests run by default.

## Source Code Structure

```
src/main/java/io/coffeebeans/connect/azure/blob/
├── sink/
│   ├── AzureBlobSinkConnector.java     # Connector entry point (extends SinkConnector)
│   ├── AzureBlobSinkTask.java          # Task implementation (extends SinkTask)
│   ├── AzureBlobSinkConnectorContext.java  # Shared context (builder pattern)
│   ├── TopicPartitionWriter.java       # Per topic-partition writer with buffering/rotation
│   │
│   ├── config/
│   │   ├── AzureBlobSinkConfig.java    # All configuration definitions (~60+ properties)
│   │   ├── NullValueBehavior.java      # Enum: IGNORE, FAIL
│   │   └── validators/                 # Custom config validators
│   │
│   ├── format/
│   │   ├── Format.java                 # Enum: PARQUET, AVRO, JSON, BYTEARRAY
│   │   ├── RecordWriter.java           # Interface: write, close, commit
│   │   ├── RecordWriterProvider.java    # Interface: factory for RecordWriter
│   │   ├── SchemaStore.java            # Interface: schema management
│   │   ├── AzureBlobOutputStream.java  # Custom output stream for Azure
│   │   ├── CompressionType.java        # Enum: GZIP, SNAPPY, LZ4, ZSTD, NONE
│   │   ├── avro/                       # Avro format implementation
│   │   ├── json/                       # JSON format implementation
│   │   ├── parquet/                    # Parquet format implementation
│   │   └── bytearray/                  # ByteArray format implementation
│   │
│   ├── partitioner/
│   │   ├── Partitioner.java            # Interface: encodePartition, generateFullPath
│   │   ├── PartitionStrategy.java      # Enum: DEFAULT, TIME, FIELD
│   │   ├── DefaultPartitioner.java     # Partitions by Kafka topic+partition
│   │   ├── field/FieldPartitioner.java # Partitions by record field value
│   │   └── time/                       # Time-based partitioning with timestamp extractors
│   │
│   ├── storage/
│   │   ├── StorageManager.java         # Interface: upload, append, async operations
│   │   └── AzureBlobStorageManager.java # Azure Blob SDK integration with retry
│   │
│   └── exception/                      # Custom exceptions
│
└── util/
    └── Version.java                    # Reads version from application.properties
```

## Test Structure

```
src/test/java/io/coffeebeans/connect/azure/blob/
├── sink/
│   ├── AzureBlobSinkConnectorTest.java
│   ├── AzureBlobSinkTaskTest.java
│   ├── AzureBlobSinkConnectorContextTest.java
│   ├── TopicPartitionWriterTest.java
│   ├── config/                         # Config and validator tests
│   ├── format/                         # Format-specific tests
│   ├── partitioner/                    # Partitioner tests
│   └── storage/                        # Storage manager tests
├── integration/                        # Integration tests (require -P integration-test)
│   ├── BaseConnectorIntegrationTest.java
│   └── AzureBlobConnectorIntegrationTest.java
└── util/                               # Test utilities
```

**Test frameworks:** JUnit 5 (Jupiter), Mockito 4.x, AssertJ, Reactor Test

## Architecture

The data flow through the connector:

```
Kafka Topics → AzureBlobSinkConnector → AzureBlobSinkTask(s)
  → TopicPartitionWriter (per topic-partition)
    → Partitioner (determines blob path)
    → RecordWriter (serializes to chosen format)
      → AzureBlobOutputStream → AzureBlobStorageManager → Azure Blob Storage
```

Key design patterns:
- **Strategy pattern** for partitioners, format writers, and timestamp extractors
- **Builder pattern** for `AzureBlobSinkConnectorContext`
- **Provider/Factory pattern** for `RecordWriterProvider` implementations
- **One writer per topic-partition** managed by `TopicPartitionWriter`, which handles buffering, rotation by record count (`flush.size`) or time interval (`rotate.interval.ms`)

## Code Style and Conventions

Enforced via `checkstyle/checkstyle.xml` (based on Google Java Style Guide with modifications):

- **Max line length:** 120 characters
- **Indentation:** 4 spaces (2 for braces)
- **Imports:** Explicit only (no wildcards). Static imports first, blank line, then all other imports lexicographically ordered.
- **Javadoc:** Required on public/protected methods and classes. Must include `@param`, `@return`, `@throws` tags.
- **Naming:** PascalCase for classes, camelCase for methods/fields, UPPER_SNAKE_CASE for constants
- **Braces:** K&R style (opening brace on same line)
- **One top-level class per file**

Run `mvn site` to generate a checkstyle report. The build will fail on checkstyle violations.

## Key Configuration Properties

The connector exposes configuration groups defined in `AzureBlobSinkConfig.java`:

| Group | Key Properties |
|-------|---------------|
| **Azure** | `azblob.connection.string`, `azblob.container.name` |
| **Connector** | `format`, `flush.size`, `rotate.interval.ms`, `null.value.behavior` |
| **Format** | `avro.codec`, `parquet.codec`, `compression.type`, `format.bytearray.extension` |
| **Partitioner** | `partition.strategy`, `partition.field.name`, `path.format`, `timezone`, `timestamp.extractor` |
| **Storage** | `topics.dir`, `retry.type`, `max.retries`, `connection.timeout.ms` |

## Key Dependencies

| Dependency | Purpose |
|-----------|---------|
| `org.apache.kafka:connect-api` | Kafka Connect framework (provided scope) |
| `com.azure:azure-storage-blob` | Azure Blob Storage SDK |
| `io.confluent:kafka-connect-avro-data` | Confluent Avro support |
| `org.apache.parquet:parquet-avro` | Parquet format support |
| `org.apache.hadoop:hadoop-common` | Required by Parquet/Avro |
| `tech.allegro.schema.json2avro:converter` | JSON-to-Avro conversion |
| `com.fasterxml.jackson.*` | JSON serialization |

## Docker / Local Development

- `docker-compose.yml` provides Zookeeper, Kafka, Schema Registry, and Kafka Connect for local testing
- `Dockerfile` builds on `confluentinc/cp-kafka-connect:7.2.0`
- `quickstart/` contains sample Avro schemas and a datagen connector install script

## Repository Layout

```
checkstyle/          # Checkstyle configuration
docs/                # Docusaurus documentation site
licenses/            # Third-party license files
quickstart/          # Sample schemas and setup scripts
src/assembly/        # Maven assembly descriptors (package, development, standalone)
src/main/            # Production source code
src/test/            # Test source code
.github/             # Issue/PR templates
```

## Common Tasks for AI Assistants

- **Adding a new output format:** Implement `RecordWriter` and `RecordWriterProvider` interfaces, add to `Format` enum, wire into `AzureBlobSinkTask`
- **Adding a new partitioner:** Implement `Partitioner` interface, add to `PartitionStrategy` enum, wire into `AzureBlobSinkTask`
- **Adding a configuration property:** Add to `AzureBlobSinkConfig.java` with appropriate group, validator, and documentation string
- **Running tests after changes:** `mvn clean test` for unit tests; ensure all existing tests pass before submitting
