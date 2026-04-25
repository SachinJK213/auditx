#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "╔══════════════════════════════════════════════╗"
echo "║       AuditX E2E Regression Suite           ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

TOTAL_PASS=0; TOTAL_FAIL=0; SUITE_FAIL=0

run_suite() {
  local script=$1
  echo "▶ Running: ${script}"
  if bash "$script"; then
    echo ""
  else
    echo "  ← SUITE FAILED"
    echo ""
    ((SUITE_FAIL++))
  fi
}

run_suite 00_health_check.sh
run_suite 01_ingest_and_verify.sh
run_suite 02_risk_scoring.sh
run_suite 03_live_stream.sh
run_suite 04_compliance.sh
run_suite 05_report.sh
run_suite 06_idempotency.sh
run_suite 07_auth.sh
run_suite 08_tenant_isolation.sh

echo "╔══════════════════════════════════════════════╗"
if [[ $SUITE_FAIL -eq 0 ]]; then
  echo "║  ALL SUITES PASSED ✓                        ║"
else
  echo "║  ${SUITE_FAIL} SUITE(S) FAILED ✗                      ║"
fi
echo "╚══════════════════════════════════════════════╝"

[[ $SUITE_FAIL -eq 0 ]] && exit 0 || exit 1
