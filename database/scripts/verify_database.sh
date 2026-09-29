#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
start_local_cluster
verify_log="$(mktemp)"
trap 'rm -f "${verify_log}"' EXIT
psql -X -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${DB_ROOT}/database/tests/verify.sql" 2>&1 | tee "${verify_log}"
DB_NAME="${DB_NAME}" python3 "${DB_ROOT}/database/tests/audit_schema.py"
pass_count="$(grep -c 'NOTICE:  PASS' "${verify_log}")"
negative_count="$(grep -c '\[rejected:' "${verify_log}")"
echo "TEST SUMMARY PASS: ${pass_count} assertions, ${negative_count} expected rejections, 0 failures."
