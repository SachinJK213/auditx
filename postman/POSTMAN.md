# AuditX — Postman Collection Guide

## Import

1. Open Postman
2. Click **Import** (top left)
3. Select `AuditX-API.postman_collection.json` from this folder
4. The **AuditX Platform — Complete API** collection appears in the sidebar

## Configure Variables

Click the collection name → **Variables** tab. Confirm:

| Variable | Default | Notes |
|---|---|---|
| `tenant_id` | `tenant-demo` | Your tenant identifier |
| `api_key` | `demo-api-key` | API key for the demo tenant |
| `base_ingestion` | `http://localhost:8081` | Change if using a different port |
| `base_report` | `http://localhost:8086` | Dashboard + report service |

All other base URLs default to the correct local ports.

## Prerequisites

The full stack must be running:
```bash
docker compose up --build -d
bash demo/seed-demo-data.sh
```

## Quick Start — Demo Flow

Run these requests in order to see the full pipeline:

### Step 1 — Verify all services
Run all requests in **🏥 Health Checks**. Every response should be:
```json
{"status":"UP"}
```

### Step 2 — Send events
In **📥 Ingestion — REST API**:
1. Run "Ingest Raw Event (Key=Value)" → expect HTTP 202 + `{"eventId":"..."}`
2. Run "Ingest JSON Event" → 202
3. Run "Ingest Failed Login" × 5 (change `idempotencyKey` each time) → triggers risk alert

### Step 3 — Watch the pipeline
Wait 5 seconds for Kafka consumers. Then:
- **Dashboard Summary** → totalEvents > 0
- **Dashboard Alerts** → alert fired for mallory
- **Get User Risk Profile** (userId=mallory) → riskScore > 0

### Step 4 — Check compliance
- **GDPR Summary** → violations recorded
- **PII Summary** → any events with email-format userIds flagged

### Step 5 — Generate report
Run **Generate Full Compliance Report** → saves HTML body as response (preview in Postman)

---

## Webhook Demo

Register a source, then simulate an external system:

1. Run "Register Webhook Source" → creates `github-prod` source for `tenant-demo`
2. Run "Send Event via Webhook (GitHub-style)" → event enters the pipeline without API key

---

## PII Detection Demo

Send an event with a user ID that looks like an email:
```bash
curl -X POST http://localhost:8081/api/events/raw \
  -H "X-API-Key: demo-api-key" \
  -H "Content-Type: application/json" \
  -d '{"payload":"timestamp=2024-06-01T12:00:00Z userId=sachin@crossidentity.com action=LOGIN sourceIp=10.0.0.1 tenantId=tenant-demo outcome=SUCCESS","payloadType":"RAW","idempotencyKey":"pii-demo-001"}'
```

Then run **PII Findings (7 days)** — the event will be flagged with `EMAIL` type.

---

## Slack Notifications Demo

1. Create a Slack Incoming Webhook at [api.slack.com/apps](https://api.slack.com/apps)
2. Run "Add Slack Channel" with your webhook URL
3. Send several failed login events to trigger an alert
4. When the risk engine fires an alert, Slack receives a message

---

## Geo-IP Enrichment Demo

1. Send an event with a public IP (not private range):
   - sourceIp=`8.8.8.8` (Google DNS — shows as US, Mountain View)
   - sourceIp=`103.26.10.1` (shows as India)
2. Run **Enriched Events** → country, city, ISP fields populated

---

## Tips

- **SSE Live Stream**: Postman doesn't support SSE natively. Use the dashboard at `http://localhost:3000` or `curl http://localhost:8089/api/v1/stream/live?tenantId=tenant-demo`
- **File Upload**: Use the Postman Body → form-data tab, add key `file` as type File
- **Alert ID**: Copy an alertId from "List Alerts" and paste into "Acknowledge Alert"
- **LLM events**: Copy an eventId from "Dashboard Events" and paste into "Explain Event"
- **idempotencyKey**: Must be unique per event — change it each time you want a new event stored

---

## Port Reference

| Service | Port | Folder |
|---|---|---|
| Ingestion | 8081 | 📥 Ingestion — REST API |
| Parser | 8082 | 🏥 Health Checks |
| Risk Engine | 8083 | 🔴 Risk Engine |
| LLM | 8084 | 🤖 LLM — AI Features |
| Alert | 8085 | ⚠️ Alerts |
| Report / Dashboard | 8086 | 📊 Dashboard & Reports |
| Policy Engine | 8087 | 📋 Policy Rules |
| Compliance | 8088 | ✅ Compliance |
| Live Stream | 8089 | (use curl or dashboard) |
| PII Detector | 8090 | 🔍 PII Detection |
| Enrichment | 8091 | 🌍 Geo-IP Enrichment |
| Notification | 8092 | 🔔 Notifications |
