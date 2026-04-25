#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# AUDITX Demo Seed Script
# Seeds MongoDB with a tenant + risk rules, then fires a burst of events
# through the ingestion API so the full pipeline runs end-to-end.
# ─────────────────────────────────────────────────────────────────────────────

INGESTION_URL="${INGESTION_URL:-http://localhost:8081}"
MONGO_CONTAINER="${MONGO_CONTAINER:-auditx-mongodb}"

# SHA-256 of "demo-api-key"
API_KEY="demo-api-key"
API_KEY_HASH="b94f6f125c79e3a5ffaa826f584c10d52ada669e6762051b826b55776d05a15a"
TENANT_ID="tenant-demo"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " AUDITX Demo Seed"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── Step 1: Seed tenant ───────────────────────────────────────────────────────
echo ""
echo "[1/4] Seeding tenant into MongoDB..."
docker exec "$MONGO_CONTAINER" mongosh auditx --quiet --eval "
  db.tenants.deleteMany({ tenantId: '$TENANT_ID' });
  db.tenants.insertOne({
    tenantId:       '$TENANT_ID',
    apiKeyHash:     '$API_KEY_HASH',
    alertThreshold: 60,
    webhookUrl:     'http://localhost:9999/webhook',
    alertEmail:     'security@demo.com',
    webhookEnabled: false,
    emailEnabled:   true
  });
  print('Tenant inserted: $TENANT_ID');
"

# ── Step 2: Seed risk rules ───────────────────────────────────────────────────
echo ""
echo "[2/4] Seeding risk rules..."
docker exec "$MONGO_CONTAINER" mongosh auditx --quiet --eval "
  db.risk_rules.deleteMany({ tenantId: '$TENANT_ID' });
  db.risk_rules.insertMany([
    {
      tenantId:      '$TENANT_ID',
      ruleName:      'FAILED_LOGIN_BURST',
      ruleType:      'FAILED_LOGIN_THRESHOLD',
      weight:        50.0,
      active:        true,
      threshold:     3,
      windowMinutes: 10
    },
    {
      tenantId:      '$TENANT_ID',
      ruleName:      'GEO_ANOMALY_DETECTION',
      ruleType:      'GEO_ANOMALY',
      weight:        40.0,
      active:        true,
      windowMinutes: 1440
    },
    {
      tenantId:      '$TENANT_ID',
      ruleName:      'SUSPICIOUS_ACTION',
      ruleType:      'CUSTOM',
      weight:        20.0,
      active:        true
    }
  ]);
  print('Risk rules inserted: 3');
"

# ── Step 3: Wait for ingestion to be ready ────────────────────────────────────
echo ""
echo "[3/4] Waiting for ingestion service..."
until curl -s "$INGESTION_URL/actuator/health" | grep -q '"status":"UP"'; do
  echo "  ... not ready yet, retrying in 3s"
  sleep 3
done
echo "  Ingestion service is UP"

# ── Step 4: Fire demo events ──────────────────────────────────────────────────
echo ""
echo "[4/4] Firing demo events..."

post_event() {
  local payload="$1"
  local key="$2"
  curl -s -X POST "$INGESTION_URL/api/events/raw" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -d "{
      \"payload\": \"$payload\",
      \"payloadType\": \"RAW\",
      \"idempotencyKey\": \"$key\"
    }" | python3 -c "import sys,json; d=json.load(sys.stdin); print('  eventId:', d.get('eventId','?'))" 2>/dev/null \
      || echo "  (sent)"
}

echo ""
echo "  → Normal login (alice, SUCCESS)"
post_event "timestamp=2024-06-01T08:00:00Z userId=alice action=LOGIN sourceIp=192.168.1.10 tenantId=$TENANT_ID outcome=SUCCESS" "demo-001"

echo "  → Normal login (bob, SUCCESS)"
post_event "timestamp=2024-06-01T08:01:00Z userId=bob action=LOGIN sourceIp=10.0.0.5 tenantId=$TENANT_ID outcome=SUCCESS" "demo-002"

echo "  → Failed login #1 (mallory)"
post_event "timestamp=2024-06-01T08:05:00Z userId=mallory action=LOGIN sourceIp=203.0.113.42 tenantId=$TENANT_ID outcome=FAILURE" "demo-003"

echo "  → Failed login #2 (mallory)"
post_event "timestamp=2024-06-01T08:05:30Z userId=mallory action=LOGIN sourceIp=203.0.113.42 tenantId=$TENANT_ID outcome=FAILURE" "demo-004"

echo "  → Failed login #3 (mallory) — threshold breach triggers alert"
post_event "timestamp=2024-06-01T08:06:00Z userId=mallory action=LOGIN sourceIp=203.0.113.42 tenantId=$TENANT_ID outcome=FAILURE" "demo-005"

echo "  → Failed login #4 (mallory)"
post_event "timestamp=2024-06-01T08:06:30Z userId=mallory action=LOGIN sourceIp=203.0.113.42 tenantId=$TENANT_ID outcome=FAILURE" "demo-006"

echo "  → Geo anomaly — alice logs in from unusual region"
post_event "timestamp=2024-06-01T09:00:00Z userId=alice action=LOGIN sourceIp=45.33.32.156 tenantId=$TENANT_ID outcome=SUCCESS" "demo-007"

echo "  → Admin action (bob)"
post_event "timestamp=2024-06-01T09:15:00Z userId=bob action=DELETE_USER sourceIp=10.0.0.5 tenantId=$TENANT_ID outcome=SUCCESS" "demo-008"

echo "  → Malformed log (will land in raw_logs as FAILED parse)"
post_event "this is not a structured log line at all" "demo-009"

echo "  → Idempotency test (same key as demo-001 — should return 200)"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$INGESTION_URL/api/events/raw" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{"payload":"...","payloadType":"RAW","idempotencyKey":"demo-001"}')
echo "  Duplicate event HTTP status: $STATUS (expected 200)"

echo "  → Missing API key (should return 401)"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$INGESTION_URL/api/events/raw" \
  -H "Content-Type: application/json" \
  -d '{"payload":"test","payloadType":"RAW"}')
echo "  No API key HTTP status: $STATUS (expected 401)"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " Seed complete. Wait ~5s for the pipeline to process, then:"
echo ""
echo "  Kibana:      http://localhost:5601"
echo "  Discover:    http://localhost:5601/app/discover"
echo "  MongoDB:     docker exec -it auditx-mongodb mongosh auditx"
echo ""
echo " Useful Kibana searches:"
echo "   level:ERROR"
echo "   tenantId:\"$TENANT_ID\""
echo "   service:\"auditx-parser\" AND log_message:*FAILED*"
echo "   service:\"auditx-risk-engine\" AND log_message:*Alert*"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
