# AuditX — Identity Observability & Compliance Platform

**AuditX** is an enterprise-grade, event-driven platform for real-time identity audit, risk scoring, and compliance reporting. Built for IAM teams who need end-to-end visibility into user behaviour across applications, cloud environments, and on-prem systems.

[![CI](https://github.com/SachinJK213/auditx/actions/workflows/ci.yml/badge.svg)](https://github.com/SachinJK213/auditx/actions/workflows/ci.yml)

---

## What it does

| Capability | Detail |
|---|---|
| **Universal Ingestion** | REST API, file upload (JSON · CSV · Syslog · Raw), batch processing |
| **Real-time Parsing** | Auto-detect format, OCSF-aligned structured events |
| **Risk Scoring** | Rule-based 0–100 score; failed-login threshold, geo-anomaly, custom rules |
| **Live Stream** | Server-Sent Events feed — every event visible as it flows through the pipeline |
| **Policy Engine** | SpEL-based rule evaluation → policy violations published to Kafka |
| **Compliance** | GDPR · SOC2 · DPDP · ISO27001 mapping from policy violations |
| **Alerts & Escalation** | L1 → L2 → L3 escalation with webhook / email dispatch |
| **Reports** | On-demand HTML/PDF compliance reports |
| **LLM Integration** | AI-powered event explanation, natural-language query, and summary |
| **Dashboard** | React UI — live feed, event table, alert view, file upload, source explorer |

---

## Architecture

```
Client App / File Upload
        │
        ▼
  Ingestion (8081)          ← API Key auth, Redis idempotency
        │
        ▼  Kafka [raw-events]
  Parser (8082)             ← JSON / CSV / Syslog / Key=value auto-detect
        │
        ▼  Kafka [structured-events]
  ┌─────┴──────────────┐
  │                    │
  ▼                    ▼
Risk Engine (8083)   Policy Engine (8087)
  │ Kafka [alerts]      │ Kafka [policy-violations]
  ▼                    ▼
Alert (8085)         Compliance (8088)
  │                    │
  └────── MongoDB ──────┘

Live Stream (8089)    ← SSE fan-out of structured-events
Gateway (8080)        ← JWT auth + rate limiting (Redis)
LLM (8084)            ← OpenAI / Azure OpenAI
Report (8086)         ← HTML/PDF from MongoDB
Dashboard (3000)      ← React UI
```

---

## Service Map

| Service | Port | Role |
|---|---|---|
| `auditx-gateway` | 8080 | JWT auth, rate limiting, reverse proxy |
| `auditx-ingestion` | 8081 | REST intake (API key), file upload, idempotency |
| `auditx-parser` | 8082 | Format detection, event normalization |
| `auditx-risk-engine` | 8083 | Rule-based scoring (0–100), alert emission |
| `auditx-llm` | 8084 | AI explain / query / summarize |
| `auditx-alert` | 8085 | Alert persistence, escalation, dispatch |
| `auditx-report` | 8086 | Compliance report generation |
| `auditx-policy-engine` | 8087 | SpEL policy rule evaluation |
| `auditx-compliance` | 8088 | GDPR / SOC2 / DPDP / ISO27001 records |
| `auditx-live-stream` | 8089 | Server-Sent Events live log feed |
| `auditx-dashboard` | 3000 | React UI |
| Kibana | 5601 | Structured log explorer |
| Elasticsearch | 9200 | Log search backend |

---

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Docker Desktop | 24+ | `docker --version` |
| Docker Compose | 2.x | `docker compose version` |
| Java 21+ | optional — for local builds | `java -version` |
| Maven 3.9+ | optional — for local builds | `mvn -version` |

**Memory:** Docker needs at least **6 GB RAM** (ELK is memory-hungry).  
Set in Docker Desktop → Settings → Resources → Memory ≥ 6 GB.

**Windows users:** Run all shell commands in **Git Bash** or **WSL2**.

---

## Quick Start

### 1. Clone and configure

```bash
git clone https://github.com/SachinJK213/auditx.git
cd auditx
cp .env.example .env
```

Edit `.env` if you want to connect a real LLM:

```env
LLM_PROVIDER=openai          # or azure
LLM_API_KEY=sk-...           # your OpenAI key
```

All other defaults work out of the box.

### 2. Start the full stack

```bash
docker compose up --build
```

First run takes **5–10 minutes** (Maven downloads dependencies inside containers).

Watch startup progress:

```bash
docker compose logs -f --tail=20
```

Wait until you see:

```
auditx-ingestion   | Started AuditxIngestionApplication in ...
auditx-parser      | Started AuditxParserApplication in ...
auditx-risk-engine | Started AuditxRiskEngineApplication in ...
auditx-alert       | Started AuditxAlertApplication in ...
auditx-report      | Started AuditxReportApplication in ...
auditx-llm         | Started AuditxLlmApplication in ...
auditx-kibana      | Kibana is now available
```

### 3. Verify all services are healthy

```bash
docker compose ps
```

All AUDITX services: `running`. Infrastructure (mongodb, redis, kafka, elasticsearch): `healthy`.

```bash
for port in 8080 8081 8082 8083 8084 8085 8086 8087 8088 8089; do
  status=$(curl -s http://localhost:$port/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','?'))" 2>/dev/null)
  echo "Port $port: $status"
done
```

### 4. Seed demo data

```bash
bash demo/seed-demo-data.sh
```

Inserts the demo tenant, API key, risk rules, and fires 9 test events through the pipeline.

### 5. Open the dashboard

**http://localhost:3000**

The dashboard gives you:
- **Live Feed** — real-time event stream via SSE
- **Events** — stored events with risk badges and filtering
- **Alerts** — fired alerts with escalation status
- **Upload** — drag-and-drop file upload (JSON, CSV, Syslog, Raw)
- **Sources** — ingestion source reference

### 6. Open Kibana (optional)

```bash
bash elk/setup-kibana.sh   # creates auditx-logs-* index pattern
```

Then open **http://localhost:5601** → Discover → `auditx-logs-*` → Last 1 hour.

---

## Sending Events

### REST API — single event

```bash
curl -s -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-api-key" \
  -d '{
    "payload": "timestamp=2024-06-01T12:00:00Z userId=alice action=LOGIN sourceIp=192.168.1.10 tenantId=tenant-demo outcome=SUCCESS",
    "payloadType": "RAW",
    "idempotencyKey": "test-001"
  }'
```

### File Upload — batch ingest

Upload a file with one event per line. Supported formats:

**JSON** (`.json` / `.jsonl`):
```json
{"userId":"alice","action":"LOGIN","sourceIp":"192.168.1.10","tenantId":"tenant-demo","outcome":"SUCCESS"}
{"userId":"mallory","action":"LOGIN","sourceIp":"203.0.113.42","tenantId":"tenant-demo","outcome":"FAILURE"}
```

**CSV** (`.csv`) — `timestamp,userId,action,sourceIp,tenantId,outcome`:
```
2024-06-01T12:00:00Z,alice,LOGIN,192.168.1.10,tenant-demo,SUCCESS
2024-06-01T12:01:00Z,mallory,LOGIN,203.0.113.42,tenant-demo,FAILURE
```

**Syslog** (`.log` / `.syslog`) — RFC 3164:
```
<34>2024-06-01T12:00:00Z host app: userId=alice action=LOGIN outcome=SUCCESS sourceIp=192.168.1.10 tenantId=tenant-demo
```

Upload via curl:
```bash
curl -s -X POST http://localhost:8081/api/v1/ingest/upload \
  -H "X-API-Key: demo-api-key" \
  -F "file=@events.json"
```

Response:
```json
{"total":2,"accepted":2,"failed":0,"eventIds":["uuid-1","uuid-2"]}
```

Or use the **Upload** tab in the dashboard at **http://localhost:3000**.

---

## Verify the Pipeline

After sending events, wait ~5 seconds for Kafka consumers, then:

```bash
bash demo/verify-pipeline.sh
```

Expected:
```
✓ auditx-gateway is UP        ✓ auditx-ingestion is UP
✓ auditx-parser is UP         ✓ auditx-risk-engine is UP
✓ auditx-llm is UP            ✓ auditx-alert is UP
✓ auditx-report is UP

✓ audit_events: 8 documents
✓ alerts: 1 documents
✓ auditx-logs-* index: 47 documents
✓ POST /api/reports/generate → 200
```

---

## Generate a Compliance Report

```bash
curl -s -X POST http://localhost:8086/api/reports/generate \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant-demo",
    "startDate": "2024-01-01",
    "endDate": "2024-12-31",
    "reportType": "FULL"
  }' -o report.html

# Open report.html in a browser
```

Or click **Compliance Report → Download** in the dashboard.

---

## Demo Credentials

| Item | Value |
|---|---|
| Tenant ID | `tenant-demo` |
| API Key | `demo-api-key` |
| Dashboard | http://localhost:3000 |
| Alert threshold | 60 (risk score > 60 fires an alert) |

---

## Running Tests

### Unit tests (no Docker)

```bash
mvn test --no-transfer-progress
```

### Integration tests (Testcontainers — Docker required)

```bash
mvn test \
  -pl auditx-common,auditx-ingestion,auditx-parser,auditx-live-stream \
  -Dtest="IngestionPipelineIntegrationTest,ParserPipelineIntegrationTest,LiveStreamIntegrationTest" \
  --no-transfer-progress
```

### E2E tests (full stack must be running)

```bash
bash demo/seed-demo-data.sh
bash test/e2e/run_all.sh
```

See [TESTING.md](TESTING.md) for the full testing guide.

---

## Configuration

Copy `.env.example` to `.env` and adjust as needed:

| Variable | Default | Description |
|---|---|---|
| `LLM_PROVIDER` | `openai` | `openai` or `azure` |
| `LLM_API_KEY` | `placeholder` | OpenAI API key |
| `AZURE_OPENAI_ENDPOINT` | — | Azure endpoint URL |
| `AZURE_OPENAI_DEPLOYMENT` | `gpt-4o-mini` | Azure deployment name |
| `FAILED_LOGIN_THRESHOLD` | `5` | Failed logins before risk rule fires |
| `FAILED_LOGIN_WINDOW_MINUTES` | `60` | Sliding window for failed login count |
| `GEO_ANOMALY_LOOKBACK_DAYS` | `30` | Days of history for geo anomaly detection |
| `L1_TIMEOUT_MINUTES` | `15` | Minutes before L1 alert escalates to L2 |
| `L2_TIMEOUT_MINUTES` | `30` | Minutes before L2 alert escalates to L3 |
| `RATE_LIMIT_REPLENISH` | `10` | Gateway rate limit (requests/second) |
| `RATE_LIMIT_BURST` | `20` | Gateway burst capacity |

---

## Tear Down

```bash
# Stop all containers (keep data)
docker compose down

# Stop and wipe all data (clean slate)
docker compose down -v
```

---

## Project Structure

```
auditx/
├── auditx-common/          # Shared DTOs, enums, Kafka utils, MDC
├── auditx-gateway/         # JWT auth + rate limiting (Spring Cloud Gateway)
├── auditx-ingestion/       # Event intake — REST + file upload
├── auditx-parser/          # Format detection + event normalization
├── auditx-risk-engine/     # Rule-based risk scoring
├── auditx-llm/             # AI explain / query / summarize
├── auditx-alert/           # Alert dispatch + escalation
├── auditx-report/          # HTML/PDF compliance report generator
├── auditx-policy-engine/   # SpEL policy rule engine
├── auditx-compliance/      # GDPR / SOC2 / DPDP / ISO27001 records
├── auditx-live-stream/     # SSE real-time event feed
├── auditx-sdk/             # Client SDK (Spring Boot auto-config)
├── auditx-dashboard/       # React 18 frontend
├── elk/                    # ELK stack config (Logstash pipeline, Kibana, Filebeat)
├── demo/                   # Seed data + verification scripts
├── test/e2e/               # End-to-end bash test suite
├── .github/workflows/      # GitHub Actions CI
├── docker-compose.yml      # Full 15-container stack
├── RUNNING.md              # Detailed run and test guide
├── TESTING.md              # Test layer documentation
└── SPECIFICATION_V2.md     # Full enterprise platform specification
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (virtual threads) |
| Framework | Spring Boot 3.2.5, Spring WebFlux (reactive) |
| Messaging | Apache Kafka |
| Database | MongoDB 7 (reactive) |
| Cache / Rate limit | Redis 7 |
| Frontend | React 18 |
| Containerisation | Docker + Docker Compose |
| Observability | ELK Stack (Elasticsearch, Logstash, Kibana, Filebeat) |
| Testing | JUnit 5, Mockito, StepVerifier, Testcontainers |
| CI | GitHub Actions |

---

## Roadmap

| Phase | Theme | Status |
|---|---|---|
| Phase 0 | Core pipeline, live stream, React dashboard | ✅ Complete |
| Phase 1 | Multi-format ingestion (JSON/CSV/Syslog), file upload, full test suite | ✅ Complete |
| Phase 2 | OCSF normalization, webhook connectors, CloudWatch, PII detection | Planned |
| Phase 3 | Anomaly detection (EWMA + Isolation Forest), ACRS risk formula, SIEM integrations | Planned |

---

Built by [Cross Identity](https://www.crossidentity.com) · AuditX Platform
