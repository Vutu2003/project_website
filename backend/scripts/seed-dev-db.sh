#!/usr/bin/env bash
set +x
set -euo pipefail
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${PROJECT_ROOT}/scripts/use-toolchain.sh"
if [[ "${1:-}" == '--test-env' ]]; then
    [[ $# == 1 && "${DB_NAME:-}" =~ ^medical_maintenance_[a-z0-9_]+_test$ ]] || exit 2
else
    [[ $# == 0 ]] || exit 2
    source "${PROJECT_ROOT}/.local-postgres/backend-dev.env"
fi
source "${PROJECT_ROOT}/.local-postgres/backend-security.env"
[[ "${DB_HOST}" == 127.0.0.1 && ("${DB_NAME}" == medical_maintenance_v2 || "${DB_NAME}" =~ ^medical_maintenance_[a-z0-9_]+_test$) ]] || exit 1
export PGPASSWORD="${DB_PASSWORD}"
PSQL=(psql -X -At -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}")
[[ "$("${PSQL[@]}" -c 'SELECT count(*) FROM department')" == 0 ]] || { echo 'Canonical seed requires empty business tables; existing data left untouched.' >&2; exit 1; }
expected="$(find "${PROJECT_ROOT}/database/migrations" -maxdepth 1 -name 'V*.sql' | wc -l)"
[[ "$("${PSQL[@]}" -c 'SELECT count(*) FROM flyway_schema_history WHERE success')" == "${expected}" ]] || { echo 'Run all Flyway migrations first.' >&2; exit 1; }
python3 "${PROJECT_ROOT}/database/seeds/local_credentials.py"
