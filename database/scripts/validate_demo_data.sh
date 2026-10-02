#!/usr/bin/env bash
set +x
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT}/scripts/use-toolchain.sh"
source "${ROOT}/.local-postgres/backend-dev.env"
export PGPASSWORD="${DB_PASSWORD}"
psql -X -q -v ON_ERROR_STOP=1 -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" -f "${ROOT}/database/tests/validate_v2_canonical.sql"
echo 'Canonical V2 data validation PASS (ROLLBACK).'
