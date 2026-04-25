# AUDITX — Testing Guide

Three independent test layers. Run them in order: unit → integration → E2E.

---

## Layer 1 — Unit Tests (no Docker required)

Runs in ~30 seconds. Tests all business logic in isolation with mocks.

```bash
mvn test --no-transfer-progress
```

To run a single module:

```bash
mvn test -pl auditx-parser --no-transfer-progress
mvn test -pl auditx-ingestion --no-transfer-progress
mvn test -pl auditx-risk-engine --no-transfer-progress
mvn test -pl auditx-alert --no-transfer-progress
mvn test -pl auditx-compliance --no-transfer-progress
mvn test -pl auditx-policy-engine --no-transfer-progress
mvn test -pl auditx-live-stream --no-transfer-progress
```

To run a specific test class:

```bash
mvn test -pl auditx-parser -Dtest=RegexParsingStrategyTest --no-transfer-progress
mvn test -pl auditx-risk-engine -Dtest=RiskScoringEngineTest --no-transfer-progress
```

### What each module tests

| Module | Test classes | Tests |
|---|---|---|
| auditx-parser | RegexParsingStrategyTest, ParsingStrategyRegistryTest, ParserServiceTest | 15 |
| auditx-ingestion | IngestionServiceTest, IdempotencyServiceTest | 8 |
| auditx-risk-engine | RiskScoringEngineTest, UserRiskAggregationServiceTest | 13 |
| auditx-alert | AlertServiceTest, NotificationDispatcherTest | 7 |
| auditx-compliance | ComplianceServiceTest | 6 |
| auditx-policy-engine | PolicyEngineServiceTest | 6 |
| auditx-live-stream | EventBroadcastServiceTest, LiveStreamControllerTest | 9 |

Expected output per module: `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0`.

### Coverage report

Jacoco generates HTML coverage reports after the test run:

```bash
mvn test jacoco:report --no-transfer-progress
# Open target/site/jacoco/index.html in each module directory
```

---

## Layer 2 — Integration Tests (Docker required)

Starts real infrastructure (Kafka, MongoDB, Redis) via Testcontainers. Runs in ~2–4 minutes per suite.

> **Prerequisite**: Docker Desktop must be running.

### Ingestion pipeline

Tests the full ingestion path: HTTP POST → Redis idempotency check → Kafka publish → MongoDB store.

```bash
mvn test \
  -pl auditx-common,auditx-ingestion \
  -Dtest=IngestionPipelineIntegrationTest \
  --no-transfer-progress
```

### Parser pipeline

Tests the full parser path: Kafka consume (raw-events) → parse → MongoDB upsert → Kafka publish (structured-events).

```bash
mvn test \
  -pl auditx-common,auditx-parser \
  -Dtest=ParserPipelineIntegrationTest \
  --no-transfer-progress
```

### Live stream

Tests SSE fan-out: Kafka event → broadcast → SSE subscriber receives it.

```bash
mvn test \
  -pl auditx-common,auditx-live-stream \
  -Dtest=LiveStreamIntegrationTest \
  --no-transfer-progress
```

### Run all integration tests at once

```bash
mvn test \
  -pl auditx-common,auditx-ingestion,auditx-parser,auditx-live-stream \
  -Dtest="IngestionPipelineIntegrationTest,ParserPipelineIntegrationTest,LiveStreamIntegrationTest" \
  --no-transfer-progress
```

### What Testcontainers spins up

Each integration test uses `@Testcontainers` to start isolated containers:

| Container | Image | Purpose |
|---|---|---|
| Kafka | confluentinc/cp-kafka:7.6.0 | Event streaming |
| MongoDB | mongo:7.0 | Persistent store |
| Redis | redis:7-alpine | Idempotency |

Containers start before the first test and stop after the last one. No ports need to be free — Testcontainers picks random ephemeral ports.

---

## Layer 3 — E2E Tests (full stack required)

Tests the running platform end-to-end over HTTP. Requires the full Docker Compose stack.

### Step 1 — Start the stack

```bash
docker compose up --build -d
```

Wait for all services to be healthy (~3–5 minutes on first run):

```bash
docker compose ps
```

All AUDITX services should show `running`. Infrastructure should show `healthy`.

### Step 2 — Seed demo data

