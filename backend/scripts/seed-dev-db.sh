#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.local-postgres/backend-dev.env"
[[ -f "${ENV_FILE}" ]] || {
    echo 'Run ./backend/scripts/setup-dev-db.sh first.' >&2
    exit 1
}
source "${ENV_FILE}"
[[ "${DB_NAME}" == 'medical_maintenance_backend_dev' && "${DB_HOST}" == '127.0.0.1' ]] || {
    echo 'Refusing to seed a database other than the isolated backend dev database.' >&2
    exit 1
}
export PGPASSWORD="${DB_PASSWORD}"
PSQL=(psql -X -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}")

applied="$("${PSQL[@]}" -At -c 'SELECT count(*) FROM flyway_schema_history WHERE success = true')"
existing="$("${PSQL[@]}" -At -c 'SELECT count(*) FROM department')"
[[ "${applied}" == '6' && "${existing}" == '0' ]] || {
    echo 'Demo seed requires all six Flyway migrations and empty business tables.' >&2
    exit 1
}

args=()
for seed_file in "${PROJECT_ROOT}"/database/seeds/0[1-6]_*.sql; do
    [[ -f "${seed_file}" ]] || { echo 'Missing Phase 1.3 seed SQL.' >&2; exit 1; }
    args+=(-f "${seed_file}")
done
"${PSQL[@]}" -q --single-transaction -v ON_ERROR_STOP=1 "${args[@]}"
echo 'Loaded Phase 1.3 synthetic demo data separately from Flyway migrations.'
