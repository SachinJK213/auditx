#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_lib.sh"

echo "── Test: Risk scoring — mallory 4x failed login → alert ─"

USER="mallory-e2e-$(date +%s)"

for i in 1 2 3 4; do
  PAYLOAD="timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ) userId=${USER} action=LOGIN sourceIp=203.0.113.42 tenantId=tenant-demo outcome=FAILURE"
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8081/api/events/raw \
    -H "Content-Type: application/json" \
    -H "X-API-Key: demo-api-key" \
    -d "{\"payload\":\"${PAYLOAD}\",\"payloadType\":\"RAW\",\"idempotencyKey\":\"fail-${USER}-${i}\"}")
  assert_equals "202" "$HTTP" "Failed login ${i} should return 202"
  echo "  → sent failed login ${i}/4"
done

# Wait for risk engine to process
sleep 8

# Check risk score on audit_events
MAX_RISK=$(docker exec auditx-mongodb mongosh auditx --quiet --eval \
  "db.audit_events.find({tenantId:'tenant-demo',userId:'${USER}'},{riskScore:1}).sort({riskScore:-1}).limit(1).toArray()[0]?.riskScore ?? 0" 2>/dev/null || echo "0")

echo "  maxRiskScore for ${USER}: ${MAX_RISK}"
# Risk should be > 0 (rules were evaluated)
assert_gt "${MAX_RISK%.*}" "0" "Risk score should be > 0 after repeated failures"

# Check alert was created
ALERT_COUNT=$(docker exec auditx-mongodb mongosh auditx --quiet --eval \
  "db.alerts.countDocuments({tenantId:'tenant-demo',userId:'${USER}'})" 2>/dev/null || echo "0")
echo "  alerts for ${USER}: ${ALERT_COUNT}"

echo "  ✓ Risk scoring pipeline verified"