```bash
bash demo/seed-demo-data.sh
```

This inserts the demo tenant, API key, and risk rules into MongoDB.

### Step 3 — Run all E2E suites

```bash
bash test/e2e/run_all.sh
```

Expected output:

```
────────────────────────────────────────────────────
  AuditX E2E Test Suite
────────────────────────────────────────────────────
  ✓ 00_health_check
  ✓ 01_ingest_and_verify
  ✓ 02_risk_scoring
  ✓ 03_live_stream
  ✓ 04_compliance
  ✓ 05_report
  ✓ 06_idempotency
  ✓ 07_auth
  ✓ 08_tenant_isolation
────────────────────────────────────────────────────
  Passed: 9 / 9
  ALL TESTS PASSED
────────────────────────────────────────────────────
```

### Run individual suites

```bash
bash test/e2e/00_health_check.sh      # All 9 services respond healthy
bash test/e2e/01_ingest_and_verify.sh # POST event → appears in MongoDB
bash test/e2e/02_risk_scoring.sh      # Failed logins → riskScore > 0
bash test/e2e/03_live_stream.sh       # SSE endpoint streams events
bash test/e2e/04_compliance.sh        # Compliance + policy engine APIs
bash test/e2e/05_report.sh            # Report generation + validation
bash test/e2e/06_idempotency.sh       # Same key → same eventId
bash test/e2e/07_auth.sh              # 401 on missing/wrong API key
bash test/e2e/08_tenant_isolation.sh  # Events scoped to correct tenant
```

### What each suite covers

| Suite | Tests |
|---|---|
| 00_health_check | `/actuator/health` on ports 8081–8089 all return UP |
| 01_ingest_and_verify | Event posted via API appears in `audit_events` collection |
| 02_risk_scoring | 4 failed logins for same user triggers risk score > 0 |
| 03_live_stream | SSE endpoint returns 200 text/event-stream; status endpoint works |
| 04_compliance | GDPR summary returns 200; SOC2 records non-empty; policy engine active |
| 05_report | POST `/api/reports/generate` returns 200 with HTML body; missing tenantId → 400 |
| 06_idempotency | Same idempotency key returns same eventId on repeated POSTs |
| 07_auth | Missing API key → 401; wrong key → 401; valid key → 202 |
| 08_tenant_isolation | MongoDB query for unused tenant returns 0 events |

### Tear down

```bash
docker compose down -v   # stops all containers, removes data volumes
```

---

## CI — GitHub Actions

The CI pipeline runs all three layers automatically on every push and pull request.

### Pipeline overview

```
push / PR
    │
    ├── unit-tests (parallel)
    │     mvn test (excludes IntegrationTest)
    │     uploads Jacoco coverage
    │
    ├── integration-tests (parallel)
    │     mvn test (IngestionPipeline + Parser + LiveStream integration)
    │     Testcontainers handles infrastructure
    │
    └── e2e-tests (after unit + integration pass)
          docker compose up --build -d
          bash demo/seed-demo-data.sh
          bash test/e2e/run_all.sh
          docker compose down -v
```

### Workflow file

`.github/workflows/ci.yml`

### How to read CI results

- **unit-tests**: Failing here means a logic regression in a service. Check the Jacoco report artifact.
- **integration-tests**: Failing here means a real infrastructure interaction is broken (Kafka publish, MongoDB write, SSE fan-out). Usually a schema mismatch or config issue.
- **e2e-tests**: Failing here means a full-stack flow is broken. The CI uploads `docker compose logs` as an artifact on failure — download and grep the failing service's logs.

### Coverage badge

Add this to the top of your project README:

```markdown
![CI](https://github.com/<org>/auditx/actions/workflows/ci.yml/badge.svg)
```

---

## Quick reference

| What to run | Command | Docker needed |
|---|---|---|
| All unit tests | `mvn test --no-transfer-progress` | No |
| One module | `mvn test -pl auditx-parser --no-transfer-progress` | No |
| Integration tests | `mvn test -Dtest=*IntegrationTest ...` | Yes (Testcontainers) |
| E2E tests | `bash test/e2e/run_all.sh` | Yes (full stack) |
| Full stack health | `bash demo/verify-pipeline.sh` | Yes (full stack) |
