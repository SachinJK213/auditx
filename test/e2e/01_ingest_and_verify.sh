#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_lib.sh"

IDEM_KEY="e2e-ingest-$(date +%s)"
PAYLOAD="timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ) userId=e2e-user action=LOGIN sourceIp=10.0.0.1 tenantId=tenant-demo outcome=SUCCESS"

echo "── Test: Ingest event and verify in MongoDB ─────────"

# POST event
RESPONSE=$(curl -s -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-api-key" \
  -d "{\"payload\":\"${PAYLOAD}\",\"payloadType\":\"RAW\",\"idempotencyKey\":\"${IDEM_KEY}\"}")

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-api-key" \
  -d "{\"payload\":\"${PAYLOAD}\",\"payloadType\":\"RAW\",\"idempotencyKey\":\"${IDEM_KEY}-check\"}")

assert_equals "202" "$HTTP_STATUS" "POST /api/events/raw should return 202"

EVENT_ID=$(echo "$RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('eventId',''))" 2>/dev/null)
assert_not_empty "$EVENT_ID" "Response should contain eventId"
echo "  eventId: ${EVENT_ID}"

# Wait for Kafka pipeline
sleep 5

# Verify in MongoDB
DOC_COUNT=$(docker exec auditx-mongodb mongosh auditx --quiet --eval \
  "db.audit_events.countDocuments({tenantId:'tenant-demo',userId:'e2e-user'})" 2>/dev/null || echo "0")
assert_gt "$DOC_COUNT" "0" "Event should be stored in audit_events"

echo "  ✓ Event ingested and persisted"
