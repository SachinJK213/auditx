# AUDITX — Team & Management Demo Guide

This guide walks you through a live demo of the AUDITX platform. It's structured as a
narrative you can follow while screen-sharing, with talking points at each step.

---

## Before the Demo (Prep — 10 min before)

Run these commands before your audience joins. The stack takes a few minutes to start.

```bash
# 1. Start everything
docker compose up --build -d

# 2. Wait for all services to be healthy (~3-5 min)
docker compose ps

# 3. Seed demo data
bash demo/seed-demo-data.sh

# 4. Set up Kibana
bash elk/setup-kibana.sh
```

Verify everything is ready:
```bash
bash demo/verify-pipeline.sh
```

All checks should show ✓. Now you're ready.

Open these browser tabs before starting:
- Tab 1: **http://localhost:3000** (AUDITX Dashboard — main UI)
- Tab 2: http://localhost:5601 (Kibana)
- Tab 3: http://localhost:8081/actuator/health (Ingestion health)
- Tab 4: A terminal window

---

## Demo Script (20–30 minutes)

---

### Scene 0 — "The Dashboard" (3 min) ← START HERE

**What to show:** Open http://localhost:3000 in full screen.

**Talking points:**

> "Before we dive into the internals, let me show you what the platform looks like from
> the outside. This is the AUDITX dashboard — a real-time view of everything happening
> across your tenant."

Point out the four stat cards at the top:

> "Total events processed, high-risk events — those with a score of 70 or above —
> total alerts that fired, and the average risk score across all events. All scoped
> to the selected tenant and time period."

Scroll down to the events table:

> "Every event is here with its risk score badge — LOW, MEDIUM, HIGH, or CRITICAL.
> Green outcomes, red failures. You can see at a glance which users are generating
> suspicious activity."

Point to the action breakdown chart on the right:

> "And here's the breakdown by action type — LOGIN, LOGOUT, DELETE_USER — so you can
> spot unusual patterns immediately."

> "The dashboard is talking directly to the backend APIs in real time. Let me show you
> what happens when we send a new event."

---

### Scene 1 — "What is AUDITX?" (2 min)

**What to show:** Architecture diagram or just talk through it.

**Talking points:**

> "AUDITX is a production-grade Identity Observability platform. Think of it as the
> security nervous system for any application — it captures every login, every action,
> every API call, scores the risk in real time, fires alerts when something looks wrong,
> and gives compliance teams a one-click PDF report.
>
> It's built as 7 independent microservices on Java 21 with virtual threads, Spring Boot 3,
> Kafka for async event streaming, MongoDB for storage, and Redis for rate limiting.
> Everything runs in Docker with a single command."

**Architecture flow to describe:**
```
Client App → API Gateway (JWT auth + rate limiting)
           → Ingestion Service (API key auth, idempotency)
           → Kafka [raw-events]
           → Parser Service (regex extraction → structured JSON)
           → Kafka [structured-events]
           → Risk Engine (rule-based scoring 0-100)
           → Kafka [alerts] if score > threshold
           → Alert Service (webhook + email dispatch)
           → Report Service (PDF generation)
           → ELK Stack (all logs visible in Kibana)
```

---

### Scene 2 — "The platform is running" (2 min)

**What to show:** Terminal + browser health checks.

```bash
docker compose ps
```

**Talking points:**

> "With one command — `docker compose up` — we have 15 containers running: 7 microservices,
> MongoDB, Redis, Kafka, ZooKeeper, Elasticsearch, Logstash, Filebeat, and Kibana.
> Every service exposes a health endpoint."

Show in browser: http://localhost:8081/actuator/health

> "Each service reports its own health. In production this feeds into your monitoring
> system — Prometheus, Datadog, whatever you use."

---

### Scene 3 — "Ingesting an event — live on the dashboard" (3 min)

**What to show:** Dashboard "Ingest Live Event" form + terminal side by side.

Switch to the dashboard tab: **http://localhost:3000**

Scroll to the bottom-left panel — "Ingest Live Event".

**Talking points:**

> "Instead of using curl, let me use the dashboard form. I'll set the user to 'mallory',
> action to LOGIN, outcome to FAILURE, and hit Send."

Fill in the form:
- User ID: `mallory`
- Action: `LOGIN`
- Source IP: `203.0.113.42`
- Outcome: `FAILURE`

Click **Send Event**.

> "The green confirmation shows the eventId — HTTP 202 Accepted. The event is now in Kafka.
> I'll wait about 3 seconds for the pipeline to process it, then hit Refresh."

Click **↻ Refresh** (top right).

> "The event count went up by one. And notice mallory's event appears in the table with
> a risk score badge. The parser extracted the fields, the risk engine scored it,
> all automatically."

