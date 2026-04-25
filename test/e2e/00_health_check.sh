#!/usr/bin/env bash
set -euo pipefail

PASS=0; FAIL=0
check() {
  local name=$1 port=$2
  local status
  status=$(curl -s --max-time 5 "http://localhost:${port}/actuator/health" \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','?'))" 2>/dev/null || echo "UNREACHABLE")
  if [[ "$status" == "UP" ]]; then
    echo "  ✓ ${name} (port ${port})"
    ((PASS++))
  else
    echo "  ✗ ${name} (port ${port}) — got: ${status}"
    ((FAIL++))
  fi
}

echo "── Service Health ──────────────────────────────────"
check "auditx-ingestion"     8081
check "auditx-parser"        8082
check "auditx-risk-engine"   8083
check "auditx-llm"           8084
check "auditx-alert"         8085
check "auditx-report"        8086
check "auditx-policy-engine" 8087
check "auditx-compliance"    8088
check "auditx-live-stream"   8089

echo ""
echo "── Result: ${PASS} passed, ${FAIL} failed ──────────"
[[ $FAIL -eq 0 ]] && exit 0 || exit 1
