#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_lib.sh"

echo "── Test: Idempotency ────────────────────────────────"

IDEM_KEY="e2e-idem-$(date +%s)"
BODY="{\"payload\":\"timestamp=2024-01-01T00:00:00Z userId=idem-user action=LOGIN sourceIp=1.2.3.4 tenantId=tenant-demo outcome=SUCCESS\",\"payloadType\":\"RAW\",\"idempotencyKey\":\"${IDEM_KEY}\"}"

# First request → 202
RESP1=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" -H "X-API-Key: demo-api-key" -d "$BODY")
BODY1=$(echo "$RESP1" | head -1)
CODE1=$(echo "$RESP1" | tail -1)
EVENT_ID1=$(echo "$BODY1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('eventId',''))" 2>/dev/null)

assert_equals "202" "$CODE1" "First request should be 202"
assert_not_empty "$EVENT_ID1" "First request should return eventId"
echo "  ✓ First request → 202, eventId: ${EVENT_ID1}"

# Second request with same key → 200 with same eventId
RESP2=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" -H "X-API-Key: demo-api-key" -d "$BODY")
BODY2=$(echo "$RESP2" | head -1)
CODE2=$(echo "$RESP2" | tail -1)
EVENT_ID2=$(echo "$BODY2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('eventId',''))" 2>/dev/null)

assert_equals "200" "$CODE2" "Duplicate request should be 200"
assert_equals "$EVENT_ID1" "$EVENT_ID2" "Duplicate should return same eventId"
echo "  ✓ Second request → 200, same eventId"