Now show the same thing via terminal to prove it's real:

```bash
curl -s -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-api-key" \
  -d '{
    "payload": "timestamp=2024-06-01T10:00:00Z userId=alice action=LOGIN sourceIp=192.168.1.10 tenantId=tenant-demo outcome=SUCCESS",
    "payloadType": "RAW",
    "idempotencyKey": "live-demo-001"
  }'
```

> "The dashboard form and the API are the same thing — the UI just wraps the REST call.
> Any client application can integrate the same way."

Show idempotency:

```bash
# Same idempotencyKey — should return HTTP 200 with same eventId
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" \
  -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-api-key" \
  -d '{"payload":"...","payloadType":"RAW","idempotencyKey":"live-demo-001"}'
```

> "HTTP 200 — idempotent. The event was not duplicated."

Show security:

```bash
# No API key — should return 401
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" \
  -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -d '{"payload":"test","payloadType":"RAW"}'
```

> "HTTP 401. No API key, no entry."

---

### Scene 4 — "The pipeline processes it automatically" (3 min)

**What to show:** MongoDB data.

**Talking points:**

> "Behind the scenes, the Parser Service consumed that event from Kafka, extracted the
> structured fields using regex, and stored it in MongoDB. The Risk Engine then scored it.
> Let me show you the data."

```bash
docker exec -it auditx-mongodb mongosh auditx --eval \
  "db.audit_events.find({tenantId:'tenant-demo'},{userId:1,action:1,outcome:1,riskScore:1,_id:0}).limit(5).pretty()"
```

> "Every event has a userId, action, outcome, and a riskScore computed by the Risk Engine.
> This all happened automatically — no manual processing."

Show the failed parse:

```bash
docker exec -it auditx-mongodb mongosh auditx --eval \
  "db.raw_logs.find({tenantId:'tenant-demo'}).pretty()"
```

> "When a log line doesn't match any parsing strategy, it lands in raw_logs with
> parseStatus FAILED. Nothing is lost — it's stored for manual review."

---

### Scene 5 — "Risk scoring and automatic alerts" (4 min)

**What to show:** Alerts in MongoDB + the risk rules.

**Talking points:**

> "The Risk Engine applies configurable rules to every event. We have three rules set up
> for this demo: a failed login threshold rule, a geo-anomaly rule, and a custom rule."

Show the rules:

```bash
docker exec -it auditx-mongodb mongosh auditx --eval \
  "db.risk_rules.find({tenantId:'tenant-demo'},{ruleName:1,ruleType:1,weight:1,threshold:1,_id:0}).pretty()"
```

> "The failed login threshold rule: if a user has 3 or more failed logins within 10 minutes,
> the risk score jumps by 50 points. Our demo user 'mallory' triggered this."

Show the alert:

```bash
docker exec -it auditx-mongodb mongosh auditx --eval \
  "db.alerts.find({tenantId:'tenant-demo'}).pretty()"
```

> "An alert was automatically created and dispatched. The Alert Service consumed it from
> Kafka, persisted it, and would send a webhook or email to the security team.
> The alert threshold is configurable per tenant — here it's set to 60."

---

### Scene 6 — "Compliance report in one click" (3 min)

**What to show:** curl command + open the HTML file.

**Talking points:**

> "Compliance officers need to submit audit evidence to regulators. Instead of manually
> exporting data, they call one endpoint."

```bash
curl -s -X POST http://localhost:8086/api/reports/generate \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "tenant-demo",
    "startDate": "2024-01-01",
    "endDate": "2024-12-31",
    "reportType": "FULL"
  }' -o demo-report.html

echo "Report saved — opening..."
```

Open `demo-report.html` in a browser.

> "The report contains: total event count, breakdown by action type, all high-risk events
> with scores above 70, and all alerts in the date range. It comes back as a PDF-ready
> HTML document. The renderer interface is designed to plug in Puppeteer for true PDF
> output in production."

Show the 400 validation:

```bash
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" \
  -X POST http://localhost:8086/api/reports/generate \
  -H "Content-Type: application/json" \
  -d '{"startDate":"2024-01-01","endDate":"2024-12-31","reportType":"FULL"}'
```

> "Missing tenantId returns 400. Every endpoint enforces tenant isolation — you can never
> accidentally query another tenant's data."

---

### Scene 7 — "Live log visibility in Kibana" (5 min)

**What to show:** Kibana Discover view.

Switch to the Kibana browser tab: **http://localhost:5601**

Navigate to: Discover → select `auditx-logs-*` → Last 1 hour

**Talking points:**

> "Every service emits structured JSON logs. Filebeat ships them to Logstash, which
> parses and indexes them into Elasticsearch. Kibana gives us a real-time view."

Point out the columns:

