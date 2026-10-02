#!/usr/bin/env bash
set +x
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT}/scripts/use-toolchain.sh"
source "${ROOT}/.local-postgres/backend-dev.env"
export PGPASSWORD="${DB_PASSWORD}" PGHOST="${DB_HOST}" PGPORT="${DB_PORT}" PGUSER="${DB_USERNAME}"
python3 "${ROOT}/database/tests/audit_schema.py"
