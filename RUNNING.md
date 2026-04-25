# AUDITX — Running & Testing Guide

This guide takes you from a clean checkout to a fully running platform with ELK log visibility in Kibana.

---

## Prerequisites

Install these before starting:

| Tool | Version | Check |
|---|---|---|
| Java | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker Desktop | 24+ | `docker -version` |
| Docker Compose | 2.x | `docker compose version` |
| curl | any | `curl --version` |

**Windows users:** run all commands in **Git Bash** or **WSL2**, not PowerShell or CMD.

**Memory:** Docker needs at least **6 GB RAM** allocated (ELK is memory-hungry). In Docker Desktop → Settings → Resources → set Memory to 6 GB or more.

---

## Step 1 — Build all JARs

The Dockerfiles build inside the container, so you do **not** need to build locally first. Docker handles it. Skip to Step 2.

If you want to verify the code compiles locally before running Docker:

```bash
mvn compile --no-transfer-progress
```

Expected: `BUILD SUCCESS` for all 10 modules.

---

## Step 2 — Start the full stack

```bash
docker compose up --build
```

This builds all 7 service images and starts 15 containers total. **First run takes 5–10 minutes** because Maven downloads dependencies inside the build containers.

Open a second terminal and watch the startup:

```bash
docker compose logs -f --tail=20
```

Wait until you see these lines (in any order):

```
auditx-ingestion   | Started AuditxIngestionApplication in ...
auditx-parser      | Started AuditxParserApplication in ...
auditx-risk-engine | Started AuditxRiskEngineApplication in ...
auditx-alert       | Started AuditxAlertApplication in ...
auditx-report      | Started AuditxReportApplication in ...
auditx-llm         | Started AuditxLlmApplication in ...
auditx-kibana      | [info][status] Kibana is now available
```

Check all containers are healthy:

```bash
docker compose ps
```

All AUDITX services should show `running`. Infrastructure (mongodb, redis, kafka, elasticsearch) should show `healthy`.

---

## Step 3 — Verify health endpoints

Run this in your terminal to confirm all 7 services are up:

```bash
for port in 8080 8081 8082 8083 8084 8085 8086; do
  status=$(curl -s http://localhost:$port/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','?'))" 2>/dev/null)
  echo "Port $port: $status"
done
```

Expected output:
```
Port 8080: UP    ← gateway
Port 8081: UP    ← ingestion
Port 8082: UP    ← parser
Port 8083: UP    ← risk-engine
Port 8084: UP    ← llm
Port 8085: UP    ← alert
Port 8086: UP    ← report
```

If a service shows `DOWN` or connection refused, check its logs:

```bash
docker logs auditx-ingestion --tail=50
```

---

## Step 4 — Set up Kibana index pattern

Run this once after Kibana is ready:

```bash
bash elk/setup-kibana.sh
```

This creates the `auditx-logs-*` index pattern and two saved searches. You'll see JSON responses confirming creation.

Then open Kibana: **http://localhost:5601**

---

## Step 5 — Seed demo data

This script inserts a test tenant, risk rules, and fires 9 events through the ingestion API:

```bash
bash demo/seed-demo-data.sh
```

What it does:
- Inserts `tenant-demo` with API key `demo-api-key` into MongoDB
- Inserts 3 risk rules (failed login threshold, geo anomaly, custom)
- Fires events: normal logins, 4 failed logins from `mallory` (triggers alert), geo anomaly, malformed log
- Tests idempotency (same key → 200) and missing API key (→ 401)

---

## Step 6 — Verify the pipeline

Wait ~5 seconds for Kafka consumers to process, then:

```bash
bash demo/verify-pipeline.sh
```

Expected output:
```
── Service Health ──────────────────────────────────────────────
  ✓ auditx-gateway is UP
  ✓ auditx-ingestion is UP
  ✓ auditx-parser is UP
  ✓ auditx-risk-engine is UP
  ✓ auditx-llm is UP
  ✓ auditx-alert is UP
  ✓ auditx-report is UP

── MongoDB — Pipeline Data ─────────────────────────────────────
  ✓ audit_events: 8 documents
  ✓ raw_logs (failed parses): 1 documents
  ✓ alerts: 1 documents

── Elasticsearch — Log Index ───────────────────────────────────
  ✓ auditx-logs-* index: 47 documents

── Report Service ───────────────────────────────────────────────
  ✓ POST /api/reports/generate → 200
  ✓ Missing tenantId → 400
```

---

## Step 7 — Explore Kibana

Open **http://localhost:5601**

1. Click **Discover** in the left sidebar
2. Select index pattern `auditx-logs-*`
3. Set time range to **Last 1 hour** (top right clock icon)

You'll see structured log entries with these fields as columns:
`service`, `level`, `traceId`, `tenantId`, `userId`, `eventId`, `log_message`

### Useful KQL searches

Paste these into the search bar at the top:

```
# All errors across all services
level : "ERROR"

# Trace one request end-to-end (copy a traceId from any log row)
traceId : "paste-a-trace-id-here"

# All activity for the demo tenant
tenantId : "tenant-demo"

# Watch the parser process events
service : "auditx-parser"

# See the failed parse (malformed log)
service : "auditx-parser" and log_message : *FAILED*

# See risk scoring
service : "auditx-risk-engine"

# See the alert that fired for mallory
service : "auditx-risk-engine" and log_message : *Alert*

# Filter by user
userId : "mallory"
```

