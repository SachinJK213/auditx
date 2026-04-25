#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_lib.sh"

echo "── Test: Authentication ─────────────────────────────"

# Missing API key → 401
HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -d '{"payload":"test","payloadType":"RAW"}')
assert_equals "401" "$HTTP" "Missing API key should return 401"
echo "  ✓ Missing API key → 401"

# Wrong API key → 401
HTTP2=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: totally-wrong-key-xyz" \
  -d '{"payload":"test","payloadType":"RAW"}')
assert_equals "401" "$HTTP2" "Wrong API key should return 401"
echo "  ✓ Wrong API key → 401"

# Valid API key → 202
HTTP3=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/events/raw \
  -H "Content-Type: application/json" \
  -H "X-API-Key: demo-api-key" \
  -d "{\"payload\":\"timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ) userId=auth-test action=LOGIN sourceIp=1.1.1.1 tenantId=tenant-demo outcome=SUCCESS\",\"payloadType\":\"RAW\",\"idempotencyKey\":\"auth-$(date +%s)\"}")
assert_equals "202" "$HTTP3" "Valid API key should return 202"
echo "  ✓ Valid API key → 202"
