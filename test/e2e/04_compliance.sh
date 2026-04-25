#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_lib.sh"

echo "── Test: Compliance records ─────────────────────────"

# Check compliance endpoint is reachable
HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8088/api/compliance/GDPR/summary?tenantId=tenant-demo")
assert_equals "200" "$HTTP" "Compliance summary should return 200"
echo "  ✓ Compliance service reachable"

# Check open records query
HTTP2=$(curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8088/api/compliance/SOC2/records?tenantId=tenant-demo&status=OPEN")
assert_in "200 404" "$HTTP2" "SOC2 records endpoint should return 200 or 404"
echo "  ✓ SOC2 records endpoint: HTTP ${HTTP2}"

# Check policy rules can be listed
HTTP3=$(curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8087/api/policy-rules?tenantId=tenant-demo")
assert_in "200 404" "$HTTP3" "Policy rules endpoint reachable"
echo "  ✓ Policy engine reachable: HTTP ${HTTP3}"
