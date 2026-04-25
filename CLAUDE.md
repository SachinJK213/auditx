# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**AUDITX** is an event-driven Identity Observability and Compliance Platform. It captures audit events from client applications, scores them for risk in real-time, fires alerts on suspicious activity, and generates compliance reports.

- **Backend**: Java 21 + Spring Boot 3.2.5 (reactive, WebFlux)
- **Frontend**: React 18 + TypeScript (Vite)
- **Infrastructure**: MongoDB, Redis, Kafka, ELK Stack (Elasticsearch + Logstash + Kibana + Filebeat)

---

## Commands

### Backend (Maven)

```bash
# Build all modules
mvn compile --no-transfer-progress

# Run all tests
mvn test --no-transfer-progress

# Test a single module
mvn test -pl auditx-common --no-transfer-progress

# Run a specific test class
mvn test -pl auditx-ingestion -Dtest=IngestionPipelineIntegrationTest --no-transfer-progress
```

### Frontend (Dashboard)

```bash
cd auditx-dashboard
npm install
npm start        # dev server on port 3000
npm run build
```

### Docker (Full Stack)

```bash
docker compose up --build        # build + start all 15 containers
docker compose up --build -d     # background
docker compose logs -f --tail=20
docker compose ps
docker compose down              # stop
docker compose down -v           # stop + wipe volumes (clean slate)
```

### Demo / Verification Scripts

```bash
bash demo/seed-demo-data.sh      # seeds tenant, rules, test events
bash demo/verify-pipeline.sh     # health checks + data validation
bash elk/setup-kibana.sh         # creates Kibana index patterns
```

---

## Architecture

### Event Pipeline

```
Client App (SDK)
  ↓  API Key auth
Ingestion (8081)      →  Kafka [raw-events]
  ↓  regex extraction
Parser (8082)         →  MongoDB (audit_events) + Kafka [structured-events]
  ↓  (fan-out — two consumers on structured-events)
  ├─ Risk Engine (8083) → score + MongoDB (user_risk_profiles) + Kafka [alerts]
  └─ Policy Engine (8087) → SpEL rule eval + Kafka [policy-violations]
          ↓
  Alert (8085)          →  MongoDB (alerts) + L1→L2→L3 escalation + webhook/email
  Compliance (8088)     →  MongoDB (compliance_records) per GDPR/SOC2/DPDP
```

All external traffic enters through **Gateway (8080)**, which handles JWT auth and Redis-based rate limiting, then reverse-proxies to Ingestion (8081), LLM (8084), and Report (8086).

### Service Map

| Module | Port | Role |
|--------|------|------|
| `auditx-gateway` | 8080 | JWT auth, rate limiting, reverse proxy |
| `auditx-ingestion` | 8081 | REST intake (API key auth), idempotency via Redis |
| `auditx-parser` | 8082 | Kafka consumer, regex extraction, writes to MongoDB |
| `auditx-risk-engine` | 8083 | Kafka consumer, rule-based scoring (0–100), emits alerts |
| `auditx-llm` | 8084 | AI explain/query/summarize via OpenAI or Azure OpenAI |
| `auditx-alert` | 8085 | Kafka consumer, persists alerts, dispatches webhooks/email |
| `auditx-report` | 8086 | On-demand HTML/PDF compliance reports from MongoDB |
| `auditx-policy-engine` | 8087 | Kafka consumer; evaluates SpEL-based policy rules → publishes `policy-violations` |
| `auditx-compliance` | 8088 | Kafka consumer; maps violations to GDPR/SOC2/DPDP → stores `compliance_records` |
| `auditx-sdk` | — | Client library; Spring Boot auto-config, async virtual threads |
| `auditx-common` | — | Shared DTOs, exceptions, Kafka utils, MDC propagation |
| `auditx-dashboard` | 3000 | React UI (event table, risk badges, stat cards, charts) |

### Multi-Tenancy

Tenant isolation is enforced at every layer — API keys are scoped per tenant, every Kafka message carries `tenantId` in MDC headers, all MongoDB collections are indexed on `tenantId`, and the LLM service injects a `tenantId` filter before executing any generated query.

### Key Technical Patterns

- **Virtual threads**: All Spring Boot services use `spring.threads.virtual.enabled: true` (Java 21).
- **Idempotency**: Ingestion checks a Redis key (`tenantId:idempotencyKey`) before processing; duplicates return the cached `eventId`.
- **Distributed tracing**: MDC fields (`traceId`, `tenantId`, `userId`, `eventId`) propagate through Kafka headers and appear in Kibana for end-to-end trace correlation.
- **Risk scoring**: Scores are clamped 0–100. Built-in rules: `FAILED_LOGIN_THRESHOLD` (N failures in M minutes), `GEO_ANOMALY` (unexpected region), `CUSTOM` (tenant-defined). Alert fires when score exceeds the tenant's threshold.
- **User risk aggregation**: After each event is scored, `UserRiskAggregationService` updates a `user_risk_profiles` document (weighted moving average, high-risk event count) per `(tenantId, userId)`. Exposed at `GET /api/user-risk` on port 8083.
- **Policy Engine**: Loads `policy_rules` (MongoDB) per tenant; evaluates each rule's `condition` field as a SpEL expression against the event (`#userId`, `#action`, `#outcome`, `#sourceIp`, `#riskScore`). Violations published to `policy-violations` Kafka topic. Manage rules via `POST/PUT/DELETE /api/policy-rules` on port 8087.
- **Compliance Engine**: Consumes `policy-violations`; for each `complianceFrameworks` entry on the violation, creates a `compliance_records` document. REST API at `GET /api/compliance/{framework}/records` and `GET /api/compliance/{framework}/summary` on port 8088.
- **Escalation**: Alert service runs a scheduled job (every 5 min by default) that promotes `OPEN` alerts from L1→L2 after `L1_TIMEOUT_MINUTES` (default 15) and L2→L3 after `L2_TIMEOUT_MINUTES` (default 30). Acknowledge alerts with `POST /api/alerts/{alertId}/acknowledge` on port 8085.
- **Code coverage**: JaCoCo enforces a 70% line coverage minimum; build fails if not met.

### Configuration

Copy `.env.example` to `.env` before running locally. Key variables:

- `LLM_PROVIDER` — `openai` or `azure`
- `OPENAI_API_KEY` / `AZURE_OPENAI_*` — LLM credentials
- `JWT_PUBLIC_KEY_PATH` — gateway JWT validation
- `RISK_FAILED_LOGIN_WINDOW_MINUTES`, `RISK_FAILED_LOGIN_THRESHOLD`, `RISK_ALERT_THRESHOLD` — risk engine tuning
- `L1_TIMEOUT_MINUTES`, `L2_TIMEOUT_MINUTES`, `ESCALATION_CHECK_INTERVAL_MS` — escalation thresholds

### Demo Tenant Credentials

- Tenant ID: `tenant-demo`
- API Key: `demo-api-key`
- Alert threshold: 60
