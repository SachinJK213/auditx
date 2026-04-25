#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# AUDITX Pipeline Verification Script
# Checks each stage of the pipeline has data after seeding.
# ─────────────────────────────────────────────────────────────────────────────

MONGO_CONTAINER="${MONGO_CONTAINER:-auditx-mongodb}"
ES_URL="${ES_URL:-http://localhost:9200}"
INGESTION_URL="${INGESTION_URL:-http://localhost:8081}"
REPORT_URL="${REPORT_URL:-http://localhost:8086}"

pass() { echo "  ✓ $1"; }
fail() { echo "  ✗ $1"; }
section() { echo ""; echo "── $1 ──────────────────────────────────────────"; }

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " AUDITX Pipeline Verification"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── Health checks ─────────────────────────────────────────────────────────────
section "Service Health"
for svc in "8080:gateway" "8081:ingestion" "8082:parser" "8083:risk-engine" "8084:llm" "8085:alert" "8086:report"; do
  port="${svc%%:*}"
  name="${svc##*:}"
  status=$(curl -s "http://localhost:$port/actuator/health" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','?'))" 2>/dev/null)
  if [ "$status" = "UP" ]; then
    pass "auditx-$name is UP"
  else
    fail "auditx-$name is NOT UP (got: $status)"
  fi
done

# ── MongoDB checks ────────────────────────────────────────────────────────────
section "MongoDB — Pipeline Data"

AUDIT_COUNT=$(docker exec "$MONGO_CONTAINER" mongosh auditx --quiet --eval \
  "db.audit_events.countDocuments({tenantId:'tenant-demo'})" 2>/dev/null | tail -1)
RAW_COUNT=$(docker exec "$MONGO_CONTAINER" mongosh auditx --quiet --eval \
  "db.raw_logs.countDocuments({tenantId:'tenant-demo'})" 2>/dev/null | tail -1)
ALERT_COUNT=$(docker exec "$MONGO_CONTAINER" mongosh auditx --quiet --eval \
  "db.alerts.countDocuments({tenantId:'tenant-demo'})" 2>/dev/null | tail -1)

[ "$AUDIT_COUNT" -gt 0 ] 2>/dev/null && pass "audit_events: $AUDIT_COUNT documents" || fail "audit_events: 0 documents (parser may not have run yet)"
[ "$RAW_COUNT" -gt 0 ] 2>/dev/null && pass "raw_logs (failed parses): $RAW_COUNT documents" || fail "raw_logs: 0 documents"
[ "$ALERT_COUNT" -gt 0 ] 2>/dev/null && pass "alerts: $ALERT_COUNT documents" || fail "alerts: 0 documents (risk engine may not have triggered)"

echo ""
echo "  Sample audit_events:"
docker exec "$MONGO_CONTAINER" mongosh auditx --quiet --eval \
  "db.audit_events.find({tenantId:'tenant-demo'},{eventId:1,userId:1,action:1,outcome:1,riskScore:1,_id:0}).limit(5).forEach(d=>print('  ',JSON.stringify(d)))" 2>/dev/null

echo ""
echo "  Alerts:"
docker exec "$MONGO_CONTAINER" mongosh auditx --quiet --eval \
  "db.alerts.find({tenantId:'tenant-demo'},{alertId:1,riskScore:1,status:1,ruleMatches:1,_id:0}).forEach(d=>print('  ',JSON.stringify(d)))" 2>/dev/null

# ── Elasticsearch check ───────────────────────────────────────────────────────
section "Elasticsearch — Log Index"
ES_COUNT=$(curl -s "$ES_URL/auditx-logs-*/_count" | python3 -c "import sys,json; print(json.load(sys.stdin).get('count',0))" 2>/dev/null)
[ "$ES_COUNT" -gt 0 ] 2>/dev/null && pass "auditx-logs-* index: $ES_COUNT documents" || fail "auditx-logs-* index: 0 documents (Filebeat/Logstash may still be starting)"

# ── Report endpoint check ─────────────────────────────────────────────────────
section "Report Service"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$REPORT_URL/api/reports/generate" \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"tenant-demo","startDate":"2024-01-01","endDate":"2024-12-31","reportType":"FULL"}')
[ "$HTTP_CODE" = "200" ] && pass "POST /api/reports/generate → 200" || fail "POST /api/reports/generate → $HTTP_CODE"

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$REPORT_URL/api/reports/generate" \
  -H "Content-Type: application/json" \
  -d '{"startDate":"2024-01-01","endDate":"2024-12-31","reportType":"FULL"}')
[ "$HTTP_CODE" = "400" ] && pass "Missing tenantId → 400" || fail "Missing tenantId → $HTTP_CODE (expected 400)"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " Kibana: http://localhost:5601"
echo " Discover → index pattern: auditx-logs-*"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
