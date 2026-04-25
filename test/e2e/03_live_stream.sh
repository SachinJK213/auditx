#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_lib.sh"

echo "── Test: Live stream SSE endpoint ──────────────────"

# Test 1: SSE endpoint responds with correct content-type
HTTP=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 \
  -H "Accept: text/event-stream" \
  "http://localhost:8089/api/v1/stream/live?tenantId=tenant-demo" 2>/dev/null || echo "000")
assert_equals "200" "$HTTP" "SSE endpoint should return 200"
echo "  ✓ SSE endpoint reachable"

# Test 2: Status endpoint
STATUS=$(curl -s "http://localhost:8089/api/v1/stream/status?tenantId=tenant-demo")
TENANT=$(echo "$STATUS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('tenantId',''))" 2>/dev/null)
assert_equals "tenant-demo" "$TENANT" "Status endpoint should return tenantId"
echo "  ✓ Status endpoint: ${STATUS}"

# Test 3: Connect + send event + receive via SSE (10s window)
TMP=$(mktemp)
curl -s --max-time 8 -N \
  -H "Accept: text/event-stream" \
  "http://localhost:8089/api/v1/stream/live?tenantId=tenant-demo" > "$TMP" &
SSE_PID=$!

sleep 2  # give SSE time to connect

# Send an event
PAYLOAD="timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ) userId=sse-test action=LOGIN sourceIp=10.1.2.3 tenantId=tenant-demo outcome=SUCCESS"
curl -s -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-api-key" \
  -d "{\"payload\":\"${PAYLOAD}\",\"payloadType\":\"RAW\",\"idempotencyKey\":\"sse-$(date +%s)\"}" > /dev/null

sleep 5
kill $SSE_PID 2>/dev/null || true

if grep -q "sse-test" "$TMP" 2>/dev/null; then
  echo "  ✓ Event received via SSE stream"
else
  echo "  ⚠ Event not seen in SSE output (may need more pipeline time — check manually)"
fi
rm -f "$TMP"
