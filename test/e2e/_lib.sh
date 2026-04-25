#!/usr/bin/env bash
# Shared assertion helpers for all E2E tests

PASS=0; FAIL=0

assert_equals() {
  local expected=$1 actual=$2 msg=$3
  if [[ "$actual" == "$expected" ]]; then
    echo "  ✓ ${msg}"
    ((PASS++))
  else
    echo "  ✗ ${msg} — expected: '${expected}', got: '${actual}'"
    ((FAIL++))
    return 1
  fi
}

assert_not_empty() {
  local value=$1 msg=$2
  if [[ -n "$value" ]]; then
    echo "  ✓ ${msg}"
    ((PASS++))
  else
    echo "  ✗ ${msg} — value was empty"
    ((FAIL++))
    return 1
  fi
}

assert_gt() {
  local actual=$1 threshold=$2 msg=$3
  if (( actual > threshold )); then
    echo "  ✓ ${msg} (${actual} > ${threshold})"
    ((PASS++))
  else
    echo "  ✗ ${msg} — expected > ${threshold}, got ${actual}"
    ((FAIL++))
    return 1
  fi
}

assert_in() {
  local allowed=$1 actual=$2 msg=$3
  if echo "$allowed" | grep -qw "$actual"; then
    echo "  ✓ ${msg} (${actual})"
    ((PASS++))
  else
    echo "  ✗ ${msg} — '${actual}' not in [${allowed}]"
    ((FAIL++))
    return 1
  fi
}
