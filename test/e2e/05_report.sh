#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_lib.sh"

echo "── Test: Report generation ──────────────────────────"

# Valid report request
HTTP=$(curl -s -o /tmp/e2e-report.html -w "%{http_code}" -X POST \
  http://localhost:8086/api/reports/generate \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"tenant-demo","startDate":"2024-01-01","endDate":"2099-12-31","reportType":"FULL"}')
assert_equals "200" "$HTTP" "Report generation should return 200"

SIZE=$(wc -c < /tmp/e2e-report.html)
assert_gt "$SIZE" "100" "Report should not be empty"
echo "  ✓ Report generated (${SIZE} bytes)"

# Missing tenantId → 400
HTTP2=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  http://localhost:8086/api/reports/generate \
  -H "Content-Type: application/json" \
  -d '{"startDate":"2024-01-01","endDate":"2024-12-31","reportType":"FULL"}')
assert_equals "400" "$HTTP2" "Missing tenantId should return 400"
echo "  ✓ Missing tenantId → 400"

rm -f /tmp/e2e-report.html
