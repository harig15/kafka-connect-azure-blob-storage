# Changelog

All notable changes to the **DB Cleanup Application** are documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0-SNAPSHOT] — 2026-04-09

### Added

#### Application Bootstrap
- `DbCleanupApplication` — Spring Boot 3.2 entry point with `@EnableAsync` for non-blocking policy execution.

#### Policy Engine
- `CleanupPolicy` model — captures `id`, `name`, `description`, `databaseType`, `collectionName`, `maxDeletionLimit`, `agePeriodDays`, `targetField`, `conditions`, `enabled`.
- `PolicyCondition` model — per-field filter supporting operators: `EQ`, `NEQ`, `LT`, `LTE`, `GT`, `GTE`, `EXISTS`.
- `PolicyConfig` — `@ConfigurationProperties(prefix = "cleanup")` loader; reads policy list from `policies.yml` at startup. Exposes `findById()` and `getEnabledPolicies()` helpers.

#### Cleanup Service
- `CleanupService.createRunningRecord()` — persists an initial `RUNNING` `ExecutionRecord` before async work begins, enabling real-time dashboard feedback.
- `CleanupService.executePolicy()` — `@Async` method that:
  1. Builds a MongoDB `Query` combining an age-based date filter (`targetField < now - agePeriodDays`) with any additional `PolicyCondition` criteria.
  2. Counts matching documents and stores `documentsMatched`.
  3. Fetches up to `maxDeletionLimit` document IDs and deletes them individually.
  4. Updates the `ExecutionRecord` with final status (`SUCCESS`, `FAILED`, `SKIPPED`), `documentsDeleted`, and `endTime`.
- `CleanupService.buildQuery()` — translates policy configuration into a Spring Data `Criteria` object.
- `CleanupService.buildConditionCriteria()` — maps `PolicyCondition` operator strings to Spring Data MongoDB `Criteria` calls.

#### Persistence
- `ExecutionRecord` — `@Document(collection = "cleanup_executions")` MongoDB document storing full execution audit trail: policy metadata, status, matched/deleted counts, start/end timestamps, error message.
- `ExecutionRecordRepository` — Spring Data `MongoRepository` with custom finders:
  - `findByPolicyIdOrderByStartTimeDesc`
  - `findTop50ByOrderByStartTimeDesc`
  - `findByStatus`

#### REST API (`/api`)
- `GET /api/policies` — returns all configured policies.
- `GET /api/policies/{id}` — returns a single policy by ID.
- `POST /api/policies/{id}/execute` — triggers async execution; responds `202 Accepted` with the initial `ExecutionRecord`.
- `GET /api/executions` — returns the 50 most recent execution records (newest first).
- `GET /api/executions/{id}` — returns a specific execution record.
- `GET /api/stats` — returns summary: total/enabled policy counts, execution counts by status, cumulative documents deleted.

#### Web Dashboard
- `DashboardController` — serves Thymeleaf template; `GET /` redirects to `GET /dashboard`.
- `dashboard.html` — Bootstrap 5 single-page dashboard with:
  - **Stat cards**: total policies, enabled policies, total executions, cumulative docs deleted.
  - **Policy cards**: per-policy details (collection, age threshold, limit, conditions), enable/disable badge, last execution status, **Execute** button.
  - **Execution history table**: policy name, collection, status badge, documents matched, documents deleted, start time, duration, error message.
  - **Auto-refresh** every 4 seconds.
  - **Toast notifications** on execution trigger success or failure.
  - Spinning badge indicator when any execution is in `RUNNING` state.

#### Sample Data & PoC
- `DataSeederService` — seeds three MongoDB collections on `ApplicationReadyEvent`:
  - `orders`: 50 documents (30 eligible: `COMPLETED` + older than 30 days).
  - `users`: 55 documents (20 eligible: `active=false` + last login > 90 days ago).
  - `sessions`: 65 documents (50 eligible: `expiresAt` older than 7 days).
- Seeding is idempotent — skips if the collection already contains documents.

#### Configuration
- `application.yml` — Spring Boot config: embedded MongoDB database name, Thymeleaf settings, server port (8080), log levels.
- `policies.yml` — 4 pre-defined policies:

  | ID | Name | Collection | Age | Limit | Status |
  |----|------|-----------|-----|-------|--------|
  | `policy-orders-cleanup` | Old Completed Orders Cleanup | `orders` | 30 d | 1 000 | Enabled |
  | `policy-inactive-users` | Inactive User Accounts Cleanup | `users` | 90 d | 500 | Enabled |
  | `policy-expired-sessions` | Expired Sessions Purge | `sessions` | 7 d | 5 000 | Enabled |
  | `policy-audit-logs` | Old Audit Logs Cleanup | `audit_logs` | 180 d | 2 000 | **Disabled** |

#### Infrastructure
- `pom.xml` — Spring Boot 3.2.4 parent; dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-thymeleaf`, `de.flapdoodle.embed.mongo.spring30x:4.13.1` (embedded MongoDB), Lombok, Spring Boot Maven Plugin.

---

## Planned — Future Releases

### [1.1.0] — Scheduled Execution
- Cron-based automatic policy execution via `@Scheduled`.
- Configurable schedule per policy in `policies.yml`.

### [1.2.0] — Multi-Database Support
- Abstract `DatabaseAdapter` interface.
- Add adapters for PostgreSQL and MySQL (time-based `DELETE` queries).
- Policy `databaseType` field drives adapter selection.

### [1.3.0] — Policy Management API
- `POST /api/policies` — create policies at runtime (stored in MongoDB).
- `PUT /api/policies/{id}` — update policy configuration.
- `DELETE /api/policies/{id}` — remove a policy.
- YAML-defined policies remain read-only; runtime policies are mutable.

### [1.4.0] — Dry-Run Mode
- `POST /api/policies/{id}/execute?dryRun=true` — counts eligible documents without deleting.
- Dashboard toggle for dry-run before live execution.

### [1.5.0] — Alerting & Notifications
- Webhook callback on policy completion.
- Email notification on `FAILED` status.
- Configurable thresholds: alert if `documentsDeleted` exceeds N.