> "Notice the fields: service, level, traceId, tenantId, userId, eventId. These are
> the same MDC fields we inject at every request boundary. Every log line has them."

**Demo 1 — Trace a request end-to-end:**

Click any log row from `auditx-ingestion`, expand it, copy the `traceId` value.

Type in the search bar:
```
traceId : "paste-the-value-here"
```

> "This single traceId shows the same request flowing through ingestion, then the parser,
> then the risk engine. Full distributed tracing without any external tracing infrastructure —
> just MDC propagation through Kafka headers."

**Demo 2 — Find the security incident:**

```
userId : "mallory"
```

> "Every action by mallory is here — all 4 failed logins, the risk scoring, the alert.
> A security analyst can reconstruct the entire incident timeline from this view."

**Demo 3 — Filter by service:**

```
service : "auditx-risk-engine" and log_message : *Alert*
```

> "We can filter to just the risk engine and see exactly when alerts fired and why."

**Demo 4 — Errors only:**

```
level : "ERROR"
```

> "In production, you'd set up an alert in Kibana to notify on-call when ERROR count
> spikes. All the data is already here."

---

### Scene 8 — "Multi-tenancy" (2 min)

**What to show:** Talk through it, optionally show MongoDB query.

**Talking points:**

> "Every single database query, every Kafka message, every API call is scoped to a tenantId.
> It's not optional — if a request arrives without a resolvable tenantId, it's rejected
> with HTTP 400 before any database operation runs.
>
> MongoDB has indexes on tenantId in every collection. The LLM query endpoint enforces
> tenantId even on AI-generated MongoDB queries — the system injects the filter before
> execution so the LLM can't accidentally return another tenant's data."

```bash
# Show tenant isolation — query without tenantId returns nothing
docker exec -it auditx-mongodb mongosh auditx --eval \
  "db.audit_events.countDocuments({})"

# vs scoped query
docker exec -it auditx-mongodb mongosh auditx --eval \
  "db.audit_events.countDocuments({tenantId:'tenant-demo'})"
```

---

### Scene 9 — "The SDK" (2 min, optional)

**What to show:** Code snippet or just talk through it.

**Talking points:**

> "Client applications don't need to write any integration code. They add the auditx-sdk
> dependency to their pom.xml and add three lines to application.yml:"

```yaml
auditx:
  enabled: true
  endpoint: http://auditx-ingestion:8081/api/events/raw
  api-key: their-api-key
  tenant-id: their-tenant-id
  async: true
```

> "That's it. The SDK auto-configures a Spring HandlerInterceptor that captures every
> HTTP request — method, path, status code, userId from the security context, duration.
> It sends events asynchronously on Java 21 virtual threads so there's zero impact on
> request latency. It retries on failure with exponential backoff and never throws
> exceptions to the calling application."

---

### Closing (1 min)

**Talking points:**

> "To summarize what we've just seen:
>
> - A complete event-driven audit pipeline running on a single `docker compose up`
> - Real-time risk scoring with configurable rules per tenant
> - Automatic alert dispatch when thresholds are exceeded
> - One-click compliance PDF reports
> - Full distributed tracing across 7 services visible in Kibana
> - Strict multi-tenancy enforced at every layer
> - A drop-in SDK for client applications
>
> The entire platform is Java 21 with virtual threads, Spring Boot 3, and runs on
> standard infrastructure — MongoDB, Redis, Kafka. No proprietary dependencies."

---

## Quick Reset Between Demos

If you need to run the demo again for a second audience:

```bash
# Wipe all data and re-seed
docker exec auditx-mongodb mongosh auditx --eval \
  "db.audit_events.deleteMany({}); db.alerts.deleteMany({}); db.raw_logs.deleteMany({}); db.tenants.deleteMany({}); db.risk_rules.deleteMany({});"

bash demo/seed-demo-data.sh
```

---

## If Something Goes Wrong During the Demo

| Problem | Quick fix |
|---|---|
| Service not responding | `docker compose restart auditx-ingestion` |
| No data in Kibana | `docker compose restart filebeat` then wait 30s |
| MongoDB query returns nothing | Wait 5s for Kafka consumers, then retry |
| Port conflict | Check `docker compose ps` for unhealthy containers |
| Kibana blank screen | Hard refresh (Ctrl+Shift+R), wait 30s |

Check any service log instantly:
```bash
docker logs auditx-ingestion --tail=20
docker logs auditx-risk-engine --tail=20
```

---

## Demo Credentials Reference

| Item | Value |
|---|---|
| Tenant ID | `tenant-demo` |
| API Key | `demo-api-key` |
| Alert threshold | 60 (risk score > 60 triggers alert) |
| Kibana | http://localhost:5601 |
| Ingestion API | http://localhost:8081 |
| Report API | http://localhost:8086 |
