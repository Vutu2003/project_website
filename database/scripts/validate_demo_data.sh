#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
start_local_cluster
validation_log="$(mktemp)"
trap 'rm -f "${validation_log}"' EXIT
psql -X -v ON_ERROR_STOP=1 -d "${DB_NAME}" -f "${DB_ROOT}/database/tests/validate_demo_data.sql" 2>&1 | tee "${validation_log}"
pass_count="$(grep -c 'NOTICE:  PASS' "${validation_log}")"
echo "PHASE 1.3 DATA VALIDATION PASS: ${pass_count} assertions, 0 failures."
