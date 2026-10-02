#!/usr/bin/env bash
set +x
set -euo pipefail
PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${PROJECT_ROOT}/scripts/use-toolchain.sh"
baseline=v2
test_env=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --test-env) test_env=true; shift ;;
        --baseline) [[ "${2:-}" == v2 || "${2:-}" == v3 ]] || exit 2; baseline="$2"; shift 2 ;;
        *) echo 'Usage: seed-dev-db.sh [--test-env] [--baseline v2|v3]' >&2; exit 2 ;;
    esac
done
if [[ "${test_env}" == true ]]; then
    [[ "${DB_NAME:-}" =~ ^medical_maintenance_[a-z0-9_]+_test$ ]] || exit 2
else
    source "${PROJECT_ROOT}/.local-postgres/backend-dev.env"
fi
source "${PROJECT_ROOT}/.local-postgres/backend-security.env"
[[ "${DB_HOST}" == 127.0.0.1 && ("${DB_NAME}" == medical_maintenance_v2 || "${DB_NAME}" =~ ^medical_maintenance_[a-z0-9_]+_test$) ]] || exit 1
export PGPASSWORD="${DB_PASSWORD}"
PSQL=(psql -X -At -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}")
[[ "$("${PSQL[@]}" -c 'SELECT count(*) FROM department')" == 0 ]] || { echo 'Canonical seed requires empty business tables; existing data left untouched.' >&2; exit 1; }
expected="$(find "${PROJECT_ROOT}/database/migrations" -maxdepth 1 -name 'V*.sql' | wc -l)"
[[ "$("${PSQL[@]}" -c 'SELECT count(*) FROM flyway_schema_history WHERE success')" == "${expected}" ]] || { echo 'Run all Flyway migrations first.' >&2; exit 1; }
python3 "${PROJECT_ROOT}/database/seeds/local_credentials.py" --baseline "${baseline}"
