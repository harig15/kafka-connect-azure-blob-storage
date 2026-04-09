# Low Level Design — DB Cleanup Application

**Version:** 1.0.0-SNAPSHOT
**Date:** 2026-04-09
**Author:** Engineering Team
**Branch:** `claude/java-db-cleanup-app-GVAjV`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Technology Stack](#2-technology-stack)
3. [Module Structure](#3-module-structure)
4. [Component Design](#4-component-design)
   - 4.1 [PolicyConfig](#41-policyconfig)
   - 4.2 [CleanupPolicy & PolicyCondition](#42-cleanuppolicy--policycondition)
   - 4.3 [CleanupService](#43-cleanupservice)
   - 4.4 [ExecutionRecord & Repository](#44-executionrecord--repository)
   - 4.5 [CleanupController (REST API)](#45-cleanupcontroller-rest-api)
   - 4.6 [DashboardController](#46-dashboardcontroller)
   - 4.7 [DataSeederService](#47-dataseederservice)
5. [Data Models](#5-data-models)
6. [API Specification](#6-api-specification)
7. [Policy Query Construction](#7-policy-query-construction)
8. [Execution Flow — Sequence Diagram](#8-execution-flow--sequence-diagram)
9. [Configuration Reference](#9-configuration-reference)
10. [Database Schema](#10-database-schema)
11. [Web Dashboard Design](#11-web-dashboard-design)
12. [Error Handling Strategy](#12-error-handling-strategy)
13. [Threading Model](#13-threading-model)
14. [Constraints & Safety Mechanisms](#14-constraints--safety-mechanisms)
15. [Extension Points](#15-extension-points)

---

## 1. Overview

The DB Cleanup Application is a Spring Boot service that executes **policy-driven, on-demand data deletions** against a MongoDB database. Each cleanup policy declares:

- Which collection to target
- How old documents must be (age threshold)
- Which document field carries the reference date
- A cap on how many documents are deleted per run (safety limit)
- Optional additional filter conditions

A web dashboard exposes all configured policies, triggers executions, and displays real-time status and results.

### Design Goals

| Goal | Mechanism |
|------|-----------|
| Zero-setup PoC | Embedded Flapdoodle MongoDB |
| Non-blocking execution | `@Async` + Spring's task executor |
| Safety against bulk deletes | Per-policy `maxDeletionLimit` |
| Idempotent sample data | Collection-exists check before seeding |
| Extensible to new DB types | `databaseType` field in policy (adapter pattern planned) |
| Real-time feedback | Execution record saved as RUNNING before async work; dashboard polls every 4 s |

---

## 2. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.4 |
| Web | Spring MVC | 6.x |
| Template Engine | Thymeleaf | 3.x |
| Database Client | Spring Data MongoDB | 4.x |
| Embedded DB (PoC) | Flapdoodle Embed Mongo | 4.13.1 |
| Build Tool | Apache Maven | 3.9+ |
| Frontend | Bootstrap 5.3 + Bootstrap Icons 1.11 | CDN |
| Async Execution | Spring `@Async` / `ThreadPoolTaskExecutor` | — |

---

## 3. Module Structure

```
db-cleanup-app/
├── pom.xml                                      # Maven build descriptor
├── CHANGELOG.md                                 # Version history
├── LLD.md                                       # This document
└── src/
    └── main/
        ├── java/com/dbcleanup/
        │   ├── DbCleanupApplication.java         # @SpringBootApplication entry point
        │   ├── config/
        │   │   └── PolicyConfig.java             # @ConfigurationProperties loader
        │   ├── model/
        │   │   ├── CleanupPolicy.java            # Policy definition POJO
        │   │   ├── PolicyCondition.java          # Per-field filter condition
        │   │   └── ExecutionRecord.java          # @Document — execution audit entry
        │   ├── repository/
        │   │   └── ExecutionRecordRepository.java# Spring Data MongoRepository
        │   ├── service/
        │   │   ├── CleanupService.java           # Core deletion + query logic
        │   │   └── DataSeederService.java        # PoC sample data loader
        │   └── web/
        │       ├── CleanupController.java        # REST API (@RestController)
        │       └── DashboardController.java      # Page controller (@Controller)
        └── resources/
            ├── application.yml                   # Spring Boot config
            ├── policies.yml                      # Policy definitions
            └── templates/
                └── dashboard.html               # Thymeleaf + Bootstrap UI
```

---

## 4. Component Design

### 4.1 PolicyConfig

```
@Configuration
@ConfigurationProperties(prefix = "cleanup")
PolicyConfig
├── List<CleanupPolicy> policies          ← bound from policies.yml
├── getEnabledPolicies() → List<CleanupPolicy>
└── findById(String id) → Optional<CleanupPolicy>
```

- Loaded once at startup via Spring's relaxed binding.
- Policies are **read-only** at runtime (YAML source of truth).
- `findById` performs a linear scan — acceptable for O(10) policies.

---

### 4.2 CleanupPolicy & PolicyCondition

```
CleanupPolicy
├── String id                   ← unique key (e.g. "policy-orders-cleanup")
├── String name
├── String description
├── String databaseType         ← "MONGODB" (future: "POSTGRESQL", "MYSQL")
├── String collectionName       ← target MongoDB collection
├── int    maxDeletionLimit     ← hard cap per execution
├── int    agePeriodDays        ← cutoff = now − agePeriodDays
├── String targetField          ← date/timestamp field for age comparison
├── List<PolicyCondition> conditions
└── boolean enabled

PolicyCondition
├── String field                ← MongoDB field name
├── String operator             ← EQ | NEQ | LT | LTE | GT | GTE | EXISTS
└── Object value                ← String, Number, Boolean (from YAML)
```

**Operator mapping to MongoDB:**

| Operator | Spring Data Criteria | MongoDB Wire |
|----------|---------------------|-------------|
| EQ | `.is(value)` | `{ field: value }` |
| NEQ | `.ne(value)` | `{ field: { $ne: value } }` |
| LT | `.lt(value)` | `{ field: { $lt: value } }` |
| LTE | `.lte(value)` | `{ field: { $lte: value } }` |
| GT | `.gt(value)` | `{ field: { $gt: value } }` |
| GTE | `.gte(value)` | `{ field: { $gte: value } }` |
| EXISTS | `.exists(boolean)` | `{ field: { $exists: bool } }` |

---

### 4.3 CleanupService

The central component. Key responsibilities:

```
CleanupService
├── createRunningRecord(policyId) → ExecutionRecord
│     Persists status=RUNNING before async work begins
│
└── executePolicy(policyId, executionId)   [@Async]
      1. Load policy from PolicyConfig
      2. Load ExecutionRecord by executionId
      3. Check policy.enabled → SKIPPED if false
      4. buildQuery(policy) → count matching docs → set documentsMatched
      5. buildQuery(policy).limit(maxDeletionLimit) → fetch _id list
      6. Delete each document by _id
      7. Update ExecutionRecord: status=SUCCESS, documentsDeleted, endTime
      8. On exception → status=FAILED, errorMessage, endTime
```

**Query construction — `buildQuery(policy)`:**

```
Criteria ageCriteria = where(targetField).lt(Date.from(now - agePeriodDays))

if conditions not empty:
    Criteria[] extras = conditions.stream().map(buildConditionCriteria)
    finalCriteria = AND(ageCriteria, AND(extras...))
else:
    finalCriteria = ageCriteria

return new Query(finalCriteria)
```

**Why fetch IDs then delete individually?**

MongoDB's `deleteMany` with `limit` is not directly supported in the driver. Fetching IDs and deleting by `_id` allows exact enforcement of `maxDeletionLimit` without a collection scan for each delete.

---

### 4.4 ExecutionRecord & Repository

```
@Document(collection = "cleanup_executions")
ExecutionRecord
├── @Id String id               ← MongoDB ObjectId (auto-generated)
├── String policyId
├── String policyName
├── String collectionName
├── String status               ← RUNNING | SUCCESS | FAILED | SKIPPED
├── long   documentsMatched     ← count before limit applied
├── long   documentsDeleted     ← actual deletions performed
├── Instant startTime
├── Instant endTime
├── String errorMessage
└── getDurationMs()             ← derived: endTime − startTime (ms)

ExecutionRecordRepository extends MongoRepository<ExecutionRecord, String>
├── findByPolicyIdOrderByStartTimeDesc(policyId)
├── findTop50ByOrderByStartTimeDesc()
└── findByStatus(status)
```

---

### 4.5 CleanupController (REST API)

```
@RestController @RequestMapping("/api")
CleanupController
├── GET  /policies              → policyConfig.getPolicies()
├── GET  /policies/{id}         → policyConfig.findById(id)
├── POST /policies/{id}/execute → createRunningRecord + executePolicy(@Async)
│                                 → 202 Accepted + ExecutionRecord(RUNNING)
├── GET  /executions            → findTop50ByOrderByStartTimeDesc()
├── GET  /executions/{id}       → findById(id)
└── GET  /stats                 → aggregated Map<String, Object>
      {
        totalPolicies, enabledPolicies,
        totalExecutions, successCount, failedCount, runningCount,
        totalDocumentsDeleted
      }
```

---

### 4.6 DashboardController

```
@Controller
DashboardController
├── GET /           → redirect:/dashboard
└── GET /dashboard  → "dashboard"  (resolves to templates/dashboard.html)
```

All data rendering is client-side (JavaScript fetch calls to `/api/*`).
Thymeleaf is used purely for template resolution and HTML escaping support.

---

### 4.7 DataSeederService

```
@Service
DataSeederService
└── @EventListener(ApplicationReadyEvent) seed()
      ├── seedOrders()    → inserts 50 docs into "orders"    (skips if non-empty)
      ├── seedUsers()     → inserts 55 docs into "users"     (skips if non-empty)
      └── seedSessions()  → inserts 65 docs into "sessions"  (skips if non-empty)
```

**Seed distribution (designed for visible PoC results):**

| Collection | Total | Eligible | Reason ineligible |
|-----------|-------|---------|-------------------|
| orders | 50 | 30 | 15 too recent; 5 wrong status (PENDING) |
| users | 55 | 20 | 25 active=true; 10 inactive but recent |
| sessions | 65 | 50 | 15 not yet expired |

---

## 5. Data Models

### CleanupPolicy (YAML → Java)

```yaml
- id:               String        # required, unique
  name:             String        # required
  description:      String        # optional
  databaseType:     String        # MONGODB (default)
  collectionName:   String        # required
  maxDeletionLimit: int           # required, > 0
  agePeriodDays:    int           # required, > 0
  targetField:      String        # required — must be a Date field in MongoDB
  enabled:          boolean       # true | false
  conditions:                     # optional
    - field:    String
      operator: String            # EQ|NEQ|LT|LTE|GT|GTE|EXISTS
      value:    any               # matched to Java type by YAML parser
```

### ExecutionRecord (MongoDB Document)

```json
{
  "_id":               "ObjectId",
  "policyId":          "policy-orders-cleanup",
  "policyName":        "Old Completed Orders Cleanup",
  "collectionName":    "orders",
  "status":            "SUCCESS",
  "documentsMatched":  30,
  "documentsDeleted":  30,
  "startTime":         "2026-04-09T10:00:00.000Z",
  "endTime":           "2026-04-09T10:00:00.312Z",
  "errorMessage":      null
}
```

### Seeded: orders document

```json
{
  "orderId":      "ORD-0001",
  "customerId":   "CUST-142",
  "status":       "COMPLETED",
  "totalAmount":  279,
  "items":        3,
  "createdAt":    "2026-03-01T08:00:00.000Z"
}
```

### Seeded: users document

```json
{
  "username":      "user_inactive_1",
  "email":         "inactive1@example.com",
  "active":        false,
  "registeredAt":  "2025-08-01T00:00:00.000Z",
  "lastLoginAt":   "2025-09-12T00:00:00.000Z"
}
```

### Seeded: sessions document

```json
{
  "sessionId":   "sess-<uuid>",
  "userId":      "CUST-187",
  "userAgent":   "Mozilla/5.0 (compatible; PoC)",
  "ipAddress":   "192.168.1.55",
  "createdAt":   "2026-03-25T09:00:00.000Z",
  "expiresAt":   "2026-03-25T10:00:00.000Z"
}
```

---

## 6. API Specification

### `GET /api/policies`

Returns the full list of configured policies (enabled and disabled).

**Response 200:**
```json
[
  {
    "id": "policy-orders-cleanup",
    "name": "Old Completed Orders Cleanup",
    "description": "Removes completed purchase orders older than 30 days.",
    "databaseType": "MONGODB",
    "collectionName": "orders",
    "maxDeletionLimit": 1000,
    "agePeriodDays": 30,
    "targetField": "createdAt",
    "conditions": [{ "field": "status", "operator": "EQ", "value": "COMPLETED" }],
    "enabled": true
  }
]
```

---

### `POST /api/policies/{id}/execute`

Triggers async execution of the specified policy.

**Path param:** `id` — policy ID

**Response 202 Accepted:**
```json
{
  "id": "6613abc123def456",
  "policyId": "policy-orders-cleanup",
  "policyName": "Old Completed Orders Cleanup",
  "collectionName": "orders",
  "status": "RUNNING",
  "documentsMatched": 0,
  "documentsDeleted": 0,
  "startTime": "2026-04-09T10:00:00.000Z",
  "endTime": null,
  "errorMessage": null
}
```

**Response 404:** Policy ID not found.

Poll `GET /api/executions/{id}` for the final result.

---

### `GET /api/executions`

Returns the 50 most recent execution records, newest first.

**Response 200:** Array of `ExecutionRecord` objects (same shape as above with final status).

---

### `GET /api/stats`

**Response 200:**
```json
{
  "totalPolicies":          4,
  "enabledPolicies":        3,
  "totalExecutions":        12,
  "successCount":           10,
  "failedCount":            1,
  "runningCount":           1,
  "totalDocumentsDeleted":  98
}
```

---

## 7. Policy Query Construction

The generated MongoDB query for each policy is:

```
{
  $and: [
    { <targetField>: { $lt: <cutoffDate> } },      ← age filter
    { <cond1.field>: { $<op>: <cond1.value> } },   ← extra condition 1
    { <cond2.field>: { $<op>: <cond2.value> } },   ← extra condition N
    ...
  ]
}
```

**Example — policy-orders-cleanup:**

```json
{
  "$and": [
    { "createdAt": { "$lt": { "$date": "2026-03-10T10:00:00.000Z" } } },
    { "status":    { "$eq":  "COMPLETED" } }
  ]
}
```

**Example — policy-inactive-users:**

```json
{
  "$and": [
    { "lastLoginAt": { "$lt": { "$date": "2026-01-09T10:00:00.000Z" } } },
    { "active":      { "$eq":  false } }
  ]
}
```

---

## 8. Execution Flow — Sequence Diagram

```
Browser / API Client          CleanupController        CleanupService         MongoDB
        |                            |                       |                   |
        |  POST /api/policies        |                       |                   |
        |  /{id}/execute             |                       |                   |
        |--------------------------->|                       |                   |
        |                            | createRunningRecord() |                   |
        |                            |---------------------->|                   |
        |                            |                       | INSERT RUNNING     |
        |                            |                       |------------------>|
        |                            |                       |<-- ExecutionRecord|
        |                            |<-- ExecutionRecord(RUNNING)               |
        |<-- 202 Accepted            |                       |                   |
        |    ExecutionRecord(RUNNING)|                       |                   |
        |                            |  executePolicy()      |                   |
        |                            |  [@Async returns]     |                   |
        |                            |---------------------->|                   |
        .                            .                       | buildQuery()       |
        .  (async on thread pool)    .                       | COUNT documents   |
        .                            .                       |------------------>|
        .                            .                       |<-- matched count  |
        .                            .                       | FIND _ids (limit) |
        .                            .                       |------------------>|
        .                            .                       |<-- [ _id, ... ]   |
        .                            .                       | DELETE by _id (×N)|
        .                            .                       |------------------>|
        .                            .                       | UPDATE ExecutionRecord|
        .                            .                       | status=SUCCESS    |
        .                            .                       |------------------>|
        |                            |                       |                   |
        |  GET /api/executions/{id}  |                       |                   |
        |--------------------------->|                       |                   |
        |                            |          findById()   |                   |
        |                            |---------------------->|                   |
        |                            |                       | FIND by _id       |
        |                            |                       |------------------>|
        |                            |                       |<-- ExecutionRecord|
        |                            |<-- ExecutionRecord    |   (SUCCESS)       |
        |<-- 200 OK                  |                       |                   |
        |    ExecutionRecord(SUCCESS)|                       |                   |
```

---

## 9. Configuration Reference

### `application.yml`

| Key | Default | Description |
|-----|---------|-------------|
| `spring.application.name` | `db-cleanup-app` | App name for logging/actuator |
| `spring.data.mongodb.database` | `cleanup_poc_db` | Embedded MongoDB database name |
| `spring.thymeleaf.cache` | `false` | Template cache (set true in prod) |
| `spring.config.import` | `classpath:policies.yml` | Imports policy definitions |
| `server.port` | `8080` | HTTP listen port |
| `logging.level.com.dbcleanup` | `DEBUG` | App-level log verbosity |

### `policies.yml` — per-policy fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | String | Yes | Unique policy identifier |
| `name` | String | Yes | Display name |
| `description` | String | No | Human-readable purpose |
| `databaseType` | String | Yes | `MONGODB` |
| `collectionName` | String | Yes | Target collection |
| `maxDeletionLimit` | int | Yes | Max docs deleted per run |
| `agePeriodDays` | int | Yes | Minimum age in days to be eligible |
| `targetField` | String | Yes | Date field to compare against cutoff |
| `conditions[].field` | String | No | Additional filter field |
| `conditions[].operator` | String | No | `EQ\|NEQ\|LT\|LTE\|GT\|GTE\|EXISTS` |
| `conditions[].value` | any | No | Comparison value |
| `enabled` | boolean | Yes | `true` to allow execution |

---

## 10. Database Schema

### Collection: `cleanup_executions` (managed by application)

| Field | BSON Type | Indexed | Notes |
|-------|-----------|---------|-------|
| `_id` | ObjectId | PK | Auto-generated |
| `policyId` | String | Recommended | For `findByPolicyId` queries |
| `policyName` | String | — | Denormalized for display |
| `collectionName` | String | — | Denormalized for display |
| `status` | String | Recommended | RUNNING/SUCCESS/FAILED/SKIPPED |
| `documentsMatched` | Long | — | Count before limit |
| `documentsDeleted` | Long | — | Actual deletions |
| `startTime` | Date | Recommended | For `findTop50ByOrderByStartTimeDesc` |
| `endTime` | Date | — | Null while RUNNING |
| `errorMessage` | String | — | Null unless FAILED |

**Recommended indexes for production:**
```javascript
db.cleanup_executions.createIndex({ policyId: 1, startTime: -1 })
db.cleanup_executions.createIndex({ status: 1 })
db.cleanup_executions.createIndex({ startTime: -1 })
```

### Collections: `orders`, `users`, `sessions` (PoC sample data)

Seeded by `DataSeederService`. No fixed schema enforced — MongoDB is schemaless.
The policy's `targetField` and `conditions[].field` must resolve to actual fields in the target collection.

---

## 11. Web Dashboard Design

### Page Structure

```
┌──────────────────────────────────────────────────────────┐
│  Navbar: "DB Cleanup Dashboard"          Auto-refresh 4s │
├──────────────────────────────────────────────────────────┤
│  [Stat Card]     [Stat Card]     [Stat Card]  [Stat Card]│
│  Total Policies  Enabled         Executions   Docs Del.  │
├──────────────────────────────────────────────────────────┤
│  Cleanup Policies                                        │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────┐│
│  │ Policy Card │ │ Policy Card │ │ Policy Card │ │DIS ││
│  │ [Execute]   │ │ [Execute]   │ │ [Execute]   │ │BLED││
│  └─────────────┘ └─────────────┘ └─────────────┘ └────┘│
├──────────────────────────────────────────────────────────┤
│  Execution History                        [Refresh]      │
│  ┌────────────────────────────────────────────────────┐  │
│  │ Policy | Collection | Status | Matched | Deleted … │  │
│  │ …      │ …          │ ●SUCCESS│ 30     │ 30       │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Client-Side Data Flow

```
DOMContentLoaded
    │
    ├─ loadAll()  ←──────────────────── setInterval(4000ms)
    │     ├─ fetch GET /api/policies
    │     ├─ fetch GET /api/executions
    │     └─ fetch GET /api/stats
    │
    ├─ renderStats()     → fills #stats-row
    ├─ renderPolicies()  → fills #policies-row
    └─ renderExecutions()→ fills #executions-tbody
                           shows #running-badge if any RUNNING
```

### Execute Button Flow

```
User clicks [Execute]
    │
    ├─ Button → disabled + spinner shown
    │
    ├─ POST /api/policies/{id}/execute
    │     └─ 202 Accepted → toast "Policy triggered — execution ID: …"
    │
    ├─ loadAll() (refresh all data)
    │
    └─ Button → re-enabled
       Dashboard auto-refresh picks up RUNNING → SUCCESS transition
```

---

## 12. Error Handling Strategy

| Scenario | Behaviour |
|----------|-----------|
| Policy ID not found (API) | `404 Not Found` |
| Policy is disabled | `ExecutionRecord.status = SKIPPED`, `endTime` set immediately |
| No documents match filter | `status = SUCCESS`, `documentsDeleted = 0` |
| MongoDB query/delete exception | `status = FAILED`, exception message stored in `errorMessage` |
| Unknown operator in `PolicyCondition` | `IllegalArgumentException` thrown, caught, stored as `FAILED` |
| Dashboard fetch error | `console.error` only — UI retains last valid state until next poll |

---

## 13. Threading Model

```
HTTP Request Thread (Tomcat)
    └─ CleanupController.executePolicy()
          ├─ cleanupService.createRunningRecord()  [synchronous — saves to Mongo]
          ├─ cleanupService.executePolicy()        [@Async — submits to thread pool]
          └─ returns 202 immediately

Spring Async Thread Pool (SimpleAsyncTaskExecutor by default)
    └─ CleanupService.executePolicy()
          ├─ count()
          ├─ find() IDs
          ├─ delete() × N
          └─ save() ExecutionRecord (FINAL STATUS)
```

For production, configure a bounded `ThreadPoolTaskExecutor` to prevent unbounded thread growth:

```java
@Bean
public Executor taskExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(2);
    exec.setMaxPoolSize(5);
    exec.setQueueCapacity(10);
    exec.setThreadNamePrefix("cleanup-");
    exec.initialize();
    return exec;
}
```

---

## 14. Constraints & Safety Mechanisms

| Constraint | Value | Rationale |
|-----------|-------|-----------|
| `maxDeletionLimit` per policy | Configurable (e.g. 1000) | Prevents runaway bulk deletes |
| Deletion method | ID-by-ID loop | Enforces limit exactly; MongoDB `deleteMany` has no native LIMIT |
| Idempotent seeding | Collection-exists check | Re-running the app does not duplicate seed data |
| Async isolation | Each execution has its own `ExecutionRecord` | Concurrent executions of different policies do not interfere |
| RUNNING record first | Saved before `@Async` is called | Dashboard shows activity even before deletion starts |

---

## 15. Extension Points

### Adding a New Database Type

1. Create interface `DatabaseCleanupAdapter`:
   ```java
   public interface DatabaseCleanupAdapter {
       long count(CleanupPolicy policy);
       long delete(CleanupPolicy policy);
   }
   ```
2. Implement `MongoCleanupAdapter` (extract current `CleanupService` logic).
3. Implement `PostgresCleanupAdapter` (time-based `DELETE ... WHERE ... LIMIT`).
4. Register adapters in a `Map<String, DatabaseCleanupAdapter>` bean keyed by `databaseType`.
5. `CleanupService.executePolicy()` looks up the adapter by `policy.getDatabaseType()`.

### Adding Scheduled Execution

```yaml
cleanup:
  policies:
    - id: policy-orders-cleanup
      schedule: "0 2 * * *"   # cron: daily at 02:00
```

```java
@Scheduled(cron = "#{@policyConfig.findById('policy-orders-cleanup').get().schedule}")
public void runOrdersCleanup() { ... }
```

### Adding Dry-Run Support

- Add `dryRun` query param to `POST /api/policies/{id}/execute?dryRun=true`.
- `CleanupService` skips the delete loop; sets `status = DRY_RUN`, `documentsDeleted = 0`.
- Dashboard shows matched count without actual deletion.