---

## Manual API Testing

### Ingest a raw event

```bash
curl -s -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-api-key" \
  -d '{
    "payload": "timestamp=2024-06-01T12:00:00Z userId=charlie action=LOGIN sourceIp=198.51.100.1 tenantId=tenant-demo outcome=SUCCESS",
    "payloadType": "RAW",
    "idempotencyKey": "manual-001"
  }'
```

Expected: `{"eventId":"<uuid>"}`

### Test idempotency (same key → 200)

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
  -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-api-key" \
  -d '{"payload":"test","payloadType":"RAW","idempotencyKey":"manual-001"}'
```

Expected: `HTTP 200`

### Test missing API key → 401

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
  -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -d '{"payload":"test","payloadType":"RAW"}'
```

Expected: `HTTP 401`

### Generate a compliance report

```bash
curl -s -X POST http://localhost:8086/api/reports/generate \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant-demo",
    "startDate": "2024-01-01",
    "endDate": "2024-12-31",
    "reportType": "FULL"
  }' -o report.html

echo "Report saved to report.html — open it in a browser"
```

### Test report with missing tenantId → 400

```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
  -X POST http://localhost:8086/api/reports/generate \
  -H "Content-Type: application/json" \
  -d '{"startDate":"2024-01-01","endDate":"2024-12-31","reportType":"FULL"}'
```

Expected: `HTTP 400`

### Check MongoDB directly

```bash
# See parsed events
docker exec -it auditx-mongodb mongosh auditx --eval \
  "db.audit_events.find({tenantId:'tenant-demo'},{userId:1,action:1,outcome:1,riskScore:1,_id:0}).pretty()"

# See alerts
docker exec -it auditx-mongodb mongosh auditx --eval \
  "db.alerts.find({tenantId:'tenant-demo'}).pretty()"

# See failed parses
docker exec -it auditx-mongodb mongosh auditx --eval \
  "db.raw_logs.find({tenantId:'tenant-demo'}).pretty()"
```

### Watch Kafka topics live

Open three terminals:

```bash
# Terminal 1 — raw events (ingestion → Kafka)
docker exec auditx-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic raw-events --from-beginning

# Terminal 2 — structured events (parser → Kafka)
docker exec auditx-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic structured-events --from-beginning

# Terminal 3 — alerts (risk-engine → Kafka)
docker exec auditx-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic alerts --from-beginning
```

Then post a new event (Step 7 above) and watch it flow through all three topics.

---

## Troubleshooting

### A service won't start

```bash
# Check its logs
docker logs auditx-ingestion --tail=100

# Restart just that service
docker compose restart auditx-ingestion
```

### Elasticsearch is unhealthy

It needs time and memory. Check:

```bash
docker logs auditx-elasticsearch --tail=30
curl http://localhost:9200/_cluster/health
```

If it shows `red`, increase Docker memory to 6+ GB in Docker Desktop settings.

### Kibana shows no data

Filebeat needs the Docker socket. On Linux, check:

```bash
docker logs auditx-filebeat --tail=30
```

If you see permission errors on `/var/run/docker.sock`, run:

```bash
sudo chmod 666 /var/run/docker.sock
docker compose restart filebeat
```

### Gateway returns 401 on all requests

The gateway validates JWTs. For direct testing, bypass the gateway and call services directly on their ports (8081–8086). The gateway is only needed when routing through port 8080 with a valid JWT.

### Port already in use

Edit `.env` to change any port:

```env
INGESTION_PORT=9081
```

Then restart: `docker compose up -d`

---

## Running Unit Tests Only (no Docker needed)

```bash
# All unit tests
mvn test --no-transfer-progress

# Single module
mvn test -pl auditx-common --no-transfer-progress
mvn test -pl auditx-parser --no-transfer-progress
```

## Running Integration Tests (Docker required)

```bash
# Ingestion pipeline integration test (spins up Testcontainers)
mvn test -pl auditx-common,auditx-ingestion \
  -Dtest=IngestionPipelineIntegrationTest \
  --no-transfer-progress
```

---

## Tear Down

```bash
# Stop all containers, keep data volumes
docker compose down

# Stop and delete all data (clean slate)
docker compose down -v
```

---

## Port Reference

| Service | URL | Purpose |
|---|---|---|
| Gateway | http://localhost:8080 | JWT-authenticated entry point |
| Ingestion | http://localhost:8081 | POST raw events (API key auth) |
| Parser | http://localhost:8082 | Health check only |
| Risk Engine | http://localhost:8083 | Health check only |
| LLM | http://localhost:8084 | AI explain/query/summarize |
| Alert | http://localhost:8085 | Health check only |
| Report | http://localhost:8086 | PDF report generation |
| Live Stream | http://localhost:8089 | SSE live log feed |
| Kibana | http://localhost:5601 | Log viewer UI |
| Elasticsearch | http://localhost:9200 | Log search API |
| MongoDB | localhost:27017 | Primary data store |
| Kafka | localhost:9092 | Event streaming |
| Redis | localhost:6379 | Rate limiting + idempotency |

---

## Demo Credentials

| Item | Value |
|---|---|
| Tenant ID | `tenant-demo` |
| API Key | `demo-api-key` |
| API Key Hash (SHA-256) | `b94f6f125c79e3a5ffaa826f584c10d52ada669e6762051b826b55776d05a15a` |
| Alert threshold | 60 (risk score > 60 triggers alert) |
