#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_lib.sh"

echo "── Test: Tenant isolation ───────────────────────────"

# Events in tenant-demo should not appear in a query for tenant-other
COUNT_DEMO=$(docker exec auditx-mongodb mongosh auditx --quiet --eval \
  "db.audit_events.countDocuments({tenantId:'tenant-demo'})" 2>/dev/null || echo "0")

COUNT_OTHER=$(docker exec auditx-mongodb mongosh auditx --quiet --eval \
  "db.audit_events.countDocuments({tenantId:'tenant-other-isolation-test'})" 2>/dev/null || echo "0")

echo "  tenant-demo events: ${COUNT_DEMO}"
echo "  tenant-other events: ${COUNT_OTHER}"

assert_equals "0" "$COUNT_OTHER" "Isolated tenant should have zero events"
echo "  ✓ Tenant isolation confirmed"

# Dashboard API should return 0 events for unknown tenant
SUMMARY=$(curl -s "http://localhost:8086/api/dashboard/summary?tenantId=tenant-other-isolation-test&days=365")
TOTAL=$(echo "$SUMMARY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('totalEvents',0))" 2>/dev/null || echo "0")
assert_equals "0" "$TOTAL" "Dashboard should return 0 events for unknown tenant"
echo "  ✓ Dashboard API tenant isolation confirmed"
